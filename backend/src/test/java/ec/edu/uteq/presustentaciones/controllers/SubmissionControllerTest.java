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
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @SuppressWarnings("unchecked")
    private ResponseWrapper<Object> wrapperDe(ResponseEntity<?> response) {
        return (ResponseWrapper<Object>) response.getBody();
    }

    private void autenticar(String email, String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null,
                        List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList()));
    }

    private Submission submissionDe(String emailPropietario) {
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
        Submission datos = Submission.builder().tituloTopic("Tema").build();
        Submission creada = Submission.builder().id(1L).build();
        when(submissionService.createSubmission(7L, datos)).thenReturn(creada);

        ResponseEntity<?> response = controller.create(7L, datos);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(creada, wrapperDe(response).getData());
        assertEquals("Solicitud creada exitosamente", wrapperDe(response).getMessage());
    }

    @Test
    void createTraduceElErrorDelServicioA400() {
        Submission datos = Submission.builder().build();
        when(submissionService.createSubmission(7L, datos))
                .thenThrow(new RuntimeException("El estudiante ya tiene una solicitud activa"));

        ResponseEntity<?> response = controller.create(7L, datos);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("El estudiante ya tiene una solicitud activa", wrapperDe(response).getMessage());
    }

    @Test
    void createPorAppUserIgnoraElIdDelPathYUsaElDelToken() {
        autenticar("est@uteq.edu.ec");
        Submission datos = Submission.builder().build();
        Submission creada = Submission.builder().id(1L).build();
        when(appUserRepository.findByEmail("est@uteq.edu.ec"))
                .thenReturn(Optional.of(AppUser.builder().id(50L).build()));
        when(submissionService.createSubmissionPorAppUser(50L, datos)).thenReturn(creada);

        // El cliente manda 999 en la URL; el backend debe resolve 50 desde el JWT
        ResponseEntity<?> response = controller.createPorAppUser(999L, datos);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(submissionService).createSubmissionPorAppUser(50L, datos);
        verify(submissionService, never()).createSubmissionPorAppUser(eq(999L), any());
    }

    @Test
    void createPorAppUserConTokenDeAppUserInexistenteDevuelve400() {
        autenticar("fantasma@uteq.edu.ec");
        when(appUserRepository.findByEmail("fantasma@uteq.edu.ec")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.createPorAppUser(1L, Submission.builder().build());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Usuario no encontrado en el sistema", wrapperDe(response).getMessage());
    }

    // ── Listados propios ──────────────────────────────────────────────────────

    @Test
    void misSubmissionsResuelveElAppUserDesdeElToken() {
        autenticar("est@uteq.edu.ec");
        List<Submission> submissions = List.of(Submission.builder().id(1L).build());
        when(appUserRepository.findByEmail("est@uteq.edu.ec"))
                .thenReturn(Optional.of(AppUser.builder().id(50L).build()));
        when(submissionService.listPorAppUser(50L)).thenReturn(submissions);

        ResponseEntity<?> response = controller.listMisSubmissions();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(submissions, wrapperDe(response).getData());
    }

    @Test
    void misSubmissionsDevuelveListaVaciaEnVezDeErrorSiFallaLaResolucion() {
        autenticar("fantasma@uteq.edu.ec");
        when(appUserRepository.findByEmail("fantasma@uteq.edu.ec")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.listMisSubmissions();

        // Decisión de diseño del controlador: la pantalla del student no debe romperse,
        // muestra una lista vacía en vez de propagar el error.
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of(), wrapperDe(response).getData());
    }

    @Test
    void listPorAppUserDevuelveListaVaciaSiElServicioFalla() {
        when(submissionService.listPorAppUser(50L)).thenThrow(new RuntimeException("boom"));

        ResponseEntity<?> response = controller.listPorAppUser(50L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of(), wrapperDe(response).getData());
    }

    // ── Comprobación de propiedad (validateAccesoSubmission) ────────────────────

    @Test
    void unStudentNoPuedeOpenLaSubmissionDeOtro() {
        autenticar("otro@uteq.edu.ec");
        when(permissionService.tienePermission(any(), any())).thenReturn(false);
        when(submissionService.obtainPorId(1L)).thenReturn(Optional.of(submissionDe("dueno@uteq.edu.ec")));

        ResponseEntity<?> response = controller.obtain(1L);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Acceso denegado: no eres propietario de esta solicitud",
                wrapperDe(response).getMessage());
    }

    @Test
    void elPropietarioSiPuedeOpenSuSubmission() {
        autenticar("dueno@uteq.edu.ec");
        when(permissionService.tienePermission(any(), any())).thenReturn(false);
        Submission propia = submissionDe("dueno@uteq.edu.ec");
        when(submissionService.obtainPorId(1L)).thenReturn(Optional.of(propia));

        ResponseEntity<?> response = controller.obtain(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(propia, wrapperDe(response).getData());
    }

    @Test
    void unRevisorPuedeOpenCualquierSubmissionSinComprobarPropiedad() {
        autenticar("coord@uteq.edu.ec", "SOLICITUDES_REVISAR");
        when(permissionService.tienePermission(any(), any())).thenReturn(true);
        Submission ajena = submissionDe("dueno@uteq.edu.ec");
        when(submissionService.obtainPorId(1L)).thenReturn(Optional.of(ajena));

        ResponseEntity<?> response = controller.obtain(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(ajena, wrapperDe(response).getData());
    }

    @Test
    void unAdminPuedeOpenCualquierSubmission() {
        autenticar("admin@uteq.edu.ec", "ROLE_ADMIN");
        when(submissionService.obtainPorId(1L)).thenReturn(Optional.of(submissionDe("dueno@uteq.edu.ec")));

        assertEquals(HttpStatus.OK, controller.obtain(1L).getStatusCode());
    }

    @Test
    void obtainDevuelve404CuandoElRevisorPideUnaSubmissionInexistente() {
        autenticar("coord@uteq.edu.ec", "SOLICITUDES_REVISAR");
        when(permissionService.tienePermission(any(), any())).thenReturn(true);
        when(submissionService.obtainPorId(99L)).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.obtain(99L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Solicitud no encontrada", wrapperDe(response).getMessage());
    }

    @Test
    void sendExigeSerPropietarioAntesDeSendARevision() {
        autenticar("otro@uteq.edu.ec");
        when(permissionService.tienePermission(any(), any())).thenReturn(false);
        when(submissionService.obtainPorId(1L)).thenReturn(Optional.of(submissionDe("dueno@uteq.edu.ec")));

        ResponseEntity<?> response = controller.send(1L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(submissionService, never()).sendSubmission(any());
    }

    @Test
    void sendFuncionaParaElPropietario() {
        autenticar("dueno@uteq.edu.ec");
        when(permissionService.tienePermission(any(), any())).thenReturn(false);
        Submission enviada = Submission.builder().id(1L).build();
        when(submissionService.obtainPorId(1L)).thenReturn(Optional.of(submissionDe("dueno@uteq.edu.ec")));
        when(submissionService.sendSubmission(1L)).thenReturn(enviada);

        ResponseEntity<?> response = controller.send(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Solicitud enviada a revisión", wrapperDe(response).getMessage());
    }

    @Test
    void obtainTrackingExigeLaMismaComprobacionDePropiedad() {
        autenticar("otro@uteq.edu.ec");
        when(permissionService.tienePermission(any(), any())).thenReturn(false);
        when(submissionService.obtainPorId(1L)).thenReturn(Optional.of(submissionDe("dueno@uteq.edu.ec")));

        assertEquals(HttpStatus.FORBIDDEN, controller.obtainTracking(1L).getStatusCode());
        verify(submissionService, never()).obtainTracking(any());
    }

    @Test
    void obtainTrackingDevuelveElHistoryAlPropietario() {
        autenticar("dueno@uteq.edu.ec");
        when(permissionService.tienePermission(any(), any())).thenReturn(false);
        TrackingDTO tracking = mock(TrackingDTO.class);
        when(submissionService.obtainPorId(1L)).thenReturn(Optional.of(submissionDe("dueno@uteq.edu.ec")));
        when(submissionService.obtainTracking(1L)).thenReturn(tracking);

        ResponseEntity<?> response = controller.obtainTracking(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(tracking, wrapperDe(response).getData());
    }

    // ── Transiciones de estado (revisor) ──────────────────────────────────────

    @Test
    void approveRejectYRejectConObservacionDeleganEnElServicio() {
        Submission resultado = Submission.builder().id(1L).build();
        when(submissionService.approveSubmission(1L)).thenReturn(resultado);
        when(submissionService.rejectSubmission(2L)).thenReturn(resultado);
        when(submissionService.rejectConObservacion(3L, "Falta el anteproyecto")).thenReturn(resultado);

        assertEquals("Solicitud aprobada", wrapperDe(controller.approve(1L)).getMessage());
        assertEquals("Solicitud rechazada", wrapperDe(controller.reject(2L)).getMessage());
        assertEquals("Solicitud rechazada con observaciones",
                wrapperDe(controller.rejectConObservacion(3L, Map.of("observacion", "Falta el anteproyecto"))).getMessage());
    }

    @Test
    void rejectConObservacionSinObservacionUsaCadenaVacia() {
        when(submissionService.rejectConObservacion(3L, "")).thenReturn(Submission.builder().id(3L).build());

        assertEquals(HttpStatus.OK, controller.rejectConObservacion(3L, Map.of()).getStatusCode());
        verify(submissionService).rejectConObservacion(3L, "");
    }

    @Test
    void approveTraduceElErrorDeTransicionInvalidaA400() {
        when(submissionService.approveSubmission(1L))
                .thenThrow(new RuntimeException("La solicitud no está en estado ENVIADA"));

        ResponseEntity<?> response = controller.approve(1L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("La solicitud no está en estado ENVIADA", wrapperDe(response).getMessage());
    }

    @Test
    void suspenderPasaElMotivoAlServicioYTraduceErrores() {
        Submission suspendida = Submission.builder().id(1L).build();
        when(submissionService.suspenderSubmission(1L, "Estudiante retirado")).thenReturn(suspendida);
        assertEquals("Solicitud suspendida",
                wrapperDe(controller.suspender(1L, Map.of("motivo", "Estudiante retirado"))).getMessage());

        when(submissionService.suspenderSubmission(2L, null))
                .thenThrow(new RuntimeException("El motivo es obligatorio"));
        ResponseEntity<?> error = controller.suspender(2L, Map.of());
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertEquals("El motivo es obligatorio", wrapperDe(error).getMessage());
    }

    // ── Listados administrativos ──────────────────────────────────────────────

    @Test
    void listYCountPorEstadoDeleganEnElServicio() {
        List<Submission> todas = List.of(Submission.builder().id(1L).build());
        Map<String, Long> count = Map.of("ENVIADA", 3L);
        when(submissionService.listSubmissions()).thenReturn(todas);
        doReturn(count).when(submissionService).countPorEstado();

        assertSame(todas, wrapperDe(controller.list()).getData());
        assertSame(count, wrapperDe(controller.countPorEstado()).getData());
    }

    @Test
    void listPaginadoArmaLaRespuestaConLosMetadatosDePagina() {
        Page<Submission> pagina = new PageImpl<>(
                List.of(Submission.builder().id(1L).build()), PageRequest.of(2, 20), 45);
        LocalDate desde = LocalDate.of(2026, 1, 1);
        LocalDate hasta = LocalDate.of(2026, 12, 31);
        when(submissionService.listSubmissionsPaginado(2, 20, "ENVIADA", "tema", desde, hasta))
                .thenReturn(pagina);

        ResponseEntity<?> response = controller.listPaginado(2, 20, "ENVIADA", "tema", desde, hasta);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) wrapperDe(response).getData();
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
    void listPorStudentDelegaEnElServicio() {
        List<Submission> submissions = List.of(Submission.builder().id(1L).build());
        when(submissionService.listPorStudent(7L)).thenReturn(submissions);

        assertSame(submissions, wrapperDe(controller.listPorStudent(7L)).getData());
    }

    // ── sp_generate_reporte_defensas ───────────────────────────────────────────

    @Test
    void reporteDefensasDevuelveLasFilasDelProcedimientoAlmacenado() {
        List<Map<String, Object>> reporte = List.of(Map.of("estudianteNombre", "Ana Pérez"));
        when(submissionService.generateReporteDefensasSP("Software")).thenReturn(reporte);

        ResponseEntity<?> response = controller.reporteDefensas("Software");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(reporte, wrapperDe(response).getData());
    }

    @Test
    void reporteDefensasTraduceElErrorDelProcedimientoA400() {
        when(submissionService.generateReporteDefensasSP(""))
                .thenThrow(new RuntimeException("cursor \"reporte_defensas_cursor\" does not exist"));

        ResponseEntity<?> response = controller.reporteDefensas("");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(wrapperDe(response).getMessage().contains("reporte_defensas_cursor"));
    }
}
