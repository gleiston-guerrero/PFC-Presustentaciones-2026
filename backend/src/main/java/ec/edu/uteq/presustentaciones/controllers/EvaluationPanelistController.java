package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.dto.EvaluationPanelistDTO;
import ec.edu.uteq.presustentaciones.services.EvaluationPanelistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controlador REST de evaluation panelist.
 */
@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/evaluaciones-jurado")
@RequiredArgsConstructor
public class EvaluationPanelistController {

    private final EvaluationPanelistService service;

    /**
     * Registra o actualiza la calificación que un panelist da a una submission. El panelist se
     * comprueba contra el appUser autenticado: un teacher no puede save notas a nombre de
     * otro member del tribunal.
     *
     * @param request cuerpo con la submission, el panelist y las notas por criterio
     * @return 200 con la evaluación guardada, o 400 con el motivo del rechazo
     */
    @PostMapping("/guardar")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'EVALUACION_RUBRICA_REGISTRAR')")
    public ResponseEntity<?> save(@RequestBody Map<String, Object> request) {
        try {
            Long submissionId = Long.valueOf(request.get("solicitudId").toString());
            Long panelistId = Long.valueOf(request.get("juradoId").toString());
            Double gradePanelist = Double.valueOf(request.get("notaJurado").toString());
            String observations = request.get("observaciones") != null 
                    ? request.get("observaciones").toString() : "";

            EvaluationPanelistDTO dto = service.saveEvaluation(submissionId, panelistId, gradePanelist, observations);
            return ResponseEntity.ok(dto);
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e; // deja que GlobalExceptionHandler lo traduzca a 403, no a 400
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Calificación registrada por un panelist concreto sobre una submission.
     *
     * @param submissionId submission consultada
     * @param panelistId    panelist del que se quiere ver la calificación
     * @return 200 con la evaluación, o 400 si no existe o no hay acceso
     */
    @GetMapping("/{submissionId}/{panelistId}")
    public ResponseEntity<?> obtain(
            @PathVariable("submissionId") Long submissionId,
            @PathVariable("panelistId") Long panelistId) {
        try {
            EvaluationPanelistDTO dto = service.obtainEvaluation(submissionId, panelistId);
            if (dto == null) {
                return ResponseEntity.ok(null);
            }
            return ResponseEntity.ok(dto);
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e; // deja que GlobalExceptionHandler lo traduzca a 403, no a 400
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Calificaciones de todo el tribunal para una submission, usadas para calculate la nota
     * promedio del panelist.
     *
     * @param submissionId submission consultada
     * @return 200 con una entrada por member del tribunal
     */
    @GetMapping("/tribunal/{submissionId}")
    public ResponseEntity<List<EvaluationPanelistDTO>> obtainPanel(@PathVariable("submissionId") Long submissionId) {
        return ResponseEntity.ok(service.obtainPanel(submissionId));
    }
}
