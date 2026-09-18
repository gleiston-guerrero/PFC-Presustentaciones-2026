package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.entities.Submission;
import ec.edu.uteq.presustentaciones.repositories.AppUserRepository;
import ec.edu.uteq.presustentaciones.services.PermissionService;
import ec.edu.uteq.presustentaciones.services.SubmissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import ec.edu.uteq.presustentaciones.dto.ResponseWrapper;

@Slf4j
@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/solicitudes")
public class SubmissionController {

    private final SubmissionService submissionService;
    private final AppUserRepository appUserRepository;
    private final PermissionService permissionService;

    /**
     * Construye SubmissionController, inyectando submissionService, appUserRepository, permissionService.
     * @param submissionService submissionService
     * @param appUserRepository appUserRepository
     * @param permissionService permissionService
     */
    public SubmissionController(SubmissionService submissionService, AppUserRepository appUserRepository,
                               PermissionService permissionService) {
        this.submissionService = submissionService;
        this.appUserRepository = appUserRepository;
        this.permissionService = permissionService;
    }

    /**
     * Crea una submission en nombre de un student (uso administrativo).
     *
     * @param studentId perfil de student al que pertenecerá la submission
     * @param datos        cuerpo de la submission (topic, modality, línea, área)
     * @return 200 con la submission creada, o 400 con el motivo del rechazo
     */
    @PostMapping("/crear/{studentId}")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'SOLICITUDES_REVISAR')")
    public ResponseEntity<?> create(@PathVariable("studentId") Long studentId, @RequestBody Submission datos) {
        try {
            return ResponseEntity.ok(ResponseWrapper.success(submissionService.createSubmission(studentId, datos), "Solicitud creada exitosamente"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * Crea la submission del propio student autenticado. El backend resuelve el appUser
     * desde el JWT e ignora deliberadamente el appUserId del path, de modo que un cliente
     * no puede create submissions a nombre de otra persona.
     *
     * @param appUserId ignorado; se conserva en la ruta por compatibilidad del frontend
     * @param datos     cuerpo de la submission (topic, modality, línea, área)
     * @return 200 con la submission creada, o 400 si el appUser del token no existe o el
     *         servicio rechaza la creación
     */
    @PostMapping("/crear-por-usuario/{appUserId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> createPorAppUser(@PathVariable("appUserId") Long appUserId, @RequestBody Submission datos) {
        try {
            // Obtain email desde el JWT (más seguro que el id del path)
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String email = auth.getName();
            Long realAppUserId = appUserRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado en el sistema"))
                    .getId();
            return ResponseEntity.ok(ResponseWrapper.success(submissionService.createSubmissionPorAppUser(realAppUserId, datos), "Solicitud creada exitosamente"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * Lista las submissions del appUser autenticado, resolviendo su identidad desde el JWT
     * sin depender de ningún parámetro del cliente.
     *
     * @return 200 con las submissions propias; si la resolución del appUser falla devuelve
     *         200 con lista vacía en vez de un error, para no romper la pantalla del student
     */
    @GetMapping("/mis-solicitudes")
    public ResponseEntity<?> listMisSubmissions() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String email = auth.getName(); // el subject del JWT es el email
            Long appUserId = appUserRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"))
                    .getId();
            return ResponseEntity.ok(ResponseWrapper.success(submissionService.listPorAppUser(appUserId)));
        } catch (RuntimeException e) {
            log.error("Error al listar mis-solicitudes: {}", e.getMessage(), e);
            return ResponseEntity.ok(ResponseWrapper.success(java.util.List.of()));
        }
    }

    /**
     * Lista las submissions de un appUser por id. Se mantiene por compatibilidad con la
     * versión anterior del frontend.
     *
     * @param appUserId appUser cuyas submissions se consultan
     * @return 200 con las submissions, o 200 con lista vacía si el servicio falla
     */
    @PreAuthorize("@permissionService.tienePermission(authentication, 'SOLICITUDES_REVISAR')")
    public ResponseEntity<?> listPorAppUser(@PathVariable("appUserId") Long appUserId) {
        try {
            return ResponseEntity.ok(ResponseWrapper.success(submissionService.listPorAppUser(appUserId)));
        } catch (RuntimeException e) {
            log.error("Error al listar solicitudes por usuario {}: {}", appUserId, e.getMessage(), e);
            return ResponseEntity.ok(ResponseWrapper.success(java.util.List.of()));
        }
    }

    /**
     * Envía la submission a revisión. Exige ser el propietario de la submission o tener
     * permission de revisión.
     *
     * @param id submission a send
     * @return 200 con la submission actualizada, o 400 si no es propietario o la transición
     *         de estado no es válida
     */
    @PostMapping("/enviar/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> send(@PathVariable("id") Long id) {
        try {
            // Verify propiedad o permission
            validateAccesoSubmission(id);
            return ResponseEntity.ok(ResponseWrapper.success(submissionService.sendSubmission(id), "Solicitud enviada a revisión"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * @param id submission a approve
     * @return 200 con la submission aprobada, o 400 si no está en un estado que lo permita
     */
    @PostMapping("/aprobar/{id}")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'SOLICITUDES_REVISAR')")
    public ResponseEntity<?> approve(@PathVariable("id") Long id) {
        try {
            return ResponseEntity.ok(ResponseWrapper.success(submissionService.approveSubmission(id), "Solicitud aprobada"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * @param id submission a reject
     * @return 200 con la submission rechazada, o 400 si la transición no es válida
     */
    @PostMapping("/rechazar/{id}")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'SOLICITUDES_REVISAR')")
    public ResponseEntity<?> reject(@PathVariable("id") Long id) {
        try {
            return ResponseEntity.ok(ResponseWrapper.success(submissionService.rejectSubmission(id), "Solicitud rechazada"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * Rechaza la submission dejando constancia del motivo para el student.
     *
     * @param id   submission a reject
     * @param body cuerpo con la clave "observacion"; si falta se registra cadena vacía
     * @return 200 con la submission rechazada, o 400 si la transición no es válida
     */
    @PostMapping("/rechazar-con-observacion/{id}")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'SOLICITUDES_REVISAR')")
    public ResponseEntity<?> rejectConObservacion(
            @PathVariable("id") Long id,
            @RequestBody java.util.Map<String, String> body) {
        try {
            String observacion = body.getOrDefault("observacion", "");
            return ResponseEntity.ok(ResponseWrapper.success(submissionService.rejectConObservacion(id, observacion), "Solicitud rechazada con observaciones"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * Suspende una submission en curso (por ejemplo, si el student se retira del período).
     *
     * @param id   submission a suspender
     * @param body cuerpo con la clave "motivo"
     * @return 200 con la submission suspendida, o 400 si falta el motivo o la transición
     *         no es válida
     */
    @PostMapping("/suspender/{id}")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'SOLICITUDES_SUSPENDER')")
    public ResponseEntity<?> suspender(
            @PathVariable("id") Long id,
            @RequestBody Map<String, String> body) {
        try {
            String motivo = body.get("motivo");
            return ResponseEntity.ok(ResponseWrapper.success(submissionService.suspenderSubmission(id, motivo), "Solicitud suspendida"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * Listado completo sin paginar. ADMIN, COORDINADOR y DOCENTE pueden ver todas las
     * submissions; para volúmenes grandes conviene usar /paginado.
     *
     * @return 200 con todas las submissions
     */
    @GetMapping
    @PreAuthorize("@permissionService.tienePermission(authentication, 'SOLICITUDES_REVISAR')")
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(ResponseWrapper.success(submissionService.listSubmissions()));
    }

    /**
     * Versión paginada de {@link #list()} — evita load miles de filas de una sola vez,
     * que es lo que hacía colapsar la tabla de "Gestionar Solicitudes" en el frontend.
     * ERR-01: agrega búsqueda de texto libre (parámetro "q") combinable con el filtro de
     * estado, mismo patrón que GET /api/v1/appUsers/paginado.
     *
     * @param page        número de página, base 0
     * @param size        tamaño de página
     * @param estado      código de estado a filtrar, o {@code null} para no filtrar
     * @param q           texto libre de búsqueda, o {@code null} para no filtrar
     * @param fechaDesde  fecha mínima a incluir, o {@code null} para no acotar
     * @param fechaHasta  fecha máxima a incluir, o {@code null} para no acotar
     * @return 200 con la página de submissions y sus metadatos de paginación
     */
    @GetMapping("/paginado")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'SOLICITUDES_REVISAR')")
    public ResponseEntity<?> listPaginado(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate fechaDesde,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate fechaHasta) {
        Page<Submission> resultado = submissionService.listSubmissionsPaginado(page, size, estado, q, fechaDesde, fechaHasta);
        return ResponseEntity.ok(ResponseWrapper.success(Map.of(
                "content", resultado.getContent(),
                "totalElements", resultado.getTotalElements(),
                "totalPages", resultado.getTotalPages(),
                "page", resultado.getNumber(),
                "size", resultado.getSize()
        )));
    }

    /**
     * Count de submissions agrupadas por estado, para los contadores de las pestañas de
     * filtro del frontend.
     *
     * @return 200 con un mapa estado a cantidad
     */
    @GetMapping("/contar-por-estado")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'SOLICITUDES_REVISAR')")
    public ResponseEntity<?> countPorEstado() {
        return ResponseEntity.ok(ResponseWrapper.success(submissionService.countPorEstado()));
    }

    /**
     * @param studentId perfil de student consultado
     * @return 200 con las submissions de ese student
     */
    @GetMapping("/estudiante/{studentId}")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'SOLICITUDES_REVISAR')")
    public ResponseEntity<?> listPorStudent(@PathVariable("studentId") Long studentId) {
        return ResponseEntity.ok(ResponseWrapper.success(submissionService.listPorStudent(studentId)));
    }

    /**
     * Detalle de una submission. Un student sólo puede open la suya; quien tiene permission
     * de revisión (o es ADMIN) puede open cualquiera.
     *
     * @param id submission consultada
     * @return 200 con la submission, 404 si no existe, o 403 si no es propietario ni revisor
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> obtain(@PathVariable("id") Long id) {
        try {
            validateAccesoSubmission(id);
            return submissionService.obtainPorId(id)
                    .map(s -> ResponseEntity.ok(ResponseWrapper.success(s)))
                    .orElse(ResponseEntity.status(404).body(ResponseWrapper.error("Solicitud no encontrada")));
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * SP (Fase 3): Reporte consolidado de defensas por program.
     * Llama a presus.sp_generate_reporte_defensas(p_program)
     * Flujo: GET → SubmissionController → SubmissionService → SubmissionRepository → SP → PostgreSQL
     *
     * @param program nombre o parte del nombre de la program (búsqueda ILIKE); vacío
     *                devuelve todas las programs
     * @return 200 con las filas del reporte que devuelve el procedimiento, o 400 con el
     *         error si el cursor del procedimiento falla
     */
    @GetMapping("/reporte-defensas")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'SOLICITUDES_REVISAR')")
    public ResponseEntity<?> reporteDefensas(
            @RequestParam(name = "program", defaultValue = "") String program) {
        try {
            List<Map<String, Object>> reporte = submissionService.generateReporteDefensasSP(program);
            return ResponseEntity.ok(ResponseWrapper.success(reporte));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * Línea de tiempo del trámite (estados por los que pasó la submission). Exige la misma
     * comprobación de propiedad que el detalle.
     *
     * @param id submission consultada
     * @return 200 con el tracking, o 403 si no es propietario ni revisor
     */
    @GetMapping("/{id}/seguimiento")
    public ResponseEntity<?> obtainTracking(@PathVariable("id") Long id) {
        try {
            validateAccesoSubmission(id);
            return ResponseEntity.ok(ResponseWrapper.success(submissionService.obtainTracking(id)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * Valida que el appUser actual tenga permission de revisión (o sea ADMIN) o bien sea el
     * propietario de la submission.
     *
     * @param submissionId submission sobre la que se comprueba el acceso
     * @throws RuntimeException si la submission no existe o si el appUser autenticado no es
     *                          revisor ni propietario
     */
    private void validateAccesoSubmission(Long submissionId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        // CustomUserDetailsService solo carga "ROLE_<rol>" como authority, nunca los permissions
        // finos. Por eso no se puede comprobar "SOLICITUDES_REVISAR" contra getAuthorities():
        // un COORDINADOR (que sí tiene el permission en role_permissions) daba 403 "no eres
        // propietario". Se usa el mismo permissionService que protege el resto del controlador.
        boolean esRevisor = auth.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))
                || permissionService.tienePermission(auth, "SOLICITUDES_REVISAR");

        if (!esRevisor) {
            Submission submission = submissionService.obtainPorId(submissionId)
                    .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));
            if (!submission.getStudent().getAppUser().getEmail().equals(email)) {
                throw new RuntimeException("Acceso denegado: no eres propietario de esta solicitud");
            }
        }
    }
}