package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.TutoringPhaseDTO;
import ec.edu.uteq.presustentaciones.dto.TutoringMessageDTO;
import ec.edu.uteq.presustentaciones.dto.TutoringSummaryDTO;
import ec.edu.uteq.presustentaciones.entities.*;
import ec.edu.uteq.presustentaciones.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TutoringServiceImplTest {

    @Mock
    private TutorRepository tutorRepository;

    @Mock
    private TutoringPhaseRepository tutoringPhaseRepository;

    @Mock
    private TutoringMessageRepository tutoringMessageRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private ProposalRepository proposalRepository;

    @InjectMocks
    private TutoringServiceImpl tutoringService;

    @TempDir
    Path tempDir;

    private Tutor tutor;
    private Submission submission;
    private Student student;
    private Teacher teacher;
    private AppUser appUserTeacher;
    private AppUser appUserStudent;
    private TutoringPhase phase1;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(tutoringService, "uploadDir", tempDir.resolve("tutorias").toString());
        ReflectionTestUtils.setField(tutoringService, "uploadDirProposals", tempDir.resolve("anteproyectos").toString());

        appUserTeacher = AppUser.builder().id(10L).nombre("Profesor").apellido("Docente").role("DOCENTE").email("pdocente@uteq.edu.ec").build();
        teacher = Teacher.builder().id(1L).appUser(appUserTeacher).available(true).build();

        appUserStudent = AppUser.builder().id(20L).nombre("Alumno").apellido("Estudiante").role("ESTUDIANTE").email("aestudiante@uteq.edu.ec").build();
        student = Student.builder().id(2L).appUser(appUserStudent).build();

        submission = Submission.builder().id(100L).student(student).tituloTopic("Sistema Web").build();
        tutor = Tutor.builder().id(1L).submission(submission).teacher(teacher).status("EN_PROCESO").build();

        phase1 = TutoringPhase.builder()
                .id(1L)
                .tutor(tutor)
                .numeroPhase(1)
                .status("PENDIENTE_ESTUDIANTE")
                .build();
    }

    @Test
    void testObtainSummary() {
        // Hallazgo real (2026-09-01): validateAccesoATutoring() (control de acceso real agregado
        // por el equipo) busca al appUser por ID -- faltaba este stub, escrito antes del cambio.
        when(appUserRepository.findById(10L)).thenReturn(Optional.of(appUserTeacher));
        when(tutorRepository.findById(1L)).thenReturn(Optional.of(tutor));
        when(tutoringPhaseRepository.findByTutorIdOrderByNumeroPhaseAsc(1L)).thenReturn(List.of(phase1));

        TutoringSummaryDTO summary = tutoringService.obtainSummary(1L, 10L);

        assertNotNull(summary);
        assertEquals(1L, summary.getTutorId());
        assertEquals("EN_PROCESO", summary.getStatusTutoring());
        assertEquals("Sistema Web", summary.getTituloTopic());
    }

    @Test
    void testCreatePhaseWithObservationSuccessful() {
        when(tutorRepository.findById(1L)).thenReturn(Optional.of(tutor));
        when(tutoringPhaseRepository.countByTutorId(1L)).thenReturn(0L);
        when(tutoringPhaseRepository.save(any(TutoringPhase.class))).thenAnswer(inv -> {
            TutoringPhase f = inv.getArgument(0);
            f.setId(1L);
            return f;
        });
        when(appUserRepository.findById(10L)).thenReturn(Optional.of(appUserTeacher));

        TutoringPhaseDTO phaseDTO = tutoringService.createPhaseWithObservation(1L, 10L, "Favor corregir la introducción.");

        assertNotNull(phaseDTO);
        assertEquals(1, phaseDTO.getNumeroPhase());
        verify(tutoringMessageRepository).save(any(TutoringMessage.class));
    }

    @Test
    void testCreatePhaseWithObservationFailsIfAppUserNotIsTutor() {
        when(tutorRepository.findById(1L)).thenReturn(Optional.of(tutor));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                tutoringService.createPhaseWithObservation(1L, 999L, "Observación"));
        assertEquals("No autorizado", ex.getMessage());
    }

    @Test
    void testCreatePhaseWithObservationFailsIfExceedsThreePhases() {
        when(tutorRepository.findById(1L)).thenReturn(Optional.of(tutor));
        when(tutoringPhaseRepository.countByTutorId(1L)).thenReturn(3L);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                tutoringService.createPhaseWithObservation(1L, 10L, "Observación"));
        assertTrue(ex.getMessage().contains("No se pueden crear más de 3 fases"));
    }

    @Test
    void testUploadPdfCorrectedSuccessful() {
        when(tutoringPhaseRepository.findById(1L)).thenReturn(Optional.of(phase1));
        when(tutoringPhaseRepository.save(any(TutoringPhase.class))).thenAnswer(inv -> inv.getArgument(0));
        when(appUserRepository.findById(20L)).thenReturn(Optional.of(appUserStudent));

        MockMultipartFile filePdf = new MockMultipartFile(
                "archivo", "documento.pdf", "application/pdf", "%PDF-1.4 demo content".getBytes());

        TutoringPhaseDTO result = tutoringService.uploadPdfCorrected(1L, filePdf, 20L);

        assertNotNull(result);
        assertEquals("PENDIENTE_TUTOR", result.getStatus());
        verify(tutoringMessageRepository).save(any(TutoringMessage.class));
    }

    @Test
    void testUploadPdfFailsIfSubmissionIsSuspendida() {
        StatusSubmission statusSusp = StatusSubmission.builder().code("SUSPENDIDA").nombre("Suspendida").build();
        submission.setStatus(statusSusp);
        submission.setMotivoSuspension("Plagio detectado");

        when(tutoringPhaseRepository.findById(1L)).thenReturn(Optional.of(phase1));

        MockMultipartFile filePdf = new MockMultipartFile(
                "archivo", "documento.pdf", "application/pdf", "%PDF-1.4 demo".getBytes());

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                tutoringService.uploadPdfCorrected(1L, filePdf, 20L));
        assertTrue(ex.getMessage().contains("suspendido"));
    }

    @Test
    void testApprovePhaseSuccessful() {
        phase1.setStatus("PENDIENTE_TUTOR");
        phase1.setFilePdfStudent("archivo_fase1.pdf");

        when(tutoringPhaseRepository.findById(1L)).thenReturn(Optional.of(phase1));
        when(tutoringPhaseRepository.save(any(TutoringPhase.class))).thenAnswer(inv -> inv.getArgument(0));
        when(appUserRepository.findById(10L)).thenReturn(Optional.of(appUserTeacher));
        when(tutoringPhaseRepository.countByTutorId(1L)).thenReturn(1L);
        when(tutoringPhaseRepository.countByTutorIdAndStatus(1L, "APROBADA")).thenReturn(1L);

        TutoringPhaseDTO result = tutoringService.approvePhase(1L, 10L, "Excelente trabajo");

        assertNotNull(result);
        assertEquals("APROBADA", result.getStatus());
        verify(tutoringMessageRepository).save(any(TutoringMessage.class));
    }

    @Test
    void testSendMessageSuccessfulByTutorAndStudent() {
        when(tutoringPhaseRepository.findById(1L)).thenReturn(Optional.of(phase1));
        when(appUserRepository.findById(10L)).thenReturn(Optional.of(appUserTeacher));
        when(tutoringMessageRepository.save(any(TutoringMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        TutoringMessageDTO dto = tutoringService.sendMessage(1L, 10L, "Mensaje de prueba", "OBSERVACION");
        assertNotNull(dto);
        assertEquals("Mensaje de prueba", dto.getContenido());
    }

    @Test
    void testSendMessageRejectsAppUserNotAuthorized() {
        AppUser ajeno = AppUser.builder().id(999L).role("ESTUDIANTE").build();
        when(tutoringPhaseRepository.findById(1L)).thenReturn(Optional.of(phase1));
        when(appUserRepository.findById(999L)).thenReturn(Optional.of(ajeno));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                tutoringService.sendMessage(1L, 999L, "Mensaje sospechoso", "OBSERVACION"));
        assertTrue(ex.getMessage().contains("No autorizado"));
    }

    // ── validateAccesoATutoring (via obtainResumen/obtainFases) ─────────────

    @Test
    void obtainSummaryThrowsIfAppUserNotExists() {
        when(tutorRepository.findById(1L)).thenReturn(Optional.of(tutor));
        when(appUserRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> tutoringService.obtainSummary(1L, 999L));
    }

    @Test
    void obtainSummaryAllowsAccessToAdminWithoutBeTutorOrStudent() {
        AppUser admin = AppUser.builder().id(500L).role("ADMIN").build();
        when(tutorRepository.findById(1L)).thenReturn(Optional.of(tutor));
        when(appUserRepository.findById(500L)).thenReturn(Optional.of(admin));
        when(tutoringPhaseRepository.findByTutorIdOrderByNumeroPhaseAsc(1L)).thenReturn(List.of(phase1));

        assertDoesNotThrow(() -> tutoringService.obtainSummary(1L, 500L));
    }

    @Test
    void obtainSummaryRejectsAppUserForeign() {
        AppUser ajeno = AppUser.builder().id(999L).role("ESTUDIANTE").build();
        when(tutorRepository.findById(1L)).thenReturn(Optional.of(tutor));
        when(appUserRepository.findById(999L)).thenReturn(Optional.of(ajeno));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> tutoringService.obtainSummary(1L, 999L));
    }

    @Test
    void obtainPhasesAllowsToOwnStudent() {
        when(tutorRepository.findById(1L)).thenReturn(Optional.of(tutor));
        when(appUserRepository.findById(20L)).thenReturn(Optional.of(appUserStudent));
        when(tutoringPhaseRepository.findByTutorIdOrderByNumeroPhaseAsc(1L)).thenReturn(List.of(phase1));
        when(tutoringMessageRepository.findByPhaseIdOrderByDateEnvioAsc(1L)).thenReturn(List.of());

        List<TutoringPhaseDTO> phases = tutoringService.obtainPhases(1L, 20L);
        assertEquals(1, phases.size());
    }

    // ── createFaseConObservacion: rama restante ──────────────────────────────

    @Test
    void createPhaseWithObservationFailsIfPhasePreviousNotIsApproved() {
        TutoringPhase phaseAnteriorPendiente = TutoringPhase.builder().id(1L).tutor(tutor).numeroPhase(1).status("PENDIENTE_TUTOR").build();
        when(tutorRepository.findById(1L)).thenReturn(Optional.of(tutor));
        when(tutoringPhaseRepository.countByTutorId(1L)).thenReturn(1L);
        when(tutoringPhaseRepository.findByTutorIdOrderByNumeroPhaseAsc(1L)).thenReturn(List.of(phaseAnteriorPendiente));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> tutoringService.createPhaseWithObservation(1L, 10L, "obs"));
        assertTrue(ex.getMessage().contains("Debes aprobar la fase actual"));
    }

    // ── uploadPdfCorregido: ramas de validacion ──────────────────────────────

    @Test
    void uploadPdfRejectsAppUserThatNotIsStudentOfSubmission() {
        when(tutoringPhaseRepository.findById(1L)).thenReturn(Optional.of(phase1));
        MockMultipartFile pdf = new MockMultipartFile("archivo", "d.pdf", "application/pdf", "x".getBytes());

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> tutoringService.uploadPdfCorrected(1L, pdf, 999L));
    }

    @Test
    void uploadPdfRejectsStatusDifferentOfPendingStudent() {
        phase1.setStatus("PENDIENTE_TUTOR");
        when(tutoringPhaseRepository.findById(1L)).thenReturn(Optional.of(phase1));
        MockMultipartFile pdf = new MockMultipartFile("archivo", "d.pdf", "application/pdf", "x".getBytes());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> tutoringService.uploadPdfCorrected(1L, pdf, 20L));
        assertTrue(ex.getMessage().contains("cuando el tutor ha enviado observaciones"));
    }

    @Test
    void uploadPdfRejectsContentTypeDifferentOfPdf() {
        when(tutoringPhaseRepository.findById(1L)).thenReturn(Optional.of(phase1));
        MockMultipartFile file = new MockMultipartFile("archivo", "d.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "x".getBytes());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> tutoringService.uploadPdfCorrected(1L, file, 20L));
        assertTrue(ex.getMessage().contains("Solo se permiten archivos PDF"));
    }

    @Test
    void uploadPdfRejectsFileGreaterThan10MB() {
        when(tutoringPhaseRepository.findById(1L)).thenReturn(Optional.of(phase1));
        byte[] contenidoGrande = new byte[11 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("archivo", "grande.pdf", "application/pdf", contenidoGrande);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> tutoringService.uploadPdfCorrected(1L, file, 20L));
        assertTrue(ex.getMessage().contains("no puede superar los 10 MB"));
    }

    @Test
    void uploadPdfRemovesFilePreviousIfExists() throws Exception {
        // sube un primer PDF, luego uno de reemplazo -- ejercita la rama de borrado del anterior.
        when(tutoringPhaseRepository.findById(1L)).thenReturn(Optional.of(phase1));
        when(tutoringPhaseRepository.save(any(TutoringPhase.class))).thenAnswer(inv -> inv.getArgument(0));
        when(appUserRepository.findById(20L)).thenReturn(Optional.of(appUserStudent));
        MockMultipartFile pdf1 = new MockMultipartFile("archivo", "d1.pdf", "application/pdf", "contenido 1".getBytes());
        tutoringService.uploadPdfCorrected(1L, pdf1, 20L);

        phase1.setStatus("PENDIENTE_ESTUDIANTE"); // el tutor volvio a pedir correccion
        MockMultipartFile pdf2 = new MockMultipartFile("archivo", "d2.pdf", "application/pdf", "contenido 2".getBytes());
        TutoringPhaseDTO result = tutoringService.uploadPdfCorrected(1L, pdf2, 20L);

        assertNotNull(result.getFilePdfStudent());
    }

    // ── approveFase: ramas de validacion y flujo de cierre (3 fases) ────────

    @Test
    void approvePhaseRejectsAppUserThatNotIsTutor() {
        when(tutoringPhaseRepository.findById(1L)).thenReturn(Optional.of(phase1));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> tutoringService.approvePhase(1L, 999L, "ok"));
    }

    @Test
    void approvePhaseRejectsIfNotIsPendingOfTutor() {
        phase1.setStatus("PENDIENTE_ESTUDIANTE");
        when(tutoringPhaseRepository.findById(1L)).thenReturn(Optional.of(phase1));
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> tutoringService.approvePhase(1L, 10L, "ok"));
        assertTrue(ex.getMessage().contains("sin correcciones del estudiante"));
    }

    @Test
    void approvePhaseRejectsIfNotHasPdfOfStudent() {
        phase1.setStatus("PENDIENTE_TUTOR");
        phase1.setFilePdfStudent(null);
        when(tutoringPhaseRepository.findById(1L)).thenReturn(Optional.of(phase1));
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> tutoringService.approvePhase(1L, 10L, "ok"));
        assertTrue(ex.getMessage().contains("No existe un PDF"));
    }

    @Test
    void approvePhaseUsesCommentByDefaultIfComesEmpty() {
        phase1.setStatus("PENDIENTE_TUTOR");
        phase1.setFilePdfStudent("a.pdf");
        when(tutoringPhaseRepository.findById(1L)).thenReturn(Optional.of(phase1));
        when(tutoringPhaseRepository.save(any(TutoringPhase.class))).thenAnswer(inv -> inv.getArgument(0));
        when(appUserRepository.findById(10L)).thenReturn(Optional.of(appUserTeacher));
        when(tutoringPhaseRepository.countByTutorId(1L)).thenReturn(1L);
        when(tutoringPhaseRepository.countByTutorIdAndStatus(1L, "APROBADA")).thenReturn(1L);

        tutoringService.approvePhase(1L, 10L, "   ");

        verify(tutoringMessageRepository).save(argThat(m -> "Fase aprobada.".equals(m.getContenido())));
    }

    @Test
    void approvePhaseCompleteThreePhasesAndUpdatesProposal() throws Exception {
        // Prepara fisicamente el PDF de la fase 3 en el tempDir, para que Files.copy() real
        // encuentre el origen (mismo mecanismo que usa el codigo de produccion).
        String uploadDir = (String) ReflectionTestUtils.getField(tutoringService, "uploadDir");
        java.nio.file.Path dirPhase3 = java.nio.file.Paths.get(uploadDir, "1", "fase_3");
        java.nio.file.Files.createDirectories(dirPhase3);
        java.nio.file.Files.write(dirPhase3.resolve("final_fase3.pdf"), "contenido final".getBytes());

        TutoringPhase phase3 = TutoringPhase.builder().id(3L).tutor(tutor).numeroPhase(3).status("PENDIENTE_TUTOR")
                .filePdfStudent("final_fase3.pdf").sha256Pdf("abc123").sizePdfBytes(15L).build();

        when(tutoringPhaseRepository.findById(3L)).thenReturn(Optional.of(phase3));
        when(tutoringPhaseRepository.save(any(TutoringPhase.class))).thenAnswer(inv -> inv.getArgument(0));
        when(appUserRepository.findById(10L)).thenReturn(Optional.of(appUserTeacher));
        when(tutoringPhaseRepository.countByTutorId(1L)).thenReturn(3L);
        when(tutoringPhaseRepository.countByTutorIdAndStatus(1L, "APROBADA")).thenReturn(3L);
        when(tutoringPhaseRepository.findByTutorIdOrderByNumeroPhaseAsc(1L)).thenReturn(List.of(phase3));
        when(tutorRepository.save(any(Tutor.class))).thenAnswer(inv -> inv.getArgument(0));
        Proposal proposal = Proposal.builder().id(1L).build();
        when(proposalRepository.findBySubmissionId(100L)).thenReturn(Optional.of(proposal));
        when(proposalRepository.save(any(Proposal.class))).thenAnswer(inv -> inv.getArgument(0));

        tutoringService.approvePhase(3L, 10L, "Fase final aprobada");

        assertEquals("COMPLETADA", tutor.getStatus());
        verify(proposalRepository).save(argThat(a ->
                "final_fase3.pdf".equals(a.getFilePdf()) && "APROBADO".equals(a.getStatus())));
    }

    // ── sendMensaje: ramas restantes ───────────────────────────────────────

    @Test
    void sendMessageAllowsToStudent() {
        when(tutoringPhaseRepository.findById(1L)).thenReturn(Optional.of(phase1));
        when(appUserRepository.findById(20L)).thenReturn(Optional.of(appUserStudent));
        when(tutoringMessageRepository.save(any(TutoringMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> tutoringService.sendMessage(1L, 20L, "Ya subí el PDF", "RESPUESTA"));
    }

    @Test
    void sendMessageAllowsToAppUserPrivilegedStillNotBeingPartOfTutoring() {
        AppUser coordinator = AppUser.builder().id(700L).role("COORDINADOR").build();
        when(tutoringPhaseRepository.findById(1L)).thenReturn(Optional.of(phase1));
        when(appUserRepository.findById(700L)).thenReturn(Optional.of(coordinator));
        when(tutoringMessageRepository.save(any(TutoringMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> tutoringService.sendMessage(1L, 700L, "Mensaje de coordinación", "INFO"));
    }

    // ── marcarMensajesLeidos / listados / registerAvanceSP / obtainPdfFase ─

    @Test
    void markMessagesReadMarksAllNotRead() {
        TutoringMessage m1 = TutoringMessage.builder().id(1L).leido(false).build();
        TutoringMessage m2 = TutoringMessage.builder().id(2L).leido(false).build();
        when(tutoringMessageRepository.findByPhaseIdAndLeidoFalseAndSenderIdNot(1L, 20L))
                .thenReturn(new ArrayList<>(List.of(m1, m2)));

        tutoringService.markMessagesRead(1L, 20L);

        assertTrue(m1.getLeido());
        assertTrue(m2.getLeido());
        verify(tutoringMessageRepository).saveAll(anyList());
    }

    @Test
    void obtainTutoringsStudentDelegates() {
        when(tutorRepository.findBySubmissionStudentAppUserId(20L)).thenReturn(List.of(tutor));
        when(tutoringPhaseRepository.findByTutorIdOrderByNumeroPhaseAsc(1L)).thenReturn(List.of());

        List<TutoringSummaryDTO> result = tutoringService.obtainTutoringsStudent(20L);
        assertEquals(1, result.size());
    }

    @Test
    void obtainTutoringsTeacherDelegates() {
        when(tutorRepository.findByTeacherAppUserId(10L)).thenReturn(List.of(tutor));
        when(tutoringPhaseRepository.findByTutorIdOrderByNumeroPhaseAsc(1L)).thenReturn(List.of());

        List<TutoringSummaryDTO> result = tutoringService.obtainTutoringsTeacher(10L);
        assertEquals(1, result.size());
    }

    @Test
    void registerProgressSPValidatesAccessAndDelegatesToProcedure() {
        when(tutorRepository.findById(1L)).thenReturn(Optional.of(tutor));
        when(appUserRepository.findById(10L)).thenReturn(Optional.of(appUserTeacher));

        tutoringService.registerProgressSP(1L, 2, "archivo.pdf", 1024L, "hash", 10L);

        verify(tutoringPhaseRepository).spRegisterTutoringProgress(1L, 2, "archivo.pdf", 1024L, "hash");
    }

    @Test
    void obtainPdfPhaseThrowsIfNotHasFile() {
        when(tutoringPhaseRepository.findById(1L)).thenReturn(Optional.of(phase1));
        when(appUserRepository.findById(10L)).thenReturn(Optional.of(appUserTeacher));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> tutoringService.obtainPdfPhase(1L, 10L));
        assertTrue(ex.getMessage().contains("no tiene PDF"));
    }
}
