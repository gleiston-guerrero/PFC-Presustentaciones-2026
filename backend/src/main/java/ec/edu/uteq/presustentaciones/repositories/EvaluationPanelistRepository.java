package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.EvaluationPanelist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EvaluationPanelistRepository extends JpaRepository<EvaluationPanelist, Long> {
    
    Optional<EvaluationPanelist> findBySubmissionIdAndPanelistId(Long submissionId, Long panelistId);
    
    List<EvaluationPanelist> findBySubmissionId(Long submissionId);
    
    boolean existsBySubmissionIdAndPanelistId(Long submissionId, Long panelistId);
}
