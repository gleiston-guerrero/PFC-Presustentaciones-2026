package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.*;
import ec.edu.uteq.presustentaciones.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Cubre validatePrerequisitosParaSchedule (tribunal completo + tutoría COMPLETADA) y la
 * validación cruzada sp_validate_conflicto_panelist (Fase 3 / Criterio P1) conectada en
 * createSchedule -- ninguno de los dos tenía prueba dedicada (ScheduleServiceImplTest
 * no existía, ver docs/trazabilidad/matriz.csv RF-04).
 */
@ExtendWith(MockitoExtension.class)
class ScheduleServiceImplTest {

    @Mock private ScheduleRepository scheduleRepository;
    @Mock private SubmissionRepository submissionRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private PanelistRepository panelistRepository;
    @Mock private TutorRepository tutorRepository;
    @Mock private NotificationService notificationService;
    @Mock private StatusScheduleRepository statusScheduleRepository;

    @InjectMocks
    private ScheduleServiceImpl scheduleService;

    private Submission submission;
    private Room room;
    private Teacher teacher;
    private Panelist presidente;
    private Panelist vocal;
    private Panelist secretario;
    private Tutor tutorCompletado;

    @BeforeEach
    void setUp() {
        submission = Submission.builder().id(10L).tituloTopic("Sistema X").build();
        room = Room.builder().id(1L).nombre("Aula 1").available(true).build();
        AppUser appUserTeacher = AppUser.builder().id(50L).nombre("Ana").apellido("Torres").build();
        teacher = Teacher.builder().id(1L).appUser(appUserTeacher).build();

        presidente = Panelist.builder().id(1L).submission(submission).teacher(teacher)
                .rolePanelist(RolePanelist.builder().code("PRESIDENTE").build()).build();
        // El tribunal real solo tiene 3 roles: PRESIDENTE, VOCAL_1, VOCAL_2 (sin secretario) --
        // ver PanelistServiceImpl.rolesValidos y ScheduleServiceImpl.validatePrerequisitosParaSchedule.
        vocal = Panelist.builder().id(2L).submission(submission).teacher(teacher)
                .rolePanelist(RolePanelist.builder().code("VOCAL_1").build()).build();
        secretario = Panelist.builder().id(3L).submission(submission).teacher(teacher)
                .rolePanelist(RolePanelist.builder().code("VOCAL_2").build()).build();

        tutorCompletado = Tutor.builder().id(1L).submission(submission).status("COMPLETADA").build();
    }

    @Test
    void testCreateScheduleFallaSiPanelIncompleto() {
        when(panelistRepository.findBySubmissionId(10L)).thenReturn(List.of(presidente, vocal));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                scheduleService.createSchedule(10L, 1L, LocalDate.now().plusDays(5), LocalTime.of(9, 0)));
        assertTrue(ex.getMessage().contains("tribunal no está completo"));
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void testCreateScheduleFallaSiTutoringNoCompletada() {
        when(panelistRepository.findBySubmissionId(10L)).thenReturn(List.of(presidente, vocal, secretario));
        when(tutorRepository.findBySubmissionId(10L)).thenReturn(Optional.of(Tutor.builder().status("EN_PROCESO").build()));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                scheduleService.createSchedule(10L, 1L, LocalDate.now().plusDays(5), LocalTime.of(9, 0)));
        assertTrue(ex.getMessage().contains("tutoría no ha sido completada"));
    }

    @Test
    void testCreateScheduleFallaByConflictoDePanelist() {
        when(panelistRepository.findBySubmissionId(10L)).thenReturn(List.of(presidente, vocal, secretario));
        when(tutorRepository.findBySubmissionId(10L)).thenReturn(Optional.of(tutorCompletado));
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(submission));
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(scheduleRepository.findConflictos(anyLong(), any(), any())).thenReturn(List.of());
        // sp_validate_conflicto_panelist (Fase 3): el teacher ya tiene otra defensa en ese horario
        when(panelistRepository.validateConflictoPanelist(anyLong(), eq(1L), any(), anyInt(), isNull()))
                .thenReturn(Boolean.FALSE);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                scheduleService.createSchedule(10L, 1L, LocalDate.now().plusDays(5), LocalTime.of(9, 0)));
        assertTrue(ex.getMessage().contains("Conflicto de horario"));
        assertTrue(ex.getMessage().contains("Ana Torres"));
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void testCreateScheduleExitosoWithoutConflictos() {
        when(panelistRepository.findBySubmissionId(10L)).thenReturn(List.of(presidente, vocal, secretario));
        when(tutorRepository.findBySubmissionId(10L)).thenReturn(Optional.of(tutorCompletado));
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(submission));
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(scheduleRepository.findConflictos(anyLong(), any(), any())).thenReturn(List.of());
        when(panelistRepository.validateConflictoPanelist(anyLong(), anyLong(), any(), anyInt(), isNull()))
                .thenReturn(Boolean.TRUE);
        when(statusScheduleRepository.findByCode("PROGRAMADO"))
                .thenReturn(Optional.of(StatusSchedule.builder().code("PROGRAMADO").nombre("Programado").build()));
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> {
            Schedule c = inv.getArgument(0);
            c.setId(99L);
            return c;
        });

        Schedule result = scheduleService.createSchedule(10L, 1L, LocalDate.now().plusDays(5), LocalTime.of(9, 0));

        assertNotNull(result);
        assertEquals("PROGRAMADO", result.getStatus().getCode());
        verify(panelistRepository, times(3)).validateConflictoPanelist(anyLong(), anyLong(), any(), anyInt(), isNull());
    }

    // ── assignAutomatico ────────────────────────────────────────────────────

    @Test
    void assignAutomaticDevuelveElExistingSiYaIsProgramado() {
        when(panelistRepository.findBySubmissionId(10L)).thenReturn(List.of(presidente, vocal, secretario));
        when(tutorRepository.findBySubmissionId(10L)).thenReturn(Optional.of(tutorCompletado));
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(submission));
        Schedule existing = Schedule.builder().id(5L)
                .status(StatusSchedule.builder().code("PROGRAMADO").build()).build();
        when(scheduleRepository.findBySubmissionId(10L)).thenReturn(Optional.of(existing));

        Schedule result = scheduleService.assignAutomatic(10L);

        assertSame(existing, result);
        verify(roomRepository, never()).findAll();
    }

    @Test
    void assignAutomaticLanzaSiNoHayRoomsAvailable() {
        when(panelistRepository.findBySubmissionId(10L)).thenReturn(List.of(presidente, vocal, secretario));
        when(tutorRepository.findBySubmissionId(10L)).thenReturn(Optional.of(tutorCompletado));
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(submission));
        when(scheduleRepository.findBySubmissionId(10L)).thenReturn(Optional.empty());
        when(roomRepository.findAll()).thenReturn(List.of());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> scheduleService.assignAutomatic(10L));
        assertTrue(ex.getMessage().contains("No hay salas disponibles"));
    }

    @Test
    void assignAutomaticEncuentraLaPrimeraFranjaFree() {
        when(panelistRepository.findBySubmissionId(10L)).thenReturn(List.of(presidente, vocal, secretario));
        when(tutorRepository.findBySubmissionId(10L)).thenReturn(Optional.of(tutorCompletado));
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(submission));
        when(scheduleRepository.findBySubmissionId(10L)).thenReturn(Optional.empty());
        when(roomRepository.findAll()).thenReturn(List.of(room));
        when(scheduleRepository.findConflictos(anyLong(), any(), any())).thenReturn(List.of());
        when(statusScheduleRepository.findByCode("PROGRAMADO"))
                .thenReturn(Optional.of(StatusSchedule.builder().code("PROGRAMADO").build()));
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> {
            Schedule c = inv.getArgument(0);
            c.setId(99L);
            return c;
        });

        Schedule result = scheduleService.assignAutomatic(10L);

        assertNotNull(result);
        assertEquals("PROGRAMADO", result.getStatus().getCode());
        assertFalse(result.getDateStart().getDayOfWeek().getValue() >= 6, "no debe caer en fin de semana");
    }

    @Test
    void assignAutomaticLanzaSiNoHayAvailabilityEn30Dias() {
        when(panelistRepository.findBySubmissionId(10L)).thenReturn(List.of(presidente, vocal, secretario));
        when(tutorRepository.findBySubmissionId(10L)).thenReturn(Optional.of(tutorCompletado));
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(submission));
        when(scheduleRepository.findBySubmissionId(10L)).thenReturn(Optional.empty());
        when(roomRepository.findAll()).thenReturn(List.of(room));
        // toda franja tiene conflicto -> nunca se libera un slot
        when(scheduleRepository.findConflictos(anyLong(), any(), any()))
                .thenReturn(List.of(Schedule.builder().id(1L).build()));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> scheduleService.assignAutomatic(10L));
        assertTrue(ex.getMessage().contains("No se encontró disponibilidad"));
    }

    @Test
    void assignAutomaticValidaPrerequisitesPrimero() {
        when(panelistRepository.findBySubmissionId(10L)).thenReturn(List.of(presidente, vocal)); // tribunal incompleto
        RuntimeException ex = assertThrows(RuntimeException.class, () -> scheduleService.assignAutomatic(10L));
        assertTrue(ex.getMessage().contains("tribunal no está completo"));
        verifyNoInteractions(roomRepository);
    }

    // ── estaDisponible / franjasDisponibles ──────────────────────────────────

    @Test
    void isAvailableEsFalsoSiHayConflicto() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 10, 9, 0);
        when(scheduleRepository.findConflictos(1L, start, start.plusMinutes(45)))
                .thenReturn(List.of(Schedule.builder().id(1L).build()));
        assertFalse(scheduleService.isAvailable(1L, start, 45));
    }

    @Test
    void isAvailableEsVerdaderoWithoutConflictos() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 10, 9, 0);
        when(scheduleRepository.findConflictos(1L, start, start.plusMinutes(45))).thenReturn(List.of());
        assertTrue(scheduleService.isAvailable(1L, start, 45));
    }

    @Test
    void slotsAvailableGeneraSlotsDe8a17WithLaDuracionIndicada() {
        List<LocalDateTime> slots = scheduleService.slotsAvailable(LocalDate.of(2026, 9, 10), 45);

        assertFalse(slots.isEmpty());
        assertEquals(LocalTime.of(8, 0), slots.get(0).toLocalTime());
        slots.forEach(f -> assertFalse(f.plusMinutes(45).toLocalTime().isAfter(LocalTime.of(17, 0))));
    }

    // ── delegados simples ────────────────────────────────────────────────────

    @Test
    void listSchedulesDelega() {
        org.springframework.data.domain.Pageable pageable = mock(org.springframework.data.domain.Pageable.class);
        org.springframework.data.domain.Page<Schedule> pagina = org.springframework.data.domain.Page.empty();
        when(scheduleRepository.findAll(pageable)).thenReturn(pagina);
        assertSame(pagina, scheduleService.listSchedules(pageable));
    }

    @Test
    void listByStudentDelega() {
        when(scheduleRepository.findByStudentId(5L)).thenReturn(List.of());
        assertTrue(scheduleService.listByStudent(5L).isEmpty());
    }

    @Test
    void listByAppUserDelega() {
        when(scheduleRepository.findByAppUserId(50L)).thenReturn(List.of());
        assertTrue(scheduleService.listByAppUser(50L).isEmpty());
    }

    @Test
    void searchBySubmissionDelega() {
        when(scheduleRepository.findBySubmissionId(10L)).thenReturn(Optional.empty());
        assertTrue(scheduleService.searchBySubmission(10L).isEmpty());
    }

    @Test
    void deleteDelega() {
        scheduleService.delete(5L);
        verify(scheduleRepository).deleteById(5L);
    }

    @Test
    void createScheduleNoPropagaFalloDeNotification() {
        when(panelistRepository.findBySubmissionId(10L)).thenReturn(List.of(presidente, vocal, secretario));
        when(tutorRepository.findBySubmissionId(10L)).thenReturn(Optional.of(tutorCompletado));
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(submission));
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(scheduleRepository.findConflictos(anyLong(), any(), any())).thenReturn(List.of());
        when(panelistRepository.validateConflictoPanelist(anyLong(), anyLong(), any(), anyInt(), isNull()))
                .thenReturn(Boolean.TRUE);
        when(statusScheduleRepository.findByCode("PROGRAMADO"))
                .thenReturn(Optional.of(StatusSchedule.builder().code("PROGRAMADO").build()));
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> inv.getArgument(0));
        // La submission del fixture no tiene student asociado: notifyProgramacion() falla
        // con NPE real al intentar leerlo, y esa excepcion debe quedar atrapada sin propagarse
        // (mismo efecto que un fallo real de notification, sin necesitar un mock adicional).

        assertDoesNotThrow(() ->
                scheduleService.createSchedule(10L, 1L, LocalDate.now().plusDays(5), LocalTime.of(9, 0)));
    }
}
