package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.AverageEvaluationResult;
import ec.edu.uteq.presustentaciones.entities.Evaluation;
import ec.edu.uteq.presustentaciones.entities.EvaluationFinal;
import ec.edu.uteq.presustentaciones.entities.Rubric;
import ec.edu.uteq.presustentaciones.entities.Submission;
import ec.edu.uteq.presustentaciones.repositories.EvaluationFinalRepository;
import ec.edu.uteq.presustentaciones.repositories.EvaluationRepository;
import ec.edu.uteq.presustentaciones.repositories.RubricRepository;
import ec.edu.uteq.presustentaciones.repositories.SubmissionRepository;
import ec.edu.uteq.presustentaciones.security.service.SubmissionAccessService;
import ec.edu.uteq.presustentaciones.security.service.CurrentAppUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Implementacion del servicio de evaluation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EvaluationServiceImpl implements EvaluationService {

    private final EvaluationFinalRepository evaluationRepository;
    private final SubmissionRepository submissionRepository;
    private final RubricRepository rubricRepository;
    private final NotificationService notificationService;
    private final ec.edu.uteq.presustentaciones.repositories.StatusSubmissionRepository statusSubmissionRepository;
    private final ec.edu.uteq.presustentaciones.repositories.ResultEvaluationRepository resultEvaluationRepository;
    private final EvaluationRepository evaluationSpRepository;
    private final CurrentAppUserService currentAppUserService;
    private final SubmissionAccessService submissionAccessService;
    private final PermissionService permissionService;

    /** ADMIN o titular de EVALUACION_CALIFICAR (ADMIN/COORDINADOR); el resto solo puede
     * consultar su propia información -- evita que un student o teacher lea las
     * evaluations de otro appUser cambiando el id en la URL (IDOR). */
    private boolean isAdminOrCoordinator() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return isAdmin || permissionService.hasPermission(auth, "EVALUACION_CALIFICAR");
    }

    private void validateAccessOwnOrAdmin(Long appUserIdObjetivo) {
        if (isAdminOrCoordinator()) return;
        Long currentAppUserId = currentAppUserService.appUser().getId();
        if (!currentAppUserId.equals(appUserIdObjetivo)) {
            throw new AccessDeniedException("No tienes permiso para consultar las evaluaciones de otro usuario");
        }
    }

    private void validateAccessStudentOwnOrAdmin(Long studentIdObjetivo) {
        if (isAdminOrCoordinator()) return;
        Long currentStudentId = currentAppUserService.studentIdOrNull();
        if (currentStudentId == null || !currentStudentId.equals(studentIdObjetivo)) {
            throw new AccessDeniedException("No tienes permiso para consultar las evaluaciones de otro estudiante");
        }
    }

    /**
     * Agrega las notas por criterio del tribunal (evaluations_criterio) con la nota del
     * instructor y persiste nota_final/estado_resultado vía sp_calculate_promedio_evaluation
     * (Fase 3 / Criterio P1, categoría "cálculos agregados").
     *
     * @param submissionId id de la submission a calculate
     * @return submissionId, nota final ponderada y estado del resultado ("APROBADO"/"REPROBADO")
     * @throws RuntimeException si la submission no existe o el procedimiento no devuelve fila
     */
    @Override
    @Transactional
    public AverageEvaluationResult calculateAverageSp(Long submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada: " + submissionId));

        // sp_calculate_promedio_evaluation necesita una fila previa en "evaluaciones" (lee su
        // nota_instructor); la crea si no existe, tomando la nota del instructor ya
        // registrada en el flujo ponderado (evaluations_finales) cuando esté disponible.
        Evaluation base = evaluationSpRepository.findBySubmissionId(submissionId)
                .orElseGet(() -> {
                    Double gradeInstructor = evaluationRepository.findBySubmissionId(submissionId)
                            .map(EvaluationFinal::getGradeInstructor)
                            .orElse(null);
                    return evaluationSpRepository.save(Evaluation.builder()
                            .submission(submission)
                            .gradeInstructor(gradeInstructor)
                            .build());
                });

        List<AverageEvaluationResult> result = evaluationSpRepository.calculateAverageEvaluation(submissionId);
        if (result.isEmpty()) {
            throw new RuntimeException("El procedimiento no devolvió resultado para la solicitud " + submissionId);
        }
        return result.get(0);
    }

    /**
     * Registra evaluación con notas separadas de instructor y panelist (RF-09).
     *
     * @param submissionId    id de la submission a evaluar
     * @param rubricId      id de la rúbrica aplicada
     * @param gradeInstructor nota del instructor del curso, entre 0 y 10
     * @param gradePanelist     nota promedio del tribunal, entre 0 y 10
     * @param observations  observaciones opcionales de la evaluación
     * @param pesoInstructor peso del instructor en la ponderación (0-100); {@code null} usa 60
     * @param pesoPanelist     peso del panelist en la ponderación (0-100); {@code null} usa 40
     * @return la evaluación final persistida, con nota final calculada y resultado asignado
     * @throws RuntimeException si la submission o la rúbrica no existen, los pesos no suman
     *                          100, o alguna nota está fuera de 0-10
     */
    @Override
    @Transactional
    public EvaluationFinal evaluateSubmission(Long submissionId, Long rubricId,
                                       Double gradeInstructor, Double gradePanelist,
                                       String observations,
                                       Double pesoInstructor, Double pesoPanelist) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada: " + submissionId));
        Rubric rubric = rubricRepository.findById(rubricId)
                .orElseThrow(() -> new RuntimeException("Rúbrica no encontrada: " + rubricId));

        double sumaPesos = (pesoInstructor != null ? pesoInstructor : 60.0)
                + (pesoPanelist != null ? pesoPanelist : 40.0);
        if (Math.abs(sumaPesos - 100.0) > 0.01) {
            throw new RuntimeException("Los pesos deben sumar 100. Suma actual: " + sumaPesos);
        }

        if (gradeInstructor < 0 || gradeInstructor > 10 || gradePanelist < 0 || gradePanelist > 10) {
            throw new RuntimeException("Las notas deben estar entre 0 y 10.");
        }

        EvaluationFinal e = EvaluationFinal.builder()
                .submission(submission)
                .rubric(rubric)
                .gradeInstructor(gradeInstructor)
                .gradePanelistAverage(gradePanelist)
                .pesoInstructor((pesoInstructor != null ? pesoInstructor : 60.0) / 100.0)
                .pesoPanelist((pesoPanelist != null ? pesoPanelist : 40.0) / 100.0)
                .observations(observations)
                .build();

        e.calculateGradeFinal();
        
        String resCod = e.getGradeFinal() >= 7.0 ? "APROBADO" : "REPROBADO";
        ec.edu.uteq.presustentaciones.entities.ResultEvaluation res = resultEvaluationRepository.findByCode(resCod)
                .orElseGet(() -> resultEvaluationRepository.save(ec.edu.uteq.presustentaciones.entities.ResultEvaluation.builder()
                        .code(resCod).nombre(resCod.substring(0,1) + resCod.substring(1).toLowerCase()).build()));
        
        e.setResult(res);
        e.setCommentPreestablecido(generateCommentByRange(e.getGradeFinal()));
        EvaluationFinal guardada = evaluationRepository.save(e);

        // Change estado a CALIFICADA
        ec.edu.uteq.presustentaciones.entities.StatusSubmission statusCalificada = statusSubmissionRepository.findByCode("CALIFICADA")
                .orElseGet(() -> statusSubmissionRepository.save(ec.edu.uteq.presustentaciones.entities.StatusSubmission.builder()
                        .code("CALIFICADA").nombre("Calificada").build()));
        submission.setStatus(statusCalificada);
        submissionRepository.save(submission);

        notifyGradeFinal(submission, guardada);

        return guardada;
    }

    /**
     * Compatibilidad: evalúa pasando nota final directa (para uso legacy).
     *
     * @param submissionId   id de la submission a evaluar
     * @param rubricId     id de la rúbrica aplicada
     * @param gradeFinal     nota final ya calculada externamente
     * @param observations observaciones opcionales
     * @return la evaluación final persistida
     * @throws RuntimeException si la submission o la rúbrica no existen
     */
    @Override
    @Transactional
    public EvaluationFinal evaluateSubmission(Long submissionId, Long rubricId,
                                       Double gradeFinal, String observations) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));
        Rubric rubric = rubricRepository.findById(rubricId)
                .orElseThrow(() -> new RuntimeException("Rúbrica no encontrada"));

        String resCod = gradeFinal >= 7 ? "APROBADO" : "REPROBADO";
        ec.edu.uteq.presustentaciones.entities.ResultEvaluation res = resultEvaluationRepository.findByCode(resCod)
                .orElseGet(() -> resultEvaluationRepository.save(ec.edu.uteq.presustentaciones.entities.ResultEvaluation.builder()
                        .code(resCod).nombre(resCod.substring(0,1) + resCod.substring(1).toLowerCase()).build()));

        EvaluationFinal e = EvaluationFinal.builder()
                .submission(submission).rubric(rubric)
                .gradeFinal(gradeFinal).observations(observations)
                .pesoInstructor(0.6).pesoPanelist(0.4)
                .result(res)
                .build();
        e.setCommentPreestablecido(generateCommentByRange(gradeFinal));
        EvaluationFinal guardada = evaluationRepository.save(e);

        // Change estado a CALIFICADA
        ec.edu.uteq.presustentaciones.entities.StatusSubmission statusCalificada = statusSubmissionRepository.findByCode("CALIFICADA")
                .orElseGet(() -> statusSubmissionRepository.save(ec.edu.uteq.presustentaciones.entities.StatusSubmission.builder()
                        .code("CALIFICADA").nombre("Calificada").build()));
        submission.setStatus(statusCalificada);
        submissionRepository.save(submission);

        notifyGradeFinal(submission, guardada);

        return guardada;
    }

    /**
     * @param pageable configuración de paginación
     * @return página de todas las evaluations finales del sistema
     */
    @Override
    public Page<EvaluationFinal> listEvaluations(Pageable pageable) {
        return evaluationRepository.findAll(pageable);
    }

    /**
     * @param studentId identificador del estudiante
     * @return las evaluations finales de las submissions de ese student
     */
    @Override
    public List<EvaluationFinal> listByStudent(Long studentId) {
        validateAccessStudentOwnOrAdmin(studentId);
        return evaluationRepository.findByStudentId(studentId);
    }

    /**
     * @param appUserId id del appUser autenticado
     * @return las evaluations finales visibles para ese appUser
     */
    @Override
    public List<EvaluationFinal> listByAppUser(Long appUserId) {
        validateAccessOwnOrAdmin(appUserId);
        return evaluationRepository.findByAppUserId(appUserId);
    }

    /**
     * @param submissionId identificador de la solicitud de pre-sustentación
     * @return la evaluación final de esa submission, si ya fue calificada
     */
    @Override
    public Optional<EvaluationFinal> searchBySubmission(Long submissionId) {
        Optional<EvaluationFinal> evaluation = evaluationRepository.findBySubmissionId(submissionId);
        evaluation.ifPresent(e -> submissionAccessService.validateAccess(e.getSubmission(), "EVALUACION_CALIFICAR"));
        return evaluation;
    }

    /**
     * Variante de {@link #calculateAverageSp} que devuelve el resultado como un mapa
     * genérico en vez de un DTO tipado, para consumo directo desde el controlador.
     *
     * @param submissionId id de la submission a calculate
     * @return mapa con las claves {@code submissionId}, {@code gradeFinal} y
     *         {@code statusResult}; vacío si el procedimiento no devolvió filas
     */
    @Override
    @Transactional
    public Map<String, Object> calculateAverageSP(Long submissionId) {
        List<Object[]> res = evaluationRepository.calculateAverageEvaluationSp(submissionId);
        Map<String, Object> map = new HashMap<>();
        if (!res.isEmpty()) {
            Object[] row = res.get(0);
            map.put("solicitudId", row[0]);
            map.put("notaFinal", row[1]);
            map.put("estadoResultado", row[2]);
        }
        return map;
    }

    /**
     * Deriva un comentario preestablecido a partir del rango de la nota final, para dejar
     * una observación por defecto cuando el evaluator no escribe una propia.
     *
     * @param gradeFinal nota final calculada, o {@code null}
     * @return un comentario según el rango (≤3, ≤6, &gt;6), o cadena vacía si {@code gradeFinal}
     *         es {@code null}
     */
    public String generateCommentByRange(Double gradeFinal) {
        if (gradeFinal == null) return "";
        if (gradeFinal <= 3) {
            return "El trabajo no cumple con los requisitos mínimos esperados. Se evidencian falencias significativas que requieren correcciones sustanciales.";
        } else if (gradeFinal <= 6) {
            return "El trabajo presenta un nivel aceptable pero con aspectos que requieren mejoras o correcciones para alcanzar los estándares esperados.";
        } else {
            return "El trabajo cumple satisfactoriamente con los objetivos y requisitos establecidos, demostrando un desempeño adecuado.";
        }
    }

    // ── Notificación nota final ───────────────────────────────────────────────

    private void notifyGradeFinal(Submission submission, EvaluationFinal evaluation) {
        try {
            Long appUserId = submission.getStudent().getAppUser().getId();
            String titulo  = submission.getTituloTopic();
            Double grade    = evaluation.getGradeFinal();
            
            String resNombre = evaluation.getResult() != null ? evaluation.getResult().getNombre() : "";
            String resCode = evaluation.getResult() != null ? evaluation.getResult().getCode() : "";
            if (resCode.isEmpty()) {
                resCode = grade != null && grade >= 7 ? "APROBADO" : "REPROBADO";
                resNombre = "APROBADO".equals(resCode) ? "Aprobado" : "Reprobado";
            }

            String emoji = "APROBADO".equals(resCode) ? "🎉" : "😔";
            String msg;

            if (grade != null) {
                msg = String.format(
                        "%s Tu pre-sustentación \"%s\" ha sido evaluada. " +
                                "Nota final: %.2f / 10 — Resultado: %s. Tu solicitud ahora está en fase de calificación.",
                        emoji, titulo, grade, resNombre);
            } else {
                msg = String.format(
                        "%s Tu pre-sustentación \"%s\" ha sido evaluada. Resultado: %s. Tu solicitud ahora está en fase de calificación.",
                        emoji, titulo, resNombre);
            }

            if (evaluation.getObservations() != null && !evaluation.getObservations().isBlank()) {
                msg += " Observaciones: " + evaluation.getObservations();
            }

            notificationService.createNotification(appUserId, msg);
        } catch (Exception e) {
            log.warn("No se pudo notificar nota final al estudiante: {}", e.getMessage());
        }
    }
}
