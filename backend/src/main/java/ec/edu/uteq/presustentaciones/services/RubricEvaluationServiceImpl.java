package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.ScaleCriterionDTO;
import ec.edu.uteq.presustentaciones.dto.EvaluationRubricRequest;
import ec.edu.uteq.presustentaciones.dto.EvaluationRubricResponse;
import ec.edu.uteq.presustentaciones.dto.ObservationsSubmissionDTO;
import ec.edu.uteq.presustentaciones.entities.*;
import ec.edu.uteq.presustentaciones.repositories.*;
import ec.edu.uteq.presustentaciones.security.service.SubmissionAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RubricEvaluationServiceImpl implements RubricEvaluationService {

    private final EvaluationCriterionRepository evalCriterionRepo;
    private final CriterionRubricRepository criterionRepo;
    private final PanelistRepository panelistRepo;
    private final SubmissionRepository submissionRepo;
    private final RubricRepository rubricRepo;
    private final TutorRepository tutorRepo;
    private final EvaluationFinalRepository evaluationFinalRepo;
    private final EvaluationPanelistRepository javaEvaluationPanelistRepo;
    private final EvaluatorRepository evaluatorRepo;
    private final KindEvaluatorRepository kindEvaluatorRepo;
    private final SubmissionAccessService submissionAccessService;
    private final PermissionService permissionService;

    /** Mismo criterio que EvaluationPanelistService.validatePuedeRegister: solo el propio
     * panelist, o ADMIN/COORDINADOR, puede register una evaluación de rúbrica -- evita que
     * un panelist registre scales a nombre de otro (IDOR de escritura). */
    private void validateCanRegister(Panelist panelist) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Usuario no autenticado");
        }
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin || permissionService.hasPermission(auth, "EVALUACION_CALIFICAR")) {
            return;
        }
        if (!permissionService.isOwnTeacher(auth, panelist.getTeacher().getId())) {
            throw new AccessDeniedException("Solo puedes registrar tu propia evaluación como jurado");
        }
    }

    /**
     * El panelist registra sus scales por criterio.
     *
     * @param req calificación por criterio de rúbrica emitida por un panelist
     * @return la evaluación registrada, con la nota calculada para ese panelist
     * @throws RuntimeException si la submission, la rúbrica o el panelist no existen
     */
    @Override
    @Transactional
    public EvaluationRubricResponse registerEvaluation(EvaluationRubricRequest req) {
        Submission submission = submissionRepo.findById(req.getSubmissionId())
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada: " + req.getSubmissionId()));

        Panelist panelist = panelistRepo.findById(req.getPanelistId())
                .orElseThrow(() -> new RuntimeException("Jurado no encontrado: " + req.getPanelistId()));

        if (!panelist.getSubmission().getId().equals(req.getSubmissionId())) {
            throw new RuntimeException("El jurado no pertenece a esta solicitud.");
        }

        validateCanRegister(panelist);

        rubricRepo.findById(req.getRubricId())
                .orElseThrow(() -> new RuntimeException("Rúbrica no encontrada: " + req.getRubricId()));

        List<CriterionRubric> criteria = criterionRepo.findByRubricIdOrderByOrdenAsc(req.getRubricId());
        if (criteria.isEmpty()) {
            throw new RuntimeException("La rúbrica no tiene criterios definidos.");
        }
        if (req.getCriteria() == null || req.getCriteria().size() != criteria.size()) {
            throw new RuntimeException("Debe evaluar todos los " + criteria.size() + " criterios de la rúbrica.");
        }
        for (ScaleCriterionDTO c : req.getCriteria()) {
            if (c.getScale() < 1 || c.getScale() > 100) {
                throw new RuntimeException("Escala inválida: " + c.getScale() + ". Use valores entre 1 y 100.");
            }
        }

        // Search o create el Evaluator correspondiente para este panelist
        Evaluator evaluator = evaluatorRepo.findBySubmissionIdAndTeacherIdAndKindEvaluatorCode(
                req.getSubmissionId(), panelist.getTeacher().getId(), "JURADO")
            .orElseGet(() -> {
                KindEvaluator kind = kindEvaluatorRepo.findByCode("JURADO")
                        .orElseThrow(() -> new RuntimeException("Tipo evaluador JURADO no configurado."));
                Evaluator ev = Evaluator.builder()
                        .submission(submission)
                        .teacher(panelist.getTeacher())
                        .memberPanel(panelist)
                        .kindEvaluator(kind)
                        .peso(1.0)
                        .build();
                return evaluatorRepo.save(ev);
            });

        // Permite re-evaluación: delete la anterior
        evalCriterionRepo.deleteBySubmissionIdAndEvaluatorId(req.getSubmissionId(), evaluator.getId());

        List<EvaluationCriterion> guardadas = new ArrayList<>();
        for (ScaleCriterionDTO cDto : req.getCriteria()) {
            CriterionRubric criterion = criteria.stream()
                    .filter(c -> c.getId().equals(cDto.getCriterionId()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Criterio no encontrado: " + cDto.getCriterionId()));

            double gradeObtenida = Math.round(criterion.getPonderacion() * cDto.getScale() / 100.0 * 100.0) / 100.0;
            String observationAuto = EvaluationCriterion.getObservationByRange(cDto.getScale());

            EvaluationCriterion ec = EvaluationCriterion.builder()
                    .submission(submission)
                    .evaluator(evaluator)
                    .panelist(panelist)
                    .criterion(criterion)
                    .scale(cDto.getScale())
                    .gradeObtenida(gradeObtenida)
                    .observationAuto(observationAuto)
                    .observationManual(cDto.getObservationManual())
                    .observations(cDto.getObservations())
                    .build();
            guardadas.add(evalCriterionRepo.save(ec));
        }

        return buildResponse(panelist, guardadas, req.getSubmissionId(), evaluator.getId());
    }
 
    /**
     * Estado de la evaluación de un panelist para una submission.
     *
     * @param submissionId id de la submission
     * @param panelistId    id del panelist
     * @return la evaluación de ese panelist para esa submission, si ya la registró
     */
    @Override
    public EvaluationRubricResponse obtainEvaluationPanelist(Long submissionId, Long panelistId) {
        Panelist panelist = panelistRepo.findById(panelistId)
                .orElseThrow(() -> new RuntimeException("Jurado no encontrado: " + panelistId));
        submissionAccessService.validateAccess(panelist.getSubmission(), "EVALUACION_CALIFICAR");

        Evaluator evaluator = evaluatorRepo.findBySubmissionIdAndTeacherIdAndKindEvaluatorCode(
                submissionId, panelist.getTeacher().getId(), "JURADO")
                .orElseThrow(() -> new RuntimeException("Evaluador no registrado para el jurado."));

        List<EvaluationCriterion> evals = evalCriterionRepo.findBySubmissionIdAndEvaluatorId(submissionId, evaluator.getId());
        return buildResponse(panelist, evals, submissionId, evaluator.getId());
    }
 
    /**
     * Resumen de todos los panelists para una submission.
     *
     * @param submissionId id de la submission
     * @return las evaluations registradas por cada panelist de esa submission
     */
    @Override
    public List<EvaluationRubricResponse> obtainEvaluationsSubmission(Long submissionId) {
        Submission submissionForAccess = submissionRepo.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada: " + submissionId));
        submissionAccessService.validateAccess(submissionForAccess, "EVALUACION_CALIFICAR");

        List<Panelist> panelists = panelistRepo.findBySubmissionId(submissionId);
        return panelists.stream()
                .map(j -> {
                    var evOpt = evaluatorRepo.findBySubmissionIdAndTeacherIdAndKindEvaluatorCode(
                            submissionId, j.getTeacher().getId(), "JURADO");
                    List<EvaluationCriterion> evals = evOpt.isPresent()
                            ? evalCriterionRepo.findBySubmissionIdAndEvaluatorId(submissionId, evOpt.get().getId())
                            : new ArrayList<>();
                    return buildResponse(j, evals, submissionId, evOpt.map(Evaluator::getId).orElse(null));
                })
                .collect(Collectors.toList());
    }
 
    /**
     * Nota promedio del tribunal (40%) lista para usar en la evaluación final.
     *
     * @param submissionId id de la submission
     * @return el promedio, redondeado a 2 decimales, de las notas de los panelists que ya
     *         evaluaron; {@code 0.0} si ninguno ha evaluado todavía
     */
    @Override
    public Double calculateGradePanel(Long submissionId) {
        Submission submissionForAccess = submissionRepo.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada: " + submissionId));
        submissionAccessService.validateAccess(submissionForAccess, "EVALUACION_CALIFICAR");
        return averagePanel(submissionId);
    }
 
    // ── Helper ──────────────────────────────────────────────────────────────
 
    /** Calcula el promedio de (suma de notas por panelist) en Java para evitar subqueries en JPQL */
    private Double averagePanel(Long submissionId) {
        List<Object[]> filas = evalCriterionRepo.sumaByEvaluator(submissionId);
        if (filas == null || filas.isEmpty()) return null;
        double suma = filas.stream()
                .mapToDouble(f -> ((Number) f[1]).doubleValue())
                .sum();
        double average = suma / filas.size();
        return Math.round(average * 100.0) / 100.0;
    }
 
    private EvaluationRubricResponse buildResponse(Panelist panelist,
                                                     List<EvaluationCriterion> evals,
                                                     Long submissionId,
                                                     Long evaluatorId) {
        String nombre = panelist.getTeacher() != null && panelist.getTeacher().getAppUser() != null
                ? panelist.getTeacher().getAppUser().getNombre() + " " + panelist.getTeacher().getAppUser().getApellido()
                : "Docente #" + panelist.getId();
 
        List<EvaluationRubricResponse.CriterionResult> detalles = evals.stream()
                .map(ec -> EvaluationRubricResponse.CriterionResult.builder()
                        .criterionId(ec.getCriterion().getId())
                        .nombreCriterion(ec.getCriterion().getNombre())
                        .ponderacion(ec.getCriterion().getPonderacion())
                        .scale(ec.getScale())
                        .rangeDescription(EvaluationCriterion.getRangeDescription(ec.getScale()))
                        .gradeObtenida(ec.getGradeObtenida())
                        .observationAuto(ec.getObservationAuto())
                        .observationManual(ec.getObservationManual())
                        .observations(ec.getObservations())
                        .build())
                .collect(Collectors.toList());
 
        double gradeTotal = evals.stream()
                .mapToDouble(EvaluationCriterion::getGradeObtenida)
                .sum();
        gradeTotal = Math.round(gradeTotal * 100.0) / 100.0;
 
        Double gradeAverage = averagePanel(submissionId);
 
        List<Panelist> allPanelists = panelistRepo.findBySubmissionId(submissionId);
        boolean complete = false;
        if (!allPanelists.isEmpty()) {
            complete = true;
            for (Panelist j : allPanelists) {
                var evOpt = evaluatorRepo.findBySubmissionIdAndTeacherIdAndKindEvaluatorCode(
                        submissionId, j.getTeacher().getId(), "JURADO");
                if (evOpt.isPresent()) {
                    if (!evalCriterionRepo.existsBySubmissionIdAndEvaluatorId(submissionId, evOpt.get().getId())) {
                        complete = false;
                        break;
                    }
                } else {
                    complete = false;
                    break;
                }
            }
        }
 
        return EvaluationRubricResponse.builder()
                .submissionId(submissionId)
                .panelistId(panelist.getId())
                .nombrePanelist(nombre)
                .rolePanelist(panelist.getRolePanelist() != null ? panelist.getRolePanelist().getNombre() : "")
                .detalles(detalles)
                .gradeTotalPanelist(evals.isEmpty() ? null : gradeTotal)
                .gradeAveragePanel(gradeAverage)
                .panelComplete(complete)
                .build();
    }

    /**
     * Obtain todas las observaciones de una submission (tutor, panelists, coordinador).
     *
     * @param submissionId id de la submission
     * @return observaciones consolidadas de todos los actores que han evaluado la submission
     */
    @Override
    @Transactional(readOnly = true)
    public ObservationsSubmissionDTO obtainObservationsSubmission(Long submissionId) {
        Submission submission = submissionRepo.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada: " + submissionId));
        submissionAccessService.validateAccess(submission, "EVALUACION_CALIFICAR");

        String nombreStudent = "";
        if (submission.getStudent() != null && submission.getStudent().getAppUser() != null) {
            nombreStudent = submission.getStudent().getAppUser().getNombre() + " " 
                    + submission.getStudent().getAppUser().getApellido();
        }

        ObservationsSubmissionDTO.ObservationsTutorDTO tutorDTO = null;
        var tutorOpt = tutorRepo.findBySubmissionId(submissionId);
        if (tutorOpt.isPresent()) {
            Tutor tutor = tutorOpt.get();
            String nombreTutor = tutor.getTeacher() != null && tutor.getTeacher().getAppUser() != null
                    ? tutor.getTeacher().getAppUser().getNombre() + " " + tutor.getTeacher().getAppUser().getApellido()
                    : "Tutor";
            String dateRecord = tutor.getDateAsignacion() != null
                    ? tutor.getDateAsignacion().toString() : null;
            tutorDTO = ObservationsSubmissionDTO.ObservationsTutorDTO.builder()
                    .tutorId(tutor.getId())
                    .nombreTutor(nombreTutor)
                    .observations(tutor.getObservations())
                    .dateRecord(dateRecord)
                    .build();
        }

        List<ObservationsSubmissionDTO.ObservationsPanelistDTO> panelistsDTO = new ArrayList<>();
        List<Panelist> panelists = panelistRepo.findBySubmissionId(submissionId);
        
        List<EvaluationPanelist> evaluationsPanelist = javaEvaluationPanelistRepo.findBySubmissionId(submissionId);
        
        for (Panelist panelist : panelists) {
            String nombrePanelist = panelist.getTeacher() != null && panelist.getTeacher().getAppUser() != null
                    ? panelist.getTeacher().getAppUser().getNombre() + " " + panelist.getTeacher().getAppUser().getApellido()
                    : "Docente";
            
            EvaluationPanelist evalPanelist = evaluationsPanelist.stream()
                    .filter(e -> e.getPanelist().getId().equals(panelist.getId()))
                    .findFirst()
                    .orElse(null);
            
            var evOpt = evaluatorRepo.findBySubmissionIdAndTeacherIdAndKindEvaluatorCode(submissionId, panelist.getTeacher().getId(), "JURADO");
            List<EvaluationCriterion> criteria = evOpt.isPresent()
                    ? evalCriterionRepo.findBySubmissionIdAndEvaluatorId(submissionId, evOpt.get().getId())
                    : new ArrayList<>();
            List<ObservationsSubmissionDTO.CriterionObservationDTO> criteriaDTO = criteria.stream()
                    .map(ec -> ObservationsSubmissionDTO.CriterionObservationDTO.builder()
                            .nombreCriterion(ec.getCriterion().getNombre())
                            .ponderacion(ec.getCriterion().getPonderacion())
                            .scale(ec.getScale())
                            .rangeDescription(EvaluationCriterion.getRangeDescription(ec.getScale()))
                            .gradeObtenida(ec.getGradeObtenida())
                            .observationAuto(ec.getObservationAuto())
                            .observationManual(ec.getObservationManual())
                            .build())
                    .collect(Collectors.toList());
            
            panelistsDTO.add(ObservationsSubmissionDTO.ObservationsPanelistDTO.builder()
                    .panelistId(panelist.getId())
                    .nombrePanelist(nombrePanelist)
                    .role(panelist.getRolePanelist() != null ? panelist.getRolePanelist().getNombre() : "")
                    .criteria(criteriaDTO)
                    .gradePanelist(evalPanelist != null ? evalPanelist.getGradePanelist() : null)
                    .observations(evalPanelist != null ? evalPanelist.getObservations() : null)
                    .result(evalPanelist != null ? evalPanelist.getResult() : null)
                    .commentPreestablecido(evalPanelist != null ? evalPanelist.getCommentPreestablecido() : null)
                    .build());
        }

        ObservationsSubmissionDTO.ObservationsCoordinatorDTO coordinatorDTO = null;
        var evaluationOpt = evaluationFinalRepo.findBySubmissionId(submissionId);
        if (evaluationOpt.isPresent()) {
            EvaluationFinal ev = evaluationOpt.get();
            coordinatorDTO = ObservationsSubmissionDTO.ObservationsCoordinatorDTO.builder()
                    .observations(ev.getObservations())
                    .gradeInstructor(ev.getGradeInstructor())
                    .gradeFinal(ev.getGradeFinal())
                    .result(ev.getResult() != null ? ev.getResult().getNombre() : "")
                    .build();
        }

        return ObservationsSubmissionDTO.builder()
                .submissionId(submissionId)
                .tituloTopic(submission.getTituloTopic())
                .nombreStudent(nombreStudent)
                .tutor(tutorDTO)
                .panelists(panelistsDTO)
                .coordinator(coordinatorDTO)
                .build();
    }
}
