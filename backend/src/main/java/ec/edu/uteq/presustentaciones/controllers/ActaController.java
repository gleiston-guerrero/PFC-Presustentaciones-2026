package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.entities.Acta;
import ec.edu.uteq.presustentaciones.services.ActaService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import ec.edu.uteq.presustentaciones.dto.CambiarEstadoActaRequest;
import ec.edu.uteq.presustentaciones.dto.ResponseWrapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/v1/actas")
public class ActaController {

    private final ActaService actaService;

    public ActaController(ActaService actaService) {
        this.actaService = actaService;
    }

    /**
     * RF-11: Genera el acta de la pre-sustentación junto con su PDF real (iText).
     *
     * @param solicitudId solicitud ya evaluada de la que se genera el acta
     * @return 200 con el acta generada, o 400 con el motivo si la solicitud todavía no
     *         tiene evaluación final
     */
    @PostMapping("/generar/{solicitudId}")
    @PreAuthorize("@permisoService.tienePermiso(authentication, 'ACTA_GENERAR')")
    public ResponseEntity<?> generarActa(@PathVariable Long solicitudId) {
        try {
            Acta acta = actaService.generarActa(solicitudId);
            return ResponseEntity.ok(ResponseWrapper.success(acta, "Acta generada exitosamente"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * RF-08: Firma el acta en nombre de un rol del tribunal, delegando la persistencia en
     * el procedimiento almacenado presus.sp_firmar_acta_digital. Cuando las cuatro firmas
     * están completas el acta pasa a COMPLETADA y se regenera su PDF.
     *
     * @param actaId      acta que se firma
     * @param rol         rol que firma: PRESIDENTE, VOCAL_1, VOCAL_2 o TUTOR
     * @param observacion comentario opcional que el procedimiento agrega a la bitácora del acta
     * @return 200 con el acta actualizada, o 400 si el rol es inválido o quien firma no es
     *         el jurado/tutor correspondiente de esa solicitud
     */
    @PostMapping("/firmar/{actaId}")
    @PreAuthorize("@permisoService.tienePermiso(authentication, 'ACTA_FIRMAR')")
    public ResponseEntity<?> firmarActa(
            @PathVariable Long actaId,
            @RequestParam String rol,
            @RequestParam(required = false) String observacion) {
        try {
            Acta acta = actaService.firmarActa(actaId, rol, observacion);
            return ResponseEntity.ok(ResponseWrapper.success(acta, "Acta firmada exitosamente"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * RF-11: Descarga el PDF del acta como archivo adjunto.
     *
     * @param actaId acta cuyo PDF se descarga
     * @return 200 con el PDF como adjunto, o el estado de error que devuelva el servicio
     */
    @GetMapping("/descargar/{actaId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> descargarPdf(@PathVariable Long actaId) {
        try {
            byte[] pdfBytes = actaService.obtenerPdfBytes(actaId);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"acta_" + actaId + ".pdf\"")
                    .body(pdfBytes);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Vista en línea del PDF del acta, para abrirlo en el navegador sin descargarlo.
     *
     * @param actaId acta cuyo PDF se muestra
     * @return 200 con el PDF y cabecera inline
     */
    @GetMapping("/ver/{actaId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> verPdf(@PathVariable Long actaId) {
        try {
            byte[] pdfBytes = actaService.obtenerPdfBytes(actaId);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"acta_" + actaId + ".pdf\"")
                    .body(pdfBytes);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * @param pageable página y tamaño solicitados
     * @return 200 con la página de actas
     */
    @GetMapping
    @PreAuthorize("@permisoService.tienePermiso(authentication, 'ACTAS_VER')")
    public ResponseEntity<?> listar(Pageable pageable) {
        try {
            return ResponseEntity.ok(ResponseWrapper.success(actaService.listarActas(pageable)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    // ── Módulo 2: gestión e historial de actas ──────────────────────────────

    /**
     * DOCENTE: actas de las pre-sustentaciones en las que el docente autenticado participa
     * como tutor o como jurado.
     *
     * @param auth     autenticación de la sesión, de la que se resuelve el docente
     * @param pageable página y tamaño solicitados
     * @return 200 con las actas del docente autenticado
     */
    @GetMapping("/mis-actas")
    @PreAuthorize("@permisoService.tienePermiso(authentication, 'ACTAS_VER_PROPIAS')")
    public ResponseEntity<?> misActas(Authentication auth, Pageable pageable) {
        return ResponseEntity.ok(ResponseWrapper.success(actaService.listarMisActas(auth.getName(), pageable)));
    }

    /**
     * COORDINADOR / ADMINISTRADOR: búsqueda y filtrado de todas las actas (permiso ACTAS_VER).
     * El coordinador consulta y cambia estado según el flujo académico; ACTAS_GESTIONAR
     * (solo ADMIN) queda reservado para operaciones administrativas adicionales.
     *
     * @param estado   código de estado del acta a filtrar, o {@code null} para no filtrar
     * @param carrera  carrera a filtrar, o {@code null} para no filtrar
     * @param desde    fecha mínima de generación, o {@code null} para no acotar
     * @param hasta    fecha máxima de generación, o {@code null} para no acotar
     * @param q        texto libre de búsqueda, o {@code null} para no filtrar
     * @param pageable página y tamaño solicitados
     * @return 200 con la página de actas que cumplen los filtros recibidos
     */
    @GetMapping("/buscar")
    @PreAuthorize("@permisoService.tienePermiso(authentication, 'ACTAS_VER')")
    public ResponseEntity<?> buscar(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String carrera,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String q,
            Pageable pageable) {
        return ResponseEntity.ok(ResponseWrapper.success(
                actaService.buscarActas(estado, carrera, desde, hasta, q, pageable)));
    }

    /**
     * Detalle de un acta. El control de acceso lo aplica el servicio, que exige ser admin,
     * jurado, tutor o el estudiante dueño (previene IDOR).
     *
     * @param id acta consultada
     * @return 200 con el detalle, o el error que devuelva el servicio si no hay acceso
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> detalle(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(ResponseWrapper.success(actaService.obtenerDetalle(id)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * Historial de trazabilidad (timeline) de cambios de estado del acta. Mismo control de
     * acceso que el detalle.
     *
     * @param id acta consultada
     * @return 200 con el historial, o el error que devuelva el servicio si no hay acceso
     */
    @GetMapping("/{id}/historial")
    @PreAuthorize("@permisoService.tienePermiso(authentication, 'ACTA_HISTORIAL_VER')")
    public ResponseEntity<?> historial(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(ResponseWrapper.success(actaService.obtenerHistorial(id)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * COORDINADOR / ADMINISTRADOR: cambia el estado del acta. Cada cambio queda registrado
     * en el historial con el usuario, el estado anterior, el nuevo y el motivo.
     *
     * @param id  acta cuyo estado se cambia
     * @param req nuevo estado y motivo del cambio
     * @return 200 con el acta actualizada, o 400 si la transición no es válida o falta el
     *         motivo en los estados que lo exigen (OBSERVADA, ANULADA)
     */
    @PatchMapping("/{id}/estado")
    @PreAuthorize("@permisoService.tienePermiso(authentication, 'ACTA_ESTADO_CAMBIAR')")
    public ResponseEntity<?> cambiarEstado(@PathVariable Long id,
                                           @Valid @RequestBody CambiarEstadoActaRequest req) {
        try {
            Acta acta = actaService.cambiarEstado(id, req.getNuevoEstado(), req.getMotivo());
            return ResponseEntity.ok(ResponseWrapper.success(acta, "Estado del acta actualizado"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * @param solicitudId solicitud consultada
     * @return 200 con el acta de esa solicitud, o el error del servicio si no existe o no
     *         hay acceso
     */
    @GetMapping("/solicitud/{solicitudId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> porSolicitud(@PathVariable Long solicitudId) {
        try {
            return actaService.buscarPorSolicitud(solicitudId)
                    .map(acta -> ResponseEntity.ok(ResponseWrapper.success(acta)))
                    .orElse(ResponseEntity.notFound().build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * Hallazgo real de auditoría (2026-09-04): usaba isAuthenticated() a secas, así que
     * ActaServiceImpl.eliminarActa() (que reutiliza validarAcceso(), pensado para LECTURA:
     * admin, jurado, tutor o el propio estudiante dueño) terminaba autorizando también el
     * borrado permanente del acta a cualquiera de esos participantes -- un estudiante podía
     * eliminar el acta oficial de su propia defensa, o un docente jurado/tutor sin ningún
     * permiso administrativo. Se exige ACTAS_GESTIONAR (hoy solo ADMIN), igual que el resto
     * de acciones administrativas de este controlador (generar/firmar/cambiarEstado).
     *
     * @param id acta a eliminar permanentemente
     * @return 204 sin cuerpo, o el error del servicio si el acta no existe
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@permisoService.tienePermiso(authentication, 'ACTAS_GESTIONAR')")
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        try {
            actaService.eliminarActa(id);
            return ResponseEntity.ok(ResponseWrapper.success(null, "Acta eliminada exitosamente"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }
}
