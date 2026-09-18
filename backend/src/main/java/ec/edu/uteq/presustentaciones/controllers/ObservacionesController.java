package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.dto.ObservacionesSubmissionDTO;
import ec.edu.uteq.presustentaciones.services.RubricEvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/observaciones")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ObservacionesController {

    private final RubricEvaluationService rubricEvaluationService;

    /**
     * Observaciones registradas sobre una submission (las que el revisor deja al reject o
     * al pedir correcciones).
     *
     * @param submissionId submission consultada
     * @return 200 con las observaciones de esa submission
     */
    @GetMapping("/solicitud/{submissionId}")
    public ResponseEntity<?> obtainObservaciones(@PathVariable("submissionId") Long submissionId) {
        try {
            ObservacionesSubmissionDTO obs = rubricEvaluationService.obtainObservacionesSubmission(submissionId);
            return ResponseEntity.ok(obs);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
