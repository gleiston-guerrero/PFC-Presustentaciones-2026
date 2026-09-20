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
    private String errorOf(ResponseEntity<?> response) {
        return ((Map<String, String>) response.getBody()).get("error");
    }

    @Test
    void createReturnsScheduleScheduled() {
        LocalDate date = LocalDate.of(2026, 9, 10);
        LocalTime hora = LocalTime.of(9, 0);
        Schedule schedule = Schedule.builder().id(1L).build();
        when(scheduleService.createSchedule(1L, 2L, date, hora)).thenReturn(schedule);

        ResponseEntity<?> response = controller.create(1L, 2L, date, hora);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(schedule, response.getBody());
    }

    @Test
    void createWithPanelistInConflictReturns400WithMessageOfProcedure() {
        LocalDate date = LocalDate.of(2026, 9, 10);
        LocalTime hora = LocalTime.of(9, 0);
        when(scheduleService.createSchedule(1L, 2L, date, hora))
                .thenThrow(new RuntimeException("El docente Ana Pérez ya tiene una defensa en ese horario"));

        ResponseEntity<?> response = controller.create(1L, 2L, date, hora);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("El docente Ana Pérez ya tiene una defensa en ese horario", errorOf(response));
    }

    @Test
    void assignAutomaticReturnsScheduleAndTranslatesErrorsTo400() {
        Schedule schedule = Schedule.builder().id(1L).build();
        when(scheduleService.assignAutomatic(1L)).thenReturn(schedule);
        assertSame(schedule, controller.assignAutomatic(1L).getBody());

        when(scheduleService.assignAutomatic(2L))
                .thenThrow(new RuntimeException("No hay franjas disponibles esta semana"));
        ResponseEntity<?> error = controller.assignAutomatic(2L);
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertEquals("No hay franjas disponibles esta semana", errorOf(error));
    }

    @Test
    void availabilityReturnsSlotsWithDateAndDurationQueried() {
        LocalDate date = LocalDate.of(2026, 9, 10);
        List<LocalDateTime> slots = List.of(date.atTime(9, 0), date.atTime(10, 0));
        when(scheduleService.slotsAvailable(date, 45)).thenReturn(slots);

        Map<String, Object> body = controller.availability(date, 45).getBody();

        assertNotNull(body);
        assertEquals(date, body.get("fecha"));
        assertEquals(45, body.get("duracionMin"));
        assertSame(slots, body.get("franjas"));
    }

    @Test
    void verifyAvailabilityReturnsMessageDifferentAccordingResult() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 10, 9, 0);
        when(scheduleService.isAvailable(1L, start, 45)).thenReturn(true);
        when(scheduleService.isAvailable(2L, start, 45)).thenReturn(false);

        Map<String, Object> free = controller.verifyAvailability(1L, start, 45).getBody();
        Map<String, Object> ocupada = controller.verifyAvailability(2L, start, 45).getBody();

        assertNotNull(free);
        assertNotNull(ocupada);
        assertEquals(true, free.get("disponible"));
        assertTrue(((String) free.get("mensaje")).contains("disponible"));
        assertEquals(false, ocupada.get("disponible"));
        assertTrue(((String) ocupada.get("mensaje")).contains("ocupada"));
    }

    @Test
    void listByStudentAndByAppUserDelegateInService() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Schedule> pagina = new PageImpl<>(List.of(Schedule.builder().id(1L).build()));
        List<Schedule> byStudent = List.of(Schedule.builder().id(2L).build());
        List<Schedule> byAppUser = List.of(Schedule.builder().id(3L).build());
        when(scheduleService.listSchedules(pageable)).thenReturn(pagina);
        when(scheduleService.listByStudent(7L)).thenReturn(byStudent);
        when(scheduleService.listByAppUser(50L)).thenReturn(byAppUser);

        assertSame(pagina, controller.list(pageable).getBody());
        assertSame(byStudent, controller.byStudent(7L));
        assertSame(byAppUser, controller.byAppUser(50L));
    }

    @Test
    void bySubmissionReturns404WhenNotHasScheduleScheduled() {
        when(scheduleService.searchBySubmission(1L)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, controller.bySubmission(1L).getStatusCode());
    }

    @Test
    void bySubmissionReturnsScheduleWhenExists() {
        Schedule schedule = Schedule.builder().id(1L).build();
        when(scheduleService.searchBySubmission(1L)).thenReturn(Optional.of(schedule));

        assertSame(schedule, controller.bySubmission(1L).getBody());
    }

    @Test
    void deleteReturns204() {
        assertEquals(HttpStatus.NO_CONTENT, controller.delete(3L).getStatusCode());
        verify(scheduleService).delete(3L);
    }
}
