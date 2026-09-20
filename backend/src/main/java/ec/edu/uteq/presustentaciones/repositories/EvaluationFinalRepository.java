package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.EvaluationFinal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Contrato. Repositorio de acceso a datos de evaluation final.
 */
@Repository
public interface EvaluationFinalRepository extends JpaRepository<EvaluationFinal, Long> {
    /**
     * Busca el/los registro(s) con submission id.
     * @param submissionId identificador de la solicitud de pre-sustentación
     * @return el registro si existe, vacío si no
     */
    Optional<EvaluationFinal> findBySubmissionId(Long submissionId);

    /**
     * Busca el/los registro(s) con student id.
     * @param studentId identificador del estudiante
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    @Query("SELECT ef FROM EvaluationFinal ef WHERE ef.submission.student.id = :studentId")
    List<EvaluationFinal> findByStudentId(@Param("studentId") Long studentId);

    /**
     * Busca el/los registro(s) con app user id.
     * @param appUserId identificador del usuario del sistema
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    @Query("SELECT ef FROM EvaluationFinal ef WHERE ef.submission.student.appUser.id = :appUserId")
    List<EvaluationFinal> findByAppUserId(@Param("appUserId") Long appUserId);

    /**
     * Calculate promedio evaluation sp.
     * @param submissionId identificador de la solicitud de pre-sustentación
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    @Query(value = "SELECT * FROM presus.sp_calcular_promedio_evaluacion(:submissionId)", nativeQuery = true)
    List<Object[]> calculateAverageEvaluationSp(@Param("submissionId") Long submissionId);

    /**
     * Find all with relationships.
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    @Query("SELECT ef FROM EvaluationFinal ef " +
           "JOIN FETCH ef.submission s " +
           "JOIN FETCH s.student e " +
           "JOIN FETCH e.appUser u " +
           "LEFT JOIN FETCH ef.result r")
    List<EvaluationFinal> findAllWithRelationships();
}
