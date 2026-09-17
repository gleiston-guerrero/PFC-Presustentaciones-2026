package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.HistoryEstadosSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HistoryEstadosSubmissionRepository extends JpaRepository<HistoryEstadosSubmission, Long> {
    /**
     * Busca el/los registro(s) con submission id o der by fecha cambio desc.
     * @param submissionId submissionId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<HistoryEstadosSubmission> findBySubmissionIdOrderByFechaCambioDesc(Long submissionId);
}
