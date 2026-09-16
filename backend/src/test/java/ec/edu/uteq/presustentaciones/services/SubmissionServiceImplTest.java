package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.*;
import ec.edu.uteq.presustentaciones.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Cobertura priorizada en docs/mediciones/jacoco/COVERAGE.md: SubmissionServiceImpl por sus
 * reglas de transición de estado (CREADA -> ENVIADA -> APROBADA/RECHAZADA, y SUSPENDIDA
 * desde cualquier estado que no sea CREADA/RECHAZADA/SUSPENDIDA). Fase 4/6.
 */
@ExtendWith(MockitoExtension.class)
class SubmissionServiceImplTest {

    @Mock private SubmissionRepository submissionRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private ProposalRepository proposalRepository;
    @Mock private NotificationService notificationService;
    @Mock private AppUserRepository appUserRepository;
    @Mock private EstadoSubmissionRepository estadoSubmissionRepository;
    @Mock private ModalityTitulacionRepository modalityTitulacionRepository;
    @Mock private AnnouncementTitulacionRepository announcementTitulacionRepository;
    @Mock private ProgramRepository programRepository;
    @Mock private PeriodAcademicoRepository periodAcademicoRepository;
    @Mock private LineInvestigacionRepository lineInvestigacionRepository;
    @Mock private AreaTematicaRepository areaTematicaRepository;
    @Mock private AuditService auditService;
    @Mock private EstadoAcademicoRepository estadoAcademicoRepository;

    @InjectMocks
    private SubmissionServiceImpl submissionService;

    private Student student;
    private AppUser appUserStudent;
    private ModalityTitulacion modality;
    private AnnouncementTitulacion announcement;

    @BeforeEach
    void setUp() {
        appUserStudent = AppUser.builder().id(201L).nombre("Mario").apellido("Alvarado")
                .email("malvarado@uteq.edu.ec").role("ESTUDIANTE").build();
        student = Student.builder().id(5L).appUser(appUserStudent).build();
        modality = ModalityTitulacion.builder().id((short) 1).codigo("PROYECTO").nombre("Proyecto Tecnológico").build();
        announcement = AnnouncementTitulacion.builder().id(1).codigo("CONV-2026-01").activa(true).build();
    }

    private EstadoSubmission estado(String codigo) {
        return EstadoSubmission.builder().codigo(codigo).nombre(codigo).build();
    }

    @Test
    void testCreateSubmissionExitosa() {
        Submission datos = Submission.builder().tituloTopic("Sistema X").modalityTitulacion(modality).build();

        when(studentRepository.findById(5L)).thenReturn(Optional.of(student));
        when(estadoSubmissionRepository.findByCodigo("CREADA")).thenReturn(Optional.of(estado("CREADA")));
        when(modalityTitulacionRepository.findById((short) 1)).thenReturn(Optional.of(modality));
        when(announcementTitulacionRepository.findFirstByActivaTrue()).thenReturn(Optional.of(announcement));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        Submission creada = submissionService.createSubmission(5L, datos);

        assertNotNull(creada);
        assertEquals("CREADA", creada.getEstado().getCodigo());
        assertEquals(student, creada.getStudent());
        verify(submissionRepository).save(any(Submission.class));
    }

    @Test
    void testCreateSubmissionFallaSinModality() {
        Submission datos = Submission.builder().tituloTopic("Sistema X").build();
        when(studentRepository.findById(5L)).thenReturn(Optional.of(student));
        when(estadoSubmissionRepository.findByCodigo("CREADA")).thenReturn(Optional.of(estado("CREADA")));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                submissionService.createSubmission(5L, datos));
        assertTrue(ex.getMessage().contains("modalidad"));
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void testCreateSubmissionFallaSiStudentNoExiste() {
        Submission datos = Submission.builder().tituloTopic("Sistema X").modalityTitulacion(modality).build();
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> submissionService.createSubmission(99L, datos));
    }

    @Test
    void testCreateSubmissionPorAppUserCreaPerfilStudentAutomaticamente() {
        Program program = Program.builder().id(1).nombre("Ingeniería en Software").build();
        Submission datos = Submission.builder().tituloTopic("Sistema Y").modalityTitulacion(modality).build();

        when(studentRepository.findByAppUserId(201L)).thenReturn(Optional.empty());
        when(appUserRepository.findById(201L)).thenReturn(Optional.of(appUserStudent));
        when(programRepository.findAll()).thenReturn(List.of(program));
        // sp_generate_codigo_expediente (Fase 3): verifica que el service SI llama al SP
        // al create el perfil, no que calcule el codigo el mismo en Java.
        when(studentRepository.generateCodigoExpediente(null, null)).thenReturn("EXP-2026-00007");
        when(estadoAcademicoRepository.findByCodigo("ACTIVO"))
                .thenReturn(Optional.of(EstadoAcademico.builder().codigo("ACTIVO").nombre("Activo").build()));
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> {
            Student e = inv.getArgument(0);
            e.setId(5L);
            return e;
        });
        when(studentRepository.findById(5L)).thenReturn(Optional.of(student));
        when(estadoSubmissionRepository.findByCodigo("CREADA")).thenReturn(Optional.of(estado("CREADA")));
        when(modalityTitulacionRepository.findById((short) 1)).thenReturn(Optional.of(modality));
        when(announcementTitulacionRepository.findFirstByActivaTrue()).thenReturn(Optional.of(announcement));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        submissionService.createSubmissionPorAppUser(201L, datos);

        verify(studentRepository).generateCodigoExpediente(null, null);
        verify(studentRepository).save(argThat(e -> "EXP-2026-00007".equals(e.getExpedienteCodigo())));
    }

    @Test
    void testSendSubmissionFallaSinPdfProposal() {
        Submission s = Submission.builder().id(10L).student(student).tituloTopic("X").build();
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(s));
        when(proposalRepository.findBySubmissionId(10L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> submissionService.sendSubmission(10L));
        assertTrue(ex.getMessage().contains("anteproyecto"));
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void testSendSubmissionExitosaConPdf() {
        Submission s = Submission.builder().id(10L).student(student).tituloTopic("X").build();
        Proposal ap = Proposal.builder().archivoPdf("anteproyecto.pdf").build();

        when(submissionRepository.findById(10L)).thenReturn(Optional.of(s));
        when(proposalRepository.findBySubmissionId(10L)).thenReturn(Optional.of(ap));
        when(estadoSubmissionRepository.findByCodigo("ENVIADA")).thenReturn(Optional.of(estado("ENVIADA")));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        Submission enviada = submissionService.sendSubmission(10L);

        assertEquals("ENVIADA", enviada.getEstado().getCodigo());
    }

    @Test
    void testApproveSubmissionTransicionaAAprobada() {
        Submission s = Submission.builder().id(10L).student(student).tituloTopic("X").estado(estado("ENVIADA")).build();
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(s));
        when(estadoSubmissionRepository.findByCodigo("APROBADA")).thenReturn(Optional.of(estado("APROBADA")));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        Submission aprobada = submissionService.approveSubmission(10L);

        assertEquals("APROBADA", aprobada.getEstado().getCodigo());
    }

    @Test
    void testRejectConObservacionGuardaElMotivo() {
        Submission s = Submission.builder().id(10L).student(student).tituloTopic("X").estado(estado("ENVIADA")).build();
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(s));
        when(estadoSubmissionRepository.findByCodigo("RECHAZADA")).thenReturn(Optional.of(estado("RECHAZADA")));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        Submission rechazada = submissionService.rejectConObservacion(10L, "Tema ya registrado por otro estudiante");

        assertEquals("RECHAZADA", rechazada.getEstado().getCodigo());
        assertEquals("Tema ya registrado por otro estudiante", rechazada.getObservaciones());
    }

    @Test
    void testSuspenderSubmissionFallaEnEstadoCreada() {
        Submission s = Submission.builder().id(10L).student(student).tituloTopic("X").estado(estado("CREADA")).build();
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(s));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                submissionService.suspenderSubmission(10L, "motivo cualquiera"));
        assertTrue(ex.getMessage().contains("no puede ser suspendida"));
    }

    @Test
    void testSuspenderSubmissionFallaSinMotivo() {
        Submission s = Submission.builder().id(10L).student(student).tituloTopic("X").estado(estado("EVALUACION")).build();
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(s));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                submissionService.suspenderSubmission(10L, "  "));
        assertTrue(ex.getMessage().contains("motivo"));
    }

    @Test
    void testSuspenderSubmissionExitosaDesdeEstadoSuspendible() {
        Submission s = Submission.builder().id(10L).student(student).tituloTopic("X").estado(estado("EVALUACION")).build();
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(s));
        when(estadoSubmissionRepository.findByCodigo("SUSPENDIDA")).thenReturn(Optional.of(estado("SUSPENDIDA")));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        Submission suspendida = submissionService.suspenderSubmission(10L, "Estudiante retirado del período");

        assertEquals("SUSPENDIDA", suspendida.getEstado().getCodigo());
        assertEquals("Estudiante retirado del período", suspendida.getMotivoSuspension());
        assertNotNull(suspendida.getSuspendidoEn());
    }

    @Test
    void testGenerateReporteDefensasSPMapeaCadaColumnaDeLaFilaCruda() {
        // sp_generate_reporte_defensas (Fase 3 / Criterio P1) devuelve filas posicionales;
        // sin este test, un cambio en el orden de columnas del SP rompería el mapeo sin
        // que ningún test lo detectara.
        Object[] fila = new Object[]{
                10L, "Mario Alvarado", "EXP-2026-00007", "Sistema X", "COMPLETADA",
                java.sql.Timestamp.valueOf("2026-09-01 09:00:00"), "Aula 1", 8.5
        };
        when(submissionRepository.generateReporteDefensasSp("Software")).thenReturn(List.<Object[]>of(fila));

        List<java.util.Map<String, Object>> reporte = submissionService.generateReporteDefensasSP("Software");

        assertEquals(1, reporte.size());
        java.util.Map<String, Object> fila0 = reporte.get(0);
        assertEquals(10L, fila0.get("solicitudId"));
        assertEquals("Mario Alvarado", fila0.get("estudianteNombre"));
        assertEquals("EXP-2026-00007", fila0.get("expediente"));
        assertEquals("Sistema X", fila0.get("tituloTema"));
        assertEquals("COMPLETADA", fila0.get("estadoSolicitud"));
        assertEquals("Aula 1", fila0.get("salaNombre"));
        assertEquals(8.5, fila0.get("notaFinal"));
    }

    @Test
    void testGenerateReporteDefensasSPRetornaListaVaciaSinFilas() {
        when(submissionRepository.generateReporteDefensasSp("Inexistente")).thenReturn(List.of());

        List<java.util.Map<String, Object>> reporte = submissionService.generateReporteDefensasSP("Inexistente");

        assertTrue(reporte.isEmpty());
    }

    // ── createSubmission: validaciones y ramas restantes ──────────────────────

    @Test
    void createSubmissionLanzaSiTituloVacio() {
        Submission datos = Submission.builder().tituloTopic("  ").build();
        when(studentRepository.findById(5L)).thenReturn(Optional.of(student));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> submissionService.createSubmission(5L, datos));
        assertTrue(ex.getMessage().contains("obligatorio"));
    }

    @Test
    void createSubmissionLanzaSiTituloExcede300Caracteres() {
        Submission datos = Submission.builder().tituloTopic("A".repeat(301)).build();
        when(studentRepository.findById(5L)).thenReturn(Optional.of(student));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> submissionService.createSubmission(5L, datos));
        assertTrue(ex.getMessage().contains("300 caracteres"));
    }

    @Test
    void createSubmissionLanzaSiModalityIndicadaNoExiste() {
        Submission datos = Submission.builder().tituloTopic("Sistema X").modalityTitulacion(modality).build();
        when(studentRepository.findById(5L)).thenReturn(Optional.of(student));
        when(estadoSubmissionRepository.findByCodigo("CREADA")).thenReturn(Optional.of(estado("CREADA")));
        when(modalityTitulacionRepository.findById((short) 1)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> submissionService.createSubmission(5L, datos));
        assertTrue(ex.getMessage().contains("Modalidad no encontrada"));
    }

    @Test
    void createSubmissionUsaAnnouncementExplicitaSiViene() {
        AnnouncementTitulacion otraConv = AnnouncementTitulacion.builder().id(2).codigo("CONV-2026-02").build();
        Submission datos = Submission.builder().tituloTopic("Sistema X").modalityTitulacion(modality)
                .announcement(AnnouncementTitulacion.builder().id(2).build()).build();
        when(studentRepository.findById(5L)).thenReturn(Optional.of(student));
        when(estadoSubmissionRepository.findByCodigo("CREADA")).thenReturn(Optional.of(estado("CREADA")));
        when(modalityTitulacionRepository.findById((short) 1)).thenReturn(Optional.of(modality));
        when(announcementTitulacionRepository.findById(2)).thenReturn(Optional.of(otraConv));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        Submission creada = submissionService.createSubmission(5L, datos);

        assertEquals("CONV-2026-02", creada.getAnnouncement().getCodigo());
        verify(announcementTitulacionRepository, never()).findFirstByActivaTrue();
    }

    @Test
    void createSubmissionLanzaSiAnnouncementExplicitaNoExiste() {
        Submission datos = Submission.builder().tituloTopic("Sistema X").modalityTitulacion(modality)
                .announcement(AnnouncementTitulacion.builder().id(99).build()).build();
        when(studentRepository.findById(5L)).thenReturn(Optional.of(student));
        when(estadoSubmissionRepository.findByCodigo("CREADA")).thenReturn(Optional.of(estado("CREADA")));
        when(modalityTitulacionRepository.findById((short) 1)).thenReturn(Optional.of(modality));
        when(announcementTitulacionRepository.findById(99)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> submissionService.createSubmission(5L, datos));
        assertTrue(ex.getMessage().contains("Convocatoria no encontrada"));
    }

    @Test
    void createSubmissionResuelveLineDeInvestigacionSiViene() {
        LineInvestigacion line = LineInvestigacion.builder().id(3).nombre("IA").build();
        Submission datos = Submission.builder().tituloTopic("Sistema X").modalityTitulacion(modality)
                .lineInvestigacion(LineInvestigacion.builder().id(3).build()).build();
        when(studentRepository.findById(5L)).thenReturn(Optional.of(student));
        when(estadoSubmissionRepository.findByCodigo("CREADA")).thenReturn(Optional.of(estado("CREADA")));
        when(modalityTitulacionRepository.findById((short) 1)).thenReturn(Optional.of(modality));
        when(announcementTitulacionRepository.findFirstByActivaTrue()).thenReturn(Optional.of(announcement));
        when(lineInvestigacionRepository.findById(3)).thenReturn(Optional.of(line));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        Submission creada = submissionService.createSubmission(5L, datos);

        assertEquals("IA", creada.getLineInvestigacion().getNombre());
    }

    @Test
    void createSubmissionLanzaSiLineDeInvestigacionIndicadaNoExiste() {
        Submission datos = Submission.builder().tituloTopic("Sistema X").modalityTitulacion(modality)
                .lineInvestigacion(LineInvestigacion.builder().id(99).build()).build();
        when(studentRepository.findById(5L)).thenReturn(Optional.of(student));
        when(estadoSubmissionRepository.findByCodigo("CREADA")).thenReturn(Optional.of(estado("CREADA")));
        when(modalityTitulacionRepository.findById((short) 1)).thenReturn(Optional.of(modality));
        when(announcementTitulacionRepository.findFirstByActivaTrue()).thenReturn(Optional.of(announcement));
        when(lineInvestigacionRepository.findById(99)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> submissionService.createSubmission(5L, datos));
        assertTrue(ex.getMessage().contains("Línea de investigación no encontrada"));
    }

    @Test
    void createSubmissionResuelveAreaTematicaValidaParaLaLine() {
        LineInvestigacion line = LineInvestigacion.builder().id(3).nombre("IA").build();
        AreaTematica area = AreaTematica.builder().id(7).nombre("Visión por computador").lineInvestigacion(line).build();
        Submission datos = Submission.builder().tituloTopic("Sistema X").modalityTitulacion(modality)
                .lineInvestigacion(LineInvestigacion.builder().id(3).build())
                .areaTematica(AreaTematica.builder().id(7).build()).build();
        when(studentRepository.findById(5L)).thenReturn(Optional.of(student));
        when(estadoSubmissionRepository.findByCodigo("CREADA")).thenReturn(Optional.of(estado("CREADA")));
        when(modalityTitulacionRepository.findById((short) 1)).thenReturn(Optional.of(modality));
        when(announcementTitulacionRepository.findFirstByActivaTrue()).thenReturn(Optional.of(announcement));
        when(lineInvestigacionRepository.findById(3)).thenReturn(Optional.of(line));
        when(areaTematicaRepository.findById(7)).thenReturn(Optional.of(area));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        Submission creada = submissionService.createSubmission(5L, datos);

        assertEquals("Visión por computador", creada.getAreaTematica().getNombre());
    }

    @Test
    void createSubmissionLanzaSiAreaTematicaNoPerteneceALaLine() {
        LineInvestigacion lineElegida = LineInvestigacion.builder().id(3).nombre("IA").build();
        LineInvestigacion otraLine = LineInvestigacion.builder().id(4).nombre("Redes").build();
        AreaTematica areaDeOtraLine = AreaTematica.builder().id(7).nombre("Seguridad de redes").lineInvestigacion(otraLine).build();
        Submission datos = Submission.builder().tituloTopic("Sistema X").modalityTitulacion(modality)
                .lineInvestigacion(LineInvestigacion.builder().id(3).build())
                .areaTematica(AreaTematica.builder().id(7).build()).build();
        when(studentRepository.findById(5L)).thenReturn(Optional.of(student));
        when(estadoSubmissionRepository.findByCodigo("CREADA")).thenReturn(Optional.of(estado("CREADA")));
        when(modalityTitulacionRepository.findById((short) 1)).thenReturn(Optional.of(modality));
        when(announcementTitulacionRepository.findFirstByActivaTrue()).thenReturn(Optional.of(announcement));
        when(lineInvestigacionRepository.findById(3)).thenReturn(Optional.of(lineElegida));
        when(areaTematicaRepository.findById(7)).thenReturn(Optional.of(areaDeOtraLine));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> submissionService.createSubmission(5L, datos));
        assertTrue(ex.getMessage().contains("no pertenece a la línea"));
    }

    @Test
    void createSubmissionLanzaSiAreaTematicaIndicadaNoExiste() {
        Submission datos = Submission.builder().tituloTopic("Sistema X").modalityTitulacion(modality)
                .areaTematica(AreaTematica.builder().id(99).build()).build();
        when(studentRepository.findById(5L)).thenReturn(Optional.of(student));
        when(estadoSubmissionRepository.findByCodigo("CREADA")).thenReturn(Optional.of(estado("CREADA")));
        when(modalityTitulacionRepository.findById((short) 1)).thenReturn(Optional.of(modality));
        when(announcementTitulacionRepository.findFirstByActivaTrue()).thenReturn(Optional.of(announcement));
        when(areaTematicaRepository.findById(99)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> submissionService.createSubmission(5L, datos));
        assertTrue(ex.getMessage().contains("Área temática no encontrada"));
    }

    // ── createPerfilStudent (via createSubmissionPorAppUser) ────────────────

    @Test
    void createSubmissionPorAppUserLanzaSiAppUserNoExiste() {
        when(studentRepository.findByAppUserId(999L)).thenReturn(Optional.empty());
        when(appUserRepository.findById(999L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> submissionService.createSubmissionPorAppUser(999L, Submission.builder().build()));
        assertTrue(ex.getMessage().contains("Usuario no encontrado"));
    }

    @Test
    void createSubmissionPorAppUserLanzaSiAppUserNoEsStudent() {
        AppUser teacherAppUser = AppUser.builder().id(300L).role("DOCENTE").build();
        when(studentRepository.findByAppUserId(300L)).thenReturn(Optional.empty());
        when(appUserRepository.findById(300L)).thenReturn(Optional.of(teacherAppUser));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> submissionService.createSubmissionPorAppUser(300L, Submission.builder().build()));
        assertTrue(ex.getMessage().contains("no tiene rol de estudiante"));
    }

    @Test
    void createSubmissionPorAppUserLanzaSiNoHayProgramsConfiguradas() {
        when(studentRepository.findByAppUserId(201L)).thenReturn(Optional.empty());
        when(appUserRepository.findById(201L)).thenReturn(Optional.of(appUserStudent));
        when(programRepository.findAll()).thenReturn(List.of());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> submissionService.createSubmissionPorAppUser(201L, Submission.builder().build()));
        assertTrue(ex.getMessage().contains("No hay carreras configuradas"));
    }

    // ── Consultas simples ────────────────────────────────────────────────────

    @Test
    void listPorAppUserDevuelveVacioSiNoTienePerfilDeStudent() {
        when(studentRepository.findByAppUserId(999L)).thenReturn(Optional.empty());
        assertTrue(submissionService.listPorAppUser(999L).isEmpty());
    }

    @Test
    void listPorAppUserDelegaAlRepositorioSiTienePerfil() {
        when(studentRepository.findByAppUserId(201L)).thenReturn(Optional.of(student));
        when(submissionRepository.findByStudentId(5L)).thenReturn(List.of());
        assertTrue(submissionService.listPorAppUser(201L).isEmpty());
    }

    @Test
    void countPorEstadoIncluyeElTotalGeneralYCadaEstado() {
        when(submissionRepository.count()).thenReturn(42L);
        SubmissionRepository.EstadoCount c1 = mock(SubmissionRepository.EstadoCount.class);
        when(c1.getCodigo()).thenReturn("CREADA");
        when(c1.getTotal()).thenReturn(10L);
        when(submissionRepository.countAgrupadoPorEstado()).thenReturn(List.of(c1));

        java.util.Map<String, Long> counts = submissionService.countPorEstado();

        assertEquals(42L, counts.get("TODAS"));
        assertEquals(10L, counts.get("CREADA"));
    }

    @Test
    void obtainPorIdDelega() {
        Submission s = Submission.builder().id(10L).build();
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(s));
        assertEquals(Optional.of(s), submissionService.obtainPorId(10L));
    }

    @Test
    void listPorStudentDelega() {
        when(submissionRepository.findByStudentId(5L)).thenReturn(List.of());
        assertTrue(submissionService.listPorStudent(5L).isEmpty());
    }

    @Test
    void rejectSubmissionDelegaARejectConObservacionSinMotivo() {
        Submission s = Submission.builder().id(10L).student(student).tituloTopic("X").estado(estado("ENVIADA")).build();
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(s));
        when(estadoSubmissionRepository.findByCodigo("RECHAZADA")).thenReturn(Optional.of(estado("RECHAZADA")));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        Submission rechazada = submissionService.rejectSubmission(10L);

        assertEquals("RECHAZADA", rechazada.getEstado().getCodigo());
        assertNull(rechazada.getObservaciones());
    }

    @Test
    void listSubmissionsDelegaConLimiteFijo() {
        when(submissionRepository.findAllWithStudent(any())).thenReturn(List.of());
        assertTrue(submissionService.listSubmissions().isEmpty());
    }

    @Test
    void listSubmissionsPaginadoAcotaPaginaYTamanio() {
        when(submissionRepository.searchConFiltros(any(), any(), any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());
        submissionService.listSubmissionsPaginado(-1, 1000, "ENVIADA", "texto", null, null);
        verify(submissionRepository).searchConFiltros(eq("ENVIADA"), eq("texto"), any(), any(), any());
    }

    // ── obtainTracking ───────────────────────────────────────────────────

    @Test
    void obtainTrackingLanzaSiSubmissionNoExiste() {
        when(submissionRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> submissionService.obtainTracking(99L));
    }

    @Test
    void obtainTrackingEstadoEnviadaSinPdf() {
        Submission s = Submission.builder().id(10L).tituloTopic("X").estado(estado("ENVIADA"))
                .fechaRegistro(java.time.LocalDateTime.now()).actualizadoEn(java.time.LocalDateTime.now()).build();
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(s));
        when(proposalRepository.findBySubmissionId(10L)).thenReturn(Optional.empty());

        var dto = submissionService.obtainTracking(10L);

        assertEquals(20, dto.getPorcentajeProgress());
        assertEquals("EN_PROCESO", dto.getEtapas().get(1).getEstadoVisual());
        assertEquals("PENDIENTE", dto.getEtapas().get(2).getEstadoVisual());
        assertEquals("PENDIENTE", dto.getEtapas().get(3).getEstadoVisual());
    }

    @Test
    void obtainTrackingEstadoAprobadaConPdf() {
        Submission s = Submission.builder().id(10L).tituloTopic("X").estado(estado("APROBADA"))
                .fechaRegistro(java.time.LocalDateTime.now()).actualizadoEn(java.time.LocalDateTime.now()).build();
        Proposal ap = Proposal.builder().archivoPdf("doc.pdf").build();
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(s));
        when(proposalRepository.findBySubmissionId(10L)).thenReturn(Optional.of(ap));

        var dto = submissionService.obtainTracking(10L);

        assertEquals(50, dto.getPorcentajeProgress()); // tienePdf=true fuerza 50 al final
        assertEquals("COMPLETADO", dto.getEtapas().get(1).getEstadoVisual());
        assertEquals("COMPLETADO", dto.getEtapas().get(2).getEstadoVisual());
        assertEquals("COMPLETADO", dto.getEtapas().get(3).getEstadoVisual());
    }

    @Test
    void obtainTrackingEstadoRechazadaIncluyeObservaciones() {
        Submission s = Submission.builder().id(10L).tituloTopic("X").estado(estado("RECHAZADA"))
                .observaciones("Tema duplicado")
                .fechaRegistro(java.time.LocalDateTime.now()).actualizadoEn(java.time.LocalDateTime.now()).build();
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(s));
        when(proposalRepository.findBySubmissionId(10L)).thenReturn(Optional.empty());

        var dto = submissionService.obtainTracking(10L);

        assertEquals("RECHAZADO", dto.getEtapas().get(2).getEstadoVisual());
        assertTrue(dto.getEtapas().get(2).getDescripcion().contains("Tema duplicado"));
    }

    @Test
    void obtainTrackingEstadoCreadaEsProgressMinimo() {
        Submission s = Submission.builder().id(10L).tituloTopic("X").estado(estado("CREADA"))
                .fechaRegistro(java.time.LocalDateTime.now()).build();
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(s));
        when(proposalRepository.findBySubmissionId(10L)).thenReturn(Optional.empty());

        var dto = submissionService.obtainTracking(10L);

        assertEquals(10, dto.getPorcentajeProgress());
        assertEquals("PENDIENTE", dto.getEtapas().get(1).getEstadoVisual());
        assertEquals("PENDIENTE", dto.getEtapas().get(2).getEstadoVisual());
    }
}
