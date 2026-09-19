package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.StatusSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Contrato. Repositorio de acceso a datos de status submission.
 */
@Repository
public interface StatusSubmissionRepository extends JpaRepository<StatusSubmission, Short> {
    /**
     * Busca el/los registro(s) con codigo.
     * @param code code
     * @return el registro si existe, vacío si no
     */
    Optional<StatusSubmission> findByCode(String code);
}
