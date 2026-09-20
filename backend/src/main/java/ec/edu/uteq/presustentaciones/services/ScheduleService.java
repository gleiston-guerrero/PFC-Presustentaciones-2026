package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.Schedule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Contrato. Servicio de schedule.
 */
public interface ScheduleService {

    /**
     * Programa la defensa de una submission en una room y horario específicos, validando
     * primero que el tribunal esté completo y la tutoría completada, y luego que ningún
     * panelist tenga conflicto de horario (vía {@code sp_validate_conflicto_panelist}).
     *
     * @param submissionId id de la submission a programar
     * @param roomId      id de la room donde se realizará la defensa
     * @param date       fecha de la defensa
     * @param hora        hora de inicio de la defensa
     * @return el schedule creado, en estado "PROGRAMADO"
     * @throws RuntimeException si el tribunal no está completo, la tutoría no está
     *                          completada, o algún panelist tiene conflicto de horario
     */
    Schedule createSchedule(Long submissionId, Long roomId, LocalDate date, LocalTime hora);

    /** RF-04: Asignación automática sin conflictos
     * @param submissionId id de la submission a programar
     * @return el schedule creado con la primera room/franja libre encontrada
     * @throws RuntimeException si no hay ninguna combinación de room/franja disponible
     */
    Schedule assignAutomatic(Long submissionId);

    /**
     * List schedules.
     * @param pageable configuración de paginación
     * @return página de todos los schedules del sistema
     */
    Page<Schedule> listSchedules(Pageable pageable);

    /**
     * List by student.
     * @param studentId identificador del estudiante
     * @return los schedules de las submissions de ese student
     */
    List<Schedule> listByStudent(Long studentId);

    /**
     * List by app user.
     * @param appUserId id del appUser autenticado
     * @return los schedules visibles para ese appUser (como student o como panelist/tutor)
     */
    List<Schedule> listByAppUser(Long appUserId);

    /**
     * Search by submission.
     * @param submissionId identificador de la solicitud de pre-sustentación
     * @return el schedule de esa submission, si ya fue programada
     */
    Optional<Schedule> searchBySubmission(Long submissionId);

    /**
     * Elimina los registros.
     * @param id id del schedule a delete
     */
    void delete(Long id);

    /** RF-04: Verify availability de room en franja horaria
     * @param roomId      id de la room a verify
     * @param start      instante de inicio de la franja propuesta
     * @param duracionMin duración de la defensa en minutos
     * @return {@code true} si la room está libre en toda esa franja, {@code false} si se
     *         solapa con otro schedule ya programado
     */
    boolean isAvailable(Long roomId, java.time.LocalDateTime start, int duracionMin);

    /** RF-04: Franjas libres para una fecha
     * @param date       fecha sobre la que search franjas libres
     * @param duracionMin duración de la defensa en minutos
     * @return lista de instantes de inicio disponibles en cualquier room esa fecha
     */
    List<java.time.LocalDateTime> slotsAvailable(LocalDate date, int duracionMin);
}
