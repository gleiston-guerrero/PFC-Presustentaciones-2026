package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.ScaleCriterionDTO;
import ec.edu.uteq.presustentaciones.dto.EvaluationRubricRequest;
import ec.edu.uteq.presustentaciones.dto.EvaluationRubricResponse;
import ec.edu.uteq.presustentaciones.dto.ObservationsSubmissionDTO;
import ec.edu.uteq.presustentaciones.entities.*;
import ec.edu.uteq.presustentaciones.repositories.*;
import ec.edu.uteq.presustentaciones.security.service.SubmissionAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Cubre calculateNotaTribunal(), el cálculo real de promedio de evaluación
 * (equivalente en Java a sp_calculate_promedio_evaluation mencionado en OBSERVACIONES.md).
 */
@ExtendWith(MockitoExtension.class)
class RubricEvaluationServiceImplTest {

    @Mock private EvaluationCriterionRepository evalCriterionRepo;
    @Mock private CriterionRubricRepository criterionRepo;
    @Mock private PanelistRepository panelistRepo;
    @Mock private SubmissionRepository submissionRepo;
    @Mock private RubricRepository rubricRepo;
    @Mock private TutorRepository tutorRepo;
    @Mock private EvaluationFinalRepository evaluationFinalRepo;
    @Mock private EvaluationPanelistRepository javaEvaluationPanelistRepo;
    @Mock private EvaluatorRepository evaluatorRepo;
    @Mock private KindEvaluatorRepository kindEvaluatorRepo;
    @Mock private SubmissionAccessService submissionAccessService;
    @Mock private PermissionService permissionService;

    @InjectMocks
    private RubricEvaluationServiceImpl service;

    private static Object[] row(long evaluatorId, double suma) {
        return new Object[]{evaluatorId, suma};
    }

    /** calculateNotaTribunal() ahora carga la Submission para delegar la autorización en
     * SubmissionAccessService (mockeado aquí -- validateAcceso() no-opea por defecto, así que
     * estos tests siguen centrados en el cálculo del promedio, no en la autorización). */
    private void stubSubmission(Long submissionId) {
        when(submissionRepo.findById(submissionId))
                .thenReturn(Optional.of(Submission.builder().id(submissionId).build()));
    }

    @BeforeEach
    void setUp() {}

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registerEvaluationRejectsToPanelistThatRecordsToNameOfOther() {
        // Caso IDOR de escritura, mismo patrón que EvaluationPanelistServiceTest: un teacher que
        // NO es el panelist asignado no puede register la evaluación de rúbrica a su nombre. El
        // caso "permitido" para este mismo guardia (validatePuedeRegister) ya se prueba a fondo
        // en EvaluationPanelistServiceTest -- es idéntico en ambos servicios -- así que aquí solo
        // se cubre la regresión específica de este archivo.
        Submission submission = Submission.builder().id(7L).build();
        AppUser appUserTeacher = AppUser.builder().id(50L).email("jurado.real@uteq.edu.ec").build();
        Teacher teacher = Teacher.builder().id(1L).appUser(appUserTeacher).build();
        Panelist panelist = Panelist.builder().id(3L).submission(submission).teacher(teacher).build();

        when(submissionRepo.findById(7L)).thenReturn(Optional.of(submission));
        when(panelistRepo.findById(3L)).thenReturn(Optional.of(panelist));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("otro.docente@uteq.edu.ec", null,
                        org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_DOCENTE")));
        when(permissionService.isOwnTeacher(any(), eq(1L))).thenReturn(false);
        when(permissionService.hasPermission(any(), eq("EVALUACION_CALIFICAR"))).thenReturn(false);

        EvaluationRubricRequest req = new EvaluationRubricRequest();
        req.setSubmissionId(7L);
        req.setPanelistId(3L);
        req.setRubricId(1L);
        req.setCriteria(List.of(new ScaleCriterionDTO()));

        assertThrows(AccessDeniedException.class, () -> service.registerEvaluation(req));
    }

    @Test
    void obtainEvaluationPanelistPropagatesAccessDeniedIfSubmissionAccessServiceRejects() {
        Submission submission = Submission.builder().id(7L).build();
        Panelist panelist = Panelist.builder().id(3L).submission(submission).build();
        when(panelistRepo.findById(3L)).thenReturn(Optional.of(panelist));
        org.mockito.Mockito.doThrow(new AccessDeniedException("No tienes permiso para acceder a la información de esta solicitud"))
                .when(submissionAccessService).validateAccess(submission, "EVALUACION_CALIFICAR");

        assertThrows(AccessDeniedException.class, () -> service.obtainEvaluationPanelist(7L, 3L));
    }

    @Test
    void calculateGradePanelAveragesSumsOfEachPanelist() {
        stubSubmission(10L);
        // 3 panelists, cada uno con su suma de notas ponderadas por criterio: (90 + 85 + 78) / 3 = 84.33
        List<Object[]> filas = Arrays.asList(row(1L, 90.0), row(2L, 85.0), row(3L, 78.0));
        when(evalCriterionRepo.sumaByEvaluator(10L)).thenReturn(filas);

        Double grade = service.calculateGradePanel(10L);

        assertEquals(84.33, grade, 0.001);
    }

    @Test
    void calculateGradePanelRoundsToTwoDecimals() {
        stubSubmission(11L);
        List<Object[]> filas = Arrays.asList(row(1L, 100.0), row(2L, 100.0), row(3L, 66.0));
        when(evalCriterionRepo.sumaByEvaluator(11L)).thenReturn(filas);

        Double grade = service.calculateGradePanel(11L);

        // (100 + 100 + 66) / 3 = 88.666... -> 88.67
        assertEquals(88.67, grade);
    }

    @Test
    void calculateGradePanelReturnsNullIfNotHasEvaluations() {
        stubSubmission(12L);
        when(evalCriterionRepo.sumaByEvaluator(12L)).thenReturn(Collections.emptyList());

        assertNull(service.calculateGradePanel(12L));
    }

    @Test
    void calculateGradePanelWithOnlyPanelistReturnsOwnGrade() {
        stubSubmission(13L);
        when(evalCriterionRepo.sumaByEvaluator(13L)).thenReturn(Collections.singletonList(row(1L, 95.5)));

        assertEquals(95.5, service.calculateGradePanel(13L));
    }

    // ── registerEvaluation: validaciones y flujo completo ──────────────────

    private void authenticateAsAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@uteq.edu.ec", null,
                        org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_ADMIN")));
    }

    private EvaluationRubricRequest requestBase(Long submissionId, Long panelistId, Long rubricId) {
        EvaluationRubricRequest req = new EvaluationRubricRequest();
        req.setSubmissionId(submissionId);
        req.setPanelistId(panelistId);
        req.setRubricId(rubricId);
        return req;
    }

    @Test
    void registerEvaluationThrowsIfSubmissionNotExists() {
        when(submissionRepo.findById(7L)).thenReturn(Optional.empty());
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.registerEvaluation(requestBase(7L, 3L, 1L)));
        assertTrue(ex.getMessage().contains("Solicitud no encontrada"));
    }

    @Test
    void registerEvaluationThrowsIfPanelistNotExists() {
        when(submissionRepo.findById(7L)).thenReturn(Optional.of(Submission.builder().id(7L).build()));
        when(panelistRepo.findById(3L)).thenReturn(Optional.empty());
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.registerEvaluation(requestBase(7L, 3L, 1L)));
        assertTrue(ex.getMessage().contains("Jurado no encontrado"));
    }

    @Test
    void registerEvaluationThrowsIfPanelistNotBelongsToSubmission() {
        Submission submission = Submission.builder().id(7L).build();
        Submission otraSubmission = Submission.builder().id(999L).build();
        when(submissionRepo.findById(7L)).thenReturn(Optional.of(submission));
        when(panelistRepo.findById(3L)).thenReturn(Optional.of(Panelist.builder().id(3L).submission(otraSubmission).build()));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.registerEvaluation(requestBase(7L, 3L, 1L)));
        assertTrue(ex.getMessage().contains("no pertenece a esta solicitud"));
    }

    private Panelist panelistOfSubmission(Submission submission, Teacher teacher) {
        return Panelist.builder().id(3L).submission(submission).teacher(teacher)
                .rolePanelist(RolePanelist.builder().code("PRESIDENTE").nombre("Presidente").build()).build();
    }

    @Test
    void registerEvaluationThrowsIfRubricNotExists() {
        Submission submission = Submission.builder().id(7L).build();
        Teacher teacher = Teacher.builder().id(1L).appUser(AppUser.builder().id(50L).build()).build();
        Panelist panelist = panelistOfSubmission(submission, teacher);
        when(submissionRepo.findById(7L)).thenReturn(Optional.of(submission));
        when(panelistRepo.findById(3L)).thenReturn(Optional.of(panelist));
        authenticateAsAdmin();
        when(rubricRepo.findById(1L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.registerEvaluation(requestBase(7L, 3L, 1L)));
        assertTrue(ex.getMessage().contains("Rúbrica no encontrada"));
    }

    @Test
    void registerEvaluationThrowsIfRubricWithoutCriteria() {
        Submission submission = Submission.builder().id(7L).build();
        Teacher teacher = Teacher.builder().id(1L).appUser(AppUser.builder().id(50L).build()).build();
        Panelist panelist = panelistOfSubmission(submission, teacher);
        when(submissionRepo.findById(7L)).thenReturn(Optional.of(submission));
        when(panelistRepo.findById(3L)).thenReturn(Optional.of(panelist));
        authenticateAsAdmin();
        when(rubricRepo.findById(1L)).thenReturn(Optional.of(Rubric.builder().id(1L).build()));
        when(criterionRepo.findByRubricIdOrderByOrdenAsc(1L)).thenReturn(List.of());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.registerEvaluation(requestBase(7L, 3L, 1L)));
        assertTrue(ex.getMessage().contains("no tiene criterios"));
    }

    @Test
    void registerEvaluationThrowsIfNotEvaluatesAllCriteria() {
        Submission submission = Submission.builder().id(7L).build();
        Teacher teacher = Teacher.builder().id(1L).appUser(AppUser.builder().id(50L).build()).build();
        Panelist panelist = panelistOfSubmission(submission, teacher);
        CriterionRubric c1 = CriterionRubric.builder().id(1L).nombre("Claridad").ponderacion(50.0).build();
        CriterionRubric c2 = CriterionRubric.builder().id(2L).nombre("Rigor").ponderacion(50.0).build();
        when(submissionRepo.findById(7L)).thenReturn(Optional.of(submission));
        when(panelistRepo.findById(3L)).thenReturn(Optional.of(panelist));
        authenticateAsAdmin();
        when(rubricRepo.findById(1L)).thenReturn(Optional.of(Rubric.builder().id(1L).build()));
        when(criterionRepo.findByRubricIdOrderByOrdenAsc(1L)).thenReturn(List.of(c1, c2));

        EvaluationRubricRequest req = requestBase(7L, 3L, 1L);
        ScaleCriterionDTO dto1 = new ScaleCriterionDTO();
        dto1.setCriterionId(1L);
        dto1.setScale(80);
        req.setCriteria(List.of(dto1)); // solo 1 de 2

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.registerEvaluation(req));
        assertTrue(ex.getMessage().contains("Debe evaluar todos los 2 criterios"));
    }

    @Test
    void registerEvaluationThrowsIfScaleOutsideOfRange() {
        Submission submission = Submission.builder().id(7L).build();
        Teacher teacher = Teacher.builder().id(1L).appUser(AppUser.builder().id(50L).build()).build();
        Panelist panelist = panelistOfSubmission(submission, teacher);
        CriterionRubric c1 = CriterionRubric.builder().id(1L).nombre("Claridad").ponderacion(100.0).build();
        when(submissionRepo.findById(7L)).thenReturn(Optional.of(submission));
        when(panelistRepo.findById(3L)).thenReturn(Optional.of(panelist));
        authenticateAsAdmin();
        when(rubricRepo.findById(1L)).thenReturn(Optional.of(Rubric.builder().id(1L).build()));
        when(criterionRepo.findByRubricIdOrderByOrdenAsc(1L)).thenReturn(List.of(c1));

        EvaluationRubricRequest req = requestBase(7L, 3L, 1L);
        ScaleCriterionDTO dto1 = new ScaleCriterionDTO();
        dto1.setCriterionId(1L);
        dto1.setScale(150);
        req.setCriteria(List.of(dto1));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.registerEvaluation(req));
        assertTrue(ex.getMessage().contains("Escala inválida"));
    }

    @Test
    void registerEvaluationThrowsIfCriterionOfDtoNotExistsInRubric() {
        Submission submission = Submission.builder().id(7L).build();
        Teacher teacher = Teacher.builder().id(1L).appUser(AppUser.builder().id(50L).build()).build();
        Panelist panelist = panelistOfSubmission(submission, teacher);
        CriterionRubric c1 = CriterionRubric.builder().id(1L).nombre("Claridad").ponderacion(100.0).build();
        when(submissionRepo.findById(7L)).thenReturn(Optional.of(submission));
        when(panelistRepo.findById(3L)).thenReturn(Optional.of(panelist));
        authenticateAsAdmin();
        when(rubricRepo.findById(1L)).thenReturn(Optional.of(Rubric.builder().id(1L).build()));
        when(criterionRepo.findByRubricIdOrderByOrdenAsc(1L)).thenReturn(List.of(c1));
        when(evaluatorRepo.findBySubmissionIdAndTeacherIdAndKindEvaluatorCode(7L, 1L, "JURADO"))
                .thenReturn(Optional.of(Evaluator.builder().id(20L).build()));

        EvaluationRubricRequest req = requestBase(7L, 3L, 1L);
        ScaleCriterionDTO dto1 = new ScaleCriterionDTO();
        dto1.setCriterionId(999L); // no coincide con ningún criterio de la rúbrica
        dto1.setScale(80);
        req.setCriteria(List.of(dto1));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.registerEvaluation(req));
        assertTrue(ex.getMessage().contains("Criterio no encontrado"));
    }

    @Test
    void registerEvaluationThrowsIfKindEvaluatorPanelistNotConfigured() {
        Submission submission = Submission.builder().id(7L).build();
        Teacher teacher = Teacher.builder().id(1L).appUser(AppUser.builder().id(50L).build()).build();
        Panelist panelist = panelistOfSubmission(submission, teacher);
        CriterionRubric c1 = CriterionRubric.builder().id(1L).nombre("Claridad").ponderacion(100.0).build();
        when(submissionRepo.findById(7L)).thenReturn(Optional.of(submission));
        when(panelistRepo.findById(3L)).thenReturn(Optional.of(panelist));
        authenticateAsAdmin();
        when(rubricRepo.findById(1L)).thenReturn(Optional.of(Rubric.builder().id(1L).build()));
        when(criterionRepo.findByRubricIdOrderByOrdenAsc(1L)).thenReturn(List.of(c1));
        when(evaluatorRepo.findBySubmissionIdAndTeacherIdAndKindEvaluatorCode(7L, 1L, "JURADO"))
                .thenReturn(Optional.empty());
        when(kindEvaluatorRepo.findByCode("JURADO")).thenReturn(Optional.empty());

        EvaluationRubricRequest req = requestBase(7L, 3L, 1L);
        ScaleCriterionDTO dto1 = new ScaleCriterionDTO();
        dto1.setCriterionId(1L);
        dto1.setScale(80);
        req.setCriteria(List.of(dto1));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.registerEvaluation(req));
        assertTrue(ex.getMessage().contains("Tipo evaluador JURADO no configurado"));
    }

    @Test
    void registerEvaluationSuccessfulCreatesEvaluatorNewAndCalculatesGrade() {
        Submission submission = Submission.builder().id(7L).build();
        Teacher teacher = Teacher.builder().id(1L).appUser(AppUser.builder().id(50L).nombre("Ana").apellido("Ruiz").build()).build();
        Panelist panelist = panelistOfSubmission(submission, teacher);
        CriterionRubric c1 = CriterionRubric.builder().id(1L).nombre("Claridad").ponderacion(60.0).build();
        CriterionRubric c2 = CriterionRubric.builder().id(2L).nombre("Rigor").ponderacion(40.0).build();
        when(submissionRepo.findById(7L)).thenReturn(Optional.of(submission));
        when(panelistRepo.findById(3L)).thenReturn(Optional.of(panelist));
        authenticateAsAdmin();
        when(rubricRepo.findById(1L)).thenReturn(Optional.of(Rubric.builder().id(1L).build()));
        when(criterionRepo.findByRubricIdOrderByOrdenAsc(1L)).thenReturn(List.of(c1, c2));
        when(evaluatorRepo.findBySubmissionIdAndTeacherIdAndKindEvaluatorCode(7L, 1L, "JURADO"))
                .thenReturn(Optional.empty());
        when(kindEvaluatorRepo.findByCode("JURADO"))
                .thenReturn(Optional.of(KindEvaluator.builder().id((short) 1).code("JURADO").build()));
        when(evaluatorRepo.save(any(Evaluator.class))).thenAnswer(inv -> {
            Evaluator e = inv.getArgument(0);
            e.setId(20L);
            return e;
        });
        when(evalCriterionRepo.save(any(EvaluationCriterion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(panelistRepo.findBySubmissionId(7L)).thenReturn(List.of(panelist));
        when(evalCriterionRepo.sumaByEvaluator(7L)).thenReturn(List.<Object[]>of(row(20L, 92.0)));

        EvaluationRubricRequest req = requestBase(7L, 3L, 1L);
        ScaleCriterionDTO dto1 = new ScaleCriterionDTO();
        dto1.setCriterionId(1L);
        dto1.setScale(100); // nota = 60 * 100 / 100 = 60
        ScaleCriterionDTO dto2 = new ScaleCriterionDTO();
        dto2.setCriterionId(2L);
        dto2.setScale(80); // nota = 40 * 80 / 100 = 32
        req.setCriteria(List.of(dto1, dto2));

        EvaluationRubricResponse resp = service.registerEvaluation(req);

        assertEquals(92.0, resp.getGradeTotalPanelist()); // 60 + 32
        assertEquals("Ana Ruiz", resp.getNombrePanelist());
        // tribunalCompleto no se afirma aqui: el evaluator recien creado no se re-stubea para
        // la segunda consulta que hace buildResponse -- ese caso ya se cubre en
        // obtainEvaluationsSubmissionMarcaTribunalIncompletoSiFaltaUnPanelist.
        assertEquals(2, resp.getDetalles().size());
        verify(evalCriterionRepo).deleteBySubmissionIdAndEvaluatorId(7L, 20L);
        verify(evaluatorRepo).save(any(Evaluator.class));
    }

    @Test
    void registerEvaluationReusesEvaluatorExistingAndAllowsReevaluation() {
        Submission submission = Submission.builder().id(7L).build();
        Teacher teacher = Teacher.builder().id(1L).appUser(AppUser.builder().id(50L).nombre("Ana").apellido("Ruiz").build()).build();
        Panelist panelist = panelistOfSubmission(submission, teacher);
        CriterionRubric c1 = CriterionRubric.builder().id(1L).nombre("Claridad").ponderacion(100.0).build();
        Evaluator evaluatorExisting = Evaluator.builder().id(20L).build();
        when(submissionRepo.findById(7L)).thenReturn(Optional.of(submission));
        when(panelistRepo.findById(3L)).thenReturn(Optional.of(panelist));
        authenticateAsAdmin();
        when(rubricRepo.findById(1L)).thenReturn(Optional.of(Rubric.builder().id(1L).build()));
        when(criterionRepo.findByRubricIdOrderByOrdenAsc(1L)).thenReturn(List.of(c1));
        when(evaluatorRepo.findBySubmissionIdAndTeacherIdAndKindEvaluatorCode(7L, 1L, "JURADO"))
                .thenReturn(Optional.of(evaluatorExisting));
        when(evalCriterionRepo.save(any(EvaluationCriterion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(panelistRepo.findBySubmissionId(7L)).thenReturn(List.of(panelist));
        when(evalCriterionRepo.existsBySubmissionIdAndEvaluatorId(7L, 20L)).thenReturn(true);
        when(evalCriterionRepo.sumaByEvaluator(7L)).thenReturn(List.<Object[]>of(row(20L, 50.0)));

        EvaluationRubricRequest req = requestBase(7L, 3L, 1L);
        ScaleCriterionDTO dto1 = new ScaleCriterionDTO();
        dto1.setCriterionId(1L);
        dto1.setScale(50);
        req.setCriteria(List.of(dto1));

        service.registerEvaluation(req);

        verify(evaluatorRepo, never()).save(any());
        verify(evalCriterionRepo).deleteBySubmissionIdAndEvaluatorId(7L, 20L);
    }

    // ── obtainEvaluationsSubmission ─────────────────────────────────────────

    @Test
    void obtainEvaluationsSubmissionThrowsIfSubmissionNotExists() {
        when(submissionRepo.findById(7L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> service.obtainEvaluationsSubmission(7L));
    }

    @Test
    void obtainEvaluationsSubmissionMarksPanelIncompleteIfMissingPanelist() {
        Submission submission = Submission.builder().id(7L).build();
        Teacher teacher1 = Teacher.builder().id(1L).appUser(AppUser.builder().id(50L).nombre("A").apellido("B").build()).build();
        Teacher teacher2 = Teacher.builder().id(2L).appUser(AppUser.builder().id(51L).nombre("C").apellido("D").build()).build();
        Panelist j1 = panelistOfSubmission(submission, teacher1);
        Panelist j2 = Panelist.builder().id(4L).submission(submission).teacher(teacher2)
                .rolePanelist(RolePanelist.builder().code("VOCAL_1").build()).build();
        when(submissionRepo.findById(7L)).thenReturn(Optional.of(submission));
        when(panelistRepo.findBySubmissionId(7L)).thenReturn(List.of(j1, j2));
        when(evaluatorRepo.findBySubmissionIdAndTeacherIdAndKindEvaluatorCode(7L, 1L, "JURADO"))
                .thenReturn(Optional.of(Evaluator.builder().id(20L).build()));
        when(evalCriterionRepo.findBySubmissionIdAndEvaluatorId(7L, 20L)).thenReturn(List.of());
        // teacher2 (panelist j2) aun no tiene evaluator -> tribunal incompleto
        when(evaluatorRepo.findBySubmissionIdAndTeacherIdAndKindEvaluatorCode(7L, 2L, "JURADO"))
                .thenReturn(Optional.empty());

        List<EvaluationRubricResponse> result = service.obtainEvaluationsSubmission(7L);

        assertEquals(2, result.size());
        assertFalse(result.get(0).isPanelComplete());
    }

    // ── obtainObservacionesSubmission ────────────────────────────────────────

    @Test
    void obtainObservationsSubmissionThrowsIfSubmissionNotExists() {
        when(submissionRepo.findById(7L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> service.obtainObservationsSubmission(7L));
    }

    @Test
    void obtainObservationsSubmissionReturnsAllEmptyWithoutTutorPanelistsOrEvaluationFinal() {
        Submission submission = Submission.builder().id(7L).tituloTopic("Tema").build();
        when(submissionRepo.findById(7L)).thenReturn(Optional.of(submission));
        when(tutorRepo.findBySubmissionId(7L)).thenReturn(Optional.empty());
        when(panelistRepo.findBySubmissionId(7L)).thenReturn(List.of());
        when(javaEvaluationPanelistRepo.findBySubmissionId(7L)).thenReturn(List.of());
        when(evaluationFinalRepo.findBySubmissionId(7L)).thenReturn(Optional.empty());

        ObservationsSubmissionDTO dto = service.obtainObservationsSubmission(7L);

        assertEquals("", dto.getNombreStudent());
        assertNull(dto.getTutor());
        assertTrue(dto.getPanelists().isEmpty());
        assertNull(dto.getCoordinator());
    }

    @Test
    void obtainObservationsSubmissionBuildsReportComplete() {
        AppUser appUserStudent = AppUser.builder().id(1L).nombre("Ana").apellido("Torres").build();
        Student student = Student.builder().id(1L).appUser(appUserStudent).build();
        Submission submission = Submission.builder().id(7L).tituloTopic("Tema X").student(student).build();

        AppUser appUserTutor = AppUser.builder().id(60L).nombre("Luis").apellido("Mora").build();
        Teacher teacherTutor = Teacher.builder().id(5L).appUser(appUserTutor).build();
        Tutor tutor = Tutor.builder().id(9L).teacher(teacherTutor).observations("Buen avance").build();

        AppUser appUserPanelist = AppUser.builder().id(50L).nombre("Carla").apellido("Zamora").build();
        Teacher teacherPanelist = Teacher.builder().id(1L).appUser(appUserPanelist).build();
        Panelist panelist = panelistOfSubmission(submission, teacherPanelist);
        EvaluationPanelist evalPanelist = EvaluationPanelist.builder().id(3L).panelist(panelist)
                .gradePanelist(85.0).observations("Bien").result("APROBADO")
                .commentPreestablecido("Cumple").build();

        CriterionRubric criterion = CriterionRubric.builder().id(1L).nombre("Claridad").ponderacion(100.0).build();
        Evaluator evaluator = Evaluator.builder().id(20L).build();
        EvaluationCriterion ec = EvaluationCriterion.builder().id(1L).criterion(criterion).scale(90)
                .gradeObtenida(90.0).observationAuto("Excelente").build();

        EvaluationFinal evaluationFinal = EvaluationFinal.builder().id(1L)
                .observations("Todo bien").gradeInstructor(88.0).gradeFinal(89.0)
                .result(ResultEvaluation.builder().code("APROBADO").nombre("Aprobado").build())
                .build();

        when(submissionRepo.findById(7L)).thenReturn(Optional.of(submission));
        when(tutorRepo.findBySubmissionId(7L)).thenReturn(Optional.of(tutor));
        when(panelistRepo.findBySubmissionId(7L)).thenReturn(List.of(panelist));
        when(javaEvaluationPanelistRepo.findBySubmissionId(7L)).thenReturn(List.of(evalPanelist));
        when(evaluatorRepo.findBySubmissionIdAndTeacherIdAndKindEvaluatorCode(7L, 1L, "JURADO"))
                .thenReturn(Optional.of(evaluator));
        when(evalCriterionRepo.findBySubmissionIdAndEvaluatorId(7L, 20L)).thenReturn(List.of(ec));
        when(evaluationFinalRepo.findBySubmissionId(7L)).thenReturn(Optional.of(evaluationFinal));

        ObservationsSubmissionDTO dto = service.obtainObservationsSubmission(7L);

        assertEquals("Ana Torres", dto.getNombreStudent());
        assertEquals("Luis Mora", dto.getTutor().getNombreTutor());
        assertEquals("Buen avance", dto.getTutor().getObservations());
        assertEquals(1, dto.getPanelists().size());
        assertEquals("Carla Zamora", dto.getPanelists().get(0).getNombrePanelist());
        assertEquals(85.0, dto.getPanelists().get(0).getGradePanelist());
        assertEquals(1, dto.getPanelists().get(0).getCriteria().size());
        assertEquals("Aprobado", dto.getCoordinator().getResult());
        assertEquals(89.0, dto.getCoordinator().getGradeFinal());
    }
}
