package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.HistoryEstadosSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HistoryEstadosSubmissionRepository extends JpaRepository<HistoryEstadosSubmission, Long> {
    List<HistoryEstadosSubmission> findBySubmissionIdOrderByFechaCambioDesc(Long submissionId);
}
