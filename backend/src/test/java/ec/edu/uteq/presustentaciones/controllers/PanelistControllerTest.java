package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.dto.ResponseWrapper;
import ec.edu.uteq.presustentaciones.entities.Teacher;
import ec.edu.uteq.presustentaciones.entities.Panelist;
import ec.edu.uteq.presustentaciones.entities.RolePanelist;
import ec.edu.uteq.presustentaciones.entities.Tutor;
import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.services.PanelistService;
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

    @InjectMocks
    private PanelistController controller;

    @SuppressWarnings("unchecked")
    private ResponseWrapper<Object> wrapperDe(ResponseEntity<?> response) {
        return (ResponseWrapper<Object>) response.getBody();
    }

    private Panelist panelistConTeacher(String nombre, String apellido, String roleCodigo) {
        return Panelist.builder()
                .id(1L)
                .confirmado(true)
                .rolePanelist(RolePanelist.builder().codigo(roleCodigo).build())
                .teacher(Teacher.builder().id(1L)
                        .appUser(AppUser.builder().id(1L).nombre(nombre).apellido(apellido).build())
                        .build())
                .build();
    }

    // ── Asignación individual ─────────────────────────────────────────────────

    @Test
    void assignPanelistDevuelveElPanelistAsignado() {
        Panelist panelist = Panelist.builder().id(1L).build();
        when(panelistService.assignPanelist(1L, 2L, "PRESIDENTE")).thenReturn(panelist);

        ResponseEntity<?> response = controller.assignPanelist(1L, 2L, "PRESIDENTE");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(wrapperDe(response).isSuccess());
        assertSame(panelist, wrapperDe(response).getData());
        assertEquals("Jurado asignado exitosamente", wrapperDe(response).getMessage());
    }

    @Test
    void assignPanelistTraduceElErrorDelServicioA400() {
        when(panelistService.assignPanelist(1L, 2L, "PRESIDENTE"))
                .thenThrow(new RuntimeException("El docente ya es jurado de esta solicitud"));

        ResponseEntity<?> response = controller.assignPanelist(1L, 2L, "PRESIDENTE");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse(wrapperDe(response).isSuccess());
        assertEquals("El docente ya es jurado de esta solicitud", wrapperDe(response).getMessage());
    }

    @Test
    void assignAutomaticamenteDevuelveLosPanelistsResultantes() {
        List<Panelist> panelists = List.of(Panelist.builder().id(1L).build(), Panelist.builder().id(2L).build());
        when(panelistService.listPorSubmission(1L)).thenReturn(panelists);

        ResponseEntity<?> response = controller.assignAutomaticamente(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(panelists, wrapperDe(response).getData());
        verify(panelistService).assignPanelistsAutomaticamente(1L);
    }

    @Test
    void assignAutomaticamenteTraduceElErrorDelServicioA400() {
        doThrow(new RuntimeException("No hay suficientes docentes disponibles"))
                .when(panelistService).assignPanelistsAutomaticamente(1L);

        ResponseEntity<?> response = controller.assignAutomaticamente(1L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("No hay suficientes docentes disponibles", wrapperDe(response).getMessage());
        verify(panelistService, never()).listPorSubmission(any());
    }

    // ── Consultas ─────────────────────────────────────────────────────────────

    @Test
    void listPorSubmissionEnvuelveLaListaDelServicio() {
        List<Panelist> panelists = List.of(Panelist.builder().id(1L).build());
        when(panelistService.listPorSubmission(1L)).thenReturn(panelists);

        assertSame(panelists, wrapperDe(controller.listPorSubmission(1L)).getData());
    }

    @Test
    void listTodosPropagaLaPaginacionRecibida() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Panelist> pagina = new PageImpl<>(List.of(Panelist.builder().id(1L).build()));
        when(panelistService.listTodos(pageable)).thenReturn(pagina);

        assertSame(pagina, wrapperDe(controller.listTodos(pageable)).getData());
    }

    @Test
    void deletePanelistDevuelve204() {
        assertEquals(HttpStatus.NO_CONTENT, controller.deletePanelist(9L).getStatusCode());
        verify(panelistService).deletePanelist(9L);
    }

    @Test
    void sugerirTeachersUsaLaCantidadSolicitada() {
        List<Teacher> teachers = List.of(Teacher.builder().id(1L).build());
        when(panelistService.sugerirTeachers(1L, 3)).thenReturn(teachers);

        assertSame(teachers, wrapperDe(controller.sugerirTeachers(1L, 3)).getData());
    }

    @Test
    void listPorTeacherYTutoringsPorTeacherDeleganEnElServicio() {
        when(panelistService.listPorTeacher(4L)).thenReturn(List.of(Panelist.builder().id(1L).build()));
        when(panelistService.listTutoringsPorTeacher(4L)).thenReturn(List.of(Tutor.builder().id(1L).build()));

        assertEquals(HttpStatus.OK, controller.listPorTeacher(4L).getStatusCode());
        assertEquals(HttpStatus.OK, controller.listTutoringsPorTeacher(4L).getStatusCode());
    }

    // ── Tutor ─────────────────────────────────────────────────────────────────

    @Test
    void assignTutorDevuelveElTutorAsignado() {
        Tutor tutor = Tutor.builder().id(1L).build();
        when(panelistService.assignTutor(1L, 2L)).thenReturn(tutor);

        ResponseEntity<?> response = controller.assignTutor(1L, 2L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(tutor, wrapperDe(response).getData());
    }

    @Test
    void assignTutorTraduceElErrorDelServicioA400() {
        when(panelistService.assignTutor(1L, 2L)).thenThrow(new RuntimeException("La solicitud ya tiene tutor"));

        ResponseEntity<?> response = controller.assignTutor(1L, 2L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("La solicitud ya tiene tutor", wrapperDe(response).getMessage());
    }

    @Test
    void obtainTutorDevuelve404CuandoLaSubmissionNoTieneTutor() {
        when(panelistService.obtainTutorDeSubmission(1L)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, controller.obtainTutor(1L).getStatusCode());
    }

    @Test
    void obtainTutorDevuelveElTutorCuandoExiste() {
        Tutor tutor = Tutor.builder().id(1L).build();
        when(panelistService.obtainTutorDeSubmission(1L)).thenReturn(Optional.of(tutor));

        ResponseEntity<?> response = controller.obtainTutor(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(tutor, wrapperDe(response).getData());
    }

    @Test
    void deleteTutorDevuelve204() {
        assertEquals(HttpStatus.NO_CONTENT, controller.deleteTutor(7L).getStatusCode());
        verify(panelistService).deleteTutor(7L);
    }

    // ── Info de panelist (armado manual del Map de respuesta) ───────────────────

    @Test
    void obtainInfoPanelistArmaElNombreDelTeacherCuandoLaCadenaEstaCompleta() {
        when(panelistService.obtainInfoPanelist(1L, 2L))
                .thenReturn(Optional.of(panelistConTeacher("Ana", "Pérez", "PRESIDENTE")));

        ResponseEntity<?> response = controller.obtainInfoPanelist(1L, 2L);

        @SuppressWarnings("unchecked")
        Map<String, Object> info = (Map<String, Object>) wrapperDe(response).getData();
        assertEquals(1L, info.get("id"));
        assertEquals("PRESIDENTE", info.get("rol"));
        assertEquals(true, info.get("confirmado"));
        assertEquals("Ana Pérez", info.get("nombreDocente"));
    }

    @Test
    void obtainInfoPanelistSinTeacherNiRoleNoRompeYDevuelveCadenasVacias() {
        // Panelist sin teacher y sin rolePanelist: getRole() devuelve null y el nombre queda vacío.
        // Map.of no admite valores nulos, así que si el controlador no hiciera el fallback
        // este endpoint reventaría con NullPointerException en produccion.
        when(panelistService.obtainInfoPanelist(1L, 2L))
                .thenReturn(Optional.of(Panelist.builder().id(5L).confirmado(false).build()));

        ResponseEntity<?> response = controller.obtainInfoPanelist(1L, 2L);

        @SuppressWarnings("unchecked")
        Map<String, Object> info = (Map<String, Object>) wrapperDe(response).getData();
        assertEquals(5L, info.get("id"));
        assertEquals("", info.get("rol"));
        assertEquals(false, info.get("confirmado"));
        assertEquals("", info.get("nombreDocente"));
    }

    @Test
    void obtainInfoPanelistDevuelveDataNulaCuandoNoHayAsignacion() {
        when(panelistService.obtainInfoPanelist(1L, 2L)).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.obtainInfoPanelist(1L, 2L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(wrapperDe(response).getData());
    }

    // ── sp_assign_panelist_masivo ──────────────────────────────────────────────

    @Test
    void assignMasivoConvierteLosIdsJsonAArreglosLongYLlamaAlProcedimiento() {
        ResponseEntity<?> response = controller.assignMasivo(Map.of(
                "solicitudIds", List.of(1, 2, 3),
                "docenteIds", List.of(4, 5, 6),
                "rol", "PRESIDENTE"));

        assertEquals(HttpStatus.OK, response.getStatusCode());

        // Jackson deserializa los enteros del JSON como Integer; el procedimiento espera Long[].
        ArgumentCaptor<Long[]> submissions = ArgumentCaptor.forClass(Long[].class);
        ArgumentCaptor<Long[]> teachers = ArgumentCaptor.forClass(Long[].class);
        verify(panelistService).assignPanelistMasivoSP(submissions.capture(), teachers.capture(), eq("PRESIDENTE"));
        assertArrayEquals(new Long[]{1L, 2L, 3L}, submissions.getValue());
        assertArrayEquals(new Long[]{4L, 5L, 6L}, teachers.getValue());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) wrapperDe(response).getData();
        assertEquals(3, data.get("asignados"));
        assertEquals("PRESIDENTE", data.get("rol"));
    }

    @Test
    void assignMasivoRechazaElCuerpoIncompletoSinLlamarAlProcedimiento() {
        ResponseEntity<?> sinRole = controller.assignMasivo(Map.of(
                "solicitudIds", List.of(1), "docenteIds", List.of(2)));

        assertEquals(HttpStatus.BAD_REQUEST, sinRole.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> error = (Map<String, String>) sinRole.getBody();
        assertEquals("Se requieren 'solicitudIds', 'docenteIds' y 'rol'", error.get("error"));
        verify(panelistService, never()).assignPanelistMasivoSP(any(), any(), any());
    }

    @Test
    void assignMasivoTraduceElErrorDelProcedimientoA400() {
        doThrow(new RuntimeException("rol_jurado inexistente"))
                .when(panelistService).assignPanelistMasivoSP(any(), any(), eq("INVENTADO"));

        ResponseEntity<?> response = controller.assignMasivo(Map.of(
                "solicitudIds", List.of(1),
                "docenteIds", List.of(2),
                "rol", "INVENTADO"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("rol_jurado inexistente", wrapperDe(response).getMessage());
    }
}
