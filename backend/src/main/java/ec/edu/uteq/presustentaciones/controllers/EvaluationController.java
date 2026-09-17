package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.entities.EvaluationFinal;
import ec.edu.uteq.presustentaciones.services.EvaluationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/evaluaciones")
public class EvaluationController {

    private final EvaluationService evaluationService;

    /**
     * Construye EvaluationController, inyectando evaluationService.
     * @param evaluationService evaluationService
     */
    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    /**
     * RF-09: Registra la evaluación final con ponderación configurable entre la nota del
     * instructor y la del tribunal.
     *
     * @param submissionId    submission de pre-sustentación que se está calificando
     * @param rubricId      rúbrica con la que se evaluó
     * @param notaInstructor nota del teacher de Titulación (pesa 60 % por defecto)
     * @param notaPanelist     nota promedio del tribunal (pesa 40 % por defecto)
     * @param observaciones  comentario del evaluator, se persiste junto con la nota
     * @param pesoInstructor peso de la nota del instructor; junto con {@code pesoPanelist} debe sumar 100
     * @param pesoPanelist     peso de la nota del tribunal
     * @return 200 con la {@link EvaluationFinal} persistida, o 400 con {@code {"error": ...}}
     *         si el servicio rechaza los pesos o el estado de la submission
     */
    @PostMapping("/evaluar-ponderado")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'EVALUACION_CALIFICAR')")
    public ResponseEntity<?> evaluarPonderado(
            @RequestParam(name = "solicitudId") Long submissionId,
            @RequestParam(name = "rubricaId") Long rubricId,
            @RequestParam Double notaInstructor,
            @RequestParam(name = "notaJurado") Double notaPanelist,
            @RequestParam String observaciones,
            @RequestParam(name = "pesoInstructor", defaultValue = "60.0") Double pesoInstructor,
            @RequestParam(name = "pesoJurado", defaultValue = "40.0") Double pesoPanelist) {
        try {
            EvaluationFinal e = evaluationService.evaluarSubmission(
                    submissionId, rubricId,
                    notaInstructor, notaPanelist,
                    observaciones, pesoInstructor, pesoPanelist);
            return ResponseEntity.ok(e);
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * Endpoint legado: recibe la nota final ya calculada por el cliente, sin ponderar.
     * Se conserva por compatibilidad con la versión anterior del frontend.
     *
     * @param submissionId   submission que se califica
     * @param rubricId     rúbrica utilizada
     * @param notaFinal     nota final ya calculada
     * @param observaciones comentario del evaluator
     * @return la {@link EvaluationFinal} persistida
     */
    @PostMapping("/evaluar")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'EVALUACION_CALIFICAR')")
    public EvaluationFinal evaluar(@RequestParam(name = "solicitudId") Long submissionId,
                              @RequestParam(name = "rubricaId") Long rubricId,
                              @RequestParam Double notaFinal,
                              @RequestParam String observaciones) {
        return evaluationService.evaluarSubmission(submissionId, rubricId, notaFinal, observaciones);
    }

    /**
     * Lista paginada de todas las evaluations finales registradas.
     *
     * @param pageable página y tamaño solicitados por el cliente
     * @return 200 con la página de evaluations
     */
    @GetMapping
    @PreAuthorize("@permissionService.tienePermission(authentication, 'EVALUACION_CALIFICAR')")
    public ResponseEntity<Page<EvaluationFinal>> list(Pageable pageable) {
        return ResponseEntity.ok(evaluationService.listEvaluations(pageable));
    }

    /**
     * Evaluations de un student concreto.
     *
     * @param studentId identificador del perfil de student (no del appUser)
     * @return lista de evaluations, vacía si el student aún no fue evaluado
     */
    @GetMapping("/estudiante/{studentId}")
    @PreAuthorize("isAuthenticated()")
    public List<EvaluationFinal> listPorStudent(@PathVariable Long studentId) {
        return evaluationService.listPorStudent(studentId);
    }

    /**
     * Evaluations asociadas a un appUser, resolviendo internamente su perfil de student.
     *
     * @param appUserId identificador del appUser autenticable
     * @return lista de evaluations, vacía si el appUser no tiene perfil de student evaluado
     */
    @GetMapping("/usuario/{appUserId}")
    @PreAuthorize("isAuthenticated()")
    public List<EvaluationFinal> listPorAppUser(@PathVariable Long appUserId) {
        return evaluationService.listPorAppUser(appUserId);
    }

    /**
     * Evaluación final de una submission concreta.
     *
     * @param submissionId submission consultada
     * @return 200 con la evaluación, o 404 si la submission todavía no fue evaluada
     */
    @GetMapping("/solicitud/{submissionId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<EvaluationFinal> porSubmission(@PathVariable Long submissionId) {
        return evaluationService.searchPorSubmission(submissionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * RF-09 (SP): Calcula la nota ponderada final vía stored procedure
     * presus.sp_calculate_promedio_evaluation(p_submission_id).
     * Flujo: POST → EvaluationController → EvaluationService → EvaluationFinalRepository → SP → PostgreSQL
     *
     * @param submissionId submission cuyo promedio se recalcula en la base de datos
     * @return 200 con el mapa {@code {submissionId, notaFinal, estadoResultado}} que devuelve
     *         el procedimiento, o 400 con {@code {"error": ...}} si el procedimiento no
     *         encuentra evaluations por criterio para esa submission
     */
    @PostMapping("/calcular-promedio/{submissionId}")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'EVALUACION_CALIFICAR')")
    public ResponseEntity<?> calculatePromedio(@PathVariable Long submissionId) {
        try {
            Map<String, Object> resultado = evaluationService.calculatePromedioSP(submissionId);
            return ResponseEntity.ok(resultado);
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }
}
