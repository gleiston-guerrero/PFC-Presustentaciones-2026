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
    Optional<Tutor> findBySubmissionId(Long submissionId);
    List<Tutor> findByTeacherId(Long teacherId);
    long countByTeacherIdAndEstado(Long teacherId, String estado);

    List<Tutor> findBySubmissionStudentAppUserId(Long appUserId);
    List<Tutor> findByTeacherAppUserId(Long appUserId);

    @Query(value = "SELECT * FROM presus.sp_obtener_estadisticas_tutores()", nativeQuery = true)
    List<Object[]> obtainEstadisticasTutoresSp();

    /** Reportes: cuántas tutorías tiene asignadas cada teacher (GROUP BY en la base). */
    @Query("SELECT t.teacher.id, COUNT(t) FROM Tutor t GROUP BY t.teacher.id")
    List<Object[]> countTutoringsPorTeacher();

    boolean existsBySubmissionIdAndTeacherAppUserEmail(Long submissionId, String email);
}
