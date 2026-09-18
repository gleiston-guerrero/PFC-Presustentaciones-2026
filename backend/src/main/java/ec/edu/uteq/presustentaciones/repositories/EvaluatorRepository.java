package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.Evaluator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EvaluatorRepository extends JpaRepository<Evaluator, Long> {
    /**
     * Busca el/los registro(s) con submission id.
     * @param submissionId submissionId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<Evaluator> findBySubmissionId(Long submissionId);
    /**
     * Busca el/los registro(s) con submission id y teacher id y tipo evaluator codigo.
     * @param submissionId submissionId
     * @param teacherId teacherId
     * @param kindEvaluatorCode kindEvaluatorCode
     * @return el registro si existe, vacío si no
     */
    Optional<Evaluator> findBySubmissionIdAndTeacherIdAndKindEvaluatorCode(Long submissionId, Long teacherId, String kindEvaluatorCode);
}
