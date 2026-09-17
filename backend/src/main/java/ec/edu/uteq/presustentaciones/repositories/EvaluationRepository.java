package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.dto.PromedioEvaluationResult;
import ec.edu.uteq.presustentaciones.entities.Evaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {
    @Query("SELECT ev FROM Evaluation ev JOIN ev.submission s JOIN s.student e WHERE e.id = :studentId")
    /**
     * Busca el/los registro(s) con student id.
     * @param studentId studentId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<Evaluation> findByStudentId(@Param("studentId") Long studentId);

    @Query("SELECT ev FROM Evaluation ev JOIN ev.submission s JOIN s.student e JOIN e.appUser u WHERE u.id = :appUserId")
    /**
     * Busca el/los registro(s) con app user id.
     * @param appUserId appUserId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<Evaluation> findByAppUserId(@Param("appUserId") Long appUserId);

    /**
     * Busca el/los registro(s) con submission id.
     * @param submissionId submissionId
     * @return el registro si existe, vacío si no
     */
    Optional<Evaluation> findBySubmissionId(Long submissionId);

    /**
     * Invoca sp_calculate_promedio_evaluation (JPA 2.1 @NamedStoredProcedureQuery declarada
     * en Evaluation.java) -- agrega las notas de evaluations_criterio y persiste
     * nota_final/resultado en esta misma tabla. Fase 3 / Criterio P1.
     */
    @Procedure(name = "Evaluacion.calcularPromedioEvaluacion")
    List<PromedioEvaluationResult> calculatePromedioEvaluation(@Param("p_submission_id") Long submissionId);
}
