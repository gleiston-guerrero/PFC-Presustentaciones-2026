package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.dto.NewMessageRequest;
import ec.edu.uteq.presustentaciones.dto.ResponseWrapper;
import ec.edu.uteq.presustentaciones.dto.TutoringPhaseDTO;
import ec.edu.uteq.presustentaciones.dto.TutoringMessageDTO;
import ec.edu.uteq.presustentaciones.dto.TutoringSummaryDTO;
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
    void cleanContexto() {
        SecurityContextHolder.clearContext();
    }

    @SuppressWarnings("unchecked")
    private ResponseWrapper<Object> wrapperOf(ResponseEntity<?> response) {
        return (ResponseWrapper<Object>) response.getBody();
    }

    /** Autentica un appUser con el role dado y lo deja resoluble por email. */
    private AppUser authenticate(Long id, String role) {
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
    void studentNotCanViewTutoringsOfOtherAppUserAlthoughAsksInUrl() {
        authenticate(50L, "ESTUDIANTE");
        when(tutoringService.obtainTutoringsStudent(50L)).thenReturn(List.of());

        // Pide explícitamente el appUser 99, pero el controlador ignora ese id
        controller.obtainTutoringsStudent(99L);

        verify(tutoringService).obtainTutoringsStudent(50L);
        verify(tutoringService, never()).obtainTutoringsStudent(99L);
    }

    @Test
    void coordinatorIfCanViewTutoringsOfOtherAppUser() {
        authenticate(1L, "COORDINADOR");
        when(tutoringService.obtainTutoringsTeacher(99L)).thenReturn(List.of());

        ResponseEntity<?> response = controller.obtainTutoringsTeacher(99L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(tutoringService).obtainTutoringsTeacher(99L);
    }

    @Test
    void adminWithoutAppUserIdExplicitQueryOwn() {
        authenticate(1L, "ADMIN");
        when(tutoringService.obtainSummary(5L, 1L)).thenReturn(mock(TutoringSummaryDTO.class));

        assertEquals(HttpStatus.OK, controller.obtainSummary(5L, null).getStatusCode());
        verify(tutoringService).obtainSummary(5L, 1L);
    }

    @Test
    void withoutAuthenticationEndpointReturns400InTimeOfBurst() {
        ResponseEntity<?> response = controller.obtainTutoringsStudent(1L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Usuario no autenticado", wrapperOf(response).getMessage());
        verifyNoInteractions(tutoringService);
    }

    @Test
    void withAppUserAnonymousEndpointReturns400() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        ResponseEntity<?> response = controller.obtainTutoringsTeacher(1L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Usuario no autenticado", wrapperOf(response).getMessage());
    }

    @Test
    void withTokenOfAppUserAlreadyDeletedEndpointReturns400() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("fantasma@uteq.edu.ec", null, List.of()));
        when(appUserRepository.findByEmail("fantasma@uteq.edu.ec")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.obtainPhases(1L, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Usuario autenticado no encontrado en el sistema", wrapperOf(response).getMessage());
    }

    // ── Consultas ─────────────────────────────────────────────────────────────

    @Test
    void obtainPhasesReturnsPhasesOfService() {
        authenticate(50L, "ESTUDIANTE");
        List<TutoringPhaseDTO> phases = List.of(mock(TutoringPhaseDTO.class));
        when(tutoringService.obtainPhases(5L, 50L)).thenReturn(phases);

        ResponseEntity<?> response = controller.obtainPhases(5L, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(phases, wrapperOf(response).getData());
    }

    @Test
    void obtainSummaryTranslatesErrorOfServiceTo400() {
        authenticate(50L, "ESTUDIANTE");
        when(tutoringService.obtainSummary(5L, 50L))
                .thenThrow(new RuntimeException("No tienes acceso a esta tutoría"));

        ResponseEntity<?> response = controller.obtainSummary(5L, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("No tienes acceso a esta tutoría", wrapperOf(response).getMessage());
    }

    // ── Operaciones sobre fases ───────────────────────────────────────────────

    @Test
    void createPhaseUsesAlwaysAppUserAuthenticatedNotOfParameter() {
        authenticate(60L, "DOCENTE");
        TutoringPhaseDTO phase = mock(TutoringPhaseDTO.class);
        when(tutoringService.createPhaseWithObservation(5L, 60L, "Revisar capítulo 2")).thenReturn(phase);

        ResponseEntity<?> response = controller.createPhaseWithObservation(5L, "Revisar capítulo 2", 999L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(phase, wrapperOf(response).getData());
        verify(tutoringService).createPhaseWithObservation(5L, 60L, "Revisar capítulo 2");
    }

    @Test
    void uploadPdfCorrectedDelegatesWithStudentAuthenticated() {
        authenticate(50L, "ESTUDIANTE");
        MultipartFile file = new MockMultipartFile("archivo", "cap2.pdf",
                MediaType.APPLICATION_PDF_VALUE, "contenido".getBytes());
        TutoringPhaseDTO phase = mock(TutoringPhaseDTO.class);
        when(tutoringService.uploadPdfCorrected(7L, file, 50L)).thenReturn(phase);

        ResponseEntity<?> response = controller.uploadPdfCorrected(7L, file, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(phase, wrapperOf(response).getData());
    }

    @Test
    void uploadPdfCorrectedTranslatesErrorOfServiceTo400() {
        authenticate(50L, "ESTUDIANTE");
        MultipartFile file = new MockMultipartFile("archivo", "malo.exe",
                MediaType.APPLICATION_OCTET_STREAM_VALUE, new byte[]{1});
        when(tutoringService.uploadPdfCorrected(7L, file, 50L))
                .thenThrow(new RuntimeException("Solo se admiten archivos PDF"));

        ResponseEntity<?> response = controller.uploadPdfCorrected(7L, file, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Solo se admiten archivos PDF", wrapperOf(response).getMessage());
    }

    @Test
    void approvePhaseDelegatesWithTutorAuthenticated() {
        authenticate(60L, "DOCENTE");
        TutoringPhaseDTO phase = mock(TutoringPhaseDTO.class);
        when(tutoringService.approvePhase(7L, 60L, "Buen avance")).thenReturn(phase);

        ResponseEntity<?> response = controller.approvePhase(7L, null, "Buen avance");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(phase, wrapperOf(response).getData());
    }

    @Test
    void sendMessageUsesSenderAuthenticatedAndBodyOfRequest() {
        authenticate(50L, "ESTUDIANTE");
        NewMessageRequest request = new NewMessageRequest();
        request.setContenido("¿Puede revisar el capítulo 3?");
        request.setKind("CONSULTA");
        TutoringMessageDTO message = mock(TutoringMessageDTO.class);
        when(tutoringService.sendMessage(7L, 50L, "¿Puede revisar el capítulo 3?", "CONSULTA"))
                .thenReturn(message);

        ResponseEntity<?> response = controller.sendMessage(7L, 999L, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(message, wrapperOf(response).getData());
        verify(tutoringService).sendMessage(7L, 50L, "¿Puede revisar el capítulo 3?", "CONSULTA");
    }

    @Test
    void markMessagesReadReturnsOkWithoutBodyOfData() {
        authenticate(50L, "ESTUDIANTE");

        ResponseEntity<?> response = controller.markMessagesRead(7L, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(wrapperOf(response).getData());
        verify(tutoringService).markMessagesRead(7L, 50L);
    }

    @Test
    void markMessagesReadTranslatesErrorOfServiceTo400() {
        authenticate(50L, "ESTUDIANTE");
        doThrow(new RuntimeException("Fase inexistente"))
                .when(tutoringService).markMessagesRead(7L, 50L);

        ResponseEntity<?> response = controller.markMessagesRead(7L, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Fase inexistente", wrapperOf(response).getMessage());
    }

    // ── PDF ───────────────────────────────────────────────────────────────────

    @Test
    void obtainPdfPhaseReturnsResourceWithHeaderInline() {
        authenticate(50L, "ESTUDIANTE");
        Resource resource = new ByteArrayResource("%PDF-1.4".getBytes()) {
            @Override public String getFilename() { return "fase-1.pdf"; }
        };
        when(tutoringService.obtainPdfPhase(7L, 50L)).thenReturn(resource);

        ResponseEntity<?> response = controller.obtainPdfPhase(7L, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("fase-1.pdf"));
        assertSame(resource, response.getBody());
    }

    @Test
    void obtainPdfPhaseTranslatesErrorOfServiceTo400() {
        authenticate(50L, "ESTUDIANTE");
        when(tutoringService.obtainPdfPhase(7L, 50L))
                .thenThrow(new RuntimeException("La fase no tiene PDF cargado"));

        ResponseEntity<?> response = controller.obtainPdfPhase(7L, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("La fase no tiene PDF cargado", wrapperOf(response).getMessage());
    }

    // ── sp_register_tutoring_avance ───────────────────────────────────────────

    @Test
    void registerProgressConvertsSizeNumericAndCallsToProcedure() {
        authenticate(50L, "ESTUDIANTE");

        ResponseEntity<?> response = controller.registerProgressSP(5L, Map.of(
                "numeroFase", 2,
                "archivoPdf", "capitulo2.pdf",
                "tamanoBytes", 12345,   // Jackson lo entrega como Integer, el SP espera Long
                "sha256", "abc123"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(tutoringService).registerProgressSP(5L, 2, "capitulo2.pdf", 12345L, "abc123", 50L);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) wrapperOf(response).getData();
        assertEquals(5L, data.get("tutorId"));
        assertEquals(2, data.get("numeroFase"));
    }

    @Test
    void registerProgressWithoutSizeOrSha256PassesNullsToProcedure() {
        authenticate(50L, "ESTUDIANTE");

        ResponseEntity<?> response = controller.registerProgressSP(5L, Map.of(
                "numeroFase", 1, "archivoPdf", "capitulo1.pdf"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(tutoringService).registerProgressSP(5L, 1, "capitulo1.pdf", null, null, 50L);
    }

    @Test
    void registerProgressRejectsBodyIncompleteWithoutCallToProcedure() {
        ResponseEntity<?> response = controller.registerProgressSP(5L, Map.of("numeroFase", 1));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Se requieren 'numeroFase' y 'archivoPdf'", wrapperOf(response).getMessage());
        verify(tutoringService, never()).registerProgressSP(any(), any(), any(), any(), any(), any());
    }

    @Test
    void registerProgressTranslatesErrorOfProcedureTo400() {
        authenticate(50L, "ESTUDIANTE");
        doThrow(new RuntimeException("No se puede registrar la fase 3, la fase 2 debe estar APROBADA"))
                .when(tutoringService).registerProgressSP(5L, 3, "capitulo3.pdf", null, null, 50L);

        ResponseEntity<?> response = controller.registerProgressSP(5L, Map.of(
                "numeroFase", 3, "archivoPdf", "capitulo3.pdf"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(wrapperOf(response).getMessage().contains("la fase 2 debe estar APROBADA"));
    }
}
