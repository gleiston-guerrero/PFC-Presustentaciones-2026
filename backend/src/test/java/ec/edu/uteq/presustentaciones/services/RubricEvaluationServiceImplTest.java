package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.ScaleCriterioDTO;
import ec.edu.uteq.presustentaciones.dto.EvaluationRubricRequest;
import ec.edu.uteq.presustentaciones.dto.EvaluationRubricResponse;
import ec.edu.uteq.presustentaciones.dto.ObservacionesSubmissionDTO;
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

    @Mock private EvaluationCriterioRepository evalCriterioRepo;
    @Mock private CriterioRubricRepository criterioRepo;
    @Mock private PanelistRepository panelistRepo;
    @Mock private SubmissionRepository submissionRepo;
    @Mock private RubricRepository rubricRepo;
    @Mock private TutorRepository tutorRepo;
    @Mock private EvaluationFinalRepository evaluationFinalRepo;
    @Mock private EvaluationPanelistRepository javaEvaluationPanelistRepo;
    @Mock private EvaluatorRepository evaluatorRepo;
    @Mock private TipoEvaluatorRepository tipoEvaluatorRepo;
    @Mock private SubmissionAccessService submissionAccessService;
    @Mock private PermissionService permissionService;

    @InjectMocks
    private RubricEvaluationServiceImpl service;

    private static Object[] fila(long evaluatorId, double suma) {
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
    void registerEvaluationRechazaAPanelistQueRegistraANombreDeOtro() {
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
        when(permissionService.esPropioTeacher(any(), eq(1L))).thenReturn(false);
        when(permissionService.tienePermission(any(), eq("EVALUACION_CALIFICAR"))).thenReturn(false);

        EvaluationRubricRequest req = new EvaluationRubricRequest();
        req.setSubmissionId(7L);
        req.setPanelistId(3L);
        req.setRubricId(1L);
        req.setCriterios(List.of(new ScaleCriterioDTO()));

        assertThrows(AccessDeniedException.class, () -> service.registerEvaluation(req));
    }

    @Test
    void obtainEvaluationPanelistPropagaAccessDeniedSiSubmissionAccessServiceLoRechaza() {
        Submission submission = Submission.builder().id(7L).build();
        Panelist panelist = Panelist.builder().id(3L).submission(submission).build();
        when(panelistRepo.findById(3L)).thenReturn(Optional.of(panelist));
        org.mockito.Mockito.doThrow(new AccessDeniedException("No tienes permiso para acceder a la información de esta solicitud"))
                .when(submissionAccessService).validateAcceso(submission, "EVALUACION_CALIFICAR");

        assertThrows(AccessDeniedException.class, () -> service.obtainEvaluationPanelist(7L, 3L));
    }

    @Test
    void calculateNotaTribunalPromediaLasSumasDeCadaPanelist() {
        stubSubmission(10L);
        // 3 panelists, cada uno con su suma de notas ponderadas por criterio: (90 + 85 + 78) / 3 = 84.33
        List<Object[]> filas = Arrays.asList(fila(1L, 90.0), fila(2L, 85.0), fila(3L, 78.0));
        when(evalCriterioRepo.sumaPorEvaluator(10L)).thenReturn(filas);

        Double nota = service.calculateNotaTribunal(10L);

        assertEquals(84.33, nota, 0.001);
    }

    @Test
    void calculateNotaTribunalRedondeaADosDecimales() {
        stubSubmission(11L);
        List<Object[]> filas = Arrays.asList(fila(1L, 100.0), fila(2L, 100.0), fila(3L, 66.0));
        when(evalCriterioRepo.sumaPorEvaluator(11L)).thenReturn(filas);

        Double nota = service.calculateNotaTribunal(11L);

        // (100 + 100 + 66) / 3 = 88.666... -> 88.67
        assertEquals(88.67, nota);
    }

    @Test
    void calculateNotaTribunalRetornaNullSiNoHayEvaluations() {
        stubSubmission(12L);
        when(evalCriterioRepo.sumaPorEvaluator(12L)).thenReturn(Collections.emptyList());

        assertNull(service.calculateNotaTribunal(12L));
    }

    @Test
    void calculateNotaTribunalConUnSoloPanelistDevuelveSuPropiaNota() {
        stubSubmission(13L);
        when(evalCriterioRepo.sumaPorEvaluator(13L)).thenReturn(Collections.singletonList(fila(1L, 95.5)));

        assertEquals(95.5, service.calculateNotaTribunal(13L));
    }

    // ── registerEvaluation: validaciones y flujo completo ──────────────────

    private void autenticarComoAdmin() {
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
    void registerEvaluationLanzaSiSubmissionNoExiste() {
        when(submissionRepo.findById(7L)).thenReturn(Optional.empty());
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.registerEvaluation(requestBase(7L, 3L, 1L)));
        assertTrue(ex.getMessage().contains("Solicitud no encontrada"));
    }

    @Test
    void registerEvaluationLanzaSiPanelistNoExiste() {
        when(submissionRepo.findById(7L)).thenReturn(Optional.of(Submission.builder().id(7L).build()));
        when(panelistRepo.findById(3L)).thenReturn(Optional.empty());
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.registerEvaluation(requestBase(7L, 3L, 1L)));
        assertTrue(ex.getMessage().contains("Jurado no encontrado"));
    }

    @Test
    void registerEvaluationLanzaSiPanelistNoPerteneceALaSubmission() {
        Submission submission = Submission.builder().id(7L).build();
        Submission otraSubmission = Submission.builder().id(999L).build();
        when(submissionRepo.findById(7L)).thenReturn(Optional.of(submission));
        when(panelistRepo.findById(3L)).thenReturn(Optional.of(Panelist.builder().id(3L).submission(otraSubmission).build()));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.registerEvaluation(requestBase(7L, 3L, 1L)));
        assertTrue(ex.getMessage().contains("no pertenece a esta solicitud"));
    }

    private Panelist panelistDeSubmission(Submission submission, Teacher teacher) {
        return Panelist.builder().id(3L).submission(submission).teacher(teacher)
                .rolePanelist(RolePanelist.builder().codigo("PRESIDENTE").nombre("Presidente").build()).build();
    }

    @Test
    void registerEvaluationLanzaSiRubricNoExiste() {
        Submission submission = Submission.builder().id(7L).build();
        Teacher teacher = Teacher.builder().id(1L).appUser(AppUser.builder().id(50L).build()).build();
        Panelist panelist = panelistDeSubmission(submission, teacher);
        when(submissionRepo.findById(7L)).thenReturn(Optional.of(submission));
        when(panelistRepo.findById(3L)).thenReturn(Optional.of(panelist));
        autenticarComoAdmin();
        when(rubricRepo.findById(1L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.registerEvaluation(requestBase(7L, 3L, 1L)));
        assertTrue(ex.getMessage().contains("Rúbrica no encontrada"));
    }

    @Test
    void registerEvaluationLanzaSiRubricSinCriterios() {
        Submission submission = Submission.builder().id(7L).build();
        Teacher teacher = Teacher.builder().id(1L).appUser(AppUser.builder().id(50L).build()).build();
        Panelist panelist = panelistDeSubmission(submission, teacher);
        when(submissionRepo.findById(7L)).thenReturn(Optional.of(submission));
        when(panelistRepo.findById(3L)).thenReturn(Optional.of(panelist));
        autenticarComoAdmin();
        when(rubricRepo.findById(1L)).thenReturn(Optional.of(Rubric.builder().id(1L).build()));
        when(criterioRepo.findByRubricIdOrderByOrdenAsc(1L)).thenReturn(List.of());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.registerEvaluation(requestBase(7L, 3L, 1L)));
        assertTrue(ex.getMessage().contains("no tiene criterios"));
    }

    @Test
    void registerEvaluationLanzaSiNoEvaluaTodosLosCriterios() {
        Submission submission = Submission.builder().id(7L).build();
        Teacher teacher = Teacher.builder().id(1L).appUser(AppUser.builder().id(50L).build()).build();
        Panelist panelist = panelistDeSubmission(submission, teacher);
        CriterioRubric c1 = CriterioRubric.builder().id(1L).nombre("Claridad").ponderacion(50.0).build();
        CriterioRubric c2 = CriterioRubric.builder().id(2L).nombre("Rigor").ponderacion(50.0).build();
        when(submissionRepo.findById(7L)).thenReturn(Optional.of(submission));
        when(panelistRepo.findById(3L)).thenReturn(Optional.of(panelist));
        autenticarComoAdmin();
        when(rubricRepo.findById(1L)).thenReturn(Optional.of(Rubric.builder().id(1L).build()));
        when(criterioRepo.findByRubricIdOrderByOrdenAsc(1L)).thenReturn(List.of(c1, c2));

        EvaluationRubricRequest req = requestBase(7L, 3L, 1L);
        ScaleCriterioDTO dto1 = new ScaleCriterioDTO();
        dto1.setCriterioId(1L);
        dto1.setScale(80);
        req.setCriterios(List.of(dto1)); // solo 1 de 2

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.registerEvaluation(req));
        assertTrue(ex.getMessage().contains("Debe evaluar todos los 2 criterios"));
    }

    @Test
    void registerEvaluationLanzaSiScaleFueraDeRango() {
        Submission submission = Submission.builder().id(7L).build();
        Teacher teacher = Teacher.builder().id(1L).appUser(AppUser.builder().id(50L).build()).build();
        Panelist panelist = panelistDeSubmission(submission, teacher);
        CriterioRubric c1 = CriterioRubric.builder().id(1L).nombre("Claridad").ponderacion(100.0).build();
        when(submissionRepo.findById(7L)).thenReturn(Optional.of(submission));
        when(panelistRepo.findById(3L)).thenReturn(Optional.of(panelist));
        autenticarComoAdmin();
        when(rubricRepo.findById(1L)).thenReturn(Optional.of(Rubric.builder().id(1L).build()));
        when(criterioRepo.findByRubricIdOrderByOrdenAsc(1L)).thenReturn(List.of(c1));

        EvaluationRubricRequest req = requestBase(7L, 3L, 1L);
        ScaleCriterioDTO dto1 = new ScaleCriterioDTO();
        dto1.setCriterioId(1L);
        dto1.setScale(150);
        req.setCriterios(List.of(dto1));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.registerEvaluation(req));
        assertTrue(ex.getMessage().contains("Escala inválida"));
    }

    @Test
    void registerEvaluationLanzaSiCriterioDelDtoNoExisteEnLaRubric() {
        Submission submission = Submission.builder().id(7L).build();
        Teacher teacher = Teacher.builder().id(1L).appUser(AppUser.builder().id(50L).build()).build();
        Panelist panelist = panelistDeSubmission(submission, teacher);
        CriterioRubric c1 = CriterioRubric.builder().id(1L).nombre("Claridad").ponderacion(100.0).build();
        when(submissionRepo.findById(7L)).thenReturn(Optional.of(submission));
        when(panelistRepo.findById(3L)).thenReturn(Optional.of(panelist));
        autenticarComoAdmin();
        when(rubricRepo.findById(1L)).thenReturn(Optional.of(Rubric.builder().id(1L).build()));
        when(criterioRepo.findByRubricIdOrderByOrdenAsc(1L)).thenReturn(List.of(c1));
        when(evaluatorRepo.findBySubmissionIdAndTeacherIdAndTipoEvaluatorCodigo(7L, 1L, "JURADO"))
                .thenReturn(Optional.of(Evaluator.builder().id(20L).build()));

        EvaluationRubricRequest req = requestBase(7L, 3L, 1L);
        ScaleCriterioDTO dto1 = new ScaleCriterioDTO();
        dto1.setCriterioId(999L); // no coincide con ningún criterio de la rúbrica
        dto1.setScale(80);
        req.setCriterios(List.of(dto1));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.registerEvaluation(req));
        assertTrue(ex.getMessage().contains("Criterio no encontrado"));
    }

    @Test
    void registerEvaluationLanzaSiTipoEvaluatorPanelistNoConfigurado() {
        Submission submission = Submission.builder().id(7L).build();
        Teacher teacher = Teacher.builder().id(1L).appUser(AppUser.builder().id(50L).build()).build();
        Panelist panelist = panelistDeSubmission(submission, teacher);
        CriterioRubric c1 = CriterioRubric.builder().id(1L).nombre("Claridad").ponderacion(100.0).build();
        when(submissionRepo.findById(7L)).thenReturn(Optional.of(submission));
        when(panelistRepo.findById(3L)).thenReturn(Optional.of(panelist));
        autenticarComoAdmin();
        when(rubricRepo.findById(1L)).thenReturn(Optional.of(Rubric.builder().id(1L).build()));
        when(criterioRepo.findByRubricIdOrderByOrdenAsc(1L)).thenReturn(List.of(c1));
        when(evaluatorRepo.findBySubmissionIdAndTeacherIdAndTipoEvaluatorCodigo(7L, 1L, "JURADO"))
                .thenReturn(Optional.empty());
        when(tipoEvaluatorRepo.findByCodigo("JURADO")).thenReturn(Optional.empty());

        EvaluationRubricRequest req = requestBase(7L, 3L, 1L);
        ScaleCriterioDTO dto1 = new ScaleCriterioDTO();
        dto1.setCriterioId(1L);
        dto1.setScale(80);
        req.setCriterios(List.of(dto1));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.registerEvaluation(req));
        assertTrue(ex.getMessage().contains("Tipo evaluador JURADO no configurado"));
    }

    @Test
    void registerEvaluationExitosaCreaEvaluatorNuevoYCalculaNota() {
        Submission submission = Submission.builder().id(7L).build();
        Teacher teacher = Teacher.builder().id(1L).appUser(AppUser.builder().id(50L).nombre("Ana").apellido("Ruiz").build()).build();
        Panelist panelist = panelistDeSubmission(submission, teacher);
        CriterioRubric c1 = CriterioRubric.builder().id(1L).nombre("Claridad").ponderacion(60.0).build();
        CriterioRubric c2 = CriterioRubric.builder().id(2L).nombre("Rigor").ponderacion(40.0).build();
        when(submissionRepo.findById(7L)).thenReturn(Optional.of(submission));
        when(panelistRepo.findById(3L)).thenReturn(Optional.of(panelist));
        autenticarComoAdmin();
        when(rubricRepo.findById(1L)).thenReturn(Optional.of(Rubric.builder().id(1L).build()));
        when(criterioRepo.findByRubricIdOrderByOrdenAsc(1L)).thenReturn(List.of(c1, c2));
        when(evaluatorRepo.findBySubmissionIdAndTeacherIdAndTipoEvaluatorCodigo(7L, 1L, "JURADO"))
                .thenReturn(Optional.empty());
        when(tipoEvaluatorRepo.findByCodigo("JURADO"))
                .thenReturn(Optional.of(TipoEvaluator.builder().id((short) 1).codigo("JURADO").build()));
        when(evaluatorRepo.save(any(Evaluator.class))).thenAnswer(inv -> {
            Evaluator e = inv.getArgument(0);
            e.setId(20L);
            return e;
        });
        when(evalCriterioRepo.save(any(EvaluationCriterio.class))).thenAnswer(inv -> inv.getArgument(0));
        when(panelistRepo.findBySubmissionId(7L)).thenReturn(List.of(panelist));
        when(evalCriterioRepo.sumaPorEvaluator(7L)).thenReturn(List.<Object[]>of(fila(20L, 92.0)));

        EvaluationRubricRequest req = requestBase(7L, 3L, 1L);
        ScaleCriterioDTO dto1 = new ScaleCriterioDTO();
        dto1.setCriterioId(1L);
        dto1.setScale(100); // nota = 60 * 100 / 100 = 60
        ScaleCriterioDTO dto2 = new ScaleCriterioDTO();
        dto2.setCriterioId(2L);
        dto2.setScale(80); // nota = 40 * 80 / 100 = 32
        req.setCriterios(List.of(dto1, dto2));

        EvaluationRubricResponse resp = service.registerEvaluation(req);

        assertEquals(92.0, resp.getNotaTotalPanelist()); // 60 + 32
        assertEquals("Ana Ruiz", resp.getNombrePanelist());
        // tribunalCompleto no se afirma aqui: el evaluator recien creado no se re-stubea para
        // la segunda consulta que hace buildResponse -- ese caso ya se cubre en
        // obtainEvaluationsSubmissionMarcaTribunalIncompletoSiFaltaUnPanelist.
        assertEquals(2, resp.getDetalles().size());
        verify(evalCriterioRepo).deleteBySubmissionIdAndEvaluatorId(7L, 20L);
        verify(evaluatorRepo).save(any(Evaluator.class));
    }

    @Test
    void registerEvaluationReusaEvaluatorExistenteYPermiteReevaluation() {
        Submission submission = Submission.builder().id(7L).build();
        Teacher teacher = Teacher.builder().id(1L).appUser(AppUser.builder().id(50L).nombre("Ana").apellido("Ruiz").build()).build();
        Panelist panelist = panelistDeSubmission(submission, teacher);
        CriterioRubric c1 = CriterioRubric.builder().id(1L).nombre("Claridad").ponderacion(100.0).build();
        Evaluator evaluatorExistente = Evaluator.builder().id(20L).build();
        when(submissionRepo.findById(7L)).thenReturn(Optional.of(submission));
        when(panelistRepo.findById(3L)).thenReturn(Optional.of(panelist));
        autenticarComoAdmin();
        when(rubricRepo.findById(1L)).thenReturn(Optional.of(Rubric.builder().id(1L).build()));
        when(criterioRepo.findByRubricIdOrderByOrdenAsc(1L)).thenReturn(List.of(c1));
        when(evaluatorRepo.findBySubmissionIdAndTeacherIdAndTipoEvaluatorCodigo(7L, 1L, "JURADO"))
                .thenReturn(Optional.of(evaluatorExistente));
        when(evalCriterioRepo.save(any(EvaluationCriterio.class))).thenAnswer(inv -> inv.getArgument(0));
        when(panelistRepo.findBySubmissionId(7L)).thenReturn(List.of(panelist));
        when(evalCriterioRepo.existsBySubmissionIdAndEvaluatorId(7L, 20L)).thenReturn(true);
        when(evalCriterioRepo.sumaPorEvaluator(7L)).thenReturn(List.<Object[]>of(fila(20L, 50.0)));

        EvaluationRubricRequest req = requestBase(7L, 3L, 1L);
        ScaleCriterioDTO dto1 = new ScaleCriterioDTO();
        dto1.setCriterioId(1L);
        dto1.setScale(50);
        req.setCriterios(List.of(dto1));

        service.registerEvaluation(req);

        verify(evaluatorRepo, never()).save(any());
        verify(evalCriterioRepo).deleteBySubmissionIdAndEvaluatorId(7L, 20L);
    }

    // ── obtainEvaluationsSubmission ─────────────────────────────────────────

    @Test
    void obtainEvaluationsSubmissionLanzaSiSubmissionNoExiste() {
        when(submissionRepo.findById(7L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> service.obtainEvaluationsSubmission(7L));
    }

    @Test
    void obtainEvaluationsSubmissionMarcaTribunalIncompletoSiFaltaUnPanelist() {
        Submission submission = Submission.builder().id(7L).build();
        Teacher teacher1 = Teacher.builder().id(1L).appUser(AppUser.builder().id(50L).nombre("A").apellido("B").build()).build();
        Teacher teacher2 = Teacher.builder().id(2L).appUser(AppUser.builder().id(51L).nombre("C").apellido("D").build()).build();
        Panelist j1 = panelistDeSubmission(submission, teacher1);
        Panelist j2 = Panelist.builder().id(4L).submission(submission).teacher(teacher2)
                .rolePanelist(RolePanelist.builder().codigo("VOCAL_1").build()).build();
        when(submissionRepo.findById(7L)).thenReturn(Optional.of(submission));
        when(panelistRepo.findBySubmissionId(7L)).thenReturn(List.of(j1, j2));
        when(evaluatorRepo.findBySubmissionIdAndTeacherIdAndTipoEvaluatorCodigo(7L, 1L, "JURADO"))
                .thenReturn(Optional.of(Evaluator.builder().id(20L).build()));
        when(evalCriterioRepo.findBySubmissionIdAndEvaluatorId(7L, 20L)).thenReturn(List.of());
        // teacher2 (panelist j2) aun no tiene evaluator -> tribunal incompleto
        when(evaluatorRepo.findBySubmissionIdAndTeacherIdAndTipoEvaluatorCodigo(7L, 2L, "JURADO"))
                .thenReturn(Optional.empty());

        List<EvaluationRubricResponse> resultado = service.obtainEvaluationsSubmission(7L);

        assertEquals(2, resultado.size());
        assertFalse(resultado.get(0).isTribunalCompleto());
    }

    // ── obtainObservacionesSubmission ────────────────────────────────────────

    @Test
    void obtainObservacionesSubmissionLanzaSiSubmissionNoExiste() {
        when(submissionRepo.findById(7L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> service.obtainObservacionesSubmission(7L));
    }

    @Test
    void obtainObservacionesSubmissionDevuelveTodoVacioSinTutorPanelistsNiEvaluationFinal() {
        Submission submission = Submission.builder().id(7L).tituloTopic("Tema").build();
        when(submissionRepo.findById(7L)).thenReturn(Optional.of(submission));
        when(tutorRepo.findBySubmissionId(7L)).thenReturn(Optional.empty());
        when(panelistRepo.findBySubmissionId(7L)).thenReturn(List.of());
        when(javaEvaluationPanelistRepo.findBySubmissionId(7L)).thenReturn(List.of());
        when(evaluationFinalRepo.findBySubmissionId(7L)).thenReturn(Optional.empty());

        ObservacionesSubmissionDTO dto = service.obtainObservacionesSubmission(7L);

        assertEquals("", dto.getNombreStudent());
        assertNull(dto.getTutor());
        assertTrue(dto.getPanelists().isEmpty());
        assertNull(dto.getCoordinador());
    }

    @Test
    void obtainObservacionesSubmissionArmaElReporteCompleto() {
        AppUser appUserStudent = AppUser.builder().id(1L).nombre("Ana").apellido("Torres").build();
        Student student = Student.builder().id(1L).appUser(appUserStudent).build();
        Submission submission = Submission.builder().id(7L).tituloTopic("Tema X").student(student).build();

        AppUser appUserTutor = AppUser.builder().id(60L).nombre("Luis").apellido("Mora").build();
        Teacher teacherTutor = Teacher.builder().id(5L).appUser(appUserTutor).build();
        Tutor tutor = Tutor.builder().id(9L).teacher(teacherTutor).observaciones("Buen avance").build();

        AppUser appUserPanelist = AppUser.builder().id(50L).nombre("Carla").apellido("Zamora").build();
        Teacher teacherPanelist = Teacher.builder().id(1L).appUser(appUserPanelist).build();
        Panelist panelist = panelistDeSubmission(submission, teacherPanelist);
        EvaluationPanelist evalPanelist = EvaluationPanelist.builder().id(3L).panelist(panelist)
                .notaPanelist(85.0).observaciones("Bien").resultado("APROBADO")
                .comentarioPreestablecido("Cumple").build();

        CriterioRubric criterio = CriterioRubric.builder().id(1L).nombre("Claridad").ponderacion(100.0).build();
        Evaluator evaluator = Evaluator.builder().id(20L).build();
        EvaluationCriterio ec = EvaluationCriterio.builder().id(1L).criterio(criterio).scale(90)
                .notaObtenida(90.0).observacionAuto("Excelente").build();

        EvaluationFinal evaluationFinal = EvaluationFinal.builder().id(1L)
                .observaciones("Todo bien").notaInstructor(88.0).notaFinal(89.0)
                .resultado(ResultadoEvaluation.builder().codigo("APROBADO").nombre("Aprobado").build())
                .build();

        when(submissionRepo.findById(7L)).thenReturn(Optional.of(submission));
        when(tutorRepo.findBySubmissionId(7L)).thenReturn(Optional.of(tutor));
        when(panelistRepo.findBySubmissionId(7L)).thenReturn(List.of(panelist));
        when(javaEvaluationPanelistRepo.findBySubmissionId(7L)).thenReturn(List.of(evalPanelist));
        when(evaluatorRepo.findBySubmissionIdAndTeacherIdAndTipoEvaluatorCodigo(7L, 1L, "JURADO"))
                .thenReturn(Optional.of(evaluator));
        when(evalCriterioRepo.findBySubmissionIdAndEvaluatorId(7L, 20L)).thenReturn(List.of(ec));
        when(evaluationFinalRepo.findBySubmissionId(7L)).thenReturn(Optional.of(evaluationFinal));

        ObservacionesSubmissionDTO dto = service.obtainObservacionesSubmission(7L);

        assertEquals("Ana Torres", dto.getNombreStudent());
        assertEquals("Luis Mora", dto.getTutor().getNombreTutor());
        assertEquals("Buen avance", dto.getTutor().getObservaciones());
        assertEquals(1, dto.getPanelists().size());
        assertEquals("Carla Zamora", dto.getPanelists().get(0).getNombrePanelist());
        assertEquals(85.0, dto.getPanelists().get(0).getNotaPanelist());
        assertEquals(1, dto.getPanelists().get(0).getCriterios().size());
        assertEquals("Aprobado", dto.getCoordinador().getResultado());
        assertEquals(89.0, dto.getCoordinador().getNotaFinal());
    }
}
