package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.entities.Minutes;
import ec.edu.uteq.presustentaciones.services.MinutesService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import ec.edu.uteq.presustentaciones.dto.ChangeStatusMinutesRequest;
import ec.edu.uteq.presustentaciones.dto.ResponseWrapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/v1/actas")
public class MinutesController {

    private final MinutesService minutesService;

    /**
     * Construye MinutesController, inyectando minutesService.
     * @param minutesService minutesService
     */
    public MinutesController(MinutesService minutesService) {
        this.minutesService = minutesService;
    }

    /**
     * RF-11: Genera el minutes de la pre-sustentación junto con su PDF real (iText).
     *
     * @param submissionId submission ya evaluada de la que se genera el minutes
     * @return 200 con el minutes generada, o 400 con el motivo si la submission todavía no
     *         tiene evaluación final
     */
    @PostMapping("/generar/{submissionId}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'ACTA_GENERAR')")
    public ResponseEntity<?> generateMinutes(@PathVariable("submissionId") Long submissionId) {
        try {
            Minutes minutes = minutesService.generateMinutes(submissionId);
            return ResponseEntity.ok(ResponseWrapper.success(minutes, "Acta generada exitosamente"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * RF-08: Firma el minutes en nombre de un role del tribunal, delegando la persistencia en
     * el procedimiento almacenado presus.sp_sign_minutes_digital. Cuando las cuatro firmas
     * están completas el minutes pasa a COMPLETADA y se regenera su PDF.
     *
     * @param minutesId      minutes que se firma
     * @param role         role que firma: PRESIDENTE, VOCAL_1, VOCAL_2 o TUTOR
     * @param observation comentario opcional que el procedimiento agrega a la bitácora del minutes
     * @return 200 con el minutes actualizada, o 400 si el role es inválido o quien firma no es
     *         el panelist/tutor correspondiente de esa submission
     */
    @PostMapping("/firmar/{minutesId}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'ACTA_FIRMAR')")
    public ResponseEntity<?> signMinutes(
            @PathVariable("minutesId") Long minutesId,
            @RequestParam(name = "rol") String role,
            @RequestParam(name = "observacion", required = false) String observation) {
        try {
            Minutes minutes = minutesService.signMinutes(minutesId, role, observation);
            return ResponseEntity.ok(ResponseWrapper.success(minutes, "Acta firmada exitosamente"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * RF-11: Descarga el PDF del minutes como archivo adjunto.
     *
     * @param minutesId minutes cuyo PDF se descarga
     * @return 200 con el PDF como adjunto, o el estado de error que devuelva el servicio
     */
    @GetMapping("/descargar/{minutesId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable("minutesId") Long minutesId) {
        try {
            byte[] pdfBytes = minutesService.obtainPdfBytes(minutesId);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"acta_" + minutesId + ".pdf\"")
                    .body(pdfBytes);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Vista en línea del PDF del minutes, para abrirlo en el navegador sin descargarlo.
     *
     * @param minutesId minutes cuyo PDF se muestra
     * @return 200 con el PDF y cabecera inline
     */
    @GetMapping("/ver/{minutesId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> viewPdf(@PathVariable("minutesId") Long minutesId) {
        try {
            byte[] pdfBytes = minutesService.obtainPdfBytes(minutesId);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"acta_" + minutesId + ".pdf\"")
                    .body(pdfBytes);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * @param pageable página y tamaño solicitados
     * @return 200 con la página de minutes
     */
    @GetMapping
    @PreAuthorize("@permissionService.hasPermission(authentication, 'ACTAS_VER')")
    public ResponseEntity<?> list(Pageable pageable) {
        try {
            return ResponseEntity.ok(ResponseWrapper.success(minutesService.listMinutes(pageable)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    // ── Módulo 2: gestión e history de minutes ──────────────────────────────

    /**
     * DOCENTE: minutes de las pre-sustentaciones en las que el teacher autenticado participa
     * como tutor o como panelist.
     *
     * @param auth     autenticación de la sesión, de la que se resuelve el teacher
     * @param pageable página y tamaño solicitados
     * @return 200 con las minutes del teacher autenticado
     */
    @GetMapping("/mis-actas")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'ACTAS_VER_PROPIAS')")
    public ResponseEntity<?> myMinutes(Authentication auth, Pageable pageable) {
        return ResponseEntity.ok(ResponseWrapper.success(minutesService.listMyMinutes(auth.getName(), pageable)));
    }

    /**
     * COORDINADOR / ADMINISTRADOR: búsqueda y filtrado de todas las minutes (permission ACTAS_VER).
     * El coordinador consulta y cambia estado según el flujo académico; ACTAS_GESTIONAR
     * (solo ADMIN) queda reservado para operaciones administrativas adicionales.
     *
     * @param status   código de estado del minutes a filtrar, o {@code null} para no filtrar
     * @param program  program a filtrar, o {@code null} para no filtrar
     * @param from    fecha mínima de generación, o {@code null} para no acotar
     * @param to    fecha máxima de generación, o {@code null} para no acotar
     * @param q        texto libre de búsqueda, o {@code null} para no filtrar
     * @param pageable página y tamaño solicitados
     * @return 200 con la página de minutes que cumplen los filtros recibidos
     */
    @GetMapping("/buscar")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'ACTAS_VER')")
    public ResponseEntity<?> search(
            @RequestParam(name = "estado", required = false) String status,
            @RequestParam(name = "carrera", required = false) String program,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "q", required = false) String q,
            Pageable pageable) {
        return ResponseEntity.ok(ResponseWrapper.success(
                minutesService.searchMinutes(status, program, from, to, q, pageable)));
    }

    /**
     * Detalle de un minutes. El control de acceso lo aplica el servicio, que exige ser admin,
     * panelist, tutor o el student dueño (previene IDOR).
     *
     * @param id minutes consultada
     * @return 200 con el detalle, o el error que devuelva el servicio si no hay acceso
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> detail(@PathVariable("id") Long id) {
        try {
            return ResponseEntity.ok(ResponseWrapper.success(minutesService.obtainDetail(id)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * History de trazabilidad (timeline) de cambios de estado del minutes. Mismo control de
     * acceso que el detalle.
     *
     * @param id minutes consultada
     * @return 200 con el history, o el error que devuelva el servicio si no hay acceso
     */
    @GetMapping("/{id}/historial")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'ACTA_HISTORIAL_VER')")
    public ResponseEntity<?> history(@PathVariable("id") Long id) {
        try {
            return ResponseEntity.ok(ResponseWrapper.success(minutesService.obtainHistory(id)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * COORDINADOR / ADMINISTRADOR: cambia el estado del minutes. Cada cambio queda registrado
     * en el history con el appUser, el estado anterior, el nuevo y el motivo.
     *
     * @param id  minutes cuyo estado se cambia
     * @param req nuevo estado y motivo del cambio
     * @return 200 con el minutes actualizada, o 400 si la transición no es válida o falta el
     *         motivo en los estados que lo exigen (OBSERVADA, ANULADA)
     */
    @PatchMapping("/{id}/estado")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'ACTA_ESTADO_CAMBIAR')")
    public ResponseEntity<?> changeStatus(@PathVariable("id") Long id,
                                           @Valid @RequestBody ChangeStatusMinutesRequest req) {
        try {
            Minutes minutes = minutesService.changeStatus(id, req.getTargetStatus(), req.getMotivo());
            return ResponseEntity.ok(ResponseWrapper.success(minutes, "Estado del acta actualizado"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * @param submissionId submission consultada
     * @return 200 con el minutes de esa submission, o el error del servicio si no existe o no
     *         hay acceso
     */
    @GetMapping("/solicitud/{submissionId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> bySubmission(@PathVariable("submissionId") Long submissionId) {
        try {
            return minutesService.searchBySubmission(submissionId)
                    .map(minutes -> ResponseEntity.ok(ResponseWrapper.success(minutes)))
                    .orElse(ResponseEntity.notFound().build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * Hallazgo real de auditoría (2026-09-04): usaba isAuthenticated() a secas, así que
     * MinutesServiceImpl.deleteMinutes() (que reutiliza validateAcceso(), pensado para LECTURA:
     * admin, panelist, tutor o el propio student dueño) terminaba autorizando también el
     * borrado permanente del minutes a cualquiera de esos participantes -- un student podía
     * delete el minutes oficial de su propia defensa, o un teacher panelist/tutor sin ningún
     * permission administrativo. Se exige ACTAS_GESTIONAR (hoy solo ADMIN), igual que el resto
     * de acciones administrativas de este controlador (generate/sign/changeEstado).
     *
     * @param id minutes a delete permanentemente
     * @return 204 sin cuerpo, o el error del servicio si el minutes no existe
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'ACTAS_GESTIONAR')")
    public ResponseEntity<?> delete(@PathVariable("id") Long id) {
        try {
            minutesService.deleteMinutes(id);
            return ResponseEntity.ok(ResponseWrapper.success(null, "Acta eliminada exitosamente"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }
}
