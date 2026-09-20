package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.EvaluationPanelistDTO;
import ec.edu.uteq.presustentaciones.entities.*;
import ec.edu.uteq.presustentaciones.repositories.EvaluationPanelistRepository;
import ec.edu.uteq.presustentaciones.repositories.PanelistRepository;
import ec.edu.uteq.presustentaciones.repositories.SubmissionRepository;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * EvaluationPanelistService.saveEvaluation() decide APROBADO/REPROBADO (umbral 7) y el
 * comentario preestablecido por rango de nota -- logica real sin ningun test dedicado.
 */
@ExtendWith(MockitoExtension.class)
class EvaluationPanelistServiceTest {

    @Mock private EvaluationPanelistRepository evaluationPanelistRepo;
    @Mock private SubmissionRepository submissionRepo;
    @Mock private PanelistRepository panelistRepo;
    @Mock private SubmissionAccessService submissionAccessService;
    @Mock private PermissionService permissionService;

    @InjectMocks
    private EvaluationPanelistService evaluationPanelistService;

    private Submission submission;
    private Panelist panelist;

    @BeforeEach
    void setUp() {
        submission = Submission.builder().id(7L).tituloTopic("Sistema X").build();
        AppUser appUserTeacher = AppUser.builder().id(50L).nombre("Ana").apellido("Torres").build();
        Teacher teacher = Teacher.builder().id(1L).appUser(appUserTeacher).build();
        panelist = Panelist.builder().id(3L).submission(submission).teacher(teacher)
                .rolePanelist(RolePanelist.builder().code("VOCAL_1").build()).build();

        // validatePuedeRegister() exige un SecurityContextHolder autenticado (mismo patron que
        // MinutesServiceImplTest); se autentica como ADMIN por defecto para bypasear la regla de
        // "debe ser el propio jurado" y mantener estos tests centrados en la logica de negocio
        // (umbral 7, comentario por rango), no en la autorizacion -- que ya se prueba aparte.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@uteq.edu.ec", null,
                        org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_ADMIN")));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void saveEvaluationThrowsExceptionIfSubmissionNotExists() {
        when(submissionRepo.findById(7L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class,
                () -> evaluationPanelistService.saveEvaluation(7L, 3L, 8.0, "bien"));
    }

    @Test
    void saveEvaluationThrowsExceptionIfPanelistNotExists() {
        when(submissionRepo.findById(7L)).thenReturn(Optional.of(submission));
        when(panelistRepo.findById(3L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class,
                () -> evaluationPanelistService.saveEvaluation(7L, 3L, 8.0, "bien"));
    }

    @Test
    void saveEvaluationRejectsPanelistThatNotBelongsToSubmission() {
        Submission otraSubmission = Submission.builder().id(999L).build();
        Panelist panelistDeOtraSubmission = Panelist.builder().id(3L).submission(otraSubmission).build();
        when(submissionRepo.findById(7L)).thenReturn(Optional.of(submission));
        when(panelistRepo.findById(3L)).thenReturn(Optional.of(panelistDeOtraSubmission));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> evaluationPanelistService.saveEvaluation(7L, 3L, 8.0, "bien"));
        assertTrue(ex.getMessage().contains("no pertenece"));
    }

    @Test
    void saveEvaluationRejectsGradeOutsideOfRange() {
        when(submissionRepo.findById(7L)).thenReturn(Optional.of(submission));
        when(panelistRepo.findById(3L)).thenReturn(Optional.of(panelist));

        assertThrows(RuntimeException.class,
                () -> evaluationPanelistService.saveEvaluation(7L, 3L, 0.5, "bien"));
        assertThrows(RuntimeException.class,
                () -> evaluationPanelistService.saveEvaluation(7L, 3L, 10.5, "bien"));
    }

    @Test
    void saveEvaluationWithGradeGreaterOrEqualTo7ResultsApproved() {
        when(submissionRepo.findById(7L)).thenReturn(Optional.of(submission));
        when(panelistRepo.findById(3L)).thenReturn(Optional.of(panelist));
        when(evaluationPanelistRepo.findBySubmissionIdAndPanelistId(7L, 3L)).thenReturn(Optional.empty());
        when(evaluationPanelistRepo.save(any(EvaluationPanelist.class))).thenAnswer(inv -> inv.getArgument(0));

        EvaluationPanelistDTO dto = evaluationPanelistService.saveEvaluation(7L, 3L, 7.0, "Buen trabajo");

        assertEquals("APROBADO", dto.getResult());
        assertTrue(dto.getCommentPreestablecido().contains("cumple satisfactoriamente"));
        assertEquals("Ana Torres", dto.getNombrePanelist());
        assertEquals("VOCAL_1", dto.getRolePanelist());
    }

    @Test
    void saveEvaluationWithGradeLessThan7ResultsFailed() {
        when(submissionRepo.findById(7L)).thenReturn(Optional.of(submission));
        when(panelistRepo.findById(3L)).thenReturn(Optional.of(panelist));
        when(evaluationPanelistRepo.findBySubmissionIdAndPanelistId(7L, 3L)).thenReturn(Optional.empty());
        when(evaluationPanelistRepo.save(any(EvaluationPanelist.class))).thenAnswer(inv -> inv.getArgument(0));

        EvaluationPanelistDTO dto = evaluationPanelistService.saveEvaluation(7L, 3L, 6.0, "Falta profundidad");

        assertEquals("REPROBADO", dto.getResult());
        assertTrue(dto.getCommentPreestablecido().contains("aspectos que requieren mejoras"));
    }

    @Test
    void saveEvaluationWithGradeVeryLowUsesCommentMoreSevere() {
        when(submissionRepo.findById(7L)).thenReturn(Optional.of(submission));
        when(panelistRepo.findById(3L)).thenReturn(Optional.of(panelist));
        when(evaluationPanelistRepo.findBySubmissionIdAndPanelistId(7L, 3L)).thenReturn(Optional.empty());
        when(evaluationPanelistRepo.save(any(EvaluationPanelist.class))).thenAnswer(inv -> inv.getArgument(0));

        EvaluationPanelistDTO dto = evaluationPanelistService.saveEvaluation(7L, 3L, 2.0, "Insuficiente");

        assertEquals("REPROBADO", dto.getResult());
        assertTrue(dto.getCommentPreestablecido().contains("falencias significativas"));
    }

    @Test
    void saveEvaluationUpdatesEvaluationExistingInTimeOfCreateOther() {
        EvaluationPanelist existing = EvaluationPanelist.builder().id(1L).submission(submission).panelist(panelist)
                .gradePanelist(5.0).result("REPROBADO").build();
        when(submissionRepo.findById(7L)).thenReturn(Optional.of(submission));
        when(panelistRepo.findById(3L)).thenReturn(Optional.of(panelist));
        when(evaluationPanelistRepo.findBySubmissionIdAndPanelistId(7L, 3L)).thenReturn(Optional.of(existing));
        when(evaluationPanelistRepo.save(any(EvaluationPanelist.class))).thenAnswer(inv -> inv.getArgument(0));

        EvaluationPanelistDTO dto = evaluationPanelistService.saveEvaluation(7L, 3L, 9.0, "Corregido, ahora excelente");

        assertEquals(1L, dto.getId());
        assertEquals("APROBADO", dto.getResult());
        assertEquals("Corregido, ahora excelente", dto.getObservations());
        verify(evaluationPanelistRepo).save(existing);
    }

    @Test
    void saveEvaluationAllowsToOwnPanelistRegisterGrade() {
        // Caso permitido: el teacher autenticado ES el panelist asignado a esta submission.
        when(submissionRepo.findById(7L)).thenReturn(Optional.of(submission));
        when(panelistRepo.findById(3L)).thenReturn(Optional.of(panelist));
        when(evaluationPanelistRepo.findBySubmissionIdAndPanelistId(7L, 3L)).thenReturn(Optional.empty());
        when(evaluationPanelistRepo.save(any(EvaluationPanelist.class))).thenAnswer(inv -> inv.getArgument(0));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("ana.torres@uteq.edu.ec", null,
                        org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_DOCENTE")));
        when(permissionService.isOwnTeacher(any(), eq(1L))).thenReturn(true);

        assertDoesNotThrow(() -> evaluationPanelistService.saveEvaluation(7L, 3L, 8.0, "bien"));
    }

    @Test
    void saveEvaluationRejectsToPanelistThatRecordsToNameOfOther() {
        // Caso IDOR de escritura: un teacher que NO es el panelist asignado intenta register
        // la nota a nombre de otro cambiando el panelistId.
        when(submissionRepo.findById(7L)).thenReturn(Optional.of(submission));
        when(panelistRepo.findById(3L)).thenReturn(Optional.of(panelist));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("otro.docente@uteq.edu.ec", null,
                        org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_DOCENTE")));
        when(permissionService.isOwnTeacher(any(), eq(1L))).thenReturn(false);
        when(permissionService.hasPermission(any(), eq("EVALUACION_CALIFICAR"))).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> evaluationPanelistService.saveEvaluation(7L, 3L, 8.0, "bien"));
    }

    @Test
    void obtainEvaluationReturnsNullIfNotExists() {
        when(submissionRepo.findById(7L)).thenReturn(Optional.of(submission));
        when(evaluationPanelistRepo.findBySubmissionIdAndPanelistId(7L, 3L)).thenReturn(Optional.empty());
        assertNull(evaluationPanelistService.obtainEvaluation(7L, 3L));
    }

    @Test
    void obtainEvaluationPropagatesAccessDeniedIfSubmissionAccessServiceRejects() {
        // Caso IDOR: SubmissionAccessService es quien decide; aquí solo verificamos que
        // EvaluationPanelistService no atrapa/oculta ese rechazo (debe seguir siendo 403).
        when(submissionRepo.findById(7L)).thenReturn(Optional.of(submission));
        org.mockito.Mockito.doThrow(new AccessDeniedException("No tienes permiso para acceder a la información de esta solicitud"))
                .when(submissionAccessService).validateAccess(submission, "EVALUACION_CALIFICAR");

        assertThrows(AccessDeniedException.class, () -> evaluationPanelistService.obtainEvaluation(7L, 3L));
    }

    @Test
    void obtainPanelMapsAllEvaluationsOfSubmission() {
        when(submissionRepo.findById(7L)).thenReturn(Optional.of(submission));
        EvaluationPanelist eval1 = EvaluationPanelist.builder().id(1L).submission(submission).panelist(panelist)
                .gradePanelist(8.0).result("APROBADO").build();
        when(evaluationPanelistRepo.findBySubmissionId(7L)).thenReturn(List.of(eval1));

        List<EvaluationPanelistDTO> result = evaluationPanelistService.obtainPanel(7L);

        assertEquals(1, result.size());
        assertEquals(8.0, result.get(0).getGradePanelist());
    }
}
