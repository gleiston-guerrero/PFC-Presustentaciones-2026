package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.dto.ResponseWrapper;
import ec.edu.uteq.presustentaciones.dto.TrackingDTO;
import ec.edu.uteq.presustentaciones.entities.Student;
import ec.edu.uteq.presustentaciones.entities.Submission;
import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.repositories.AppUserRepository;
import ec.edu.uteq.presustentaciones.services.PermissionService;
import ec.edu.uteq.presustentaciones.services.SubmissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SubmissionController es la puerta de entrada del flujo académico completo y expone
 * sp_generate_reporte_defensas; tenía 5 de 81 líneas cubiertas.
 *
 * El foco está en validateAccesoSubmission(): un student sólo puede open, send y
 * seguir SU propia submission, mientras que quien tiene SOLICITUDES_REVISAR (o es ADMIN)
 * puede open cualquiera. Es la misma clase de comprobación de propiedad que se corrigió
 * como IDOR en Evaluations y Proposals, y aquí no tenía prueba que la fijara.
 */
@ExtendWith(MockitoExtension.class)
class SubmissionControllerTest {

    @Mock private SubmissionService submissionService;
    @Mock private AppUserRepository appUserRepository;
    @Mock private PermissionService permissionService;

    @InjectMocks
    private SubmissionController controller;

    @AfterEach
    void cleanContexto() {
        SecurityContextHolder.clearContext();
    }

    @SuppressWarnings("unchecked")
    private ResponseWrapper<Object> wrapperOf(ResponseEntity<?> response) {
        return (ResponseWrapper<Object>) response.getBody();
    }

    private void authenticate(String email, String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null,
                        List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList()));
    }

    private Submission submissionOf(String emailPropietario) {
        return Submission.builder()
                .id(1L)
                .student(Student.builder().id(7L)
                        .appUser(AppUser.builder().id(50L).email(emailPropietario).build())
                        .build())
                .build();
    }

    // ── Creación ──────────────────────────────────────────────────────────────

    @Test
    void createDevuelveLaSubmissionCreada() {
        Submission data = Submission.builder().tituloTopic("Tema").build();
        Submission creada = Submission.builder().id(1L).build();
        when(submissionService.createSubmission(7L, data)).thenReturn(creada);

        ResponseEntity<?> response = controller.create(7L, data);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(creada, wrapperOf(response).getData());
        assertEquals("Solicitud creada exitosamente", wrapperOf(response).getMessage());
    }

    @Test
    void createTraduceElErrorDelServicioA400() {
        Submission data = Submission.builder().build();
        when(submissionService.createSubmission(7L, data))
                .thenThrow(new RuntimeException("El estudiante ya tiene una solicitud activa"));

        ResponseEntity<?> response = controller.create(7L, data);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("El estudiante ya tiene una solicitud activa", wrapperOf(response).getMessage());
    }

    @Test
    void createByAppUserIgnoraElIdDelPathYUsaElDelToken() {
        authenticate("est@uteq.edu.ec");
        Submission data = Submission.builder().build();
        Submission creada = Submission.builder().id(1L).build();
        when(appUserRepository.findByEmail("est@uteq.edu.ec"))
                .thenReturn(Optional.of(AppUser.builder().id(50L).build()));
        when(submissionService.createSubmissionByAppUser(50L, data)).thenReturn(creada);

        // El cliente manda 999 en la URL; el backend debe resolve 50 desde el JWT
        ResponseEntity<?> response = controller.createByAppUser(999L, data);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(submissionService).createSubmissionByAppUser(50L, data);
        verify(submissionService, never()).createSubmissionByAppUser(eq(999L), any());
    }

    @Test
    void createByAppUserWithTokenDeAppUserInexistenteDevuelve400() {
        authenticate("fantasma@uteq.edu.ec");
        when(appUserRepository.findByEmail("fantasma@uteq.edu.ec")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.createByAppUser(1L, Submission.builder().build());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Usuario no encontrado en el sistema", wrapperOf(response).getMessage());
    }

    // ── Listados propios ──────────────────────────────────────────────────────

    @Test
    void mySubmissionsResuelveElAppUserFromElToken() {
        authenticate("est@uteq.edu.ec");
        List<Submission> submissions = List.of(Submission.builder().id(1L).build());
        when(appUserRepository.findByEmail("est@uteq.edu.ec"))
                .thenReturn(Optional.of(AppUser.builder().id(50L).build()));
        when(submissionService.listByAppUser(50L)).thenReturn(submissions);

        ResponseEntity<?> response = controller.listMySubmissions();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(submissions, wrapperOf(response).getData());
    }

    @Test
    void mySubmissionsDevuelveListaVaciaEnVezDeErrorSiFallaLaResolucion() {
        authenticate("fantasma@uteq.edu.ec");
        when(appUserRepository.findByEmail("fantasma@uteq.edu.ec")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.listMySubmissions();

        // Decisión de diseño del controlador: la pantalla del student no debe romperse,
        // muestra una lista vacía en vez de propagar el error.
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of(), wrapperOf(response).getData());
    }

    @Test
    void listByAppUserDevuelveListaVaciaSiElServicioFalla() {
        when(submissionService.listByAppUser(50L)).thenThrow(new RuntimeException("boom"));

        ResponseEntity<?> response = controller.listByAppUser(50L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of(), wrapperOf(response).getData());
    }

    // ── Comprobación de propiedad (validateAccesoSubmission) ────────────────────

    @Test
    void unStudentNoCanOpenLaSubmissionDeOtro() {
        authenticate("otro@uteq.edu.ec");
        when(permissionService.hasPermission(any(), any())).thenReturn(false);
        when(submissionService.obtainById(1L)).thenReturn(Optional.of(submissionOf("dueno@uteq.edu.ec")));

        ResponseEntity<?> response = controller.obtain(1L);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Acceso denegado: no eres propietario de esta solicitud",
                wrapperOf(response).getMessage());
    }

    @Test
    void elPropietarioSiCanOpenSuSubmission() {
        authenticate("dueno@uteq.edu.ec");
        when(permissionService.hasPermission(any(), any())).thenReturn(false);
        Submission propia = submissionOf("dueno@uteq.edu.ec");
        when(submissionService.obtainById(1L)).thenReturn(Optional.of(propia));

        ResponseEntity<?> response = controller.obtain(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(propia, wrapperOf(response).getData());
    }

    @Test
    void unRevisorCanOpenCualquierSubmissionWithoutComprobarPropiedad() {
        authenticate("coord@uteq.edu.ec", "SOLICITUDES_REVISAR");
        when(permissionService.hasPermission(any(), any())).thenReturn(true);
        Submission ajena = submissionOf("dueno@uteq.edu.ec");
        when(submissionService.obtainById(1L)).thenReturn(Optional.of(ajena));

        ResponseEntity<?> response = controller.obtain(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(ajena, wrapperOf(response).getData());
    }

    @Test
    void unAdminCanOpenCualquierSubmission() {
        authenticate("admin@uteq.edu.ec", "ROLE_ADMIN");
        when(submissionService.obtainById(1L)).thenReturn(Optional.of(submissionOf("dueno@uteq.edu.ec")));

        assertEquals(HttpStatus.OK, controller.obtain(1L).getStatusCode());
    }

    @Test
    void obtainDevuelve404CuandoElRevisorPideUnaSubmissionInexistente() {
        authenticate("coord@uteq.edu.ec", "SOLICITUDES_REVISAR");
        when(permissionService.hasPermission(any(), any())).thenReturn(true);
        when(submissionService.obtainById(99L)).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.obtain(99L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Solicitud no encontrada", wrapperOf(response).getMessage());
    }

    @Test
    void sendExigeSerPropietarioAntesDeSendARevision() {
        authenticate("otro@uteq.edu.ec");
        when(permissionService.hasPermission(any(), any())).thenReturn(false);
        when(submissionService.obtainById(1L)).thenReturn(Optional.of(submissionOf("dueno@uteq.edu.ec")));

        ResponseEntity<?> response = controller.send(1L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(submissionService, never()).sendSubmission(any());
    }

    @Test
    void sendFuncionaForElPropietario() {
        authenticate("dueno@uteq.edu.ec");
        when(permissionService.hasPermission(any(), any())).thenReturn(false);
        Submission enviada = Submission.builder().id(1L).build();
        when(submissionService.obtainById(1L)).thenReturn(Optional.of(submissionOf("dueno@uteq.edu.ec")));
        when(submissionService.sendSubmission(1L)).thenReturn(enviada);

        ResponseEntity<?> response = controller.send(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Solicitud enviada a revisión", wrapperOf(response).getMessage());
    }

    @Test
    void obtainTrackingExigeLaMismaComprobacionDePropiedad() {
        authenticate("otro@uteq.edu.ec");
        when(permissionService.hasPermission(any(), any())).thenReturn(false);
        when(submissionService.obtainById(1L)).thenReturn(Optional.of(submissionOf("dueno@uteq.edu.ec")));

        assertEquals(HttpStatus.FORBIDDEN, controller.obtainTracking(1L).getStatusCode());
        verify(submissionService, never()).obtainTracking(any());
    }

    @Test
    void obtainTrackingDevuelveElHistoryAlPropietario() {
        authenticate("dueno@uteq.edu.ec");
        when(permissionService.hasPermission(any(), any())).thenReturn(false);
        TrackingDTO tracking = mock(TrackingDTO.class);
        when(submissionService.obtainById(1L)).thenReturn(Optional.of(submissionOf("dueno@uteq.edu.ec")));
        when(submissionService.obtainTracking(1L)).thenReturn(tracking);

        ResponseEntity<?> response = controller.obtainTracking(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(tracking, wrapperOf(response).getData());
    }

    // ── Transiciones de estado (revisor) ──────────────────────────────────────

    @Test
    void approveRejectYRejectWithObservationDeleganEnElServicio() {
        Submission result = Submission.builder().id(1L).build();
        when(submissionService.approveSubmission(1L)).thenReturn(result);
        when(submissionService.rejectSubmission(2L)).thenReturn(result);
        when(submissionService.rejectWithObservation(3L, "Falta el anteproyecto")).thenReturn(result);

        assertEquals("Solicitud aprobada", wrapperOf(controller.approve(1L)).getMessage());
        assertEquals("Solicitud rechazada", wrapperOf(controller.reject(2L)).getMessage());
        assertEquals("Solicitud rechazada con observaciones",
                wrapperOf(controller.rejectWithObservation(3L, Map.of("observacion", "Falta el anteproyecto"))).getMessage());
    }

    @Test
    void rejectWithObservationWithoutObservationUsaCadenaVacia() {
        when(submissionService.rejectWithObservation(3L, "")).thenReturn(Submission.builder().id(3L).build());

        assertEquals(HttpStatus.OK, controller.rejectWithObservation(3L, Map.of()).getStatusCode());
        verify(submissionService).rejectWithObservation(3L, "");
    }

    @Test
    void approveTraduceElErrorDeTransicionInvalidaA400() {
        when(submissionService.approveSubmission(1L))
                .thenThrow(new RuntimeException("La solicitud no está en estado ENVIADA"));

        ResponseEntity<?> response = controller.approve(1L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("La solicitud no está en estado ENVIADA", wrapperOf(response).getMessage());
    }

    @Test
    void suspendPasaElMotivoAlServicioYTraduceErrores() {
        Submission suspendida = Submission.builder().id(1L).build();
        when(submissionService.suspendSubmission(1L, "Estudiante retirado")).thenReturn(suspendida);
        assertEquals("Solicitud suspendida",
                wrapperOf(controller.suspend(1L, Map.of("motivo", "Estudiante retirado"))).getMessage());

        when(submissionService.suspendSubmission(2L, null))
                .thenThrow(new RuntimeException("El motivo es obligatorio"));
        ResponseEntity<?> error = controller.suspend(2L, Map.of());
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertEquals("El motivo es obligatorio", wrapperOf(error).getMessage());
    }

    // ── Listados administrativos ──────────────────────────────────────────────

    @Test
    void listYCountByStatusDeleganEnElServicio() {
        List<Submission> all = List.of(Submission.builder().id(1L).build());
        Map<String, Long> count = Map.of("ENVIADA", 3L);
        when(submissionService.listSubmissions()).thenReturn(all);
        doReturn(count).when(submissionService).countByStatus();

        assertSame(all, wrapperOf(controller.list()).getData());
        assertSame(count, wrapperOf(controller.countByStatus()).getData());
    }

    @Test
    void listPagedArmaLaRespuestaWithLosMetadatosDePagina() {
        Page<Submission> pagina = new PageImpl<>(
                List.of(Submission.builder().id(1L).build()), PageRequest.of(2, 20), 45);
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 12, 31);
        when(submissionService.listSubmissionsPaged(2, 20, "ENVIADA", "tema", from, to))
                .thenReturn(pagina);

        ResponseEntity<?> response = controller.listPaged(2, 20, "ENVIADA", "tema", from, to);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) wrapperOf(response).getData();
        assertEquals(1, ((List<?>) data.get("content")).size());
        // PageImpl recorta el total al offset+contenido cuando la ultima pagina viene
        // incompleta (40 elementos saltados + 1 en esta pagina), asi que el metadato real
        // que ve el frontend es 41, no el 45 nominal.
        assertEquals(41L, data.get("totalElements"));
        assertEquals(3, data.get("totalPages"));
        assertEquals(2, data.get("page"));
        assertEquals(20, data.get("size"));
    }

    @Test
    void listByStudentDelegaEnElServicio() {
        List<Submission> submissions = List.of(Submission.builder().id(1L).build());
        when(submissionService.listByStudent(7L)).thenReturn(submissions);

        assertSame(submissions, wrapperOf(controller.listByStudent(7L)).getData());
    }

    // ── sp_generate_reporte_defensas ───────────────────────────────────────────

    @Test
    void reportDefensesDevuelveLasFilasDelProcedimientoAlmacenado() {
        List<Map<String, Object>> report = List.of(Map.of("estudianteNombre", "Ana Pérez"));
        when(submissionService.generateReportDefensesSP("Software")).thenReturn(report);

        ResponseEntity<?> response = controller.reportDefenses("Software");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(report, wrapperOf(response).getData());
    }

    @Test
    void reportDefensesTraduceElErrorDelProcedimientoA400() {
        when(submissionService.generateReportDefensesSP(""))
                .thenThrow(new RuntimeException("cursor \"reporte_defensas_cursor\" does not exist"));

        ResponseEntity<?> response = controller.reportDefenses("");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(wrapperOf(response).getMessage().contains("reporte_defensas_cursor"));
    }
}
