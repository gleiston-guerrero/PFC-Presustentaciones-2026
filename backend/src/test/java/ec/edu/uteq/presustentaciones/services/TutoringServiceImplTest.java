package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.TutoringFaseDTO;
import ec.edu.uteq.presustentaciones.dto.TutoringMensajeDTO;
import ec.edu.uteq.presustentaciones.dto.TutoringResumenDTO;
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
    private TutoringFaseRepository tutoringFaseRepository;

    @Mock
    private TutoringMensajeRepository tutoringMensajeRepository;

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
    private TutoringFase fase1;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(tutoringService, "uploadDir", tempDir.resolve("tutorias").toString());
        ReflectionTestUtils.setField(tutoringService, "uploadDirProposals", tempDir.resolve("anteproyectos").toString());

        appUserTeacher = AppUser.builder().id(10L).nombre("Profesor").apellido("Docente").role("DOCENTE").email("pdocente@uteq.edu.ec").build();
        teacher = Teacher.builder().id(1L).appUser(appUserTeacher).disponible(true).build();

        appUserStudent = AppUser.builder().id(20L).nombre("Alumno").apellido("Estudiante").role("ESTUDIANTE").email("aestudiante@uteq.edu.ec").build();
        student = Student.builder().id(2L).appUser(appUserStudent).build();

        submission = Submission.builder().id(100L).student(student).tituloTopic("Sistema Web").build();
        tutor = Tutor.builder().id(1L).submission(submission).teacher(teacher).estado("EN_PROCESO").build();

        fase1 = TutoringFase.builder()
                .id(1L)
                .tutor(tutor)
                .numeroFase(1)
                .estado("PENDIENTE_ESTUDIANTE")
                .build();
    }

    @Test
    void testObtainResumen() {
        // Hallazgo real (2026-09-01): validateAccesoATutoring() (control de acceso real agregado
        // por el equipo) busca al appUser por ID -- faltaba este stub, escrito antes del cambio.
        when(appUserRepository.findById(10L)).thenReturn(Optional.of(appUserTeacher));
        when(tutorRepository.findById(1L)).thenReturn(Optional.of(tutor));
        when(tutoringFaseRepository.findByTutorIdOrderByNumeroFaseAsc(1L)).thenReturn(List.of(fase1));

        TutoringResumenDTO resumen = tutoringService.obtainResumen(1L, 10L);

        assertNotNull(resumen);
        assertEquals(1L, resumen.getTutorId());
        assertEquals("EN_PROCESO", resumen.getEstadoTutoring());
        assertEquals("Sistema Web", resumen.getTituloTopic());
    }

    @Test
    void testCreateFaseConObservacionExitoso() {
        when(tutorRepository.findById(1L)).thenReturn(Optional.of(tutor));
        when(tutoringFaseRepository.countByTutorId(1L)).thenReturn(0L);
        when(tutoringFaseRepository.save(any(TutoringFase.class))).thenAnswer(inv -> {
            TutoringFase f = inv.getArgument(0);
            f.setId(1L);
            return f;
        });
        when(appUserRepository.findById(10L)).thenReturn(Optional.of(appUserTeacher));

        TutoringFaseDTO faseDTO = tutoringService.createFaseConObservacion(1L, 10L, "Favor corregir la introducción.");

        assertNotNull(faseDTO);
        assertEquals(1, faseDTO.getNumeroFase());
        verify(tutoringMensajeRepository).save(any(TutoringMensaje.class));
    }

    @Test
    void testCreateFaseConObservacionFallaSiAppUserNoEsElTutor() {
        when(tutorRepository.findById(1L)).thenReturn(Optional.of(tutor));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                tutoringService.createFaseConObservacion(1L, 999L, "Observación"));
        assertEquals("No autorizado", ex.getMessage());
    }

    @Test
    void testCreateFaseConObservacionFallaSiExcedeTresFases() {
        when(tutorRepository.findById(1L)).thenReturn(Optional.of(tutor));
        when(tutoringFaseRepository.countByTutorId(1L)).thenReturn(3L);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                tutoringService.createFaseConObservacion(1L, 10L, "Observación"));
        assertTrue(ex.getMessage().contains("No se pueden crear más de 3 fases"));
    }

    @Test
    void testUploadPdfCorregidoExitoso() {
        when(tutoringFaseRepository.findById(1L)).thenReturn(Optional.of(fase1));
        when(tutoringFaseRepository.save(any(TutoringFase.class))).thenAnswer(inv -> inv.getArgument(0));
        when(appUserRepository.findById(20L)).thenReturn(Optional.of(appUserStudent));

        MockMultipartFile archivoPdf = new MockMultipartFile(
                "archivo", "documento.pdf", "application/pdf", "%PDF-1.4 demo content".getBytes());

        TutoringFaseDTO resultado = tutoringService.uploadPdfCorregido(1L, archivoPdf, 20L);

        assertNotNull(resultado);
        assertEquals("PENDIENTE_TUTOR", resultado.getEstado());
        verify(tutoringMensajeRepository).save(any(TutoringMensaje.class));
    }

    @Test
    void testUploadPdfFallaSiSubmissionEstaSuspendida() {
        EstadoSubmission estadoSusp = EstadoSubmission.builder().codigo("SUSPENDIDA").nombre("Suspendida").build();
        submission.setEstado(estadoSusp);
        submission.setMotivoSuspension("Plagio detectado");

        when(tutoringFaseRepository.findById(1L)).thenReturn(Optional.of(fase1));

        MockMultipartFile archivoPdf = new MockMultipartFile(
                "archivo", "documento.pdf", "application/pdf", "%PDF-1.4 demo".getBytes());

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                tutoringService.uploadPdfCorregido(1L, archivoPdf, 20L));
        assertTrue(ex.getMessage().contains("suspendido"));
    }

    @Test
    void testApproveFaseExitoso() {
        fase1.setEstado("PENDIENTE_TUTOR");
        fase1.setArchivoPdfStudent("archivo_fase1.pdf");

        when(tutoringFaseRepository.findById(1L)).thenReturn(Optional.of(fase1));
        when(tutoringFaseRepository.save(any(TutoringFase.class))).thenAnswer(inv -> inv.getArgument(0));
        when(appUserRepository.findById(10L)).thenReturn(Optional.of(appUserTeacher));
        when(tutoringFaseRepository.countByTutorId(1L)).thenReturn(1L);
        when(tutoringFaseRepository.countByTutorIdAndEstado(1L, "APROBADA")).thenReturn(1L);

        TutoringFaseDTO resultado = tutoringService.approveFase(1L, 10L, "Excelente trabajo");

        assertNotNull(resultado);
        assertEquals("APROBADA", resultado.getEstado());
        verify(tutoringMensajeRepository).save(any(TutoringMensaje.class));
    }

    @Test
    void testSendMensajeExitosoPorTutorYStudent() {
        when(tutoringFaseRepository.findById(1L)).thenReturn(Optional.of(fase1));
        when(appUserRepository.findById(10L)).thenReturn(Optional.of(appUserTeacher));
        when(tutoringMensajeRepository.save(any(TutoringMensaje.class))).thenAnswer(inv -> inv.getArgument(0));

        TutoringMensajeDTO dto = tutoringService.sendMensaje(1L, 10L, "Mensaje de prueba", "OBSERVACION");
        assertNotNull(dto);
        assertEquals("Mensaje de prueba", dto.getContenido());
    }

    @Test
    void testSendMensajeRechazaAppUserNoAutorizado() {
        AppUser ajeno = AppUser.builder().id(999L).role("ESTUDIANTE").build();
        when(tutoringFaseRepository.findById(1L)).thenReturn(Optional.of(fase1));
        when(appUserRepository.findById(999L)).thenReturn(Optional.of(ajeno));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                tutoringService.sendMensaje(1L, 999L, "Mensaje sospechoso", "OBSERVACION"));
        assertTrue(ex.getMessage().contains("No autorizado"));
    }

    // ── validateAccesoATutoring (via obtainResumen/obtainFases) ─────────────

    @Test
    void obtainResumenLanzaSiAppUserNoExiste() {
        when(tutorRepository.findById(1L)).thenReturn(Optional.of(tutor));
        when(appUserRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> tutoringService.obtainResumen(1L, 999L));
    }

    @Test
    void obtainResumenPermiteAccesoAAdminSinSerTutorNiStudent() {
        AppUser admin = AppUser.builder().id(500L).role("ADMIN").build();
        when(tutorRepository.findById(1L)).thenReturn(Optional.of(tutor));
        when(appUserRepository.findById(500L)).thenReturn(Optional.of(admin));
        when(tutoringFaseRepository.findByTutorIdOrderByNumeroFaseAsc(1L)).thenReturn(List.of(fase1));

        assertDoesNotThrow(() -> tutoringService.obtainResumen(1L, 500L));
    }

    @Test
    void obtainResumenRechazaAppUserAjeno() {
        AppUser ajeno = AppUser.builder().id(999L).role("ESTUDIANTE").build();
        when(tutorRepository.findById(1L)).thenReturn(Optional.of(tutor));
        when(appUserRepository.findById(999L)).thenReturn(Optional.of(ajeno));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> tutoringService.obtainResumen(1L, 999L));
    }

    @Test
    void obtainFasesPermiteAlPropioStudent() {
        when(tutorRepository.findById(1L)).thenReturn(Optional.of(tutor));
        when(appUserRepository.findById(20L)).thenReturn(Optional.of(appUserStudent));
        when(tutoringFaseRepository.findByTutorIdOrderByNumeroFaseAsc(1L)).thenReturn(List.of(fase1));
        when(tutoringMensajeRepository.findByFaseIdOrderByFechaEnvioAsc(1L)).thenReturn(List.of());

        List<TutoringFaseDTO> fases = tutoringService.obtainFases(1L, 20L);
        assertEquals(1, fases.size());
    }

    // ── createFaseConObservacion: rama restante ──────────────────────────────

    @Test
    void createFaseConObservacionFallaSiLaFaseAnteriorNoEstaAprobada() {
        TutoringFase faseAnteriorPendiente = TutoringFase.builder().id(1L).tutor(tutor).numeroFase(1).estado("PENDIENTE_TUTOR").build();
        when(tutorRepository.findById(1L)).thenReturn(Optional.of(tutor));
        when(tutoringFaseRepository.countByTutorId(1L)).thenReturn(1L);
        when(tutoringFaseRepository.findByTutorIdOrderByNumeroFaseAsc(1L)).thenReturn(List.of(faseAnteriorPendiente));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> tutoringService.createFaseConObservacion(1L, 10L, "obs"));
        assertTrue(ex.getMessage().contains("Debes aprobar la fase actual"));
    }

    // ── uploadPdfCorregido: ramas de validacion ──────────────────────────────

    @Test
    void uploadPdfRechazaAppUserQueNoEsElStudentDeLaSubmission() {
        when(tutoringFaseRepository.findById(1L)).thenReturn(Optional.of(fase1));
        MockMultipartFile pdf = new MockMultipartFile("archivo", "d.pdf", "application/pdf", "x".getBytes());

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> tutoringService.uploadPdfCorregido(1L, pdf, 999L));
    }

    @Test
    void uploadPdfRechazaEstadoDistintoDePendienteStudent() {
        fase1.setEstado("PENDIENTE_TUTOR");
        when(tutoringFaseRepository.findById(1L)).thenReturn(Optional.of(fase1));
        MockMultipartFile pdf = new MockMultipartFile("archivo", "d.pdf", "application/pdf", "x".getBytes());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> tutoringService.uploadPdfCorregido(1L, pdf, 20L));
        assertTrue(ex.getMessage().contains("cuando el tutor ha enviado observaciones"));
    }

    @Test
    void uploadPdfRechazaContentTypeDistintoDePdf() {
        when(tutoringFaseRepository.findById(1L)).thenReturn(Optional.of(fase1));
        MockMultipartFile archivo = new MockMultipartFile("archivo", "d.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "x".getBytes());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> tutoringService.uploadPdfCorregido(1L, archivo, 20L));
        assertTrue(ex.getMessage().contains("Solo se permiten archivos PDF"));
    }

    @Test
    void uploadPdfRechazaArchivoMayorA10MB() {
        when(tutoringFaseRepository.findById(1L)).thenReturn(Optional.of(fase1));
        byte[] contenidoGrande = new byte[11 * 1024 * 1024];
        MockMultipartFile archivo = new MockMultipartFile("archivo", "grande.pdf", "application/pdf", contenidoGrande);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> tutoringService.uploadPdfCorregido(1L, archivo, 20L));
        assertTrue(ex.getMessage().contains("no puede superar los 10 MB"));
    }

    @Test
    void uploadPdfEliminaArchivoAnteriorSiExiste() throws Exception {
        // sube un primer PDF, luego uno de reemplazo -- ejercita la rama de borrado del anterior.
        when(tutoringFaseRepository.findById(1L)).thenReturn(Optional.of(fase1));
        when(tutoringFaseRepository.save(any(TutoringFase.class))).thenAnswer(inv -> inv.getArgument(0));
        when(appUserRepository.findById(20L)).thenReturn(Optional.of(appUserStudent));
        MockMultipartFile pdf1 = new MockMultipartFile("archivo", "d1.pdf", "application/pdf", "contenido 1".getBytes());
        tutoringService.uploadPdfCorregido(1L, pdf1, 20L);

        fase1.setEstado("PENDIENTE_ESTUDIANTE"); // el tutor volvio a pedir correccion
        MockMultipartFile pdf2 = new MockMultipartFile("archivo", "d2.pdf", "application/pdf", "contenido 2".getBytes());
        TutoringFaseDTO resultado = tutoringService.uploadPdfCorregido(1L, pdf2, 20L);

        assertNotNull(resultado.getArchivoPdfStudent());
    }

    // ── approveFase: ramas de validacion y flujo de cierre (3 fases) ────────

    @Test
    void approveFaseRechazaAppUserQueNoEsElTutor() {
        when(tutoringFaseRepository.findById(1L)).thenReturn(Optional.of(fase1));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> tutoringService.approveFase(1L, 999L, "ok"));
    }

    @Test
    void approveFaseRechazaSiNoEstaPendienteDeTutor() {
        fase1.setEstado("PENDIENTE_ESTUDIANTE");
        when(tutoringFaseRepository.findById(1L)).thenReturn(Optional.of(fase1));
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> tutoringService.approveFase(1L, 10L, "ok"));
        assertTrue(ex.getMessage().contains("sin correcciones del estudiante"));
    }

    @Test
    void approveFaseRechazaSiNoHayPdfDelStudent() {
        fase1.setEstado("PENDIENTE_TUTOR");
        fase1.setArchivoPdfStudent(null);
        when(tutoringFaseRepository.findById(1L)).thenReturn(Optional.of(fase1));
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> tutoringService.approveFase(1L, 10L, "ok"));
        assertTrue(ex.getMessage().contains("No existe un PDF"));
    }

    @Test
    void approveFaseUsaComentarioPorDefectoSiVieneVacio() {
        fase1.setEstado("PENDIENTE_TUTOR");
        fase1.setArchivoPdfStudent("a.pdf");
        when(tutoringFaseRepository.findById(1L)).thenReturn(Optional.of(fase1));
        when(tutoringFaseRepository.save(any(TutoringFase.class))).thenAnswer(inv -> inv.getArgument(0));
        when(appUserRepository.findById(10L)).thenReturn(Optional.of(appUserTeacher));
        when(tutoringFaseRepository.countByTutorId(1L)).thenReturn(1L);
        when(tutoringFaseRepository.countByTutorIdAndEstado(1L, "APROBADA")).thenReturn(1L);

        tutoringService.approveFase(1L, 10L, "   ");

        verify(tutoringMensajeRepository).save(argThat(m -> "Fase aprobada.".equals(m.getContenido())));
    }

    @Test
    void approveFaseCompletaLasTresFasesYActualizaElProposal() throws Exception {
        // Prepara fisicamente el PDF de la fase 3 en el tempDir, para que Files.copy() real
        // encuentre el origen (mismo mecanismo que usa el codigo de produccion).
        String uploadDir = (String) ReflectionTestUtils.getField(tutoringService, "uploadDir");
        java.nio.file.Path dirFase3 = java.nio.file.Paths.get(uploadDir, "1", "fase_3");
        java.nio.file.Files.createDirectories(dirFase3);
        java.nio.file.Files.write(dirFase3.resolve("final_fase3.pdf"), "contenido final".getBytes());

        TutoringFase fase3 = TutoringFase.builder().id(3L).tutor(tutor).numeroFase(3).estado("PENDIENTE_TUTOR")
                .archivoPdfStudent("final_fase3.pdf").sha256Pdf("abc123").tamanoPdfBytes(15L).build();

        when(tutoringFaseRepository.findById(3L)).thenReturn(Optional.of(fase3));
        when(tutoringFaseRepository.save(any(TutoringFase.class))).thenAnswer(inv -> inv.getArgument(0));
        when(appUserRepository.findById(10L)).thenReturn(Optional.of(appUserTeacher));
        when(tutoringFaseRepository.countByTutorId(1L)).thenReturn(3L);
        when(tutoringFaseRepository.countByTutorIdAndEstado(1L, "APROBADA")).thenReturn(3L);
        when(tutoringFaseRepository.findByTutorIdOrderByNumeroFaseAsc(1L)).thenReturn(List.of(fase3));
        when(tutorRepository.save(any(Tutor.class))).thenAnswer(inv -> inv.getArgument(0));
        Proposal proposal = Proposal.builder().id(1L).build();
        when(proposalRepository.findBySubmissionId(100L)).thenReturn(Optional.of(proposal));
        when(proposalRepository.save(any(Proposal.class))).thenAnswer(inv -> inv.getArgument(0));

        tutoringService.approveFase(3L, 10L, "Fase final aprobada");

        assertEquals("COMPLETADA", tutor.getEstado());
        verify(proposalRepository).save(argThat(a ->
                "final_fase3.pdf".equals(a.getArchivoPdf()) && "APROBADO".equals(a.getEstado())));
    }

    // ── sendMensaje: ramas restantes ───────────────────────────────────────

    @Test
    void sendMensajePermiteAlStudent() {
        when(tutoringFaseRepository.findById(1L)).thenReturn(Optional.of(fase1));
        when(appUserRepository.findById(20L)).thenReturn(Optional.of(appUserStudent));
        when(tutoringMensajeRepository.save(any(TutoringMensaje.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> tutoringService.sendMensaje(1L, 20L, "Ya subí el PDF", "RESPUESTA"));
    }

    @Test
    void sendMensajePermiteAAppUserPrivilegiadoAunNoSiendoParteDeLaTutoring() {
        AppUser coordinador = AppUser.builder().id(700L).role("COORDINADOR").build();
        when(tutoringFaseRepository.findById(1L)).thenReturn(Optional.of(fase1));
        when(appUserRepository.findById(700L)).thenReturn(Optional.of(coordinador));
        when(tutoringMensajeRepository.save(any(TutoringMensaje.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> tutoringService.sendMensaje(1L, 700L, "Mensaje de coordinación", "INFO"));
    }

    // ── marcarMensajesLeidos / listados / registerAvanceSP / obtainPdfFase ─

    @Test
    void marcarMensajesLeidosMarcaTodosLosNoLeidos() {
        TutoringMensaje m1 = TutoringMensaje.builder().id(1L).leido(false).build();
        TutoringMensaje m2 = TutoringMensaje.builder().id(2L).leido(false).build();
        when(tutoringMensajeRepository.findByFaseIdAndLeidoFalseAndRemitenteIdNot(1L, 20L))
                .thenReturn(new ArrayList<>(List.of(m1, m2)));

        tutoringService.marcarMensajesLeidos(1L, 20L);

        assertTrue(m1.getLeido());
        assertTrue(m2.getLeido());
        verify(tutoringMensajeRepository).saveAll(anyList());
    }

    @Test
    void obtainTutoringsStudentDelega() {
        when(tutorRepository.findBySubmissionStudentAppUserId(20L)).thenReturn(List.of(tutor));
        when(tutoringFaseRepository.findByTutorIdOrderByNumeroFaseAsc(1L)).thenReturn(List.of());

        List<TutoringResumenDTO> resultado = tutoringService.obtainTutoringsStudent(20L);
        assertEquals(1, resultado.size());
    }

    @Test
    void obtainTutoringsTeacherDelega() {
        when(tutorRepository.findByTeacherAppUserId(10L)).thenReturn(List.of(tutor));
        when(tutoringFaseRepository.findByTutorIdOrderByNumeroFaseAsc(1L)).thenReturn(List.of());

        List<TutoringResumenDTO> resultado = tutoringService.obtainTutoringsTeacher(10L);
        assertEquals(1, resultado.size());
    }

    @Test
    void registerAvanceSPValidaAccesoYDelegaAlProcedimiento() {
        when(tutorRepository.findById(1L)).thenReturn(Optional.of(tutor));
        when(appUserRepository.findById(10L)).thenReturn(Optional.of(appUserTeacher));

        tutoringService.registerAvanceSP(1L, 2, "archivo.pdf", 1024L, "hash", 10L);

        verify(tutoringFaseRepository).spRegisterTutoringAvance(1L, 2, "archivo.pdf", 1024L, "hash");
    }

    @Test
    void obtainPdfFaseLanzaSiNoHayArchivo() {
        when(tutoringFaseRepository.findById(1L)).thenReturn(Optional.of(fase1));
        when(appUserRepository.findById(10L)).thenReturn(Optional.of(appUserTeacher));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> tutoringService.obtainPdfFase(1L, 10L));
        assertTrue(ex.getMessage().contains("no tiene PDF"));
    }
}
