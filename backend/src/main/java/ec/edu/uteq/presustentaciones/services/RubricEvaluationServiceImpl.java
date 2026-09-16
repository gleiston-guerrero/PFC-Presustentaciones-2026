package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.ScaleCriterioDTO;
import ec.edu.uteq.presustentaciones.dto.EvaluationRubricRequest;
import ec.edu.uteq.presustentaciones.dto.EvaluationRubricResponse;
import ec.edu.uteq.presustentaciones.dto.ObservacionesSubmissionDTO;
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

    private final EvaluationCriterioRepository evalCriterioRepo;
    private final CriterioRubricRepository criterioRepo;
    private final PanelistRepository panelistRepo;
    private final SubmissionRepository submissionRepo;
    private final RubricRepository rubricRepo;
    private final TutorRepository tutorRepo;
    private final EvaluationFinalRepository evaluationFinalRepo;
    private final EvaluationPanelistRepository javaEvaluationPanelistRepo;
    private final EvaluatorRepository evaluatorRepo;
    private final TipoEvaluatorRepository tipoEvaluatorRepo;
    private final SubmissionAccessService submissionAccessService;
    private final PermissionService permissionService;

    /** Mismo criterio que EvaluationPanelistService.validatePuedeRegister: solo el propio
     * panelist, o ADMIN/COORDINADOR, puede register una evaluación de rúbrica -- evita que
     * un panelist registre scales a nombre de otro (IDOR de escritura). */
    private void validatePuedeRegister(Panelist panelist) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Usuario no autenticado");
        }
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin || permissionService.tienePermission(auth, "EVALUACION_CALIFICAR")) {
            return;
        }
        if (!permissionService.esPropioTeacher(auth, panelist.getTeacher().getId())) {
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

        validatePuedeRegister(panelist);

        rubricRepo.findById(req.getRubricId())
                .orElseThrow(() -> new RuntimeException("Rúbrica no encontrada: " + req.getRubricId()));

        List<CriterioRubric> criterios = criterioRepo.findByRubricIdOrderByOrdenAsc(req.getRubricId());
        if (criterios.isEmpty()) {
            throw new RuntimeException("La rúbrica no tiene criterios definidos.");
        }
        if (req.getCriterios() == null || req.getCriterios().size() != criterios.size()) {
            throw new RuntimeException("Debe evaluar todos los " + criterios.size() + " criterios de la rúbrica.");
        }
        for (ScaleCriterioDTO c : req.getCriterios()) {
            if (c.getScale() < 1 || c.getScale() > 100) {
                throw new RuntimeException("Escala inválida: " + c.getScale() + ". Use valores entre 1 y 100.");
            }
        }

        // Search o create el Evaluator correspondiente para este panelist
        Evaluator evaluator = evaluatorRepo.findBySubmissionIdAndTeacherIdAndTipoEvaluatorCodigo(
                req.getSubmissionId(), panelist.getTeacher().getId(), "JURADO")
            .orElseGet(() -> {
                TipoEvaluator tipo = tipoEvaluatorRepo.findByCodigo("JURADO")
                        .orElseThrow(() -> new RuntimeException("Tipo evaluador JURADO no configurado."));
                Evaluator ev = Evaluator.builder()
                        .submission(submission)
                        .teacher(panelist.getTeacher())
                        .memberTribunal(panelist)
                        .tipoEvaluator(tipo)
                        .peso(1.0)
                        .build();
                return evaluatorRepo.save(ev);
            });

        // Permite re-evaluación: delete la anterior
        evalCriterioRepo.deleteBySubmissionIdAndEvaluatorId(req.getSubmissionId(), evaluator.getId());

        List<EvaluationCriterio> guardadas = new ArrayList<>();
        for (ScaleCriterioDTO cDto : req.getCriterios()) {
            CriterioRubric criterio = criterios.stream()
                    .filter(c -> c.getId().equals(cDto.getCriterioId()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Criterio no encontrado: " + cDto.getCriterioId()));

            double notaObtenida = Math.round(criterio.getPonderacion() * cDto.getScale() / 100.0 * 100.0) / 100.0;
            String observacionAuto = EvaluationCriterio.getObservacionPorRango(cDto.getScale());

            EvaluationCriterio ec = EvaluationCriterio.builder()
                    .submission(submission)
                    .evaluator(evaluator)
                    .panelist(panelist)
                    .criterio(criterio)
                    .scale(cDto.getScale())
                    .notaObtenida(notaObtenida)
                    .observacionAuto(observacionAuto)
                    .observacionManual(cDto.getObservacionManual())
                    .observaciones(cDto.getObservaciones())
                    .build();
            guardadas.add(evalCriterioRepo.save(ec));
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
        submissionAccessService.validateAcceso(panelist.getSubmission(), "EVALUACION_CALIFICAR");

        Evaluator evaluator = evaluatorRepo.findBySubmissionIdAndTeacherIdAndTipoEvaluatorCodigo(
                submissionId, panelist.getTeacher().getId(), "JURADO")
                .orElseThrow(() -> new RuntimeException("Evaluador no registrado para el jurado."));

        List<EvaluationCriterio> evals = evalCriterioRepo.findBySubmissionIdAndEvaluatorId(submissionId, evaluator.getId());
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
        Submission submissionParaAcceso = submissionRepo.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada: " + submissionId));
        submissionAccessService.validateAcceso(submissionParaAcceso, "EVALUACION_CALIFICAR");

        List<Panelist> panelists = panelistRepo.findBySubmissionId(submissionId);
        return panelists.stream()
                .map(j -> {
                    var evOpt = evaluatorRepo.findBySubmissionIdAndTeacherIdAndTipoEvaluatorCodigo(
                            submissionId, j.getTeacher().getId(), "JURADO");
                    List<EvaluationCriterio> evals = evOpt.isPresent()
                            ? evalCriterioRepo.findBySubmissionIdAndEvaluatorId(submissionId, evOpt.get().getId())
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
    public Double calculateNotaTribunal(Long submissionId) {
        Submission submissionParaAcceso = submissionRepo.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada: " + submissionId));
        submissionAccessService.validateAcceso(submissionParaAcceso, "EVALUACION_CALIFICAR");
        return promedioTribunal(submissionId);
    }
 
    // ── Helper ──────────────────────────────────────────────────────────────
 
    /** Calcula el promedio de (suma de notas por panelist) en Java para evitar subqueries en JPQL */
    private Double promedioTribunal(Long submissionId) {
        List<Object[]> filas = evalCriterioRepo.sumaPorEvaluator(submissionId);
        if (filas == null || filas.isEmpty()) return null;
        double suma = filas.stream()
                .mapToDouble(f -> ((Number) f[1]).doubleValue())
                .sum();
        double promedio = suma / filas.size();
        return Math.round(promedio * 100.0) / 100.0;
    }
 
    private EvaluationRubricResponse buildResponse(Panelist panelist,
                                                     List<EvaluationCriterio> evals,
                                                     Long submissionId,
                                                     Long evaluatorId) {
        String nombre = panelist.getTeacher() != null && panelist.getTeacher().getAppUser() != null
                ? panelist.getTeacher().getAppUser().getNombre() + " " + panelist.getTeacher().getAppUser().getApellido()
                : "Docente #" + panelist.getId();
 
        List<EvaluationRubricResponse.CriterioResultado> detalles = evals.stream()
                .map(ec -> EvaluationRubricResponse.CriterioResultado.builder()
                        .criterioId(ec.getCriterio().getId())
                        .nombreCriterio(ec.getCriterio().getNombre())
                        .ponderacion(ec.getCriterio().getPonderacion())
                        .scale(ec.getScale())
                        .rangoDescripcion(EvaluationCriterio.getRangoDescripcion(ec.getScale()))
                        .notaObtenida(ec.getNotaObtenida())
                        .observacionAuto(ec.getObservacionAuto())
                        .observacionManual(ec.getObservacionManual())
                        .observaciones(ec.getObservaciones())
                        .build())
                .collect(Collectors.toList());
 
        double notaTotal = evals.stream()
                .mapToDouble(EvaluationCriterio::getNotaObtenida)
                .sum();
        notaTotal = Math.round(notaTotal * 100.0) / 100.0;
 
        Double notaPromedio = promedioTribunal(submissionId);
 
        List<Panelist> todosPanelists = panelistRepo.findBySubmissionId(submissionId);
        boolean completo = false;
        if (!todosPanelists.isEmpty()) {
            completo = true;
            for (Panelist j : todosPanelists) {
                var evOpt = evaluatorRepo.findBySubmissionIdAndTeacherIdAndTipoEvaluatorCodigo(
                        submissionId, j.getTeacher().getId(), "JURADO");
                if (evOpt.isPresent()) {
                    if (!evalCriterioRepo.existsBySubmissionIdAndEvaluatorId(submissionId, evOpt.get().getId())) {
                        completo = false;
                        break;
                    }
                } else {
                    completo = false;
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
                .notaTotalPanelist(evals.isEmpty() ? null : notaTotal)
                .notaPromedioTribunal(notaPromedio)
                .tribunalCompleto(completo)
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
    public ObservacionesSubmissionDTO obtainObservacionesSubmission(Long submissionId) {
        Submission submission = submissionRepo.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada: " + submissionId));
        submissionAccessService.validateAcceso(submission, "EVALUACION_CALIFICAR");

        String nombreStudent = "";
        if (submission.getStudent() != null && submission.getStudent().getAppUser() != null) {
            nombreStudent = submission.getStudent().getAppUser().getNombre() + " " 
                    + submission.getStudent().getAppUser().getApellido();
        }

        ObservacionesSubmissionDTO.ObservacionesTutorDTO tutorDTO = null;
        var tutorOpt = tutorRepo.findBySubmissionId(submissionId);
        if (tutorOpt.isPresent()) {
            Tutor tutor = tutorOpt.get();
            String nombreTutor = tutor.getTeacher() != null && tutor.getTeacher().getAppUser() != null
                    ? tutor.getTeacher().getAppUser().getNombre() + " " + tutor.getTeacher().getAppUser().getApellido()
                    : "Tutor";
            String fechaRegistro = tutor.getFechaAsignacion() != null
                    ? tutor.getFechaAsignacion().toString() : null;
            tutorDTO = ObservacionesSubmissionDTO.ObservacionesTutorDTO.builder()
                    .tutorId(tutor.getId())
                    .nombreTutor(nombreTutor)
                    .observaciones(tutor.getObservaciones())
                    .fechaRegistro(fechaRegistro)
                    .build();
        }

        List<ObservacionesSubmissionDTO.ObservacionesPanelistDTO> panelistsDTO = new ArrayList<>();
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
            
            var evOpt = evaluatorRepo.findBySubmissionIdAndTeacherIdAndTipoEvaluatorCodigo(submissionId, panelist.getTeacher().getId(), "JURADO");
            List<EvaluationCriterio> criterios = evOpt.isPresent()
                    ? evalCriterioRepo.findBySubmissionIdAndEvaluatorId(submissionId, evOpt.get().getId())
                    : new ArrayList<>();
            List<ObservacionesSubmissionDTO.CriterioObservacionDTO> criteriosDTO = criterios.stream()
                    .map(ec -> ObservacionesSubmissionDTO.CriterioObservacionDTO.builder()
                            .nombreCriterio(ec.getCriterio().getNombre())
                            .ponderacion(ec.getCriterio().getPonderacion())
                            .scale(ec.getScale())
                            .rangoDescripcion(EvaluationCriterio.getRangoDescripcion(ec.getScale()))
                            .notaObtenida(ec.getNotaObtenida())
                            .observacionAuto(ec.getObservacionAuto())
                            .observacionManual(ec.getObservacionManual())
                            .build())
                    .collect(Collectors.toList());
            
            panelistsDTO.add(ObservacionesSubmissionDTO.ObservacionesPanelistDTO.builder()
                    .panelistId(panelist.getId())
                    .nombrePanelist(nombrePanelist)
                    .role(panelist.getRolePanelist() != null ? panelist.getRolePanelist().getNombre() : "")
                    .criterios(criteriosDTO)
                    .notaPanelist(evalPanelist != null ? evalPanelist.getNotaPanelist() : null)
                    .observaciones(evalPanelist != null ? evalPanelist.getObservaciones() : null)
                    .resultado(evalPanelist != null ? evalPanelist.getResultado() : null)
                    .comentarioPreestablecido(evalPanelist != null ? evalPanelist.getComentarioPreestablecido() : null)
                    .build());
        }

        ObservacionesSubmissionDTO.ObservacionesCoordinadorDTO coordinadorDTO = null;
        var evaluationOpt = evaluationFinalRepo.findBySubmissionId(submissionId);
        if (evaluationOpt.isPresent()) {
            EvaluationFinal ev = evaluationOpt.get();
            coordinadorDTO = ObservacionesSubmissionDTO.ObservacionesCoordinadorDTO.builder()
                    .observaciones(ev.getObservaciones())
                    .notaInstructor(ev.getNotaInstructor())
                    .notaFinal(ev.getNotaFinal())
                    .resultado(ev.getResultado() != null ? ev.getResultado().getNombre() : "")
                    .build();
        }

        return ObservacionesSubmissionDTO.builder()
                .submissionId(submissionId)
                .tituloTopic(submission.getTituloTopic())
                .nombreStudent(nombreStudent)
                .tutor(tutorDTO)
                .panelists(panelistsDTO)
                .coordinador(coordinadorDTO)
                .build();
    }
}
