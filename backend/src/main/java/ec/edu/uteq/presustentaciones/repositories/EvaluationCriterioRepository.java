package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.EvaluationCriterio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EvaluationCriterioRepository extends JpaRepository<EvaluationCriterio, Long> {

    /**
     * Busca el/los registro(s) con submission id.
     * @param submissionId submissionId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<EvaluationCriterio> findBySubmissionId(Long submissionId);

    /**
     * Busca el/los registro(s) con submission id y evaluator id.
     * @param submissionId submissionId
     * @param evaluatorId evaluatorId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<EvaluationCriterio> findBySubmissionIdAndEvaluatorId(Long submissionId, Long evaluatorId);

    /**
     * Indica si existe algún registro con submission id y evaluator id.
     * @param submissionId submissionId
     * @param evaluatorId evaluatorId
     * @return true si se cumple la condición, false si no
     */
    boolean existsBySubmissionIdAndEvaluatorId(Long submissionId, Long evaluatorId);

    /**
     * Elimina los registros con submission id y evaluator id.
     * @param submissionId submissionId
     * @param evaluatorId evaluatorId
     */
    void deleteBySubmissionIdAndEvaluatorId(Long submissionId, Long evaluatorId);

    /** Promedio de notas de todos los panelists para una submission por criterio */
    @Query("SELECT ec.criterio.id, AVG(ec.notaObtenida) FROM EvaluationCriterio ec " +
           "WHERE ec.submission.id = :submissionId GROUP BY ec.criterio.id")
    /**
     * Promedios por criterio.
     * @param submissionId submissionId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<Object[]> promediosPorCriterio(@Param("submissionId") Long submissionId);

    /** Nota total promedio del tribunal: promedio de (suma por panelist) usando dos pasos en Java */
    @Query("SELECT ec.evaluator.id, SUM(ec.notaObtenida) " +
           "FROM EvaluationCriterio ec " +
           "WHERE ec.submission.id = :submissionId " +
           "GROUP BY ec.evaluator.id")
    /**
     * Suma por evaluator.
     * @param submissionId submissionId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<Object[]> sumaPorEvaluator(@Param("submissionId") Long submissionId);
}
