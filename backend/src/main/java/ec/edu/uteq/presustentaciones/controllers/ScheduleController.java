package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.entities.Schedule;
import ec.edu.uteq.presustentaciones.services.ScheduleService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/cronogramas")
public class ScheduleController {

    private final ScheduleService scheduleService;
    /**
     * Construye ScheduleController, inyectando s.
     * @param s s
     */
    public ScheduleController(ScheduleService s) { this.scheduleService = s; }

    /**
     * RF-04: Programa manualmente una defensa, validando contra sp_validate_conflicto_panelist
     * que ningún panelist ya asignado tenga otra defensa solapada en ese horario.
     *
     * @param submissionId submission que se va a programar
     * @param roomId      room donde se realizará la defensa
     * @param fecha       día de la defensa
     * @param hora        hora de inicio
     * @return 200 con el {@link Schedule} creado, o 400 con el motivo del rechazo si la
     *         room está ocupada o algún panelist tiene conflicto de horario
     */
    @PostMapping("/crear")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'CRONOGRAMA_GESTIONAR')")
    public ResponseEntity<?> create(@RequestParam Long submissionId,
                                   @RequestParam Long roomId,
                                   @RequestParam LocalDate fecha,
                                   @RequestParam LocalTime hora) {
        try {
            return ResponseEntity.ok(scheduleService.createSchedule(submissionId, roomId, fecha, hora));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * RF-04: Busca la primera franja libre sin conflictos y programa la defensa ahí.
     *
     * @param submissionId submission que se va a programar
     * @return 200 con el {@link Schedule} creado, o 400 con el motivo si no queda
     *         ninguna franja disponible
     */
    @PostMapping("/auto/{submissionId}")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'CRONOGRAMA_GESTIONAR')")
    public ResponseEntity<?> assignAutomatico(@PathVariable Long submissionId) {
        try {
            return ResponseEntity.ok(scheduleService.assignAutomatico(submissionId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * RF-04: Franjas horarias libres de un día, para poblar el selector del frontend.
     *
     * @param fecha    día consultado
     * @param duracion duración de cada franja en minutos (45 por defecto)
     * @return 200 con fecha, duracionMin y la lista de franjas libres
     */
    @GetMapping("/disponibilidad")
    public ResponseEntity<Map<String, Object>> availability(
            @RequestParam LocalDate fecha,
            @RequestParam(defaultValue = "45") int duracion) {
        List<LocalDateTime> franjas = scheduleService.franjasDisponibles(fecha, duracion);
        return ResponseEntity.ok(Map.of("fecha", fecha, "duracionMin", duracion, "franjas", franjas));
    }

    /**
     * RF-04: Comprueba si una room concreta está libre en una franja.
     *
     * @param roomId   room consultada
     * @param inicio   inicio de la franja
     * @param duracion duración en minutos (45 por defecto)
     * @return 200 con el indicador de availability y un mensaje legible para la UI
     */
    @GetMapping("/verificar-disponibilidad")
    public ResponseEntity<Map<String, Object>> verifyAvailability(
            @RequestParam Long roomId,
            @RequestParam LocalDateTime inicio,
            @RequestParam(defaultValue = "45") int duracion) {
        boolean disponible = scheduleService.estaDisponible(roomId, inicio, duracion);
        return ResponseEntity.ok(Map.of("disponible", disponible,
                "mensaje", disponible ? "✓ Sala disponible en esa franja" : "✗ Sala ocupada en esa franja"));
    }

    /**
     * @param pageable página y tamaño solicitados
     * @return 200 con la página de schedules programados
     */
    @GetMapping public ResponseEntity<Page<Schedule>> list(Pageable pageable) { return ResponseEntity.ok(scheduleService.listSchedules(pageable)); }

    /**
     * @param id identificador del perfil de student
     * @return schedules de ese student, vacío si aún no tiene defensa programada
     */
    @GetMapping("/estudiante/{id}") public List<Schedule> porStudent(@PathVariable Long id) { return scheduleService.listPorStudent(id); }

    /**
     * @param id identificador del appUser autenticable
     * @return schedules asociados a ese appUser
     */
    @GetMapping("/usuario/{id}") public List<Schedule> porAppUser(@PathVariable Long id) { return scheduleService.listPorAppUser(id); }

    /**
     * @param id submission consultada
     * @return 200 con el schedule de la submission, o 404 si no tiene defensa programada
     */
    @GetMapping("/solicitud/{id}") public ResponseEntity<Schedule> porSubmission(@PathVariable Long id) {
        return scheduleService.searchPorSubmission(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
    /**
     * Cancela una defensa programada liberando su franja y su room.
     *
     * @param id schedule a delete
     * @return 204 sin cuerpo
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'CRONOGRAMA_GESTIONAR')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        scheduleService.delete(id); return ResponseEntity.noContent().build();
    }
}
