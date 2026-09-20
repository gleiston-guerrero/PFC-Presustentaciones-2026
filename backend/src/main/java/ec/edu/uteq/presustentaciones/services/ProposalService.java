package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.Proposal;
import org.springframework.web.multipart.MultipartFile;
import java.util.Optional;

/**
 * Contrato. Servicio de proposal.
 */
public interface ProposalService {

    /**
     * Sube el PDF del proposal de una submission, calcula y persiste su hash SHA-256, y
     * deja el proposal en estado pendiente de revisión.
     *
     * @param submissionId id de la submission a la que pertenece el proposal
     * @param file     archivo PDF subido por el student
     * @return el proposal creado o actualizado
     * @throws RuntimeException si la submission no existe o el archivo no es un PDF válido
     */
    Proposal sendProposal(Long submissionId, MultipartFile file);

    /**
     * Approve proposal.
     * @param id            id del proposal a approve
     * @param observations observaciones opcionales del revisor
     * @return el proposal actualizado en estado "APROBADO"
     * @throws RuntimeException si el proposal no existe
     */
    Proposal approveProposal(Long id, String observations);

    /**
     * Reject proposal.
     * @param id            id del proposal a reject
     * @param observations motivo del rechazo
     * @return el proposal actualizado en estado "RECHAZADO"
     * @throws RuntimeException si el proposal no existe
     */
    Proposal rejectProposal(Long id, String observations);

    /**
     * Search by submission.
     * @param submissionId identificador de la solicitud de pre-sustentación
     * @return el proposal de esa submission, si ya fue enviado
     */
    Optional<Proposal> searchBySubmission(Long submissionId);

    /** RF-02: Verifica que el archivo en disco coincida con el hash SHA-256 almacenado
     * @param submissionId id de la submission cuyo proposal se va a verify
     * @return {@code true} si el hash SHA-256 del archivo en disco coincide con el
     *         almacenado en base de datos (comparación con {@code MessageDigest.isEqual},
     *         resistente a ataques de timing)
     * @throws RuntimeException si la submission no tiene proposal o el archivo no existe
     *                          en disco
     */
    boolean verifyIntegrity(Long submissionId);
}
