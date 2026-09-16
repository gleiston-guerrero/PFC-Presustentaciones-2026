package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.EvaluationFinal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface EvaluationFinalRepository extends JpaRepository<EvaluationFinal, Long> {
    Optional<EvaluationFinal> findBySubmissionId(Long submissionId);

    @Query("SELECT ef FROM EvaluationFinal ef WHERE ef.submission.student.id = :studentId")
    List<EvaluationFinal> findByStudentId(@Param("studentId") Long studentId);

    @Query("SELECT ef FROM EvaluationFinal ef WHERE ef.submission.student.appUser.id = :appUserId")
    List<EvaluationFinal> findByAppUserId(@Param("appUserId") Long appUserId);

    @Query(value = "SELECT * FROM presus.sp_calcular_promedio_evaluacion(:submissionId)", nativeQuery = true)
    List<Object[]> calculatePromedioEvaluationSp(@Param("submissionId") Long submissionId);

    @Query("SELECT ef FROM EvaluationFinal ef " +
           "JOIN FETCH ef.submission s " +
           "JOIN FETCH s.student e " +
           "JOIN FETCH e.appUser u " +
           "LEFT JOIN FETCH ef.resultado r")
    List<EvaluationFinal> findAllWithRelationships();
}
