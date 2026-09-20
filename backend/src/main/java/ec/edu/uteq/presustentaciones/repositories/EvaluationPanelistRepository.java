package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.EvaluationPanelist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Contrato. Repositorio de acceso a datos de evaluation panelist.
 */
@Repository
public interface EvaluationPanelistRepository extends JpaRepository<EvaluationPanelist, Long> {
    
    /**
     * Busca el/los registro(s) con submission id y panelist id.
     * @param submissionId identificador de la solicitud de pre-sustentación
     * @param panelistId identificador del panelista (miembro del tribunal)
     * @return el registro si existe, vacío si no
     */
    Optional<EvaluationPanelist> findBySubmissionIdAndPanelistId(Long submissionId, Long panelistId);
    
    /**
     * Busca el/los registro(s) con submission id.
     * @param submissionId identificador de la solicitud de pre-sustentación
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<EvaluationPanelist> findBySubmissionId(Long submissionId);
    
    /**
     * Indica si existe algún registro con submission id y panelist id.
     * @param submissionId identificador de la solicitud de pre-sustentación
     * @param panelistId identificador del panelista (miembro del tribunal)
     * @return true si se cumple la condición, false si no
     */
    boolean existsBySubmissionIdAndPanelistId(Long submissionId, Long panelistId);
}
