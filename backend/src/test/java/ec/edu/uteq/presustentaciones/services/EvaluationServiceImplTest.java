package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.AverageEvaluationResult;
import ec.edu.uteq.presustentaciones.entities.*;
import ec.edu.uteq.presustentaciones.repositories.*;
import ec.edu.uteq.presustentaciones.security.service.SubmissionAccessService;
import ec.edu.uteq.presustentaciones.security.service.CurrentAppUserService;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * calculatePromedioSp() invoca sp_calculate_promedio_evaluation (Fase 3 / Criterio P1) via
 * EvaluationRepository.calculatePromedioEvaluation() (@NamedStoredProcedureQuery) -- sin test
 * dedicado pese a que COVERAGE.md lo declara explicitamente como brecha ("solo se verifico
 * manualmente contra Docker"). evaluarSubmission() tambien tiene reglas de negocio reales
 * (pesos deben sumar 100, notas entre 0-10) sin cobertura.
 */
@ExtendWith(MockitoExtension.class)
class EvaluationServiceImplTest {

    @Mock private EvaluationFinalRepository evaluationRepository;
    @Mock private SubmissionRepository submissionRepository;
    @Mock private RubricRepository rubricRepository;
    @Mock private NotificationService notificationService;
    @Mock private StatusSubmissionRepository statusSubmissionRepository;
    @Mock private ResultEvaluationRepository resultEvaluationRepository;
    @Mock private EvaluationRepository evaluationSpRepository;
    @Mock private CurrentAppUserService currentAppUserService;
    @Mock private SubmissionAccessService submissionAccessService;
    @Mock private PermissionService permissionService;

    @InjectMocks
    private EvaluationServiceImpl evaluationService;

    private Submission submission;
    private AppUser appUserStudent;

    @BeforeEach
    void setUp() {
        appUserStudent = AppUser.builder().id(10L).nombre("Ana").apellido("Torres").build();
        Student student = Student.builder().id(3L).appUser(appUserStudent).build();
        submission = Submission.builder().id(7L).student(student).tituloTopic("Sistema X").build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void calculateAverageSpLanzaExcepcionSiLaSubmissionNoExists() {
        when(submissionRepository.findById(7L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> evaluationService.calculateAverageSp(7L));
    }

    @Test
    void calculateAverageSpCreaLaRowBaseSiNoExistsYLuegoInvocaElProcedimiento() {
        when(submissionRepository.findById(7L)).thenReturn(Optional.of(submission));
        when(evaluationSpRepository.findBySubmissionId(7L)).thenReturn(Optional.empty());
        when(evaluationRepository.findBySubmissionId(7L)).thenReturn(Optional.empty());
        when(evaluationSpRepository.save(any(Evaluation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(evaluationSpRepository.calculateAverageEvaluation(7L))
                .thenReturn(List.of(new AverageEvaluationResult(7L, 8.5, "APROBADO")));

        AverageEvaluationResult result = evaluationService.calculateAverageSp(7L);

        assertEquals(7L, result.getSubmissionId());
        assertEquals(8.5, result.getGradeFinal());
        assertEquals("APROBADO", result.getStatusResult());
        verify(evaluationSpRepository).save(any(Evaluation.class));
        verify(evaluationSpRepository).calculateAverageEvaluation(7L);
    }

    @Test
    void calculateAverageSpNoCreaRowBaseSiYaExists() {
        Evaluation existing = Evaluation.builder().id(1L).submission(submission).gradeInstructor(8.0).build();
        when(submissionRepository.findById(7L)).thenReturn(Optional.of(submission));
        when(evaluationSpRepository.findBySubmissionId(7L)).thenReturn(Optional.of(existing));
        when(evaluationSpRepository.calculateAverageEvaluation(7L))
                .thenReturn(List.of(new AverageEvaluationResult(7L, 6.0, "REPROBADO")));

        evaluationService.calculateAverageSp(7L);

        verify(evaluationSpRepository, never()).save(any(Evaluation.class));
    }

    @Test
    void calculateAverageSpLanzaExcepcionSiElProcedimientoNoDevuelveFilas() {
        when(submissionRepository.findById(7L)).thenReturn(Optional.of(submission));
        when(evaluationSpRepository.findBySubmissionId(7L)).thenReturn(
                Optional.of(Evaluation.builder().id(1L).submission(submission).build()));
        when(evaluationSpRepository.calculateAverageEvaluation(7L)).thenReturn(List.of());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> evaluationService.calculateAverageSp(7L));
        assertTrue(ex.getMessage().contains("no devolvió resultado"));
    }

    @Test
    void evaluateSubmissionRechazaPesosQueNoSumanCien() {
        when(submissionRepository.findById(7L)).thenReturn(Optional.of(submission));
        when(rubricRepository.findById(1L)).thenReturn(Optional.of(Rubric.builder().id(1L).build()));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                evaluationService.evaluateSubmission(7L, 1L, 8.0, 7.0, "obs", 50.0, 40.0));
        assertTrue(ex.getMessage().contains("deben sumar 100"));
    }

    @Test
    void evaluateSubmissionRechazaNotasFueraDeRange() {
        when(submissionRepository.findById(7L)).thenReturn(Optional.of(submission));
        when(rubricRepository.findById(1L)).thenReturn(Optional.of(Rubric.builder().id(1L).build()));

        assertThrows(RuntimeException.class, () ->
                evaluationService.evaluateSubmission(7L, 1L, 11.0, 7.0, "obs", 60.0, 40.0));
    }

    @Test
    void evaluateSubmissionCalculaGradeFinalYCambiaStatusACalificada() {
        when(submissionRepository.findById(7L)).thenReturn(Optional.of(submission));
        when(rubricRepository.findById(1L)).thenReturn(Optional.of(Rubric.builder().id(1L).build()));
        when(resultEvaluationRepository.findByCode("APROBADO"))
                .thenReturn(Optional.of(ResultEvaluation.builder().code("APROBADO").nombre("Aprobado").build()));
        when(evaluationRepository.save(any(EvaluationFinal.class))).thenAnswer(inv -> inv.getArgument(0));
        when(statusSubmissionRepository.findByCode("CALIFICADA"))
                .thenReturn(Optional.of(StatusSubmission.builder().code("CALIFICADA").nombre("Calificada").build()));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        EvaluationFinal result = evaluationService.evaluateSubmission(7L, 1L, 8.0, 9.0, "Excelente", 60.0, 40.0);

        assertEquals("CALIFICADA", submission.getStatus().getCode());
        assertEquals("APROBADO", result.getResult().getCode());
        verify(notificationService).createNotification(eq(10L), any());
    }

    @Test
    void listByStudentRechazaConsultaDeOtroStudent() {
        // Caso IDOR: el student autenticado (id 99) intenta ver las evaluations del
        // student 3 cambiando el id en la URL.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("otro.estudiante@uteq.edu.ec", null,
                        org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_ESTUDIANTE")));
        when(currentAppUserService.studentIdOrNull()).thenReturn(99L);

        assertThrows(AccessDeniedException.class, () -> evaluationService.listByStudent(3L));
    }

    @Test
    void listByStudentPermiteConsultarLaPropiaInformacion() {
        // Caso permitido: el student autenticado consulta sus propias evaluations.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("estudiante@uteq.edu.ec", null,
                        org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_ESTUDIANTE")));
        when(currentAppUserService.studentIdOrNull()).thenReturn(3L);
        when(evaluationRepository.findByStudentId(3L)).thenReturn(List.of());

        assertDoesNotThrow(() -> evaluationService.listByStudent(3L));
    }

    @Test
    void listByStudentPermiteAAdminConsultarCualquierStudent() {
        // Caso administrativo: ADMIN/COORDINADOR conservan su acceso completo.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@uteq.edu.ec", null,
                        org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_ADMIN")));
        when(evaluationRepository.findByStudentId(3L)).thenReturn(List.of());

        assertDoesNotThrow(() -> evaluationService.listByStudent(3L));
        verify(currentAppUserService, never()).studentIdOrNull();
    }

    @Test
    void listByAppUserRechazaConsultaDeOtroAppUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("otro@uteq.edu.ec", null,
                        org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_DOCENTE")));
        when(currentAppUserService.appUser()).thenReturn(AppUser.builder().id(99L).build());

        assertThrows(AccessDeniedException.class, () -> evaluationService.listByAppUser(10L));
    }

    @Test
    void searchBySubmissionPropagaAccessDeniedSiSubmissionAccessServiceLoRechaza() {
        // Caso IDOR de lectura: la evaluación existe pero SubmissionAccessService decide que
        // este appUser no participa en la submission.
        EvaluationFinal evaluation = EvaluationFinal.builder().id(1L).submission(submission).build();
        when(evaluationRepository.findBySubmissionId(7L)).thenReturn(Optional.of(evaluation));
        doThrow(new AccessDeniedException("No tienes permiso para acceder a la información de esta solicitud"))
                .when(submissionAccessService).validateAccess(submission, "EVALUACION_CALIFICAR");

        assertThrows(AccessDeniedException.class, () -> evaluationService.searchBySubmission(7L));
    }

    @Test
    void generateCommentByRangeRetornaEmptySiGradeEsNull() {
        assertEquals("", evaluationService.generateCommentByRange(null));
    }

    @Test
    void generateCommentByRangeDistingueLosTresNiveles() {
        assertTrue(evaluationService.generateCommentByRange(2.0).contains("falencias significativas"));
        assertTrue(evaluationService.generateCommentByRange(5.0).contains("aspectos que requieren mejoras"));
        assertTrue(evaluationService.generateCommentByRange(9.0).contains("cumple satisfactoriamente"));
    }
}
