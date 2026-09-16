package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.entities.Proposal;
import ec.edu.uteq.presustentaciones.services.ProposalService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@RestController
@RequestMapping("/api/anteproyectos")
public class ProposalController {

    private final ProposalService proposalService;

    @Value("${app.upload.dir:uploads/anteproyectos}")
    private String uploadDir;

    public ProposalController(ProposalService s) {
        this.proposalService = s;
    }

    /**
     * RF-02: Sube el PDF del proposal de una submission. El servicio calcula y guarda el
     * SHA-256 del archivo, que despues permite verify que no fue alterado en disco.
     *
     * @param submissionId submission a la que pertenece el proposal
     * @param archivo     PDF enviado como multipart
     * @return 200 con el proposal registrado
     */
    @PostMapping(value = "/enviar/{submissionId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Proposal> send(@PathVariable Long submissionId,
            @RequestParam("archivo") MultipartFile archivo) {
        return ResponseEntity.ok(proposalService.sendProposal(submissionId, archivo));
    }

    /**
     * @param submissionId submission consultada
     * @return 200 con el proposal de esa submission, o el error si aun no se subio
     */
    @GetMapping("/solicitud/{submissionId}")
    public ResponseEntity<?> obtainPorSubmission(@PathVariable Long submissionId) {
        return proposalService.searchPorSubmission(submissionId)
                .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Descarga en line el PDF del proposal.
     *
     * @param submissionId submission cuyo proposal se abre
     * @return 200 con el PDF, o el estado de error que devuelva el servicio
     */
    @GetMapping("/ver/{submissionId}")
    public ResponseEntity<Resource> verPdf(@PathVariable Long submissionId) {
        Proposal ap = proposalService.searchPorSubmission(submissionId)
                .orElseThrow(() -> new RuntimeException("Anteproyecto no encontrado"));
        try {
            Path ruta = Paths.get(uploadDir).resolve(ap.getArchivoPdf()).normalize();
            Resource resource = new UrlResource(ruta.toUri());
            if (!resource.exists() || !resource.isReadable())
                return ResponseEntity.notFound().build();
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + ap.getArchivoPdf() + "\"")
                    .body(resource);
        } catch (MalformedURLException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * RF-02: Recalcula el SHA-256 del archivo en disco y lo compara con el registrado al
     * uploadlo, para detectar alteraciones posteriores.
     *
     * @param submissionId submission cuyo proposal se verifica
     * @return 200 con el resultado de la comparacion de hashes
     */
    @GetMapping("/verificar/{submissionId}")
    public ResponseEntity<Map<String, Object>> verify(@PathVariable Long submissionId) {
        try {
            boolean ok = proposalService.verifyIntegridad(submissionId);
            Proposal ap = proposalService.searchPorSubmission(submissionId).orElseThrow();
            return ResponseEntity.ok(Map.of(
                    "solicitudId", submissionId,
                    "integridadOk", ok,
                    "sha256Registrado", ap.getSha256Hash() != null ? ap.getSha256Hash() : "—",
                    "mensaje", ok ? "✓ Archivo íntegro: el hash SHA-256 coincide."
                            : "⚠ Advertencia: el archivo puede haber sido modificado."));
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e; // deja que GlobalExceptionHandler lo traduzca a 403, no a 400
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Aprueba el proposal dejando constancia de las observaciones del revisor.
     *
     * @param id            proposal a approve
     * @param observaciones comentario del revisor
     * @return 200 con el proposal aprobado
     */
    @PostMapping("/aprobar/{id}")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'ANTEPROYECTO_REVISAR')")
    public ResponseEntity<Proposal> approve(@PathVariable Long id, @RequestParam String observaciones) {
        return ResponseEntity.ok(proposalService.approveProposal(id, observaciones));
    }

    /**
     * Rechaza el proposal indicando que debe corregirse.
     *
     * @param id            proposal a reject
     * @param observaciones motivo del rechazo, visible para el student
     * @return 200 con el proposal rechazado
     */
    @PostMapping("/rechazar/{id}")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'ANTEPROYECTO_REVISAR')")
    public ResponseEntity<Proposal> reject(@PathVariable Long id, @RequestParam String observaciones) {
        return ResponseEntity.ok(proposalService.rejectProposal(id, observaciones));
    }
}
