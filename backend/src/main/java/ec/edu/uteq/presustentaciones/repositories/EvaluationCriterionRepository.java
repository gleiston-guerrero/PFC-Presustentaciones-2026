package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.EvaluationCriterion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Contrato. Repositorio de acceso a datos de evaluation criterion.
 */
public interface EvaluationCriterionRepository extends JpaRepository<EvaluationCriterion, Long> {

    /**
     * Busca el/los registro(s) con submission id.
     * @param submissionId identificador de la solicitud de pre-sustentación
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<EvaluationCriterion> findBySubmissionId(Long submissionId);

    /**
     * Busca el/los registro(s) con submission id y evaluator id.
     * @param submissionId identificador de la solicitud de pre-sustentación
     * @param evaluatorId identificador del evaluador asignado a la solicitud
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<EvaluationCriterion> findBySubmissionIdAndEvaluatorId(Long submissionId, Long evaluatorId);

    /**
     * Indica si existe algún registro con submission id y evaluator id.
     * @param submissionId identificador de la solicitud de pre-sustentación
     * @param evaluatorId identificador del evaluador asignado a la solicitud
     * @return true si se cumple la condición, false si no
     */
    boolean existsBySubmissionIdAndEvaluatorId(Long submissionId, Long evaluatorId);

    /**
     * Elimina los registros con submission id y evaluator id.
     * @param submissionId identificador de la solicitud de pre-sustentación
     * @param evaluatorId identificador del evaluador asignado a la solicitud
     */
    void deleteBySubmissionIdAndEvaluatorId(Long submissionId, Long evaluatorId);

    /**
     * Promedio de notas de todos los panelists para una submission por criterio
     * @param submissionId identificador de la solicitud de pre-sustentación
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    @Query("SELECT ec.criterion.id, AVG(ec.gradeObtenida) FROM EvaluationCriterion ec " +
           "WHERE ec.submission.id = :submissionId GROUP BY ec.criterion.id")
    List<Object[]> promediosByCriterion(@Param("submissionId") Long submissionId);

    /**
     * Nota total promedio del tribunal: promedio de (suma por panelist) usando dos pasos en Java
     * @param submissionId identificador de la solicitud de pre-sustentación
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    @Query("SELECT ec.evaluator.id, SUM(ec.gradeObtenida) " +
           "FROM EvaluationCriterion ec " +
           "WHERE ec.submission.id = :submissionId " +
           "GROUP BY ec.evaluator.id")
    List<Object[]> sumaByEvaluator(@Param("submissionId") Long submissionId);
}
