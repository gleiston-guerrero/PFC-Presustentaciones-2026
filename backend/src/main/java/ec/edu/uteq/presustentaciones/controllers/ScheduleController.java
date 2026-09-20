package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.entities.Schedule;
import ec.edu.uteq.presustentaciones.repositories.AppUserRepository;
import ec.edu.uteq.presustentaciones.security.service.SubmissionAccessService;
import ec.edu.uteq.presustentaciones.services.PermissionService;
import ec.edu.uteq.presustentaciones.services.ScheduleService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Controlador REST de schedule.
 */
@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/cronogramas")
public class ScheduleController {

    private static final String MANAGE_PERMISSION = "CRONOGRAMA_GESTIONAR";

    private final ScheduleService scheduleService;
    private final SubmissionAccessService submissionAccessService;
    private final AppUserRepository appUserRepository;
    private final PermissionService permissionService;

    /**
     * Construye ScheduleController, inyectando s, submissionAccessService, appUserRepository y permissionService.
     * @param s servicio de negocio de programaciones de sustentación, inyectado por constructor
     * @param submissionAccessService decide si el usuario puede acceder a una solicitud, inyectado por constructor
     * @param appUserRepository repositorio de usuarios, para identificar al usuario autenticado, inyectado por constructor
     * @param permissionService consulta de permisos del usuario autenticado, inyectado por constructor
     */
    public ScheduleController(ScheduleService s, SubmissionAccessService submissionAccessService,
                              AppUserRepository appUserRepository, PermissionService permissionService) {
        this.scheduleService = s;
        this.submissionAccessService = submissionAccessService;
        this.appUserRepository = appUserRepository;
        this.permissionService = permissionService;
    }

    /**
     * RF-04: Programa manualmente una defensa, validando contra sp_validate_conflicto_panelist
     * que ningún panelist ya asignado tenga otra defensa solapada en ese horario.
     *
     * @param submissionId submission que se va a programar
     * @param roomId      room donde se realizará la defensa
     * @param date       día de la defensa
     * @param hora        hora de inicio
     * @return 200 con el {@link Schedule} creado, o 400 con el motivo del rechazo si la
     *         room está ocupada o algún panelist tiene conflicto de horario
     */
    @PostMapping("/crear")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'CRONOGRAMA_GESTIONAR')")
    public ResponseEntity<?> create(@RequestParam(name = "solicitudId") Long submissionId,
                                   @RequestParam(name = "salaId") Long roomId,
                                   @RequestParam("fecha") LocalDate date,
                                   @RequestParam("hora") LocalTime hora) {
        try {
            return ResponseEntity.ok(scheduleService.createSchedule(submissionId, roomId, date, hora));
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
    @PreAuthorize("@permissionService.hasPermission(authentication, 'CRONOGRAMA_GESTIONAR')")
    public ResponseEntity<?> assignAutomatic(@PathVariable("submissionId") Long submissionId) {
        try {
            return ResponseEntity.ok(scheduleService.assignAutomatic(submissionId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * RF-04: Franjas horarias libres de un día, para poblar el selector del frontend.
     *
     * @param date    día consultado
     * @param duracion duración de cada franja en minutos (45 por defecto)
     * @return 200 con fecha, duracionMin y la lista de franjas libres
     */
    @GetMapping("/disponibilidad")
    public ResponseEntity<Map<String, Object>> availability(
            @RequestParam("fecha") LocalDate date,
            @RequestParam(name = "duracion", defaultValue = "45") int duracion) {
        List<LocalDateTime> slots = scheduleService.slotsAvailable(date, duracion);
        return ResponseEntity.ok(Map.of("fecha", date, "duracionMin", duracion, "franjas", slots));
    }

    /**
     * RF-04: Comprueba si una room concreta está libre en una franja.
     *
     * @param roomId   room consultada
     * @param start   inicio de la franja
     * @param duracion duración en minutos (45 por defecto)
     * @return 200 con el indicador de availability y un mensaje legible para la UI
     */
    @GetMapping("/verificar-disponibilidad")
    public ResponseEntity<Map<String, Object>> verifyAvailability(
            @RequestParam("roomId") Long roomId,
            @RequestParam("inicio") LocalDateTime start,
            @RequestParam(name = "duracion", defaultValue = "45") int duracion) {
        boolean available = scheduleService.isAvailable(roomId, start, duracion);
        return ResponseEntity.ok(Map.of("disponible", available,
                "mensaje", available ? "✓ Sala disponible en esa franja" : "✗ Sala ocupada en esa franja"));
    }

    /**
     * Lista de forma paginada los horarios programados.
     * @param pageable página y tamaño solicitados
     * @return 200 con la página de schedules programados
     */
    @GetMapping
    @PreAuthorize("@permissionService.hasPermission(authentication, 'CRONOGRAMA_GESTIONAR') or @permissionService.hasPermission(authentication, 'REPORTES_VER')")
    public ResponseEntity<Page<Schedule>> list(Pageable pageable) { return ResponseEntity.ok(scheduleService.listSchedules(pageable)); }

    /**
     * Devuelve los horarios de un estudiante concreto.
     * @param id identificador del perfil de student
     * @return schedules de ese student, vacío si aún no tiene defensa programada
     */
    @GetMapping("/estudiante/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'CRONOGRAMA_GESTIONAR')")
    public List<Schedule> byStudent(@PathVariable("id") Long id) { return scheduleService.listByStudent(id); }

    /**
     * Devuelve los horarios asociados a una cuenta de usuario.
     * @param id identificador del appUser autenticable
     * @return schedules asociados a ese appUser
     */
    @GetMapping("/usuario/{id}")
    public List<Schedule> byAppUser(@PathVariable("id") Long id) {
        validateOwnAccountOrManager(id);
        return scheduleService.listByAppUser(id);
    }

    /**
     * Devuelve el horario de defensa asociado a una solicitud.
     * @param id submission consultada
     * @return 200 con el schedule de la submission, o 404 si no tiene defensa programada
     */
    @GetMapping("/solicitud/{id}")
    public ResponseEntity<Schedule> bySubmission(@PathVariable("id") Long id) {
        String[] permisos = java.util.stream.Stream
                .concat(java.util.stream.Stream.of(MANAGE_PERMISSION),
                        java.util.Arrays.stream(SubmissionAccessService.PANEL_VIEW_PERMISSIONS))
                .toArray(String[]::new);
        submissionAccessService.validateAccessById(id, permisos);
        return scheduleService.searchBySubmission(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
    /**
     * Cancela una defensa programada liberando su franja y su room.
     *
     * @param id schedule a delete
     * @return 204 sin cuerpo
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'CRONOGRAMA_GESTIONAR')")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        scheduleService.delete(id); return ResponseEntity.noContent().build();
    }

    /**
     * Un usuario sólo puede pedir su propio calendario; el de otra persona exige gestionar el
     * cronograma. La identidad sale del token, no del id de la URL.
     *
     * @param appUserId usuario cuyo calendario se pide
     * @throws AccessDeniedException si no es el usuario autenticado y no gestiona el cronograma
     */
    private void validateOwnAccountOrManager(Long appUserId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Usuario no autenticado");
        }
        boolean esAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (esAdmin || permissionService.hasPermission(auth, MANAGE_PERMISSION)) {
            return;
        }
        boolean esPropio = appUserRepository.findByEmail(auth.getName())
                .map(AppUser::getId).filter(appUserId::equals).isPresent();
        if (!esPropio) {
            throw new AccessDeniedException("Solo puedes consultar tu propio calendario");
        }
    }
}
