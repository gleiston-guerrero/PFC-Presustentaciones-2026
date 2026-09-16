package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.*;
import ec.edu.uteq.presustentaciones.repositories.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MinutesServiceImpl.signMinutes() implementa las reglas reales de negocio (validacion de role,
 * invocacion de sp_sign_minutes_digital -- Fase 3 / Criterio P1 --, transicion de la submission
 * a COMPLETADA solo cuando las 4 firmas estan puestas). Sin test dedicado pese a ser el unico
 * punto del codigo que invoca ese procedimiento almacenado.
 */
@ExtendWith(MockitoExtension.class)
class MinutesServiceImplTest {

    @TempDir
    Path tempDir;

    @Mock private MinutesRepository minutesRepository;
    @Mock private SubmissionRepository submissionRepository;
    @Mock private EvaluationFinalRepository evaluationRepository;
    @Mock private PanelistRepository panelistRepository;
    @Mock private EstadoSubmissionRepository estadoSubmissionRepository;
    @Mock private EntityManager entityManager;
    @Mock private NotificationService notificationService;
    @Mock private AuditService auditService;
    @Mock private TutorRepository tutorRepository;
    @Mock private ec.edu.uteq.presustentaciones.repositories.EstadoMinutesRepository estadoMinutesRepository;
    @Mock private ec.edu.uteq.presustentaciones.repositories.HistoryEstadoMinutesRepository historyEstadoMinutesRepository;
    @Mock private ec.edu.uteq.presustentaciones.repositories.AppUserRepository appUserRepository;
    @Mock private PermissionService permissionService;

    @InjectMocks
    private MinutesServiceImpl minutesService;

    private AppUser appUserStudent;
    private Student student;
    private Submission submission;

    private static EstadoMinutes estadoMinutes(int id, String codigo) {
        return EstadoMinutes.builder().id((short) id).codigo(codigo).nombre(codigo).orden((short) id).build();
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(minutesService, "minutesDir", tempDir.toString());

        appUserStudent = AppUser.builder().id(10L).nombre("Ana").apellido("Torres")
                .email("atorres@uteq.edu.ec").role("ESTUDIANTE").build();
        student = Student.builder().id(3L).appUser(appUserStudent).build();
        submission = Submission.builder().id(7L).student(student).tituloTopic("Sistema X").build();

        // Hallazgo real (2026-09-01): MinutesServiceImpl.validateAcceso()/signMinutes() ahora exigen un
        // SecurityContextHolder autenticado (control de acceso real agregado por el equipo). Sin
        // limpiar el contexto entre tests, SecurityContextHolder (ThreadLocal) queda "sucio" entre
        // metodos -- algunos tests pasaban solo por herencia accidental del contexto ADMIN dejado
        // por deleteMinutesExitosoSiEsAdmin() al correr antes en el mismo hilo, y fallaban si corrian
        // en otro orden. Se autentica como ADMIN por defecto aqui (bypassa las reglas de propiedad,
        // igual que ya hacia deleteMinutesExitosoSiEsAdmin) para que cada test sea determinista sin
        // importar el orden; los tests que SI prueban las reglas de autorizacion (deleteMinutes*)
        // sobreescriben este contexto explicitamente como ya lo hacian.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@uteq.edu.ec", null,
                        org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_ADMIN")));

        // V19: el minutes tiene estado (catálogo estados_minutes) y cada transición se registra
        // en history_estados_minutes. Stubs leniente porque no todos los tests los ejercen.
        lenient().when(estadoMinutesRepository.findByCodigo("GENERADA")).thenReturn(Optional.of(estadoMinutes(1, "GENERADA")));
        lenient().when(estadoMinutesRepository.findByCodigo("REVISADA")).thenReturn(Optional.of(estadoMinutes(2, "REVISADA")));
        lenient().when(estadoMinutesRepository.findByCodigo("OBSERVADA")).thenReturn(Optional.of(estadoMinutes(3, "OBSERVADA")));
        lenient().when(estadoMinutesRepository.findByCodigo("FINALIZADA")).thenReturn(Optional.of(estadoMinutes(4, "FINALIZADA")));
        lenient().when(estadoMinutesRepository.findByCodigo("ANULADA")).thenReturn(Optional.of(estadoMinutes(5, "ANULADA")));
        lenient().when(appUserRepository.findByEmail(any())).thenReturn(Optional.empty());
        lenient().when(historyEstadoMinutesRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Minutes minutesConFirmas(boolean presidente, boolean vocal1, boolean vocal2, boolean tutor) {
        Minutes minutes = Minutes.builder()
                .id(1L)
                .submission(submission)
                .firmadaPresidente(presidente)
                .firmadaVocal1(vocal1)
                .firmadaVocal2(vocal2)
                .firmadaTutor(tutor)
                .build();
        minutes.updateEstadoFirma();
        return minutes;
    }

    @Test
    void signMinutesConRoleInvalidoLanzaExcepcionYNoLlamaAlProcedimiento() {
        Minutes minutes = minutesConFirmas(false, false, false, false);
        when(minutesRepository.findById(1L)).thenReturn(Optional.of(minutes));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> minutesService.signMinutes(1L, "SECRETARIO", "obs"));

        assertTrue(ex.getMessage().contains("Rol inválido"));
        verify(minutesRepository, never()).signMinutesDigital(any(), any(), any());
    }

    @Test
    void signMinutesParcialNoCompletaLaSubmissionNiRegeneraElPdf() {
        // Ya firmado por Presidente y Vocal 1; esta llamada firma Vocal 2 -- Tutor sigue pendiente.
        Minutes minutes = minutesConFirmas(true, true, false, false);
        when(minutesRepository.findById(1L)).thenReturn(Optional.of(minutes));
        when(minutesRepository.save(any(Minutes.class))).thenAnswer(inv -> inv.getArgument(0));

        Minutes resultado = minutesService.signMinutes(1L, "vocal_2", "Todo correcto");

        verify(minutesRepository).signMinutesDigital(1L, "VOCAL_2", "Todo correcto");
        verify(entityManager).refresh(minutes);
        assertFalse(resultado.isFirmada());
        verify(submissionRepository, never()).save(any());
        verify(estadoSubmissionRepository, never()).findByCodigo(any());
    }

    @Test
    void signMinutesCuandoQuedanLas4FirmasCompletaLaSubmissionYNotifica() {
        // Las 4 ya estaban en true al reload (entityManager.refresh esta mockeado como no-op,
        // asi que el objeto que devuelve findById ya simula el estado post-SP/post-refresh).
        Minutes minutes = minutesConFirmas(true, true, true, true);
        minutes.setArchivoPdf("acta_7.pdf");
        when(minutesRepository.findById(1L)).thenReturn(Optional.of(minutes));
        when(minutesRepository.save(any(Minutes.class))).thenAnswer(inv -> inv.getArgument(0));
        when(estadoSubmissionRepository.findByCodigo("COMPLETADA"))
                .thenReturn(Optional.of(EstadoSubmission.builder().codigo("COMPLETADA").nombre("Completada").build()));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));
        when(evaluationRepository.findBySubmissionId(7L)).thenReturn(Optional.empty());
        when(panelistRepository.findBySubmissionId(7L)).thenReturn(List.of());

        Minutes resultado = minutesService.signMinutes(1L, "TUTOR", null);

        assertTrue(resultado.isFirmada());
        assertEquals("COMPLETADA", submission.getEstado().getCodigo());
        verify(submissionRepository).save(submission);
        verify(notificationService).createNotification(eq(10L), contains("firmó"));
        verify(notificationService).createNotification(eq(10L), contains("finalizado"));
        // El PDF se regenera de verdad (iText real) en tempDir -- confirma que generatePdf()
        // corre sin lanzar excepcion con datos minimos (evaluation/panelists vacios).
        assertTrue(tempDir.resolve("acta_7.pdf").toFile().exists());
    }

    @Test
    void signMinutesSiFallaLaNotificationNoPropagaLaExcepcion() {
        Minutes minutes = minutesConFirmas(true, true, false, false);
        when(minutesRepository.findById(1L)).thenReturn(Optional.of(minutes));
        when(minutesRepository.save(any(Minutes.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("servicio de notificaciones caido"))
                .when(notificationService).createNotification(any(), any());

        assertDoesNotThrow(() -> minutesService.signMinutes(1L, "VOCAL_2", null));
    }

    @Test
    void obtainPdfBytesLanzaExcepcionSiElMinutesNoTienePdfGenerado() {
        Minutes minutes = minutesConFirmas(false, false, false, false);
        when(minutesRepository.findById(1L)).thenReturn(Optional.of(minutes));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> minutesService.obtainPdfBytes(1L));
        assertTrue(ex.getMessage().contains("PDF"));
    }

    @Test
    void obtainPdfBytesLanzaExcepcionSiElMinutesNoExiste() {
        when(minutesRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> minutesService.obtainPdfBytes(99L));
    }

    // generateMinutes() -- RF-06 (Must), endpoint POST /api/v1/minutes/generate. Sin test dedicado
    // pese a que matriz.csv lo cita explicitamente como "ninguna (ActaServiceImplTest.java
    // no existe)" -- ahora existe.
    @Test
    void generateMinutesLanzaExcepcionSiLaSubmissionNoExiste() {
        when(submissionRepository.findById(7L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> minutesService.generateMinutes(7L));
        verify(minutesRepository, never()).save(any());
    }

    @Test
    void generateMinutesRetornaLaExistenteSinRegenerateElPdf() {
        Minutes existente = minutesConFirmas(false, false, false, false);
        when(submissionRepository.findById(7L)).thenReturn(Optional.of(submission));
        when(evaluationRepository.findBySubmissionId(7L)).thenReturn(Optional.of(new ec.edu.uteq.presustentaciones.entities.EvaluationFinal()));
        when(panelistRepository.findBySubmissionId(7L)).thenReturn(List.of());
        when(minutesRepository.findBySubmissionId(7L)).thenReturn(Optional.of(existente));

        Minutes resultado = minutesService.generateMinutes(7L);

        assertSame(existente, resultado);
        verify(minutesRepository, never()).save(any());
    }

    @Test
    void generateMinutesCreaUnaNuevaConPdfRealYLaGuarda() {
        when(submissionRepository.findById(7L)).thenReturn(Optional.of(submission));
        when(evaluationRepository.findBySubmissionId(7L)).thenReturn(Optional.of(new ec.edu.uteq.presustentaciones.entities.EvaluationFinal()));
        when(panelistRepository.findBySubmissionId(7L)).thenReturn(List.of());
        when(minutesRepository.findBySubmissionId(7L)).thenReturn(Optional.empty());
        when(minutesRepository.save(any(Minutes.class))).thenAnswer(inv -> inv.getArgument(0));

        Minutes resultado = minutesService.generateMinutes(7L);

        assertNotNull(resultado);
        assertEquals(submission, resultado.getSubmission());
        assertNotNull(resultado.getArchivoPdf());
        assertTrue(tempDir.resolve(resultado.getArchivoPdf()).toFile().exists());
        verify(minutesRepository).save(any(Minutes.class));
    }

    @Test
    void searchPorSubmissionDelegaAlRepositorio() {
        Minutes minutes = minutesConFirmas(false, false, false, false);
        when(minutesRepository.findBySubmissionId(7L)).thenReturn(Optional.of(minutes));

        Optional<Minutes> resultado = minutesService.searchPorSubmission(7L);

        assertTrue(resultado.isPresent());
        verify(minutesRepository).findBySubmissionId(7L);
    }

    @Test
    void deleteMinutesExitosoSiEsAdmin() {
        Minutes minutes = minutesConFirmas(false, false, false, false);
        minutes.setArchivoPdf("test.pdf");
        when(minutesRepository.findById(1L)).thenReturn(Optional.of(minutes));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@uteq.edu.ec", null,
                        org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_ADMIN")));

        assertDoesNotThrow(() -> minutesService.deleteMinutes(1L));
        verify(minutesRepository).delete(minutes);
    }

    @Test
    void deleteMinutesLanzaExcepcionSiNoTieneAcceso() {
        Minutes minutes = minutesConFirmas(false, false, false, false);
        when(minutesRepository.findById(1L)).thenReturn(Optional.of(minutes));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("otro@uteq.edu.ec", null,
                        org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_ESTUDIANTE")));

        when(panelistRepository.findBySubmissionId(7L)).thenReturn(List.of());
        when(tutorRepository.findBySubmissionId(7L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> minutesService.deleteMinutes(1L));
        assertTrue(ex.getMessage().contains("No tienes permiso"));
        verify(minutesRepository, never()).delete(any());
    }

    // ═══ Módulo 2: gestión e history de minutes (V19) ═══════════════════════════

    private Minutes minutesConEstado(String codigoEstado) {
        Minutes minutes = minutesConFirmas(false, false, false, false);
        minutes.setEstado(estadoMinutes(1, codigoEstado));
        return minutes;
    }

    private void autenticarComo(String email, String rolee) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null,
                        org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_" + rolee)));
    }

    @Test
    void obtainDetalleDeMinutesAjenaLanzaExcepcionAunConIdConocido() {
        // IDOR/BOLA: teacher B pide el minutes de una submission donde no es panelist ni tutor.
        Minutes minutes = minutesConEstado("GENERADA");
        autenticarComo("docenteB@uteq.edu.ec", "DOCENTE");
        when(minutesRepository.findDetalleById(1L)).thenReturn(Optional.of(minutes));
        when(panelistRepository.findBySubmissionId(7L)).thenReturn(List.of());
        when(tutorRepository.findBySubmissionId(7L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> minutesService.obtainDetalle(1L));
        assertTrue(ex.getMessage().contains("No tienes permiso"));
    }

    @Test
    void obtainDetalleLoPermiteAlCoordinadorViaPermission() {
        Minutes minutes = minutesConEstado("GENERADA");
        autenticarComo("coord@uteq.edu.ec", "COORDINADOR");
        when(permissionService.tienePermission(any(), eq("ACTAS_VER"))).thenReturn(true);
        when(minutesRepository.findDetalleById(1L)).thenReturn(Optional.of(minutes));
        when(panelistRepository.findBySubmissionId(7L)).thenReturn(List.of());

        assertNotNull(minutesService.obtainDetalle(1L));
    }

    @Test
    void changeEstadoValidoRegistraHistoryYActualizaElMinutes() {
        Minutes minutes = minutesConEstado("GENERADA");
        when(minutesRepository.findDetalleById(1L)).thenReturn(Optional.of(minutes));
        when(minutesRepository.save(any(Minutes.class))).thenAnswer(inv -> inv.getArgument(0));

        Minutes resultado = minutesService.changeEstado(1L, "revisada", "Revisado por el coordinador");

        assertEquals("REVISADA", resultado.getEstado().getCodigo());
        var captor = org.mockito.ArgumentCaptor.forClass(ec.edu.uteq.presustentaciones.entities.HistoryEstadoMinutes.class);
        verify(historyEstadoMinutesRepository).save(captor.capture());
        assertEquals("CAMBIO_ESTADO", captor.getValue().getAccion());
        assertEquals("GENERADA", captor.getValue().getEstadoAnterior().getCodigo());
        assertEquals("REVISADA", captor.getValue().getEstadoNuevo().getCodigo());
    }

    @Test
    void changeEstadoConTransicionNoPermitidaLanzaExcepcion() {
        Minutes minutes = minutesConEstado("GENERADA");
        autenticarComo("coord@uteq.edu.ec", "COORDINADOR");
        when(minutesRepository.findDetalleById(1L)).thenReturn(Optional.of(minutes));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> minutesService.changeEstado(1L, "FINALIZADA", null));
        assertTrue(ex.getMessage().contains("no permitida"));
        verify(historyEstadoMinutesRepository, never()).save(any());
        verify(minutesRepository, never()).save(any());
    }

    @Test
    void changeEstadoAObservadaSinMotivoLanzaExcepcion() {
        Minutes minutes = minutesConEstado("GENERADA");
        when(minutesRepository.findDetalleById(1L)).thenReturn(Optional.of(minutes));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> minutesService.changeEstado(1L, "OBSERVADA", "   "));
        assertTrue(ex.getMessage().toLowerCase().contains("motivo"));
    }

    @Test
    void obtainHistoryDevuelveLasEntradasDelRepositorio() {
        Minutes minutes = minutesConEstado("GENERADA");
        when(minutesRepository.findDetalleById(1L)).thenReturn(Optional.of(minutes));
        var h = ec.edu.uteq.presustentaciones.entities.HistoryEstadoMinutes.builder()
                .id(1L).minutes(minutes).estadoNuevo(estadoMinutes(1, "GENERADA")).accion("CREAR")
                .fechaCambio(java.time.LocalDateTime.now()).build();
        when(historyEstadoMinutesRepository.findByMinutesIdOrderByFechaCambioDesc(1L)).thenReturn(List.of(h));

        var timeline = minutesService.obtainHistory(1L);

        assertEquals(1, timeline.size());
        assertEquals("CREAR", timeline.get(0).getAccion());
    }

    @Test
    void listMisMinutesDelegaAlRepositorioConElEmail() {
        org.springframework.data.domain.Page<Minutes> vacia = org.springframework.data.domain.Page.empty();
        when(minutesRepository.findMisMinutes(eq("docente@uteq.edu.ec"), any())).thenReturn(vacia);

        minutesService.listMisMinutes("docente@uteq.edu.ec", org.springframework.data.domain.PageRequest.of(0, 10));

        verify(minutesRepository).findMisMinutes(eq("docente@uteq.edu.ec"), any());
    }

    // ── listMinutes / searchMinutes ────────────────────────────────────────────

    @Test
    void listMinutesDelegaAlRepositorio() {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        org.springframework.data.domain.Page<Minutes> pagina = org.springframework.data.domain.Page.empty();
        when(minutesRepository.findAll(pageable)).thenReturn(pagina);
        assertSame(pagina, minutesService.listMinutes(pageable));
    }

    @Test
    void searchMinutesLimpiaFiltrosVaciosAntesDeDelegar() {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        org.springframework.data.domain.Page<Minutes> pagina = org.springframework.data.domain.Page.empty();
        // Sin filtro de fecha -> el service pasa un rango abierto con sentinelas (Postgres no
        // puede inferir el tipo de un parámetro de fecha null en "(:desde IS NULL OR ...)").
        java.time.LocalDate desdeMin = java.time.LocalDate.of(1900, 1, 1);
        java.time.LocalDate hastaMax = java.time.LocalDate.of(9999, 12, 31);
        when(minutesRepository.searchConFiltros(eq("FINALIZADA"), isNull(), eq(desdeMin), eq(hastaMax), eq("sistema"), eq(pageable)))
                .thenReturn(pagina);

        minutesService.searchMinutes("FINALIZADA", "   ", null, null, "sistema", pageable);

        verify(minutesRepository).searchConFiltros(eq("FINALIZADA"), isNull(), eq(desdeMin), eq(hastaMax), eq("sistema"), eq(pageable));
    }

    // ── validateAcceso: rutas de propiedad (no solo admin/ajeno) ─────────────

    @Test
    void obtainDetalleLoPermiteAlPropioStudent() {
        Minutes minutes = minutesConEstado("GENERADA");
        autenticarComo("atorres@uteq.edu.ec", "ESTUDIANTE"); // email del student del fixture
        when(minutesRepository.findDetalleById(1L)).thenReturn(Optional.of(minutes));
        when(panelistRepository.findBySubmissionId(7L)).thenReturn(List.of());

        assertDoesNotThrow(() -> minutesService.obtainDetalle(1L));
    }

    @Test
    void obtainDetalleLoPermiteAlPanelistAsignado() {
        Minutes minutes = minutesConEstado("GENERADA");
        AppUser appUserPanelist = AppUser.builder().id(50L).email("jurado@uteq.edu.ec").build();
        Teacher teacherPanelist = Teacher.builder().id(1L).appUser(appUserPanelist).build();
        Panelist panelist = Panelist.builder().id(1L).submission(submission).teacher(teacherPanelist)
                .rolePanelist(RolePanelist.builder().codigo("PRESIDENTE").build()).build();
        autenticarComo("jurado@uteq.edu.ec", "DOCENTE");
        when(minutesRepository.findDetalleById(1L)).thenReturn(Optional.of(minutes));
        when(panelistRepository.findBySubmissionId(7L)).thenReturn(List.of(panelist));

        assertDoesNotThrow(() -> minutesService.obtainDetalle(1L));
    }

    @Test
    void obtainDetalleLoPermiteAlTutorAsignado() {
        Minutes minutes = minutesConEstado("GENERADA");
        AppUser appUserTutor = AppUser.builder().id(60L).email("tutor@uteq.edu.ec").build();
        Teacher teacherTutor = Teacher.builder().id(2L).appUser(appUserTutor).build();
        Tutor tutor = Tutor.builder().id(1L).teacher(teacherTutor).build();
        autenticarComo("tutor@uteq.edu.ec", "DOCENTE");
        when(minutesRepository.findDetalleById(1L)).thenReturn(Optional.of(minutes));
        when(panelistRepository.findBySubmissionId(7L)).thenReturn(List.of());
        when(tutorRepository.findBySubmissionId(7L)).thenReturn(Optional.of(tutor));

        assertDoesNotThrow(() -> minutesService.obtainDetalle(1L));
    }

    @Test
    void validateAccesoLanzaSiNoHayAutenticacion() {
        SecurityContextHolder.clearContext();
        Minutes minutes = minutesConEstado("GENERADA");
        when(minutesRepository.findDetalleById(1L)).thenReturn(Optional.of(minutes));

        assertThrows(RuntimeException.class, () -> minutesService.obtainDetalle(1L));
    }

    // ── signMinutes: rutas autorizadas para panelist/tutor (no admin) ──────────

    @Test
    void signMinutesPermiteAlPanelistConElRoleCorrecto() {
        AppUser appUserPresidente = AppUser.builder().id(50L).email("presidente@uteq.edu.ec").build();
        Teacher teacherPresidente = Teacher.builder().id(1L).appUser(appUserPresidente).build();
        Panelist panelist = Panelist.builder().id(1L).submission(submission).teacher(teacherPresidente)
                .rolePanelist(RolePanelist.builder().codigo("PRESIDENTE").build()).build();
        Minutes minutes = minutesConFirmas(false, false, false, false);
        autenticarComo("presidente@uteq.edu.ec", "DOCENTE");
        when(minutesRepository.findById(1L)).thenReturn(Optional.of(minutes));
        when(panelistRepository.findBySubmissionId(7L)).thenReturn(List.of(panelist));
        when(minutesRepository.save(any(Minutes.class))).thenAnswer(inv -> inv.getArgument(0));
        doAnswer(inv -> { minutes.setFirmadaPresidente(true); return null; })
                .when(entityManager).refresh(minutes);

        assertDoesNotThrow(() -> minutesService.signMinutes(1L, "presidente", null));
        verify(minutesRepository).signMinutesDigital(1L, "PRESIDENTE", null);
    }

    @Test
    void signMinutesRechazaPanelistConRoleQueNoLeCorresponde() {
        AppUser appUserVocal = AppUser.builder().id(51L).email("vocal@uteq.edu.ec").build();
        Teacher teacherVocal = Teacher.builder().id(2L).appUser(appUserVocal).build();
        Panelist panelist = Panelist.builder().id(2L).submission(submission).teacher(teacherVocal)
                .rolePanelist(RolePanelist.builder().codigo("VOCAL_1").build()).build();
        Minutes minutes = minutesConFirmas(false, false, false, false);
        autenticarComo("vocal@uteq.edu.ec", "DOCENTE");
        when(minutesRepository.findById(1L)).thenReturn(Optional.of(minutes));
        when(panelistRepository.findBySubmissionId(7L)).thenReturn(List.of(panelist));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> minutesService.signMinutes(1L, "presidente", null));
        assertTrue(ex.getMessage().contains("No eres el PRESIDENTE"));
        verify(minutesRepository, never()).signMinutesDigital(anyLong(), anyString(), any());
    }

    @Test
    void signMinutesPermiteAlTutorAsignado() {
        AppUser appUserTutor = AppUser.builder().id(60L).email("tutor@uteq.edu.ec").build();
        Teacher teacherTutor = Teacher.builder().id(3L).appUser(appUserTutor).build();
        Tutor tutor = Tutor.builder().id(1L).teacher(teacherTutor).build();
        Minutes minutes = minutesConFirmas(false, false, false, false);
        autenticarComo("tutor@uteq.edu.ec", "DOCENTE");
        when(minutesRepository.findById(1L)).thenReturn(Optional.of(minutes));
        when(tutorRepository.findBySubmissionId(7L)).thenReturn(Optional.of(tutor));
        when(minutesRepository.save(any(Minutes.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> minutesService.signMinutes(1L, "tutor", null));
        verify(minutesRepository).signMinutesDigital(1L, "TUTOR", null);
    }

    @Test
    void signMinutesRechazaSiNoEsElTutorAsignado() {
        Minutes minutes = minutesConFirmas(false, false, false, false);
        autenticarComo("impostor@uteq.edu.ec", "DOCENTE");
        when(minutesRepository.findById(1L)).thenReturn(Optional.of(minutes));
        when(tutorRepository.findBySubmissionId(7L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> minutesService.signMinutes(1L, "tutor", null));
        assertTrue(ex.getMessage().contains("No eres el tutor"));
    }

    // ── changeEstado: ramas restantes ───────────────────────────────────────

    @Test
    void changeEstadoLanzaSiNuevoEstadoEsBlanco() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> minutesService.changeEstado(1L, "  ", null));
        assertTrue(ex.getMessage().contains("Debe indicar el nuevo estado"));
    }

    @Test
    void changeEstadoLanzaSiYaEstaEnEseEstado() {
        Minutes minutes = minutesConEstado("REVISADA");
        when(minutesRepository.findDetalleById(1L)).thenReturn(Optional.of(minutes));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> minutesService.changeEstado(1L, "REVISADA", null));
        assertTrue(ex.getMessage().contains("ya está en estado"));
    }

    @Test
    void changeEstadoLanzaSiElCodigoDestinoNoExiste() {
        Minutes minutes = minutesConEstado("GENERADA");
        when(minutesRepository.findDetalleById(1L)).thenReturn(Optional.of(minutes));
        when(estadoMinutesRepository.findByCodigo("INVALIDO")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> minutesService.changeEstado(1L, "INVALIDO", null));
        assertTrue(ex.getMessage().contains("Estado de acta inválido"));
    }

    @Test
    void changeEstadoAdminPuedeAnularDesdeCualquierEstado() {
        // FINALIZADA solo permite -> ANULADA en TRANSICIONES; se prueba igual la ruta explicita
        // del bypass de ADMIN, no solo la transicion ya permitida por el mapa.
        Minutes minutes = minutesConEstado("FINALIZADA");
        when(minutesRepository.findDetalleById(1L)).thenReturn(Optional.of(minutes));
        when(minutesRepository.save(any(Minutes.class))).thenAnswer(inv -> inv.getArgument(0));

        Minutes resultado = minutesService.changeEstado(1L, "ANULADA", "Error administrativo");

        assertEquals("ANULADA", resultado.getEstado().getCodigo());
        assertEquals("Error administrativo", resultado.getObservacionesMinutes());
    }

    @Test
    void changeEstadoDeObservadaARevisadaEsValido() {
        Minutes minutes = minutesConEstado("OBSERVADA");
        autenticarComo("coord@uteq.edu.ec", "COORDINADOR");
        when(permissionService.tienePermission(any(), eq("ACTAS_GESTIONAR"))).thenReturn(false);
        when(minutesRepository.findDetalleById(1L)).thenReturn(Optional.of(minutes));
        when(minutesRepository.save(any(Minutes.class))).thenAnswer(inv -> inv.getArgument(0));

        Minutes resultado = minutesService.changeEstado(1L, "REVISADA", null);

        assertEquals("REVISADA", resultado.getEstado().getCodigo());
    }

    @Test
    void changeEstadoNoPropagaFalloDeNotification() {
        Minutes minutes = minutesConEstado("GENERADA");
        when(minutesRepository.findDetalleById(1L)).thenReturn(Optional.of(minutes));
        when(minutesRepository.save(any(Minutes.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("smtp caido")).when(notificationService).createNotification(anyLong(), anyString());

        assertDoesNotThrow(() -> minutesService.changeEstado(1L, "REVISADA", null));
    }

    // ── deleteMinutes: borra tambien el archivo fisico si existe ─────────────

    @Test
    void deleteMinutesSinArchivoPdfNoIntentaEraseNada() {
        Minutes minutes = minutesConFirmas(false, false, false, false); // sin archivoPdf
        when(minutesRepository.findById(1L)).thenReturn(Optional.of(minutes));

        assertDoesNotThrow(() -> minutesService.deleteMinutes(1L));
        verify(minutesRepository).delete(minutes);
    }
}
