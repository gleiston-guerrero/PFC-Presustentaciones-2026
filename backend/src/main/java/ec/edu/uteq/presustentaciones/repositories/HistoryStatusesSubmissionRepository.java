package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.HistoryStatusesSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Contrato. Repositorio de acceso a datos de history statuses submission.
 */
@Repository
public interface HistoryStatusesSubmissionRepository extends JpaRepository<HistoryStatusesSubmission, Long> {
    /**
     * Busca el/los registro(s) con submission id o der by fecha cambio desc.
     * @param submissionId submissionId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<HistoryStatusesSubmission> findBySubmissionIdOrderByDateCambioDesc(Long submissionId);
}
