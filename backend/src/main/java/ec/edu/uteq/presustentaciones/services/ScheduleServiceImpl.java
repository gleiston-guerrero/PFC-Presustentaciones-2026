package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.Schedule;
import ec.edu.uteq.presustentaciones.entities.Panelist;
import ec.edu.uteq.presustentaciones.entities.Room;
import ec.edu.uteq.presustentaciones.entities.Submission;
import ec.edu.uteq.presustentaciones.entities.Tutor;
import ec.edu.uteq.presustentaciones.repositories.ScheduleRepository;
import ec.edu.uteq.presustentaciones.repositories.PanelistRepository;
import ec.edu.uteq.presustentaciones.repositories.RoomRepository;
import ec.edu.uteq.presustentaciones.repositories.SubmissionRepository;
import ec.edu.uteq.presustentaciones.repositories.TutorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleServiceImpl implements ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final SubmissionRepository submissionRepository;
    private final RoomRepository roomRepository;
    private final PanelistRepository panelistRepository;
    private final TutorRepository tutorRepository;
    private final NotificationService notificationService;
    private final ec.edu.uteq.presustentaciones.repositories.EstadoScheduleRepository estadoScheduleRepository;

    private static final LocalTime HORA_INICIO = LocalTime.of(8, 0);
    private static final LocalTime HORA_FIN    = LocalTime.of(17, 0);
    private static final int DURACION = 45;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm");

    /**
     * Programa la defensa de una submission en una room y horario específicos, validando
     * primero que el tribunal esté completo y la tutoría completada, y luego que ningún
     * panelist tenga conflicto de horario (vía {@code sp_validate_conflicto_panelist}).
     *
     * @param submissionId id de la submission a programar
     * @param roomId      id de la room donde se realizará la defensa
     * @param fecha       fecha de la defensa
     * @param hora        hora de inicio de la defensa
     * @return el schedule creado, en estado "PROGRAMADO"
     * @throws RuntimeException si el tribunal no está completo, la tutoría no está
     *                          completada, o algún panelist tiene conflicto de horario
     */
    @Override
    @Transactional
    public Schedule createSchedule(Long submissionId, Long roomId, LocalDate fecha, LocalTime hora) {
        validatePrerequisitosParaSchedule(submissionId);
 
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Sala no encontrada"));
 
        LocalDateTime inicio = LocalDateTime.of(fecha, hora);
        LocalDateTime fin = inicio.plusMinutes(DURACION);
 
        List<Schedule> conflictos = scheduleRepository.findConflictos(roomId, inicio, fin);
        if (!conflictos.isEmpty()) {
            throw new RuntimeException(
                    "Conflicto de horario: la sala '" + room.getNombre() +
                            "' ya tiene una pre-sustentación programada en esa franja.");
        }

        // sp_validate_conflicto_panelist (Fase 3 / Criterio P1, categoría "validaciones
        // cruzadas"): ningún teacher ya asignado como panelist de esta submission puede quedar
        // programado en dos defensas cuyos horarios se solapen.
        for (Panelist panelist : panelistRepository.findBySubmissionId(submissionId)) {
            Boolean disponible = panelistRepository.validateConflictoPanelist(
                    submissionId, panelist.getTeacher().getId(), inicio, DURACION, null);
            if (Boolean.FALSE.equals(disponible)) {
                throw new RuntimeException(
                        "Conflicto de horario: el docente " + panelist.getTeacher().getAppUser().getNombre() +
                                " " + panelist.getTeacher().getAppUser().getApellido() +
                                " ya es jurado de otra defensa programada en esa franja.");
            }
        }

        ec.edu.uteq.presustentaciones.entities.EstadoSchedule estadoProgramado = estadoScheduleRepository.findByCodigo("PROGRAMADO")
                .orElseGet(() -> estadoScheduleRepository.save(ec.edu.uteq.presustentaciones.entities.EstadoSchedule.builder()
                        .codigo("PROGRAMADO").nombre("Programado").build()));

        Schedule schedule = scheduleRepository.save(Schedule.builder()
                .submission(submission).room(room)
                .announcement(submission.getAnnouncement())
                .fechaInicio(inicio).duracionMin(DURACION).estado(estadoProgramado).build());
 
        notifyProgramacion(schedule);
        return schedule;
    }
 
    /**
     * RF-04: Asignación automática sin conflictos.
     *
     * @param submissionId id de la submission a programar
     * @return el schedule creado con la primera room/franja libre encontrada
     * @throws RuntimeException si no hay ninguna combinación de room/franja disponible
     */
    @Override
    @Transactional
    public Schedule assignAutomatico(Long submissionId) {
        validatePrerequisitosParaSchedule(submissionId);
 
        submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));
 
        Optional<Schedule> existente = scheduleRepository.findBySubmissionId(submissionId);
        if (existente.isPresent() && existente.get().getEstado() != null && "PROGRAMADO".equals(existente.get().getEstado().getCodigo())) {
            return existente.get();
        }
 
        List<Room> rooms = roomRepository.findAll().stream()
                .filter(s -> Boolean.TRUE.equals(s.getDisponible())).toList();
        if (rooms.isEmpty()) throw new RuntimeException("No hay salas disponibles.");
 
        for (int diasAdelantar = 1; diasAdelantar <= 30; diasAdelantar++) {
            LocalDate fecha = LocalDate.now().plusDays(diasAdelantar);
            if (fecha.getDayOfWeek().getValue() >= 6) continue;
 
            List<LocalDateTime> franjas = franjasDisponibles(fecha, DURACION);
            for (LocalDateTime franja : franjas) {
                for (Room room : rooms) {
                    List<Schedule> conflictos = scheduleRepository
                            .findConflictos(room.getId(), franja, franja.plusMinutes(DURACION));
                    if (conflictos.isEmpty()) {
                        Submission submission = submissionRepository.findById(submissionId).get();
                        
                        ec.edu.uteq.presustentaciones.entities.EstadoSchedule estadoProgramado = estadoScheduleRepository.findByCodigo("PROGRAMADO")
                                .orElseGet(() -> estadoScheduleRepository.save(ec.edu.uteq.presustentaciones.entities.EstadoSchedule.builder()
                                        .codigo("PROGRAMADO").nombre("Programado").build()));

                        Schedule schedule = scheduleRepository.save(Schedule.builder()
                                .submission(submission).room(room)
                                .announcement(submission.getAnnouncement())
                                .fechaInicio(franja).duracionMin(DURACION).estado(estadoProgramado)
                                .build());
                        notifyProgramacion(schedule);
                        return schedule;
                    }
                }
            }
        }
        throw new RuntimeException(
                "No se encontró disponibilidad en los próximos 30 días. Verifique las salas o el calendario.");
    }
 
    private void validatePrerequisitosParaSchedule(Long submissionId) {
        // 1. Tribunal completo: los 3 roles deben estar asignados
        List<String> rolesAsignados = panelistRepository.findBySubmissionId(submissionId)
                .stream().map(Panelist::getRole).toList();
        boolean tribunalCompleto = rolesAsignados.contains("PRESIDENTE")
                && rolesAsignados.contains("VOCAL_1")
                && rolesAsignados.contains("VOCAL_2");
        if (!tribunalCompleto) {
            throw new RuntimeException(
                    "No se puede programar la presentación: el tribunal no está completo. " +
                    "Se requieren Presidente, Vocal 1 y Vocal 2.");
        }

        // 2. Tutoría COMPLETADA
        Tutor tutor = tutorRepository.findBySubmissionId(submissionId)
                .orElseThrow(() -> new RuntimeException(
                        "No se puede programar la presentación: la tutoría no ha sido completada."));
        if (!"COMPLETADA".equals(tutor.getEstado())) {
            throw new RuntimeException(
                    "No se puede programar la presentación: la tutoría no ha sido completada.");
        }
    }

    /** Notifica al student y a los panelists asignados cuando se programa la exposición */
    private void notifyProgramacion(Schedule c) {
        try {
            Submission s = c.getSubmission();
            String fechaStr = c.getFechaInicio().format(FMT);
            String room     = c.getRoom().getNombre();
            String titulo   = s.getTituloTopic();

            // Notify al student
            Long studentAppUserId = s.getStudent().getAppUser().getId();
            notificationService.createNotification(studentAppUserId,
                    String.format("📅 Tu pre-sustentación \"%s\" ha sido programada para el %s en la sala %s. " +
                            "Duración estimada: %d minutos.", titulo, fechaStr, room, c.getDuracionMin()));
        } catch (Exception e) {
            log.warn("No se pudo enviar notificación de programación: {}", e.getMessage());
        }
    }

    /**
     * RF-04: Verify availability de room en franja horaria.
     *
     * @param roomId      id de la room a verify
     * @param inicio      instante de inicio de la franja propuesta
     * @param duracionMin duración de la defensa en minutos
     * @return {@code true} si la room está libre en toda esa franja, {@code false} si se
     *         solapa con otro schedule ya programado
     */
    @Override
    public boolean estaDisponible(Long roomId, LocalDateTime inicio, int duracionMin) {
        return scheduleRepository.findConflictos(roomId, inicio, inicio.plusMinutes(duracionMin)).isEmpty();
    }

    /**
     * RF-04: Franjas libres para una fecha.
     *
     * @param fecha       fecha sobre la que search franjas libres
     * @param duracionMin duración de la defensa en minutos
     * @return lista de instantes de inicio disponibles en cualquier room esa fecha
     */
    @Override
    public List<LocalDateTime> franjasDisponibles(LocalDate fecha, int duracionMin) {
        List<LocalDateTime> franjas = new ArrayList<>();
        LocalDateTime cursor = LocalDateTime.of(fecha, HORA_INICIO);
        LocalDateTime limite = LocalDateTime.of(fecha, HORA_FIN);
        while (!cursor.plusMinutes(duracionMin).isAfter(limite)) {
            franjas.add(cursor);
            cursor = cursor.plusMinutes(duracionMin);
        }
        return franjas;
    }

    /**
     * @param pageable configuración de paginación
     * @return página de todos los schedules del sistema
     */
    @Override public Page<Schedule> listSchedules(Pageable pageable) { return scheduleRepository.findAll(pageable); }

    /**
     * @param id id del student
     * @return los schedules de las submissions de ese student
     */
    @Override public List<Schedule> listPorStudent(Long id) { return scheduleRepository.findByStudentId(id); }

    /**
     * @param id id del appUser autenticado
     * @return los schedules visibles para ese appUser (como student o como panelist/tutor)
     */
    @Override public List<Schedule> listPorAppUser(Long id) { return scheduleRepository.findByAppUserId(id); }

    /**
     * @param id id de la submission
     * @return el schedule de esa submission, si ya fue programada
     */
    @Override public Optional<Schedule> searchPorSubmission(Long id) { return scheduleRepository.findBySubmissionId(id); }

    /** @param id id del schedule a delete */
    @Override public void delete(Long id) { scheduleRepository.deleteById(id); }
}