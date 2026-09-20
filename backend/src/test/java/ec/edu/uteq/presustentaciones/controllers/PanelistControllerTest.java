package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.dto.ResponseWrapper;
import ec.edu.uteq.presustentaciones.entities.Teacher;
import ec.edu.uteq.presustentaciones.entities.Panelist;
import ec.edu.uteq.presustentaciones.entities.RolePanelist;
import ec.edu.uteq.presustentaciones.entities.Tutor;
import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.security.service.SubmissionAccessService;
import ec.edu.uteq.presustentaciones.services.PanelistService;
import org.springframework.security.access.AccessDeniedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * PanelistController expone sp_assign_panelist_masivo, uno de los procedimientos
 * almacenados que se defienden en el examen, y tenía 3 de 53 líneas cubiertas.
 *
 * Además de la ruta feliz se cubre el manejo de error de cada endpoint: el
 * controlador traduce cualquier RuntimeException del servicio a un 400 con
 * ResponseWrapper.error, en vez de dejar que escale a un 500 -- comportamiento
 * que ninguna prueba verificaba hasta ahora.
 */
@ExtendWith(MockitoExtension.class)
class PanelistControllerTest {

    @Mock private PanelistService panelistService;
    @Mock private SubmissionAccessService submissionAccessService;

    @InjectMocks
    private PanelistController controller;

    @SuppressWarnings("unchecked")
    private ResponseWrapper<Object> wrapperOf(ResponseEntity<?> response) {
        return (ResponseWrapper<Object>) response.getBody();
    }

    private Panelist panelistWithTeacher(String nombre, String apellido, String roleCode) {
        return Panelist.builder()
                .id(1L)
                .confirmado(true)
                .rolePanelist(RolePanelist.builder().code(roleCode).build())
                .teacher(Teacher.builder().id(1L)
                        .appUser(AppUser.builder().id(1L).nombre(nombre).apellido(apellido).build())
                        .build())
                .build();
    }

    // ── Asignación individual ─────────────────────────────────────────────────

    @Test
    void assignPanelistReturnsPanelistAssigned() {
        Panelist panelist = Panelist.builder().id(1L).build();
        when(panelistService.assignPanelist(1L, 2L, "PRESIDENTE")).thenReturn(panelist);

        ResponseEntity<?> response = controller.assignPanelist(1L, 2L, "PRESIDENTE");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(wrapperOf(response).isSuccess());
        assertSame(panelist, wrapperOf(response).getData());
        assertEquals("Jurado asignado exitosamente", wrapperOf(response).getMessage());
    }

    @Test
    void assignPanelistTranslatesFailureOfServiceTo400() {
        when(panelistService.assignPanelist(1L, 2L, "PRESIDENTE"))
                .thenThrow(new RuntimeException("El docente ya es jurado de esta solicitud"));

        ResponseEntity<?> response = controller.assignPanelist(1L, 2L, "PRESIDENTE");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse(wrapperOf(response).isSuccess());
        assertEquals("El docente ya es jurado de esta solicitud", wrapperOf(response).getMessage());
    }

    @Test
    void assignAutomaticallyReturnsPanelistsResulting() {
        List<Panelist> panelists = List.of(Panelist.builder().id(1L).build(), Panelist.builder().id(2L).build());
        when(panelistService.listBySubmission(1L)).thenReturn(panelists);

        ResponseEntity<?> response = controller.assignAutomatically(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(panelists, wrapperOf(response).getData());
        verify(panelistService).assignPanelistsAutomatically(1L);
    }

    @Test
    void assignAutomaticallyTranslatesFailureOfServiceTo400() {
        doThrow(new RuntimeException("No hay suficientes docentes disponibles"))
                .when(panelistService).assignPanelistsAutomatically(1L);

        ResponseEntity<?> response = controller.assignAutomatically(1L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("No hay suficientes docentes disponibles", wrapperOf(response).getMessage());
        verify(panelistService, never()).listBySubmission(any());
    }

    // ── Consultas ─────────────────────────────────────────────────────────────

    @Test
    void listBySubmissionWrapsListOfService() {
        List<Panelist> panelists = List.of(Panelist.builder().id(1L).build());
        when(panelistService.listBySubmission(1L)).thenReturn(panelists);

        assertSame(panelists, wrapperOf(controller.listBySubmission(1L)).getData());
    }

    @Test
    void listAllPropagatesPaginationReceived() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Panelist> pagina = new PageImpl<>(List.of(Panelist.builder().id(1L).build()));
        when(panelistService.listAll(pageable)).thenReturn(pagina);

        assertSame(pagina, wrapperOf(controller.listAll(pageable)).getData());
    }

    @Test
    void deletePanelistReturns204() {
        assertEquals(HttpStatus.NO_CONTENT, controller.deletePanelist(9L).getStatusCode());
        verify(panelistService).deletePanelist(9L);
    }

    @Test
    void suggestTeachersUsesAmountRequested() {
        List<Teacher> teachers = List.of(Teacher.builder().id(1L).build());
        when(panelistService.suggestTeachers(1L, 3)).thenReturn(teachers);

        assertSame(teachers, wrapperOf(controller.suggestTeachers(1L, 3)).getData());
    }

    @Test
    void listByTeacherAndTutoringsByTeacherDelegateInService() {
        when(panelistService.listByTeacher(4L)).thenReturn(List.of(Panelist.builder().id(1L).build()));
        when(panelistService.listTutoringsByTeacher(4L)).thenReturn(List.of(Tutor.builder().id(1L).build()));

        assertEquals(HttpStatus.OK, controller.listByTeacher(4L).getStatusCode());
        assertEquals(HttpStatus.OK, controller.listTutoringsByTeacher(4L).getStatusCode());
    }

    // ── Tutor ─────────────────────────────────────────────────────────────────

    @Test
    void assignTutorReturnsTutorAssigned() {
        Tutor tutor = Tutor.builder().id(1L).build();
        when(panelistService.assignTutor(1L, 2L)).thenReturn(tutor);

        ResponseEntity<?> response = controller.assignTutor(1L, 2L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(tutor, wrapperOf(response).getData());
    }

    @Test
    void assignTutorTranslatesFailureOfServiceTo400() {
        when(panelistService.assignTutor(1L, 2L)).thenThrow(new RuntimeException("La solicitud ya tiene tutor"));

        ResponseEntity<?> response = controller.assignTutor(1L, 2L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("La solicitud ya tiene tutor", wrapperOf(response).getMessage());
    }

    @Test
    void obtainTutorReturns404WhenSubmissionNotHasTutor() {
        when(panelistService.obtainTutorOfSubmission(1L)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, controller.obtainTutor(1L).getStatusCode());
    }

    @Test
    void obtainTutorReturnsTutorWhenExists() {
        Tutor tutor = Tutor.builder().id(1L).build();
        when(panelistService.obtainTutorOfSubmission(1L)).thenReturn(Optional.of(tutor));

        ResponseEntity<?> response = controller.obtainTutor(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(tutor, wrapperOf(response).getData());
    }

    @Test
    void deleteTutorReturns204() {
        assertEquals(HttpStatus.NO_CONTENT, controller.deleteTutor(7L).getStatusCode());
        verify(panelistService).deleteTutor(7L);
    }

    // ── Info de panelist (armado manual del Map de respuesta) ───────────────────

    @Test
    void obtainInfoPanelistBuildsNameOfTeacherWhenStringIsComplete() {
        when(panelistService.obtainInfoPanelist(1L, 2L))
                .thenReturn(Optional.of(panelistWithTeacher("Ana", "Pérez", "PRESIDENTE")));

        ResponseEntity<?> response = controller.obtainInfoPanelist(1L, 2L);

        @SuppressWarnings("unchecked")
        Map<String, Object> info = (Map<String, Object>) wrapperOf(response).getData();
        assertEquals(1L, info.get("id"));
        assertEquals("PRESIDENTE", info.get("rol"));
        assertEquals(true, info.get("confirmado"));
        assertEquals("Ana Pérez", info.get("nombreDocente"));
    }

    @Test
    void obtainInfoPanelistWithoutTeacherOrRoleNotBreaksAndReturnsStringsEmpty() {
        // Panelist sin teacher y sin rolePanelist: getRole() devuelve null y el nombre queda vacío.
        // Map.of no admite valores nulos, así que si el controlador no hiciera el fallback
        // este endpoint reventaría con NullPointerException en produccion.
        when(panelistService.obtainInfoPanelist(1L, 2L))
                .thenReturn(Optional.of(Panelist.builder().id(5L).confirmado(false).build()));

        ResponseEntity<?> response = controller.obtainInfoPanelist(1L, 2L);

        @SuppressWarnings("unchecked")
        Map<String, Object> info = (Map<String, Object>) wrapperOf(response).getData();
        assertEquals(5L, info.get("id"));
        assertEquals("", info.get("rol"));
        assertEquals(false, info.get("confirmado"));
        assertEquals("", info.get("nombreDocente"));
    }

    @Test
    void obtainInfoPanelistReturnsDataNullWhenNotHasAssignment() {
        when(panelistService.obtainInfoPanelist(1L, 2L)).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.obtainInfoPanelist(1L, 2L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(wrapperOf(response).getData());
    }

    // ── sp_assign_panelist_masivo ──────────────────────────────────────────────

    @Test
    void assignBulkConvertsIdsJsonToArraysLongAndCallsToProcedure() {
        ResponseEntity<?> response = controller.assignBulk(Map.of(
                "solicitudIds", List.of(1, 2, 3),
                "docenteIds", List.of(4, 5, 6),
                "rol", "PRESIDENTE"));

        assertEquals(HttpStatus.OK, response.getStatusCode());

        // Jackson deserializa los enteros del JSON como Integer; el procedimiento espera Long[].
        ArgumentCaptor<Long[]> submissions = ArgumentCaptor.forClass(Long[].class);
        ArgumentCaptor<Long[]> teachers = ArgumentCaptor.forClass(Long[].class);
        verify(panelistService).assignPanelistBulkSP(submissions.capture(), teachers.capture(), eq("PRESIDENTE"));
        assertArrayEquals(new Long[]{1L, 2L, 3L}, submissions.getValue());
        assertArrayEquals(new Long[]{4L, 5L, 6L}, teachers.getValue());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) wrapperOf(response).getData();
        assertEquals(3, data.get("asignados"));
        assertEquals("PRESIDENTE", data.get("rol"));
    }

    @Test
    void assignBulkRejectsBodyIncompleteWithoutCallToProcedure() {
        ResponseEntity<?> sinRole = controller.assignBulk(Map.of(
                "solicitudIds", List.of(1), "docenteIds", List.of(2)));

        assertEquals(HttpStatus.BAD_REQUEST, sinRole.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> error = (Map<String, String>) sinRole.getBody();
        assertEquals("Se requieren 'solicitudIds', 'docenteIds' y 'rol'", error.get("error"));
        verify(panelistService, never()).assignPanelistBulkSP(any(), any(), any());
    }

    @Test
    void assignBulkTranslatesFailureOfProcedureTo400() {
        doThrow(new RuntimeException("rol_jurado inexistente"))
                .when(panelistService).assignPanelistBulkSP(any(), any(), eq("INVENTADO"));

        ResponseEntity<?> response = controller.assignBulk(Map.of(
                "solicitudIds", List.of(1),
                "docenteIds", List.of(2),
                "rol", "INVENTADO"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("rol_jurado inexistente", wrapperOf(response).getMessage());
    }

    // ── Acceso a los GET por solicitud (revision final, punto 5b) ──────────────────────────────

    @Test
    void listBySubmissionOfUnrelatedAppUserIsDeniedAndDoesNotQueryTheService() {
        doThrow(new AccessDeniedException("ajeno")).when(submissionAccessService)
                .validateAccessById(1L, SubmissionAccessService.PANEL_VIEW_PERMISSIONS);

        assertThrows(AccessDeniedException.class, () -> controller.listBySubmission(1L));
        verify(panelistService, never()).listBySubmission(any());
    }

    @Test
    void obtainTutorOfUnrelatedAppUserIsDeniedAndDoesNotQueryTheService() {
        doThrow(new AccessDeniedException("ajeno")).when(submissionAccessService)
                .validateAccessById(1L, SubmissionAccessService.PANEL_VIEW_PERMISSIONS);

        assertThrows(AccessDeniedException.class, () -> controller.obtainTutor(1L));
        verify(panelistService, never()).obtainTutorOfSubmission(any());
    }

    @Test
    void obtainInfoPanelistOfUnrelatedAppUserIsDeniedAndDoesNotQueryTheService() {
        doThrow(new AccessDeniedException("ajeno")).when(submissionAccessService)
                .validateAccessById(1L, SubmissionAccessService.PANEL_VIEW_PERMISSIONS);

        assertThrows(AccessDeniedException.class, () -> controller.obtainInfoPanelist(1L, 9L));
        verify(panelistService, never()).obtainInfoPanelist(any(), any());
    }
}
