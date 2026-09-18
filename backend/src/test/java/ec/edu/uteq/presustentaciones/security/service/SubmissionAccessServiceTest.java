package ec.edu.uteq.presustentaciones.security.service;

import ec.edu.uteq.presustentaciones.entities.*;
import ec.edu.uteq.presustentaciones.repositories.PanelistRepository;
import ec.edu.uteq.presustentaciones.repositories.TutorRepository;
import ec.edu.uteq.presustentaciones.services.PermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Corrección de IDOR (auditoría 2026-09-04): Evaluación, EvaluaciónPanelist, RúbricaEvaluación y
 * Proposal delegan aquí la comprobación de "¿este usuario tiene relación real con esta
 * solicitud?" antes de exponer datos académicos. Cubre exactamente los casos mínimos pedidos:
 * permitido (student/panelist/tutor propios), IDOR (appUser sin relación), administrativo
 * (ADMIN y el permission de bypass indicado).
 */
@ExtendWith(MockitoExtension.class)
class SubmissionAccessServiceTest {

    @Mock private PanelistRepository panelistRepository;
    @Mock private TutorRepository tutorRepository;
    @Mock private PermissionService permissionService;

    @InjectMocks
    private SubmissionAccessService submissionAccessService;

    private Submission submission;

    @BeforeEach
    void setUp() {
        AppUser appUserStudent = AppUser.builder().id(10L).email("estudiante.dueno@uteq.edu.ec").build();
        Student student = Student.builder().id(3L).appUser(appUserStudent).build();
        submission = Submission.builder().id(7L).student(student).tituloTopic("Sistema X").build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String email, String... roles) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null,
                        AuthorityUtils.createAuthorityList(roles)));
    }

    @Test
    void sinAuthenticateLanzaAccessDenied() {
        assertThrows(AccessDeniedException.class,
                () -> submissionAccessService.validateAccess(submission, "EVALUACION_CALIFICAR"));
    }

    @Test
    void adminSiempreTieneAccess() {
        authenticateAs("admin@uteq.edu.ec", "ROLE_ADMIN");
        assertDoesNotThrow(() -> submissionAccessService.validateAccess(submission, "EVALUACION_CALIFICAR"));
    }

    @Test
    void holderDelPermissionDeBypassTieneAccess() {
        authenticateAs("coordinador@uteq.edu.ec", "ROLE_COORDINADOR");
        when(permissionService.hasPermission(any(), eq("EVALUACION_CALIFICAR"))).thenReturn(true);
        assertDoesNotThrow(() -> submissionAccessService.validateAccess(submission, "EVALUACION_CALIFICAR"));
    }

    @Test
    void studentPropietarioTieneAccess() {
        // El chequeo de "estudiante dueño" resuelve el acceso antes de consultar panelists/tutor,
        // así que aquí solo hace falta stubear el permission de bypass (no se cumple).
        authenticateAs("estudiante.dueno@uteq.edu.ec", "ROLE_ESTUDIANTE");
        when(permissionService.hasPermission(any(), any())).thenReturn(false);

        assertDoesNotThrow(() -> submissionAccessService.validateAccess(submission, "EVALUACION_CALIFICAR"));
    }

    @Test
    void otroStudentWithoutRelacionRecibeAccessDenied() {
        // Caso IDOR: un appUser autenticado que no es el student dueño, ni panelist, ni tutor.
        authenticateAs("otro.estudiante@uteq.edu.ec", "ROLE_ESTUDIANTE");
        when(permissionService.hasPermission(any(), any())).thenReturn(false);
        when(panelistRepository.findBySubmissionId(7L)).thenReturn(List.of());
        when(tutorRepository.findBySubmissionId(7L)).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class,
                () -> submissionAccessService.validateAccess(submission, "EVALUACION_CALIFICAR"));
    }

    @Test
    void panelistAsignadoTieneAccess() {
        AppUser appUserTeacher = AppUser.builder().id(50L).email("jurado@uteq.edu.ec").build();
        Teacher teacher = Teacher.builder().id(5L).appUser(appUserTeacher).build();
        Panelist panelist = Panelist.builder().id(1L).submission(submission).teacher(teacher).build();

        authenticateAs("jurado@uteq.edu.ec", "ROLE_DOCENTE");
        when(permissionService.hasPermission(any(), any())).thenReturn(false);
        when(panelistRepository.findBySubmissionId(7L)).thenReturn(List.of(panelist));

        assertDoesNotThrow(() -> submissionAccessService.validateAccess(submission, "EVALUACION_CALIFICAR"));
    }

    @Test
    void teacherQueNoEsPanelistNiTutorRecibeAccessDenied() {
        // Caso IDOR: un DOCENTE autenticado que no participa en esta submission como panelist/tutor.
        authenticateAs("docente.ajeno@uteq.edu.ec", "ROLE_DOCENTE");
        when(permissionService.hasPermission(any(), any())).thenReturn(false);
        when(panelistRepository.findBySubmissionId(7L)).thenReturn(List.of());
        when(tutorRepository.findBySubmissionId(7L)).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class,
                () -> submissionAccessService.validateAccess(submission, "EVALUACION_CALIFICAR"));
    }

    @Test
    void tutorAsignadoTieneAccess() {
        AppUser appUserTeacher = AppUser.builder().id(60L).email("tutor@uteq.edu.ec").build();
        Teacher teacher = Teacher.builder().id(6L).appUser(appUserTeacher).build();
        Tutor tutor = Tutor.builder().id(2L).submission(submission).teacher(teacher).build();

        authenticateAs("tutor@uteq.edu.ec", "ROLE_DOCENTE");
        when(permissionService.hasPermission(any(), any())).thenReturn(false);
        when(panelistRepository.findBySubmissionId(7L)).thenReturn(List.of());
        when(tutorRepository.findBySubmissionId(7L)).thenReturn(Optional.of(tutor));

        assertDoesNotThrow(() -> submissionAccessService.validateAccess(submission, "ANTEPROYECTO_REVISAR"));
    }
}
