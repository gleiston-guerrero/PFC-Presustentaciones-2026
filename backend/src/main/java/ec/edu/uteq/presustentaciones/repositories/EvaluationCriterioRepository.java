package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.EvaluationCriterio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EvaluationCriterioRepository extends JpaRepository<EvaluationCriterio, Long> {

    List<EvaluationCriterio> findBySubmissionId(Long submissionId);

    List<EvaluationCriterio> findBySubmissionIdAndEvaluatorId(Long submissionId, Long evaluatorId);

    boolean existsBySubmissionIdAndEvaluatorId(Long submissionId, Long evaluatorId);

    void deleteBySubmissionIdAndEvaluatorId(Long submissionId, Long evaluatorId);

    /** Promedio de notas de todos los panelists para una submission por criterio */
    @Query("SELECT ec.criterio.id, AVG(ec.notaObtenida) FROM EvaluationCriterio ec " +
           "WHERE ec.submission.id = :submissionId GROUP BY ec.criterio.id")
    List<Object[]> promediosPorCriterio(@Param("submissionId") Long submissionId);

    /** Nota total promedio del tribunal: promedio de (suma por panelist) usando dos pasos en Java */
    @Query("SELECT ec.evaluator.id, SUM(ec.notaObtenida) " +
           "FROM EvaluationCriterio ec " +
           "WHERE ec.submission.id = :submissionId " +
           "GROUP BY ec.evaluator.id")
    List<Object[]> sumaPorEvaluator(@Param("submissionId") Long submissionId);
}
