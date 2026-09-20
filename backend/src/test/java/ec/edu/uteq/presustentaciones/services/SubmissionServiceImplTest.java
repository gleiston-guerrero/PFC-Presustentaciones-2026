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
    @Mock private StatusSubmissionRepository statusSubmissionRepository;
    @Mock private ModalityDegreeRepository modalityDegreeRepository;
    @Mock private AnnouncementDegreeRepository announcementDegreeRepository;
    @Mock private ProgramRepository programRepository;
    @Mock private PeriodAcademicRepository periodAcademicRepository;
    @Mock private ResearchLineRepository researchLineRepository;
    @Mock private SubjectRepository subjectRepository;
    @Mock private AuditService auditService;
    @Mock private StatusAcademicRepository statusAcademicRepository;

    @InjectMocks
    private SubmissionServiceImpl submissionService;

    private Student student;
    private AppUser appUserStudent;
    private ModalityDegree modality;
    private AnnouncementDegree announcement;

    @BeforeEach
    void setUp() {
        appUserStudent = AppUser.builder().id(201L).nombre("Mario").apellido("Alvarado")
                .email("malvarado@uteq.edu.ec").role("ESTUDIANTE").build();
        student = Student.builder().id(5L).appUser(appUserStudent).build();
        modality = ModalityDegree.builder().id((short) 1).code("PROYECTO").nombre("Proyecto Tecnológico").build();
        announcement = AnnouncementDegree.builder().id(1).code("CONV-2026-01").active(true).build();
    }

    private StatusSubmission status(String code) {
        return StatusSubmission.builder().code(code).nombre(code).build();
    }

    @Test
    void testCreateSubmissionSuccessful() {
        Submission data = Submission.builder().tituloTopic("Sistema X").modalityDegree(modality).build();

        when(studentRepository.findById(5L)).thenReturn(Optional.of(student));
        when(statusSubmissionRepository.findByCode("CREADA")).thenReturn(Optional.of(status("CREADA")));
        when(modalityDegreeRepository.findById((short) 1)).thenReturn(Optional.of(modality));
        when(announcementDegreeRepository.findFirstByActiveTrue()).thenReturn(Optional.of(announcement));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        Submission creada = submissionService.createSubmission(5L, data);

        assertNotNull(creada);
        assertEquals("CREADA", creada.getStatus().getCode());
        assertEquals(student, creada.getStudent());
        verify(submissionRepository).save(any(Submission.class));
    }

    @Test
    void testCreateSubmissionFailsWithoutModality() {
        Submission data = Submission.builder().tituloTopic("Sistema X").build();
        when(studentRepository.findById(5L)).thenReturn(Optional.of(student));
        when(statusSubmissionRepository.findByCode("CREADA")).thenReturn(Optional.of(status("CREADA")));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                submissionService.createSubmission(5L, data));
        assertTrue(ex.getMessage().contains("modalidad"));
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void testCreateSubmissionFailsIfStudentNotExists() {
        Submission data = Submission.builder().tituloTopic("Sistema X").modalityDegree(modality).build();
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> submissionService.createSubmission(99L, data));
    }

    @Test
    void testCreateSubmissionByAppUserCreatesProfileStudentAutomatically() {
        Program program = Program.builder().id(1).nombre("Ingeniería en Software").build();
        Submission data = Submission.builder().tituloTopic("Sistema Y").modalityDegree(modality).build();

        when(studentRepository.findByAppUserId(201L)).thenReturn(Optional.empty());
        when(appUserRepository.findById(201L)).thenReturn(Optional.of(appUserStudent));
        when(programRepository.findAll()).thenReturn(List.of(program));
        // sp_generate_codigo_expediente (Fase 3): verifica que el service SI llama al SP
        // al create el perfil, no que calcule el codigo el mismo en Java.
        when(studentRepository.generateCodeExpediente(null, null)).thenReturn("EXP-2026-00007");
        when(statusAcademicRepository.findByCode("ACTIVO"))
                .thenReturn(Optional.of(StatusAcademic.builder().code("ACTIVO").nombre("Activo").build()));
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> {
            Student e = inv.getArgument(0);
            e.setId(5L);
            return e;
        });
        when(studentRepository.findById(5L)).thenReturn(Optional.of(student));
        when(statusSubmissionRepository.findByCode("CREADA")).thenReturn(Optional.of(status("CREADA")));
        when(modalityDegreeRepository.findById((short) 1)).thenReturn(Optional.of(modality));
        when(announcementDegreeRepository.findFirstByActiveTrue()).thenReturn(Optional.of(announcement));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        submissionService.createSubmissionByAppUser(201L, data);

        verify(studentRepository).generateCodeExpediente(null, null);
        verify(studentRepository).save(argThat(e -> "EXP-2026-00007".equals(e.getExpedienteCode())));
    }

    @Test
    void testSendSubmissionFailsWithoutPdfProposal() {
        Submission s = Submission.builder().id(10L).student(student).tituloTopic("X").build();
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(s));
        when(proposalRepository.findBySubmissionId(10L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> submissionService.sendSubmission(10L));
        assertTrue(ex.getMessage().contains("anteproyecto"));
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void testSendSubmissionSuccessfulWithPdf() {
        Submission s = Submission.builder().id(10L).student(student).tituloTopic("X").build();
        Proposal ap = Proposal.builder().filePdf("anteproyecto.pdf").build();

        when(submissionRepository.findById(10L)).thenReturn(Optional.of(s));
        when(proposalRepository.findBySubmissionId(10L)).thenReturn(Optional.of(ap));
        when(statusSubmissionRepository.findByCode("ENVIADA")).thenReturn(Optional.of(status("ENVIADA")));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        Submission enviada = submissionService.sendSubmission(10L);

        assertEquals("ENVIADA", enviada.getStatus().getCode());
    }

    @Test
    void testApproveSubmissionTransitionsToApproved() {
        Submission s = Submission.builder().id(10L).student(student).tituloTopic("X").status(status("ENVIADA")).build();
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(s));
        when(statusSubmissionRepository.findByCode("APROBADA")).thenReturn(Optional.of(status("APROBADA")));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        Submission aprobada = submissionService.approveSubmission(10L);

        assertEquals("APROBADA", aprobada.getStatus().getCode());
    }

    @Test
    void testRejectWithObservationSavesReason() {
        Submission s = Submission.builder().id(10L).student(student).tituloTopic("X").status(status("ENVIADA")).build();
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(s));
        when(statusSubmissionRepository.findByCode("RECHAZADA")).thenReturn(Optional.of(status("RECHAZADA")));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        Submission rechazada = submissionService.rejectWithObservation(10L, "Tema ya registrado por otro estudiante");

        assertEquals("RECHAZADA", rechazada.getStatus().getCode());
        assertEquals("Tema ya registrado por otro estudiante", rechazada.getObservations());
    }

    @Test
    void testSuspendSubmissionFailsInStatusCreated() {
        Submission s = Submission.builder().id(10L).student(student).tituloTopic("X").status(status("CREADA")).build();
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(s));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                submissionService.suspendSubmission(10L, "motivo cualquiera"));
        assertTrue(ex.getMessage().contains("no puede ser suspendida"));
    }

    @Test
    void testSuspendSubmissionFailsWithoutReason() {
        Submission s = Submission.builder().id(10L).student(student).tituloTopic("X").status(status("EVALUACION")).build();
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(s));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                submissionService.suspendSubmission(10L, "  "));
        assertTrue(ex.getMessage().contains("motivo"));
    }

    @Test
    void testSuspendSubmissionSuccessfulFromStatusSuspendable() {
        Submission s = Submission.builder().id(10L).student(student).tituloTopic("X").status(status("EVALUACION")).build();
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(s));
        when(statusSubmissionRepository.findByCode("SUSPENDIDA")).thenReturn(Optional.of(status("SUSPENDIDA")));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        Submission suspendida = submissionService.suspendSubmission(10L, "Estudiante retirado del período");

        assertEquals("SUSPENDIDA", suspendida.getStatus().getCode());
        assertEquals("Estudiante retirado del período", suspendida.getMotivoSuspension());
        assertNotNull(suspendida.getSuspendidoEn());
    }

    @Test
    void testGenerateReportDefensesSPMapsEachColumnOfRowRaw() {
        // sp_generate_reporte_defensas (Fase 3 / Criterio P1) devuelve filas posicionales;
        // sin este test, un cambio en el orden de columnas del SP rompería el mapeo sin
        // que ningún test lo detectara.
        Object[] row = new Object[]{
                10L, "Mario Alvarado", "EXP-2026-00007", "Sistema X", "COMPLETADA",
                java.sql.Timestamp.valueOf("2026-09-01 09:00:00"), "Aula 1", 8.5
        };
        when(submissionRepository.generateReportDefensesSp("Software")).thenReturn(List.<Object[]>of(row));

        List<java.util.Map<String, Object>> report = submissionService.generateReportDefensesSP("Software");

        assertEquals(1, report.size());
        java.util.Map<String, Object> row0 = report.get(0);
        assertEquals(10L, row0.get("solicitudId"));
        assertEquals("Mario Alvarado", row0.get("estudianteNombre"));
        assertEquals("EXP-2026-00007", row0.get("expediente"));
        assertEquals("Sistema X", row0.get("tituloTema"));
        assertEquals("COMPLETADA", row0.get("estadoSolicitud"));
        assertEquals("Aula 1", row0.get("salaNombre"));
        assertEquals(8.5, row0.get("notaFinal"));
    }

    @Test
    void testGenerateReportDefensesSPReturnsListEmptyWithoutRows() {
        when(submissionRepository.generateReportDefensesSp("Inexistente")).thenReturn(List.of());

        List<java.util.Map<String, Object>> report = submissionService.generateReportDefensesSP("Inexistente");

        assertTrue(report.isEmpty());
    }

    // ── createSubmission: validaciones y ramas restantes ──────────────────────

    @Test
    void createSubmissionThrowsIfTitleEmpty() {
        Submission data = Submission.builder().tituloTopic("  ").build();
        when(studentRepository.findById(5L)).thenReturn(Optional.of(student));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> submissionService.createSubmission(5L, data));
        assertTrue(ex.getMessage().contains("obligatorio"));
    }

    @Test
    void createSubmissionThrowsIfTitleExceeds300Characters() {
        Submission data = Submission.builder().tituloTopic("A".repeat(301)).build();
        when(studentRepository.findById(5L)).thenReturn(Optional.of(student));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> submissionService.createSubmission(5L, data));
        assertTrue(ex.getMessage().contains("300 caracteres"));
    }

    @Test
    void createSubmissionThrowsIfModalityGivenNotExists() {
        Submission data = Submission.builder().tituloTopic("Sistema X").modalityDegree(modality).build();
        when(studentRepository.findById(5L)).thenReturn(Optional.of(student));
        when(statusSubmissionRepository.findByCode("CREADA")).thenReturn(Optional.of(status("CREADA")));
        when(modalityDegreeRepository.findById((short) 1)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> submissionService.createSubmission(5L, data));
        assertTrue(ex.getMessage().contains("Modalidad no encontrada"));
    }

    @Test
    void createSubmissionUsesAnnouncementExplicitIfComes() {
        AnnouncementDegree otraConv = AnnouncementDegree.builder().id(2).code("CONV-2026-02").build();
        Submission data = Submission.builder().tituloTopic("Sistema X").modalityDegree(modality)
                .announcement(AnnouncementDegree.builder().id(2).build()).build();
        when(studentRepository.findById(5L)).thenReturn(Optional.of(student));
        when(statusSubmissionRepository.findByCode("CREADA")).thenReturn(Optional.of(status("CREADA")));
        when(modalityDegreeRepository.findById((short) 1)).thenReturn(Optional.of(modality));
        when(announcementDegreeRepository.findById(2)).thenReturn(Optional.of(otraConv));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        Submission creada = submissionService.createSubmission(5L, data);

        assertEquals("CONV-2026-02", creada.getAnnouncement().getCode());
        verify(announcementDegreeRepository, never()).findFirstByActiveTrue();
    }

    @Test
    void createSubmissionThrowsIfAnnouncementExplicitNotExists() {
        Submission data = Submission.builder().tituloTopic("Sistema X").modalityDegree(modality)
                .announcement(AnnouncementDegree.builder().id(99).build()).build();
        when(studentRepository.findById(5L)).thenReturn(Optional.of(student));
        when(statusSubmissionRepository.findByCode("CREADA")).thenReturn(Optional.of(status("CREADA")));
        when(modalityDegreeRepository.findById((short) 1)).thenReturn(Optional.of(modality));
        when(announcementDegreeRepository.findById(99)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> submissionService.createSubmission(5L, data));
        assertTrue(ex.getMessage().contains("Convocatoria no encontrada"));
    }

    @Test
    void createSubmissionResolvesLineOfResearchIfComes() {
        ResearchLine line = ResearchLine.builder().id(3).nombre("IA").build();
        Submission data = Submission.builder().tituloTopic("Sistema X").modalityDegree(modality)
                .researchLine(ResearchLine.builder().id(3).build()).build();
        when(studentRepository.findById(5L)).thenReturn(Optional.of(student));
        when(statusSubmissionRepository.findByCode("CREADA")).thenReturn(Optional.of(status("CREADA")));
        when(modalityDegreeRepository.findById((short) 1)).thenReturn(Optional.of(modality));
        when(announcementDegreeRepository.findFirstByActiveTrue()).thenReturn(Optional.of(announcement));
        when(researchLineRepository.findById(3)).thenReturn(Optional.of(line));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        Submission creada = submissionService.createSubmission(5L, data);

        assertEquals("IA", creada.getResearchLine().getNombre());
    }

    @Test
    void createSubmissionThrowsIfLineOfResearchGivenNotExists() {
        Submission data = Submission.builder().tituloTopic("Sistema X").modalityDegree(modality)
                .researchLine(ResearchLine.builder().id(99).build()).build();
        when(studentRepository.findById(5L)).thenReturn(Optional.of(student));
        when(statusSubmissionRepository.findByCode("CREADA")).thenReturn(Optional.of(status("CREADA")));
        when(modalityDegreeRepository.findById((short) 1)).thenReturn(Optional.of(modality));
        when(announcementDegreeRepository.findFirstByActiveTrue()).thenReturn(Optional.of(announcement));
        when(researchLineRepository.findById(99)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> submissionService.createSubmission(5L, data));
        assertTrue(ex.getMessage().contains("Línea de investigación no encontrada"));
    }

    @Test
    void createSubmissionResolvesSubjectValidatesForLine() {
        ResearchLine line = ResearchLine.builder().id(3).nombre("IA").build();
        Subject area = Subject.builder().id(7).nombre("Visión por computador").researchLine(line).build();
        Submission data = Submission.builder().tituloTopic("Sistema X").modalityDegree(modality)
                .researchLine(ResearchLine.builder().id(3).build())
                .subject(Subject.builder().id(7).build()).build();
        when(studentRepository.findById(5L)).thenReturn(Optional.of(student));
        when(statusSubmissionRepository.findByCode("CREADA")).thenReturn(Optional.of(status("CREADA")));
        when(modalityDegreeRepository.findById((short) 1)).thenReturn(Optional.of(modality));
        when(announcementDegreeRepository.findFirstByActiveTrue()).thenReturn(Optional.of(announcement));
        when(researchLineRepository.findById(3)).thenReturn(Optional.of(line));
        when(subjectRepository.findById(7)).thenReturn(Optional.of(area));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        Submission creada = submissionService.createSubmission(5L, data);

        assertEquals("Visión por computador", creada.getSubject().getNombre());
    }

    @Test
    void createSubmissionThrowsIfSubjectNotBelongsToLine() {
        ResearchLine lineElegida = ResearchLine.builder().id(3).nombre("IA").build();
        ResearchLine otraLine = ResearchLine.builder().id(4).nombre("Redes").build();
        Subject areaDeOtraLine = Subject.builder().id(7).nombre("Seguridad de redes").researchLine(otraLine).build();
        Submission data = Submission.builder().tituloTopic("Sistema X").modalityDegree(modality)
                .researchLine(ResearchLine.builder().id(3).build())
                .subject(Subject.builder().id(7).build()).build();
        when(studentRepository.findById(5L)).thenReturn(Optional.of(student));
        when(statusSubmissionRepository.findByCode("CREADA")).thenReturn(Optional.of(status("CREADA")));
        when(modalityDegreeRepository.findById((short) 1)).thenReturn(Optional.of(modality));
        when(announcementDegreeRepository.findFirstByActiveTrue()).thenReturn(Optional.of(announcement));
        when(researchLineRepository.findById(3)).thenReturn(Optional.of(lineElegida));
        when(subjectRepository.findById(7)).thenReturn(Optional.of(areaDeOtraLine));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> submissionService.createSubmission(5L, data));
        assertTrue(ex.getMessage().contains("no pertenece a la línea"));
    }

    @Test
    void createSubmissionThrowsIfSubjectGivenNotExists() {
        Submission data = Submission.builder().tituloTopic("Sistema X").modalityDegree(modality)
                .subject(Subject.builder().id(99).build()).build();
        when(studentRepository.findById(5L)).thenReturn(Optional.of(student));
        when(statusSubmissionRepository.findByCode("CREADA")).thenReturn(Optional.of(status("CREADA")));
        when(modalityDegreeRepository.findById((short) 1)).thenReturn(Optional.of(modality));
        when(announcementDegreeRepository.findFirstByActiveTrue()).thenReturn(Optional.of(announcement));
        when(subjectRepository.findById(99)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> submissionService.createSubmission(5L, data));
        assertTrue(ex.getMessage().contains("Área temática no encontrada"));
    }

    // ── createPerfilStudent (via createSubmissionPorAppUser) ────────────────

    @Test
    void createSubmissionByAppUserThrowsIfAppUserNotExists() {
        when(studentRepository.findByAppUserId(999L)).thenReturn(Optional.empty());
        when(appUserRepository.findById(999L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> submissionService.createSubmissionByAppUser(999L, Submission.builder().build()));
        assertTrue(ex.getMessage().contains("Usuario no encontrado"));
    }

    @Test
    void createSubmissionByAppUserThrowsIfAppUserNotIsStudent() {
        AppUser teacherAppUser = AppUser.builder().id(300L).role("DOCENTE").build();
        when(studentRepository.findByAppUserId(300L)).thenReturn(Optional.empty());
        when(appUserRepository.findById(300L)).thenReturn(Optional.of(teacherAppUser));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> submissionService.createSubmissionByAppUser(300L, Submission.builder().build()));
        assertTrue(ex.getMessage().contains("no tiene rol de estudiante"));
    }

    @Test
    void createSubmissionByAppUserThrowsIfNotHasProgramsConfigured() {
        when(studentRepository.findByAppUserId(201L)).thenReturn(Optional.empty());
        when(appUserRepository.findById(201L)).thenReturn(Optional.of(appUserStudent));
        when(programRepository.findAll()).thenReturn(List.of());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> submissionService.createSubmissionByAppUser(201L, Submission.builder().build()));
        assertTrue(ex.getMessage().contains("No hay carreras configuradas"));
    }

    // ── Consultas simples ────────────────────────────────────────────────────

    @Test
    void listByAppUserReturnsEmptyIfNotHasProfileOfStudent() {
        when(studentRepository.findByAppUserId(999L)).thenReturn(Optional.empty());
        assertTrue(submissionService.listByAppUser(999L).isEmpty());
    }

    @Test
    void listByAppUserDelegatesToRepositoryIfHasProfile() {
        when(studentRepository.findByAppUserId(201L)).thenReturn(Optional.of(student));
        when(submissionRepository.findByStudentId(5L)).thenReturn(List.of());
        assertTrue(submissionService.listByAppUser(201L).isEmpty());
    }

    @Test
    void countByStatusIncludesTotalGeneralAndEachStatus() {
        when(submissionRepository.count()).thenReturn(42L);
        SubmissionRepository.StatusCount c1 = mock(SubmissionRepository.StatusCount.class);
        when(c1.getCode()).thenReturn("CREADA");
        when(c1.getTotal()).thenReturn(10L);
        when(submissionRepository.countAgrupadoByStatus()).thenReturn(List.of(c1));

        java.util.Map<String, Long> counts = submissionService.countByStatus();

        assertEquals(42L, counts.get("TODAS"));
        assertEquals(10L, counts.get("CREADA"));
    }

    @Test
    void obtainByIdDelegates() {
        Submission s = Submission.builder().id(10L).build();
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(s));
        assertEquals(Optional.of(s), submissionService.obtainById(10L));
    }

    @Test
    void listByStudentDelegates() {
        when(submissionRepository.findByStudentId(5L)).thenReturn(List.of());
        assertTrue(submissionService.listByStudent(5L).isEmpty());
    }

    @Test
    void rejectSubmissionDelegatesToRejectWithObservationWithoutReason() {
        Submission s = Submission.builder().id(10L).student(student).tituloTopic("X").status(status("ENVIADA")).build();
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(s));
        when(statusSubmissionRepository.findByCode("RECHAZADA")).thenReturn(Optional.of(status("RECHAZADA")));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        Submission rechazada = submissionService.rejectSubmission(10L);

        assertEquals("RECHAZADA", rechazada.getStatus().getCode());
        assertNull(rechazada.getObservations());
    }

    @Test
    void listSubmissionsDelegatesWithLimitFixed() {
        when(submissionRepository.findAllWithStudent(any())).thenReturn(List.of());
        assertTrue(submissionService.listSubmissions().isEmpty());
    }

    @Test
    void listSubmissionsPagedBoundsPageAndSize() {
        when(submissionRepository.searchWithFiltros(any(), any(), any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());
        submissionService.listSubmissionsPaged(-1, 1000, "ENVIADA", "texto", null, null);
        verify(submissionRepository).searchWithFiltros(eq("ENVIADA"), eq("texto"), any(), any(), any());
    }

    // ── obtainTracking ───────────────────────────────────────────────────

    @Test
    void obtainTrackingThrowsIfSubmissionNotExists() {
        when(submissionRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> submissionService.obtainTracking(99L));
    }

    @Test
    void obtainTrackingStatusSentWithoutPdf() {
        Submission s = Submission.builder().id(10L).tituloTopic("X").status(status("ENVIADA"))
                .dateRecord(java.time.LocalDateTime.now()).actualizadoEn(java.time.LocalDateTime.now()).build();
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(s));
        when(proposalRepository.findBySubmissionId(10L)).thenReturn(Optional.empty());

        var dto = submissionService.obtainTracking(10L);

        assertEquals(20, dto.getPorcentajeProgress());
        assertEquals("EN_PROCESO", dto.getEtapas().get(1).getStatusVisual());
        assertEquals("PENDIENTE", dto.getEtapas().get(2).getStatusVisual());
        assertEquals("PENDIENTE", dto.getEtapas().get(3).getStatusVisual());
    }

    @Test
    void obtainTrackingStatusApprovedWithPdf() {
        Submission s = Submission.builder().id(10L).tituloTopic("X").status(status("APROBADA"))
                .dateRecord(java.time.LocalDateTime.now()).actualizadoEn(java.time.LocalDateTime.now()).build();
        Proposal ap = Proposal.builder().filePdf("doc.pdf").build();
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(s));
        when(proposalRepository.findBySubmissionId(10L)).thenReturn(Optional.of(ap));

        var dto = submissionService.obtainTracking(10L);

        assertEquals(50, dto.getPorcentajeProgress()); // tienePdf=true fuerza 50 al final
        assertEquals("COMPLETADO", dto.getEtapas().get(1).getStatusVisual());
        assertEquals("COMPLETADO", dto.getEtapas().get(2).getStatusVisual());
        assertEquals("COMPLETADO", dto.getEtapas().get(3).getStatusVisual());
    }

    @Test
    void obtainTrackingStatusRejectedIncludesObservations() {
        Submission s = Submission.builder().id(10L).tituloTopic("X").status(status("RECHAZADA"))
                .observations("Tema duplicado")
                .dateRecord(java.time.LocalDateTime.now()).actualizadoEn(java.time.LocalDateTime.now()).build();
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(s));
        when(proposalRepository.findBySubmissionId(10L)).thenReturn(Optional.empty());

        var dto = submissionService.obtainTracking(10L);

        assertEquals("RECHAZADO", dto.getEtapas().get(2).getStatusVisual());
        assertTrue(dto.getEtapas().get(2).getDescription().contains("Tema duplicado"));
    }

    @Test
    void obtainTrackingStatusCreatedIsProgressMinimum() {
        Submission s = Submission.builder().id(10L).tituloTopic("X").status(status("CREADA"))
                .dateRecord(java.time.LocalDateTime.now()).build();
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(s));
        when(proposalRepository.findBySubmissionId(10L)).thenReturn(Optional.empty());

        var dto = submissionService.obtainTracking(10L);

        assertEquals(10, dto.getPorcentajeProgress());
        assertEquals("PENDIENTE", dto.getEtapas().get(1).getStatusVisual());
        assertEquals("PENDIENTE", dto.getEtapas().get(2).getStatusVisual());
    }
}
