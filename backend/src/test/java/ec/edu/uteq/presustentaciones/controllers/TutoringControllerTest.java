package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.dto.NuevoMensajeRequest;
import ec.edu.uteq.presustentaciones.dto.ResponseWrapper;
import ec.edu.uteq.presustentaciones.dto.TutoringFaseDTO;
import ec.edu.uteq.presustentaciones.dto.TutoringMensajeDTO;
import ec.edu.uteq.presustentaciones.dto.TutoringResumenDTO;
import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.repositories.AppUserRepository;
import ec.edu.uteq.presustentaciones.services.TutoringService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TutoringController expone sp_register_tutoring_avance y tenía 4 de 83 líneas cubiertas.
 *
 * Lo más relevante de esta clase no son los delegados sino resolveAppUserId(): un
 * student o teacher sólo puede consultar sus propias tutorías aunque mande otro
 * appUserId en la URL, y sólo ADMIN/COORDINADOR pueden consultar las de un tercero.
 * Esa es una comprobación de propiedad del resource equivalente a las que se corrigieron
 * como IDOR en otros controladores, y no tenía ninguna prueba que la fijara.
 */
@ExtendWith(MockitoExtension.class)
class TutoringControllerTest {

    @Mock private TutoringService tutoringService;
    @Mock private AppUserRepository appUserRepository;

    @InjectMocks
    private TutoringController controller;

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @SuppressWarnings("unchecked")
    private ResponseWrapper<Object> wrapperDe(ResponseEntity<?> response) {
        return (ResponseWrapper<Object>) response.getBody();
    }

    /** Autentica un appUser con el role dado y lo deja resoluble por email. */
    private AppUser autenticar(Long id, String role) {
        String email = "usuario" + id + "@uteq.edu.ec";
        AppUser appUser = AppUser.builder().id(id).email(email).role(role).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        lenient().when(appUserRepository.findByEmail(email)).thenReturn(Optional.of(appUser));
        return appUser;
    }

    // ── resolveAppUserId: propiedad del resource ──────────────────────────────

    @Test
    void unStudentNoPuedeConsultarLasTutoringsDeOtroAppUserAunqueLoPidaEnLaUrl() {
        autenticar(50L, "ESTUDIANTE");
        when(tutoringService.obtainTutoringsStudent(50L)).thenReturn(List.of());

        // Pide explícitamente el appUser 99, pero el controlador ignora ese id
        controller.obtainTutoringsStudent(99L);

        verify(tutoringService).obtainTutoringsStudent(50L);
        verify(tutoringService, never()).obtainTutoringsStudent(99L);
    }

    @Test
    void unCoordinadorSiPuedeConsultarLasTutoringsDeOtroAppUser() {
        autenticar(1L, "COORDINADOR");
        when(tutoringService.obtainTutoringsTeacher(99L)).thenReturn(List.of());

        ResponseEntity<?> response = controller.obtainTutoringsTeacher(99L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(tutoringService).obtainTutoringsTeacher(99L);
    }

    @Test
    void unAdminSinAppUserIdExplicitoConsultaLasSuyas() {
        autenticar(1L, "ADMIN");
        when(tutoringService.obtainResumen(5L, 1L)).thenReturn(mock(TutoringResumenDTO.class));

        assertEquals(HttpStatus.OK, controller.obtainResumen(5L, null).getStatusCode());
        verify(tutoringService).obtainResumen(5L, 1L);
    }

    @Test
    void sinAutenticacionElEndpointDevuelve400EnVezDeReventar() {
        ResponseEntity<?> response = controller.obtainTutoringsStudent(1L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Usuario no autenticado", wrapperDe(response).getMessage());
        verifyNoInteractions(tutoringService);
    }

    @Test
    void conAppUserAnonimoElEndpointDevuelve400() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        ResponseEntity<?> response = controller.obtainTutoringsTeacher(1L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Usuario no autenticado", wrapperDe(response).getMessage());
    }

    @Test
    void conTokenDeAppUserYaBorradoElEndpointDevuelve400() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("fantasma@uteq.edu.ec", null, List.of()));
        when(appUserRepository.findByEmail("fantasma@uteq.edu.ec")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.obtainFases(1L, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Usuario autenticado no encontrado en el sistema", wrapperDe(response).getMessage());
    }

    // ── Consultas ─────────────────────────────────────────────────────────────

    @Test
    void obtainFasesDevuelveLasFasesDelServicio() {
        autenticar(50L, "ESTUDIANTE");
        List<TutoringFaseDTO> fases = List.of(mock(TutoringFaseDTO.class));
        when(tutoringService.obtainFases(5L, 50L)).thenReturn(fases);

        ResponseEntity<?> response = controller.obtainFases(5L, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(fases, wrapperDe(response).getData());
    }

    @Test
    void obtainResumenTraduceElErrorDelServicioA400() {
        autenticar(50L, "ESTUDIANTE");
        when(tutoringService.obtainResumen(5L, 50L))
                .thenThrow(new RuntimeException("No tienes acceso a esta tutoría"));

        ResponseEntity<?> response = controller.obtainResumen(5L, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("No tienes acceso a esta tutoría", wrapperDe(response).getMessage());
    }

    // ── Operaciones sobre fases ───────────────────────────────────────────────

    @Test
    void createFaseUsaSiempreElAppUserAutenticadoNoElDelParametro() {
        autenticar(60L, "DOCENTE");
        TutoringFaseDTO fase = mock(TutoringFaseDTO.class);
        when(tutoringService.createFaseConObservacion(5L, 60L, "Revisar capítulo 2")).thenReturn(fase);

        ResponseEntity<?> response = controller.createFaseConObservacion(5L, "Revisar capítulo 2", 999L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(fase, wrapperDe(response).getData());
        verify(tutoringService).createFaseConObservacion(5L, 60L, "Revisar capítulo 2");
    }

    @Test
    void uploadPdfCorregidoDelegaConElStudentAutenticado() {
        autenticar(50L, "ESTUDIANTE");
        MultipartFile archivo = new MockMultipartFile("archivo", "cap2.pdf",
                MediaType.APPLICATION_PDF_VALUE, "contenido".getBytes());
        TutoringFaseDTO fase = mock(TutoringFaseDTO.class);
        when(tutoringService.uploadPdfCorregido(7L, archivo, 50L)).thenReturn(fase);

        ResponseEntity<?> response = controller.uploadPdfCorregido(7L, archivo, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(fase, wrapperDe(response).getData());
    }

    @Test
    void uploadPdfCorregidoTraduceElErrorDelServicioA400() {
        autenticar(50L, "ESTUDIANTE");
        MultipartFile archivo = new MockMultipartFile("archivo", "malo.exe",
                MediaType.APPLICATION_OCTET_STREAM_VALUE, new byte[]{1});
        when(tutoringService.uploadPdfCorregido(7L, archivo, 50L))
                .thenThrow(new RuntimeException("Solo se admiten archivos PDF"));

        ResponseEntity<?> response = controller.uploadPdfCorregido(7L, archivo, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Solo se admiten archivos PDF", wrapperDe(response).getMessage());
    }

    @Test
    void approveFaseDelegaConElTutorAutenticado() {
        autenticar(60L, "DOCENTE");
        TutoringFaseDTO fase = mock(TutoringFaseDTO.class);
        when(tutoringService.approveFase(7L, 60L, "Buen avance")).thenReturn(fase);

        ResponseEntity<?> response = controller.approveFase(7L, null, "Buen avance");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(fase, wrapperDe(response).getData());
    }

    @Test
    void sendMensajeUsaElRemitenteAutenticadoYElCuerpoDelRequest() {
        autenticar(50L, "ESTUDIANTE");
        NuevoMensajeRequest request = new NuevoMensajeRequest();
        request.setContenido("¿Puede revisar el capítulo 3?");
        request.setTipo("CONSULTA");
        TutoringMensajeDTO mensaje = mock(TutoringMensajeDTO.class);
        when(tutoringService.sendMensaje(7L, 50L, "¿Puede revisar el capítulo 3?", "CONSULTA"))
                .thenReturn(mensaje);

        ResponseEntity<?> response = controller.sendMensaje(7L, 999L, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(mensaje, wrapperDe(response).getData());
        verify(tutoringService).sendMensaje(7L, 50L, "¿Puede revisar el capítulo 3?", "CONSULTA");
    }

    @Test
    void marcarMensajesLeidosDevuelveOkSinCuerpoDeDatos() {
        autenticar(50L, "ESTUDIANTE");

        ResponseEntity<?> response = controller.marcarMensajesLeidos(7L, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(wrapperDe(response).getData());
        verify(tutoringService).marcarMensajesLeidos(7L, 50L);
    }

    @Test
    void marcarMensajesLeidosTraduceElErrorDelServicioA400() {
        autenticar(50L, "ESTUDIANTE");
        doThrow(new RuntimeException("Fase inexistente"))
                .when(tutoringService).marcarMensajesLeidos(7L, 50L);

        ResponseEntity<?> response = controller.marcarMensajesLeidos(7L, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Fase inexistente", wrapperDe(response).getMessage());
    }

    // ── PDF ───────────────────────────────────────────────────────────────────

    @Test
    void obtainPdfFaseDevuelveElResourceConCabeceraInline() {
        autenticar(50L, "ESTUDIANTE");
        Resource resource = new ByteArrayResource("%PDF-1.4".getBytes()) {
            @Override public String getFilename() { return "fase-1.pdf"; }
        };
        when(tutoringService.obtainPdfFase(7L, 50L)).thenReturn(resource);

        ResponseEntity<?> response = controller.obtainPdfFase(7L, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("fase-1.pdf"));
        assertSame(resource, response.getBody());
    }

    @Test
    void obtainPdfFaseTraduceElErrorDelServicioA400() {
        autenticar(50L, "ESTUDIANTE");
        when(tutoringService.obtainPdfFase(7L, 50L))
                .thenThrow(new RuntimeException("La fase no tiene PDF cargado"));

        ResponseEntity<?> response = controller.obtainPdfFase(7L, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("La fase no tiene PDF cargado", wrapperDe(response).getMessage());
    }

    // ── sp_register_tutoring_avance ───────────────────────────────────────────

    @Test
    void registerAvanceConvierteElTamanoNumericoYLlamaAlProcedimiento() {
        autenticar(50L, "ESTUDIANTE");

        ResponseEntity<?> response = controller.registerAvanceSP(5L, Map.of(
                "numeroFase", 2,
                "archivoPdf", "capitulo2.pdf",
                "tamanoBytes", 12345,   // Jackson lo entrega como Integer, el SP espera Long
                "sha256", "abc123"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(tutoringService).registerAvanceSP(5L, 2, "capitulo2.pdf", 12345L, "abc123", 50L);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) wrapperDe(response).getData();
        assertEquals(5L, data.get("tutorId"));
        assertEquals(2, data.get("numeroFase"));
    }

    @Test
    void registerAvanceSinTamanoNiSha256PasaNullsAlProcedimiento() {
        autenticar(50L, "ESTUDIANTE");

        ResponseEntity<?> response = controller.registerAvanceSP(5L, Map.of(
                "numeroFase", 1, "archivoPdf", "capitulo1.pdf"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(tutoringService).registerAvanceSP(5L, 1, "capitulo1.pdf", null, null, 50L);
    }

    @Test
    void registerAvanceRechazaElCuerpoIncompletoSinLlamarAlProcedimiento() {
        ResponseEntity<?> response = controller.registerAvanceSP(5L, Map.of("numeroFase", 1));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Se requieren 'numeroFase' y 'archivoPdf'", wrapperDe(response).getMessage());
        verify(tutoringService, never()).registerAvanceSP(any(), any(), any(), any(), any(), any());
    }

    @Test
    void registerAvanceTraduceElErrorDelProcedimientoA400() {
        autenticar(50L, "ESTUDIANTE");
        doThrow(new RuntimeException("No se puede registrar la fase 3, la fase 2 debe estar APROBADA"))
                .when(tutoringService).registerAvanceSP(5L, 3, "capitulo3.pdf", null, null, 50L);

        ResponseEntity<?> response = controller.registerAvanceSP(5L, Map.of(
                "numeroFase", 3, "archivoPdf", "capitulo3.pdf"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(wrapperDe(response).getMessage().contains("la fase 2 debe estar APROBADA"));
    }
}
