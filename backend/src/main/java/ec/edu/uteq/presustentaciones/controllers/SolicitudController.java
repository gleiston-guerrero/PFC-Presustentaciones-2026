package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.entities.Solicitud;
import ec.edu.uteq.presustentaciones.repositories.UsuarioRepository;
import ec.edu.uteq.presustentaciones.services.PermisoService;
import ec.edu.uteq.presustentaciones.services.SolicitudService;
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
public class SolicitudController {

    private final SolicitudService solicitudService;
    private final UsuarioRepository usuarioRepository;
    private final PermisoService permisoService;

    public SolicitudController(SolicitudService solicitudService, UsuarioRepository usuarioRepository,
                               PermisoService permisoService) {
        this.solicitudService = solicitudService;
        this.usuarioRepository = usuarioRepository;
        this.permisoService = permisoService;
    }

    /**
     * Crea una solicitud en nombre de un estudiante (uso administrativo).
     *
     * @param estudianteId perfil de estudiante al que pertenecerá la solicitud
     * @param datos        cuerpo de la solicitud (tema, modalidad, línea, área)
     * @return 200 con la solicitud creada, o 400 con el motivo del rechazo
     */
    @PostMapping("/crear/{estudianteId}")
    @PreAuthorize("@permisoService.tienePermiso(authentication, 'SOLICITUDES_REVISAR')")
    public ResponseEntity<?> crear(@PathVariable Long estudianteId, @RequestBody Solicitud datos) {
        try {
            return ResponseEntity.ok(ResponseWrapper.success(solicitudService.crearSolicitud(estudianteId, datos), "Solicitud creada exitosamente"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * Crea la solicitud del propio estudiante autenticado. El backend resuelve el usuario
     * desde el JWT e ignora deliberadamente el usuarioId del path, de modo que un cliente
     * no puede crear solicitudes a nombre de otra persona.
     *
     * @param usuarioId ignorado; se conserva en la ruta por compatibilidad del frontend
     * @param datos     cuerpo de la solicitud (tema, modalidad, línea, área)
     * @return 200 con la solicitud creada, o 400 si el usuario del token no existe o el
     *         servicio rechaza la creación
     */
    @PostMapping("/crear-por-usuario/{usuarioId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> crearPorUsuario(@PathVariable Long usuarioId, @RequestBody Solicitud datos) {
        try {
            // Obtener email desde el JWT (más seguro que el id del path)
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String email = auth.getName();
            Long realUsuarioId = usuarioRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado en el sistema"))
                    .getId();
            return ResponseEntity.ok(ResponseWrapper.success(solicitudService.crearSolicitudPorUsuario(realUsuarioId, datos), "Solicitud creada exitosamente"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * Lista las solicitudes del usuario autenticado, resolviendo su identidad desde el JWT
     * sin depender de ningún parámetro del cliente.
     *
     * @return 200 con las solicitudes propias; si la resolución del usuario falla devuelve
     *         200 con lista vacía en vez de un error, para no romper la pantalla del estudiante
     */
    @GetMapping("/mis-solicitudes")
    public ResponseEntity<?> listarMisSolicitudes() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String email = auth.getName(); // el subject del JWT es el email
            Long usuarioId = usuarioRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"))
                    .getId();
            return ResponseEntity.ok(ResponseWrapper.success(solicitudService.listarPorUsuario(usuarioId)));
        } catch (RuntimeException e) {
            log.error("Error al listar mis-solicitudes: {}", e.getMessage(), e);
            return ResponseEntity.ok(ResponseWrapper.success(java.util.List.of()));
        }
    }

    /**
     * Lista las solicitudes de un usuario por id. Se mantiene por compatibilidad con la
     * versión anterior del frontend.
     *
     * @param usuarioId usuario cuyas solicitudes se consultan
     * @return 200 con las solicitudes, o 200 con lista vacía si el servicio falla
     */
    @PreAuthorize("@permisoService.tienePermiso(authentication, 'SOLICITUDES_REVISAR')")
    public ResponseEntity<?> listarPorUsuario(@PathVariable Long usuarioId) {
        try {
            return ResponseEntity.ok(ResponseWrapper.success(solicitudService.listarPorUsuario(usuarioId)));
        } catch (RuntimeException e) {
            log.error("Error al listar solicitudes por usuario {}: {}", usuarioId, e.getMessage(), e);
            return ResponseEntity.ok(ResponseWrapper.success(java.util.List.of()));
        }
    }

    /**
     * Envía la solicitud a revisión. Exige ser el propietario de la solicitud o tener
     * permiso de revisión.
     *
     * @param id solicitud a enviar
     * @return 200 con la solicitud actualizada, o 400 si no es propietario o la transición
     *         de estado no es válida
     */
    @PostMapping("/enviar/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> enviar(@PathVariable Long id) {
        try {
            // Verificar propiedad o permiso
            validarAccesoSolicitud(id);
            return ResponseEntity.ok(ResponseWrapper.success(solicitudService.enviarSolicitud(id), "Solicitud enviada a revisión"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * @param id solicitud a aprobar
     * @return 200 con la solicitud aprobada, o 400 si no está en un estado que lo permita
     */
    @PostMapping("/aprobar/{id}")
    @PreAuthorize("@permisoService.tienePermiso(authentication, 'SOLICITUDES_REVISAR')")
    public ResponseEntity<?> aprobar(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(ResponseWrapper.success(solicitudService.aprobarSolicitud(id), "Solicitud aprobada"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * @param id solicitud a rechazar
     * @return 200 con la solicitud rechazada, o 400 si la transición no es válida
     */
    @PostMapping("/rechazar/{id}")
    @PreAuthorize("@permisoService.tienePermiso(authentication, 'SOLICITUDES_REVISAR')")
    public ResponseEntity<?> rechazar(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(ResponseWrapper.success(solicitudService.rechazarSolicitud(id), "Solicitud rechazada"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * Rechaza la solicitud dejando constancia del motivo para el estudiante.
     *
     * @param id   solicitud a rechazar
     * @param body cuerpo con la clave "observacion"; si falta se registra cadena vacía
     * @return 200 con la solicitud rechazada, o 400 si la transición no es válida
     */
    @PostMapping("/rechazar-con-observacion/{id}")
    @PreAuthorize("@permisoService.tienePermiso(authentication, 'SOLICITUDES_REVISAR')")
    public ResponseEntity<?> rechazarConObservacion(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body) {
        try {
            String observacion = body.getOrDefault("observacion", "");
            return ResponseEntity.ok(ResponseWrapper.success(solicitudService.rechazarConObservacion(id, observacion), "Solicitud rechazada con observaciones"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * Suspende una solicitud en curso (por ejemplo, si el estudiante se retira del período).
     *
     * @param id   solicitud a suspender
     * @param body cuerpo con la clave "motivo"
     * @return 200 con la solicitud suspendida, o 400 si falta el motivo o la transición
     *         no es válida
     */
    @PostMapping("/suspender/{id}")
    @PreAuthorize("@permisoService.tienePermiso(authentication, 'SOLICITUDES_SUSPENDER')")
    public ResponseEntity<?> suspender(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        try {
            String motivo = body.get("motivo");
            return ResponseEntity.ok(ResponseWrapper.success(solicitudService.suspenderSolicitud(id, motivo), "Solicitud suspendida"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * Listado completo sin paginar. ADMIN, COORDINADOR y DOCENTE pueden ver todas las
     * solicitudes; para volúmenes grandes conviene usar /paginado.
     *
     * @return 200 con todas las solicitudes
     */
    @GetMapping
    @PreAuthorize("@permisoService.tienePermiso(authentication, 'SOLICITUDES_REVISAR')")
    public ResponseEntity<?> listar() {
        return ResponseEntity.ok(ResponseWrapper.success(solicitudService.listarSolicitudes()));
    }

    /**
     * Versión paginada de {@link #listar()} — evita cargar miles de filas de una sola vez,
     * que es lo que hacía colapsar la tabla de "Gestionar Solicitudes" en el frontend.
     * ERR-01: agrega búsqueda de texto libre (parámetro "q") combinable con el filtro de
     * estado, mismo patrón que GET /api/v1/usuarios/paginado.
     *
     * @param page        número de página, base 0
     * @param size        tamaño de página
     * @param estado      código de estado a filtrar, o {@code null} para no filtrar
     * @param q           texto libre de búsqueda, o {@code null} para no filtrar
     * @param fechaDesde  fecha mínima a incluir, o {@code null} para no acotar
     * @param fechaHasta  fecha máxima a incluir, o {@code null} para no acotar
     * @return 200 con la página de solicitudes y sus metadatos de paginación
     */
    @GetMapping("/paginado")
    @PreAuthorize("@permisoService.tienePermiso(authentication, 'SOLICITUDES_REVISAR')")
    public ResponseEntity<?> listarPaginado(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate fechaDesde,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate fechaHasta) {
        Page<Solicitud> resultado = solicitudService.listarSolicitudesPaginado(page, size, estado, q, fechaDesde, fechaHasta);
        return ResponseEntity.ok(ResponseWrapper.success(Map.of(
                "content", resultado.getContent(),
                "totalElements", resultado.getTotalElements(),
                "totalPages", resultado.getTotalPages(),
                "page", resultado.getNumber(),
                "size", resultado.getSize()
        )));
    }

    /**
     * Conteo de solicitudes agrupadas por estado, para los contadores de las pestañas de
     * filtro del frontend.
     *
     * @return 200 con un mapa estado a cantidad
     */
    @GetMapping("/contar-por-estado")
    @PreAuthorize("@permisoService.tienePermiso(authentication, 'SOLICITUDES_REVISAR')")
    public ResponseEntity<?> contarPorEstado() {
        return ResponseEntity.ok(ResponseWrapper.success(solicitudService.contarPorEstado()));
    }

    /**
     * @param estudianteId perfil de estudiante consultado
     * @return 200 con las solicitudes de ese estudiante
     */
    @GetMapping("/estudiante/{estudianteId}")
    @PreAuthorize("@permisoService.tienePermiso(authentication, 'SOLICITUDES_REVISAR')")
    public ResponseEntity<?> listarPorEstudiante(@PathVariable Long estudianteId) {
        return ResponseEntity.ok(ResponseWrapper.success(solicitudService.listarPorEstudiante(estudianteId)));
    }

    /**
     * Detalle de una solicitud. Un estudiante sólo puede abrir la suya; quien tiene permiso
     * de revisión (o es ADMIN) puede abrir cualquiera.
     *
     * @param id solicitud consultada
     * @return 200 con la solicitud, 404 si no existe, o 403 si no es propietario ni revisor
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> obtener(@PathVariable Long id) {
        try {
            validarAccesoSolicitud(id);
            return solicitudService.obtenerPorId(id)
                    .map(s -> ResponseEntity.ok(ResponseWrapper.success(s)))
                    .orElse(ResponseEntity.status(404).body(ResponseWrapper.error("Solicitud no encontrada")));
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * SP (Fase 3): Reporte consolidado de defensas por carrera.
     * Llama a presus.sp_generar_reporte_defensas(p_carrera)
     * Flujo: GET → SolicitudController → SolicitudService → SolicitudRepository → SP → PostgreSQL
     *
     * @param carrera nombre o parte del nombre de la carrera (búsqueda ILIKE); vacío
     *                devuelve todas las carreras
     * @return 200 con las filas del reporte que devuelve el procedimiento, o 400 con el
     *         error si el cursor del procedimiento falla
     */
    @GetMapping("/reporte-defensas")
    @PreAuthorize("@permisoService.tienePermiso(authentication, 'SOLICITUDES_REVISAR')")
    public ResponseEntity<?> reporteDefensas(
            @RequestParam(defaultValue = "") String carrera) {
        try {
            List<Map<String, Object>> reporte = solicitudService.generarReporteDefensasSP(carrera);
            return ResponseEntity.ok(ResponseWrapper.success(reporte));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * Línea de tiempo del trámite (estados por los que pasó la solicitud). Exige la misma
     * comprobación de propiedad que el detalle.
     *
     * @param id solicitud consultada
     * @return 200 con el seguimiento, o 403 si no es propietario ni revisor
     */
    @GetMapping("/{id}/seguimiento")
    public ResponseEntity<?> obtenerSeguimiento(@PathVariable Long id) {
        try {
            validarAccesoSolicitud(id);
            return ResponseEntity.ok(ResponseWrapper.success(solicitudService.obtenerSeguimiento(id)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * Valida que el usuario actual tenga permiso de revisión (o sea ADMIN) o bien sea el
     * propietario de la solicitud.
     *
     * @param solicitudId solicitud sobre la que se comprueba el acceso
     * @throws RuntimeException si la solicitud no existe o si el usuario autenticado no es
     *                          revisor ni propietario
     */
    private void validarAccesoSolicitud(Long solicitudId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        // CustomUserDetailsService solo carga "ROLE_<rol>" como authority, nunca los permisos
        // finos. Por eso no se puede comprobar "SOLICITUDES_REVISAR" contra getAuthorities():
        // un COORDINADOR (que sí tiene el permiso en rol_permisos) daba 403 "no eres
        // propietario". Se usa el mismo permisoService que protege el resto del controlador.
        boolean esRevisor = auth.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))
                || permisoService.tienePermiso(auth, "SOLICITUDES_REVISAR");

        if (!esRevisor) {
            Solicitud solicitud = solicitudService.obtenerPorId(solicitudId)
                    .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));
            if (!solicitud.getEstudiante().getUsuario().getEmail().equals(email)) {
                throw new RuntimeException("Acceso denegado: no eres propietario de esta solicitud");
            }
        }
    }
}