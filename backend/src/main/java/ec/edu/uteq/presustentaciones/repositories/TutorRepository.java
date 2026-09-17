package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.Tutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TutorRepository extends JpaRepository<Tutor, Long> {
    /**
     * Busca el/los registro(s) con submission id.
     * @param submissionId submissionId
     * @return el registro si existe, vacío si no
     */
    Optional<Tutor> findBySubmissionId(Long submissionId);
    /**
     * Busca el/los registro(s) con teacher id.
     * @param teacherId teacherId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<Tutor> findByTeacherId(Long teacherId);
    /**
     * Cuenta los registros con teacher id y estado.
     * @param teacherId teacherId
     * @param estado estado
     * @return la cantidad de registros
     */
    long countByTeacherIdAndEstado(Long teacherId, String estado);

    /**
     * Busca el/los registro(s) con submission student app user id.
     * @param appUserId appUserId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<Tutor> findBySubmissionStudentAppUserId(Long appUserId);
    /**
     * Busca el/los registro(s) con teacher app user id.
     * @param appUserId appUserId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<Tutor> findByTeacherAppUserId(Long appUserId);

    @Query(value = "SELECT * FROM presus.sp_obtener_estadisticas_tutores()", nativeQuery = true)
    /**
     * Obtain estadisticas tutores sp.
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<Object[]> obtainEstadisticasTutoresSp();

    /** Reportes: cuántas tutorías tiene asignadas cada teacher (GROUP BY en la base). */
    @Query("SELECT t.teacher.id, COUNT(t) FROM Tutor t GROUP BY t.teacher.id")
    List<Object[]> countTutoringsPorTeacher();

    /**
     * Indica si existe algún registro con submission id y teacher app user email.
     * @param submissionId submissionId
     * @param email email
     * @return true si se cumple la condición, false si no
     */
    boolean existsBySubmissionIdAndTeacherAppUserEmail(Long submissionId, String email);
}
