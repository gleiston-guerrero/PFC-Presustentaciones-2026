package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.entities.Schedule;
import ec.edu.uteq.presustentaciones.services.ScheduleService;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ScheduleController es el punto de entrada de sp_validate_conflicto_panelist (la
 * validación cruzada que impide programar una defensa con un panelist ya ocupado) y
 * tenía 1 de 17 líneas cubiertas.
 *
 * El caso más importante que se cubre aquí es el de conflicto: el servicio lanza una
 * RuntimeException con el mensaje del procedimiento y el controlador debe devolver un
 * 400 legible, no un 500.
 */
@ExtendWith(MockitoExtension.class)
class ScheduleControllerTest {

    @Mock private ScheduleService scheduleService;

    @InjectMocks
    private ScheduleController controller;

    @SuppressWarnings("unchecked")
    private String errorDe(ResponseEntity<?> response) {
        return ((Map<String, String>) response.getBody()).get("error");
    }

    @Test
    void createDevuelveElScheduleProgramado() {
        LocalDate fecha = LocalDate.of(2026, 9, 10);
        LocalTime hora = LocalTime.of(9, 0);
        Schedule schedule = Schedule.builder().id(1L).build();
        when(scheduleService.createSchedule(1L, 2L, fecha, hora)).thenReturn(schedule);

        ResponseEntity<?> response = controller.create(1L, 2L, fecha, hora);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(schedule, response.getBody());
    }

    @Test
    void createConPanelistEnConflictoDevuelve400ConElMensajeDelProcedimiento() {
        LocalDate fecha = LocalDate.of(2026, 9, 10);
        LocalTime hora = LocalTime.of(9, 0);
        when(scheduleService.createSchedule(1L, 2L, fecha, hora))
                .thenThrow(new RuntimeException("El docente Ana Pérez ya tiene una defensa en ese horario"));

        ResponseEntity<?> response = controller.create(1L, 2L, fecha, hora);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("El docente Ana Pérez ya tiene una defensa en ese horario", errorDe(response));
    }

    @Test
    void assignAutomaticoDevuelveElScheduleYTraduceErroresA400() {
        Schedule schedule = Schedule.builder().id(1L).build();
        when(scheduleService.assignAutomatico(1L)).thenReturn(schedule);
        assertSame(schedule, controller.assignAutomatico(1L).getBody());

        when(scheduleService.assignAutomatico(2L))
                .thenThrow(new RuntimeException("No hay franjas disponibles esta semana"));
        ResponseEntity<?> error = controller.assignAutomatico(2L);
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertEquals("No hay franjas disponibles esta semana", errorDe(error));
    }

    @Test
    void availabilityDevuelveLasFranjasConLaFechaYDuracionConsultadas() {
        LocalDate fecha = LocalDate.of(2026, 9, 10);
        List<LocalDateTime> franjas = List.of(fecha.atTime(9, 0), fecha.atTime(10, 0));
        when(scheduleService.franjasDisponibles(fecha, 45)).thenReturn(franjas);

        Map<String, Object> body = controller.availability(fecha, 45).getBody();

        assertNotNull(body);
        assertEquals(fecha, body.get("fecha"));
        assertEquals(45, body.get("duracionMin"));
        assertSame(franjas, body.get("franjas"));
    }

    @Test
    void verifyAvailabilityDevuelveMensajeDistintoSegunElResultado() {
        LocalDateTime inicio = LocalDateTime.of(2026, 9, 10, 9, 0);
        when(scheduleService.estaDisponible(1L, inicio, 45)).thenReturn(true);
        when(scheduleService.estaDisponible(2L, inicio, 45)).thenReturn(false);

        Map<String, Object> libre = controller.verifyAvailability(1L, inicio, 45).getBody();
        Map<String, Object> ocupada = controller.verifyAvailability(2L, inicio, 45).getBody();

        assertNotNull(libre);
        assertNotNull(ocupada);
        assertEquals(true, libre.get("disponible"));
        assertTrue(((String) libre.get("mensaje")).contains("disponible"));
        assertEquals(false, ocupada.get("disponible"));
        assertTrue(((String) ocupada.get("mensaje")).contains("ocupada"));
    }

    @Test
    void listPorStudentYPorAppUserDeleganEnElServicio() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Schedule> pagina = new PageImpl<>(List.of(Schedule.builder().id(1L).build()));
        List<Schedule> porStudent = List.of(Schedule.builder().id(2L).build());
        List<Schedule> porAppUser = List.of(Schedule.builder().id(3L).build());
        when(scheduleService.listSchedules(pageable)).thenReturn(pagina);
        when(scheduleService.listPorStudent(7L)).thenReturn(porStudent);
        when(scheduleService.listPorAppUser(50L)).thenReturn(porAppUser);

        assertSame(pagina, controller.list(pageable).getBody());
        assertSame(porStudent, controller.porStudent(7L));
        assertSame(porAppUser, controller.porAppUser(50L));
    }

    @Test
    void porSubmissionDevuelve404CuandoNoHayScheduleProgramado() {
        when(scheduleService.searchPorSubmission(1L)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, controller.porSubmission(1L).getStatusCode());
    }

    @Test
    void porSubmissionDevuelveElScheduleCuandoExiste() {
        Schedule schedule = Schedule.builder().id(1L).build();
        when(scheduleService.searchPorSubmission(1L)).thenReturn(Optional.of(schedule));

        assertSame(schedule, controller.porSubmission(1L).getBody());
    }

    @Test
    void deleteDevuelve204() {
        assertEquals(HttpStatus.NO_CONTENT, controller.delete(3L).getStatusCode());
        verify(scheduleService).delete(3L);
    }
}
