package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.Evaluator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EvaluatorRepository extends JpaRepository<Evaluator, Long> {
    List<Evaluator> findBySubmissionId(Long submissionId);
    Optional<Evaluator> findBySubmissionIdAndTeacherIdAndTipoEvaluatorCodigo(Long submissionId, Long teacherId, String tipoEvaluatorCodigo);
}
