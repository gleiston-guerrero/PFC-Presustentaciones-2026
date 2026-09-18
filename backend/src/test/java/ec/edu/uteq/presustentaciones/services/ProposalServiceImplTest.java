package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.Proposal;
import ec.edu.uteq.presustentaciones.entities.StatusSubmission;
import ec.edu.uteq.presustentaciones.entities.Student;
import ec.edu.uteq.presustentaciones.entities.Submission;
import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.repositories.ProposalRepository;
import ec.edu.uteq.presustentaciones.repositories.SubmissionRepository;
import ec.edu.uteq.presustentaciones.repositories.AppUserRepository;
import ec.edu.uteq.presustentaciones.security.service.SubmissionAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProposalServiceImplTest {

    @Mock
    private ProposalRepository proposalRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private SubmissionAccessService submissionAccessService;

    @InjectMocks
    private ProposalServiceImpl proposalService;

    @TempDir
    Path tempDir;

    private Submission submission;
    private AppUser studentAppUser;
    private Student student;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(proposalService, "uploadDir", tempDir.resolve("anteproyectos").toString());

        studentAppUser = AppUser.builder().id(5L).nombre("Carlos").apellido("Mendoza").email("cmendoza@uteq.edu.ec").role("ESTUDIANTE").build();
        student = Student.builder().id(1L).appUser(studentAppUser).build();
        submission = Submission.builder().id(10L).student(student).tituloTopic("Sistema IA").build();

        // validatePuedeUpload() exige un SecurityContextHolder autenticado (mismo patron que
        // MinutesServiceImplTest); se autentica como el propio student dueno de la submission,
        // que es el caso real que sendProposal() protege.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("cmendoza@uteq.edu.ec", null,
                        org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_ESTUDIANTE")));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testSendProposalExitoso() {
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(submission));
        when(proposalRepository.findBySubmissionId(10L)).thenReturn(Optional.empty());
        when(proposalRepository.save(any(Proposal.class))).thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile filePdf = new MockMultipartFile(
                "archivo", "anteproyecto.pdf", "application/pdf", "%PDF-1.4 contenido".getBytes());

        Proposal ap = proposalService.sendProposal(10L, filePdf);

        assertNotNull(ap);
        assertEquals("ENVIADO", ap.getStatus());
        assertNotNull(ap.getSha256Hash());
        verify(proposalRepository).save(any(Proposal.class));
    }

    @Test
    void testSendProposalFallaSiSubmissionSuspendida() {
        StatusSubmission susp = StatusSubmission.builder().code("SUSPENDIDA").nombre("Suspendida").build();
        submission.setStatus(susp);
        submission.setMotivoSuspension("Incumplimiento de fechas");

        when(submissionRepository.findById(10L)).thenReturn(Optional.of(submission));

        MockMultipartFile filePdf = new MockMultipartFile(
                "archivo", "anteproyecto.pdf", "application/pdf", "%PDF-1.4 contenido".getBytes());

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                proposalService.sendProposal(10L, filePdf));
        assertTrue(ex.getMessage().contains("suspendido"));
    }

    @Test
    void testSendProposalFallaSiNoEsPdf() {
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(submission));

        MockMultipartFile fileTxt = new MockMultipartFile(
                "archivo", "anteproyecto.txt", "text/plain", "texto plano".getBytes());

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                proposalService.sendProposal(10L, fileTxt));
        assertTrue(ex.getMessage().contains("Solo se permiten archivos PDF"));
    }

    @Test
    void testSendProposalRechazaAAppUserQueNoEsElDuenoDeLaSubmission() {
        // Caso IDOR de escritura: un tercero (otro student) intenta upload el PDF de una
        // submission que no le pertenece cambiando el submissionId.
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(submission));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("otro.estudiante@uteq.edu.ec", null,
                        org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_ESTUDIANTE")));

        MockMultipartFile filePdf = new MockMultipartFile(
                "archivo", "anteproyecto.pdf", "application/pdf", "%PDF-1.4 contenido".getBytes());

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> proposalService.sendProposal(10L, filePdf));
    }

    @Test
    void testSearchBySubmissionPropagaAccessDeniedSiSubmissionAccessServiceLoRechaza() {
        // Caso IDOR de lectura: el proposal existe pero SubmissionAccessService decide que
        // este appUser no participa en la submission (student ajeno, ni panelist ni tutor).
        Proposal ap = Proposal.builder().id(1L).submission(submission).status("ENVIADO").build();
        when(proposalRepository.findBySubmissionId(10L)).thenReturn(Optional.of(ap));
        org.mockito.Mockito.doThrow(new org.springframework.security.access.AccessDeniedException(
                        "No tienes permiso para acceder a la información de esta solicitud"))
                .when(submissionAccessService).validateAccess(submission, "ANTEPROYECTO_REVISAR");

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> proposalService.searchBySubmission(10L));
    }

    @Test
    void testApproveProposal() {
        Proposal ap = Proposal.builder().id(1L).submission(submission).status("PENDIENTE").build();
        when(proposalRepository.findById(1L)).thenReturn(Optional.of(ap));
        when(proposalRepository.save(any(Proposal.class))).thenAnswer(inv -> inv.getArgument(0));

        Proposal result = proposalService.approveProposal(1L, "Cumple con todos los requisitos.");

        assertNotNull(result);
        assertEquals("APROBADO", result.getStatus());
        assertEquals("Cumple con todos los requisitos.", result.getObservations());
        verify(notificationService).createNotification(eq(5L), anyString());
    }

    @Test
    void testRejectProposal() {
        Proposal ap = Proposal.builder().id(1L).submission(submission).status("PENDIENTE").build();
        when(proposalRepository.findById(1L)).thenReturn(Optional.of(ap));
        when(proposalRepository.save(any(Proposal.class))).thenAnswer(inv -> inv.getArgument(0));

        Proposal result = proposalService.rejectProposal(1L, "Falta marco teórico.");

        assertNotNull(result);
        assertEquals("RECHAZADO", result.getStatus());
        assertEquals("Falta marco teórico.", result.getObservations());
        verify(notificationService).createNotification(eq(5L), anyString());
    }
}
