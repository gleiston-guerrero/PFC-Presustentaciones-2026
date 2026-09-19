package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.Tutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Contrato. Repositorio de acceso a datos de tutor.
 */
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
     * @param status status
     * @return la cantidad de registros
     */
    long countByTeacherIdAndStatus(Long teacherId, String status);

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

    /**
     * Obtain estadisticas tutores sp.
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    @Query(value = "SELECT * FROM presus.sp_obtener_estadisticas_tutores()", nativeQuery = true)
    List<Object[]> obtainStatsTutorsSp();

    /**
     * Reportes: cuántas tutorías tiene asignadas cada teacher (GROUP BY en la base).
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    @Query("SELECT t.teacher.id, COUNT(t) FROM Tutor t GROUP BY t.teacher.id")
    List<Object[]> countTutoringsByTeacher();

    /**
     * Indica si existe algún registro con submission id y teacher app user email.
     * @param submissionId submissionId
     * @param email email
     * @return true si se cumple la condición, false si no
     */
    boolean existsBySubmissionIdAndTeacherAppUserEmail(Long submissionId, String email);
}
