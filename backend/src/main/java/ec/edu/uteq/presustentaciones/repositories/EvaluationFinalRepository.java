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
    /**
     * Busca el/los registro(s) con submission id.
     * @param submissionId submissionId
     * @return el registro si existe, vacío si no
     */
    Optional<EvaluationFinal> findBySubmissionId(Long submissionId);

    @Query("SELECT ef FROM EvaluationFinal ef WHERE ef.submission.student.id = :studentId")
    /**
     * Busca el/los registro(s) con student id.
     * @param studentId studentId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<EvaluationFinal> findByStudentId(@Param("studentId") Long studentId);

    @Query("SELECT ef FROM EvaluationFinal ef WHERE ef.submission.student.appUser.id = :appUserId")
    /**
     * Busca el/los registro(s) con app user id.
     * @param appUserId appUserId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<EvaluationFinal> findByAppUserId(@Param("appUserId") Long appUserId);

    @Query(value = "SELECT * FROM presus.sp_calcular_promedio_evaluacion(:submissionId)", nativeQuery = true)
    /**
     * Calculate promedio evaluation sp.
     * @param submissionId submissionId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<Object[]> calculateAverageEvaluationSp(@Param("submissionId") Long submissionId);

    @Query("SELECT ef FROM EvaluationFinal ef " +
           "JOIN FETCH ef.submission s " +
           "JOIN FETCH s.student e " +
           "JOIN FETCH e.appUser u " +
           "LEFT JOIN FETCH ef.result r")
    /**
     * Find all with relationships.
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<EvaluationFinal> findAllWithRelationships();
}
