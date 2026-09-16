package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.PromedioEvaluationResult;
import ec.edu.uteq.presustentaciones.entities.*;
import ec.edu.uteq.presustentaciones.repositories.*;
import ec.edu.uteq.presustentaciones.security.service.SubmissionAccessService;
import ec.edu.uteq.presustentaciones.security.service.AppUserActualService;
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
    @Mock private EstadoSubmissionRepository estadoSubmissionRepository;
    @Mock private ResultadoEvaluationRepository resultadoEvaluationRepository;
    @Mock private EvaluationRepository evaluationSpRepository;
    @Mock private AppUserActualService appUserActualService;
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
    void calculatePromedioSpLanzaExcepcionSiLaSubmissionNoExiste() {
        when(submissionRepository.findById(7L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> evaluationService.calculatePromedioSp(7L));
    }

    @Test
    void calculatePromedioSpCreaLaFilaBaseSiNoExisteYLuegoInvocaElProcedimiento() {
        when(submissionRepository.findById(7L)).thenReturn(Optional.of(submission));
        when(evaluationSpRepository.findBySubmissionId(7L)).thenReturn(Optional.empty());
        when(evaluationRepository.findBySubmissionId(7L)).thenReturn(Optional.empty());
        when(evaluationSpRepository.save(any(Evaluation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(evaluationSpRepository.calculatePromedioEvaluation(7L))
                .thenReturn(List.of(new PromedioEvaluationResult(7L, 8.5, "APROBADO")));

        PromedioEvaluationResult resultado = evaluationService.calculatePromedioSp(7L);

        assertEquals(7L, resultado.getSubmissionId());
        assertEquals(8.5, resultado.getNotaFinal());
        assertEquals("APROBADO", resultado.getEstadoResultado());
        verify(evaluationSpRepository).save(any(Evaluation.class));
        verify(evaluationSpRepository).calculatePromedioEvaluation(7L);
    }

    @Test
    void calculatePromedioSpNoCreaFilaBaseSiYaExiste() {
        Evaluation existente = Evaluation.builder().id(1L).submission(submission).notaInstructor(8.0).build();
        when(submissionRepository.findById(7L)).thenReturn(Optional.of(submission));
        when(evaluationSpRepository.findBySubmissionId(7L)).thenReturn(Optional.of(existente));
        when(evaluationSpRepository.calculatePromedioEvaluation(7L))
                .thenReturn(List.of(new PromedioEvaluationResult(7L, 6.0, "REPROBADO")));

        evaluationService.calculatePromedioSp(7L);

        verify(evaluationSpRepository, never()).save(any(Evaluation.class));
    }

    @Test
    void calculatePromedioSpLanzaExcepcionSiElProcedimientoNoDevuelveFilas() {
        when(submissionRepository.findById(7L)).thenReturn(Optional.of(submission));
        when(evaluationSpRepository.findBySubmissionId(7L)).thenReturn(
                Optional.of(Evaluation.builder().id(1L).submission(submission).build()));
        when(evaluationSpRepository.calculatePromedioEvaluation(7L)).thenReturn(List.of());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> evaluationService.calculatePromedioSp(7L));
        assertTrue(ex.getMessage().contains("no devolvió resultado"));
    }

    @Test
    void evaluarSubmissionRechazaPesosQueNoSumanCien() {
        when(submissionRepository.findById(7L)).thenReturn(Optional.of(submission));
        when(rubricRepository.findById(1L)).thenReturn(Optional.of(Rubric.builder().id(1L).build()));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                evaluationService.evaluarSubmission(7L, 1L, 8.0, 7.0, "obs", 50.0, 40.0));
        assertTrue(ex.getMessage().contains("deben sumar 100"));
    }

    @Test
    void evaluarSubmissionRechazaNotasFueraDeRango() {
        when(submissionRepository.findById(7L)).thenReturn(Optional.of(submission));
        when(rubricRepository.findById(1L)).thenReturn(Optional.of(Rubric.builder().id(1L).build()));

        assertThrows(RuntimeException.class, () ->
                evaluationService.evaluarSubmission(7L, 1L, 11.0, 7.0, "obs", 60.0, 40.0));
    }

    @Test
    void evaluarSubmissionCalculaNotaFinalYCambiaEstadoACalificada() {
        when(submissionRepository.findById(7L)).thenReturn(Optional.of(submission));
        when(rubricRepository.findById(1L)).thenReturn(Optional.of(Rubric.builder().id(1L).build()));
        when(resultadoEvaluationRepository.findByCodigo("APROBADO"))
                .thenReturn(Optional.of(ResultadoEvaluation.builder().codigo("APROBADO").nombre("Aprobado").build()));
        when(evaluationRepository.save(any(EvaluationFinal.class))).thenAnswer(inv -> inv.getArgument(0));
        when(estadoSubmissionRepository.findByCodigo("CALIFICADA"))
                .thenReturn(Optional.of(EstadoSubmission.builder().codigo("CALIFICADA").nombre("Calificada").build()));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        EvaluationFinal resultado = evaluationService.evaluarSubmission(7L, 1L, 8.0, 9.0, "Excelente", 60.0, 40.0);

        assertEquals("CALIFICADA", submission.getEstado().getCodigo());
        assertEquals("APROBADO", resultado.getResultado().getCodigo());
        verify(notificationService).createNotification(eq(10L), any());
    }

    @Test
    void listPorStudentRechazaConsultaDeOtroStudent() {
        // Caso IDOR: el student autenticado (id 99) intenta ver las evaluations del
        // student 3 cambiando el id en la URL.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("otro.estudiante@uteq.edu.ec", null,
                        org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_ESTUDIANTE")));
        when(appUserActualService.studentIdOrNull()).thenReturn(99L);

        assertThrows(AccessDeniedException.class, () -> evaluationService.listPorStudent(3L));
    }

    @Test
    void listPorStudentPermiteConsultarLaPropiaInformacion() {
        // Caso permitido: el student autenticado consulta sus propias evaluations.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("estudiante@uteq.edu.ec", null,
                        org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_ESTUDIANTE")));
        when(appUserActualService.studentIdOrNull()).thenReturn(3L);
        when(evaluationRepository.findByStudentId(3L)).thenReturn(List.of());

        assertDoesNotThrow(() -> evaluationService.listPorStudent(3L));
    }

    @Test
    void listPorStudentPermiteAAdminConsultarCualquierStudent() {
        // Caso administrativo: ADMIN/COORDINADOR conservan su acceso completo.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@uteq.edu.ec", null,
                        org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_ADMIN")));
        when(evaluationRepository.findByStudentId(3L)).thenReturn(List.of());

        assertDoesNotThrow(() -> evaluationService.listPorStudent(3L));
        verify(appUserActualService, never()).studentIdOrNull();
    }

    @Test
    void listPorAppUserRechazaConsultaDeOtroAppUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("otro@uteq.edu.ec", null,
                        org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_DOCENTE")));
        when(appUserActualService.appUser()).thenReturn(AppUser.builder().id(99L).build());

        assertThrows(AccessDeniedException.class, () -> evaluationService.listPorAppUser(10L));
    }

    @Test
    void searchPorSubmissionPropagaAccessDeniedSiSubmissionAccessServiceLoRechaza() {
        // Caso IDOR de lectura: la evaluación existe pero SubmissionAccessService decide que
        // este appUser no participa en la submission.
        EvaluationFinal evaluation = EvaluationFinal.builder().id(1L).submission(submission).build();
        when(evaluationRepository.findBySubmissionId(7L)).thenReturn(Optional.of(evaluation));
        doThrow(new AccessDeniedException("No tienes permiso para acceder a la información de esta solicitud"))
                .when(submissionAccessService).validateAcceso(submission, "EVALUACION_CALIFICAR");

        assertThrows(AccessDeniedException.class, () -> evaluationService.searchPorSubmission(7L));
    }

    @Test
    void generateComentarioPorRangoRetornaVacioSiNotaEsNull() {
        assertEquals("", evaluationService.generateComentarioPorRango(null));
    }

    @Test
    void generateComentarioPorRangoDistingueLosTresNiveles() {
        assertTrue(evaluationService.generateComentarioPorRango(2.0).contains("falencias significativas"));
        assertTrue(evaluationService.generateComentarioPorRango(5.0).contains("aspectos que requieren mejoras"));
        assertTrue(evaluationService.generateComentarioPorRango(9.0).contains("cumple satisfactoriamente"));
    }
}
