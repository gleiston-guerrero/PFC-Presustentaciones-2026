package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.dto.EvaluationRubricRequest;
import ec.edu.uteq.presustentaciones.dto.EvaluationRubricResponse;
import ec.edu.uteq.presustentaciones.dto.ObservacionesSubmissionDTO;
import ec.edu.uteq.presustentaciones.entities.CriterioRubric;
import ec.edu.uteq.presustentaciones.repositories.CriterioRubricRepository;
import ec.edu.uteq.presustentaciones.services.RubricEvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/rubrica-evaluacion")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class RubricEvaluationController {

    private final RubricEvaluationService service;
    private final CriterioRubricRepository criterioRepo;

    /**
     * RF-07: Un panelist registra su calificacion criterio por criterio segun la scale de la
     * rubric.
     *
     * @param request submission, panelist y scale elegida en cada criterio
     * @return 200 con la evaluation registrada, o 400 con el motivo del rechazo
     */
    @PostMapping("/registrar")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'EVALUACION_RUBRICA_REGISTRAR')")
    public ResponseEntity<?> register(@RequestBody EvaluationRubricRequest request) {
        try {
            EvaluationRubricResponse resp = service.registerEvaluation(request);
            return ResponseEntity.ok(resp);
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e; // deja que GlobalExceptionHandler lo traduzca a 403, no a 400
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Evaluation registrada por un panelist concreto.
     *
     * @param submissionId submission consultada
     * @param panelistId    panelist del que se quiere la evaluation
     * @return 200 con la evaluation, o 400 si no existe
     */
    @GetMapping("/solicitud/{submissionId}/jurado/{panelistId}")
    public ResponseEntity<?> obtainPanelist(
            @PathVariable("submissionId") Long submissionId,
            @PathVariable("panelistId") Long panelistId) {
        try {
            return ResponseEntity.ok(service.obtainEvaluationPanelist(submissionId, panelistId));
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e; // deja que GlobalExceptionHandler lo traduzca a 403, no a 404
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * @param submissionId submission consultada
     * @return evaluations de todos los panelists del tribunal para esa submission
     */
    @GetMapping("/solicitud/{submissionId}")
    public List<EvaluationRubricResponse> obtainSubmission(@PathVariable("submissionId") Long submissionId) {
        return service.obtainEvaluationsSubmission(submissionId);
    }

    /**
     * Nota promedio del tribunal, que es la que entra con peso 40 % en la evaluation final.
     *
     * @param submissionId submission consultada
     * @return 200 siempre: si ya hay evaluations devuelve {@code {nota}}; si todavia no hay
     *         ninguna devuelve {@code {nota: null, mensaje}} en vez de un error, para que el
     *         formulario de evaluation final pueda mostrar el aviso sin tratarlo como fallo
     */
    @GetMapping("/nota-tribunal/{submissionId}")
    public ResponseEntity<?> notaTribunal(@PathVariable("submissionId") Long submissionId) {
        Double nota = service.calculateNotaTribunal(submissionId);
        if (nota == null) {
            return ResponseEntity.ok(Map.of("nota", (Object) null,
                    "mensaje", "No hay evaluaciones registradas aún."));
        }
        return ResponseEntity.ok(Map.of("nota", nota));
    }

    /**
     * @param rubricId rubric consultada
     * @return criterios de esa rubric, para pintar el formulario de calificacion
     */
    @GetMapping("/criterios/{rubricId}")
    public List<CriterioRubric> criteriosPorRubric(@PathVariable("rubricId") Long rubricId) {
        return criterioRepo.findByRubricIdOrderByOrdenAsc(rubricId);
    }

    /**
     * Observaciones de todos los actores sobre una submission (tutor, panelists y coordinador),
     * consolidadas en una sola respuesta.
     *
     * @param submissionId submission consultada
     * @return 200 con las observaciones consolidadas, o 400 si no existe la submission
     */
    @GetMapping("/observaciones/{submissionId}")
    public ResponseEntity<?> obtainObservaciones(@PathVariable("submissionId") Long submissionId) {
        try {
            ObservacionesSubmissionDTO obs = service.obtainObservacionesSubmission(submissionId);
            return ResponseEntity.ok(obs);
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e; // deja que GlobalExceptionHandler lo traduzca a 403, no a 400
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
