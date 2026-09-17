package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.Proposal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ProposalRepository extends JpaRepository<Proposal, Long> {
    /**
     * Busca el/los registro(s) con submission id.
     * @param submissionId submissionId
     * @return el registro si existe, vacío si no
     */
    Optional<Proposal> findBySubmissionId(Long submissionId);
}
