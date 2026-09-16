package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.dto.NuevoMensajeRequest;
import ec.edu.uteq.presustentaciones.dto.TutoringFaseDTO;
import ec.edu.uteq.presustentaciones.dto.TutoringMensajeDTO;
import ec.edu.uteq.presustentaciones.dto.TutoringResumenDTO;
import ec.edu.uteq.presustentaciones.dto.ResponseWrapper;
import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.repositories.AppUserRepository;
import ec.edu.uteq.presustentaciones.services.TutoringService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tutorias")
@CrossOrigin(origins = "http://localhost:4200")
public class TutoringController {

    private final TutoringService tutoringService;
    private final AppUserRepository appUserRepository;

    public TutoringController(TutoringService tutoringService, AppUserRepository appUserRepository) {
        this.tutoringService = tutoringService;
        this.appUserRepository = appUserRepository;
    }

    // ── Listados por appUser ──────────────────────────────────────────────────

    /**
     * Tutorías en las que el appUser participa como student.
     *
     * @param appUserId appUser consultado; sólo se respeta si quien pregunta es ADMIN o
     *                  COORDINADOR, en caso contrario se ignora y se usa el del token
     * @return 200 con las tutorías, o 400 si no hay sesión válida
     */
    @GetMapping("/estudiante/{appUserId}")
    public ResponseEntity<?> obtainTutoringsStudent(@PathVariable Long appUserId) {
        try {
            Long realAppUserId = resolveAppUserId(appUserId);
            List<TutoringResumenDTO> resultado = tutoringService.obtainTutoringsStudent(realAppUserId);
            return ResponseEntity.ok(ResponseWrapper.success(resultado));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * Tutorías en las que el appUser participa como teacher tutor.
     *
     * @param appUserId appUser consultado; sólo se respeta para ADMIN o COORDINADOR
     * @return 200 con las tutorías, o 400 si no hay sesión válida
     */
    @GetMapping("/docente/{appUserId}")
    public ResponseEntity<?> obtainTutoringsTeacher(@PathVariable Long appUserId) {
        try {
            Long realAppUserId = resolveAppUserId(appUserId);
            List<TutoringResumenDTO> resultado = tutoringService.obtainTutoringsTeacher(realAppUserId);
            return ResponseEntity.ok(ResponseWrapper.success(resultado));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    // ── Resumen y fases ───────────────────────────────────────────────────────

    /**
     * Resumen de una tutoría: fases, avance y datos del student.
     *
     * @param tutorId   tutoría consultada
     * @param appUserId appUser en cuyo nombre se consulta; sólo se respeta para ADMIN o COORDINADOR
     * @return 200 con el resumen, o 400 si no hay acceso a esa tutoría
     */
    @GetMapping("/{tutorId}/resumen")
    public ResponseEntity<?> obtainResumen(@PathVariable Long tutorId,
                                            @RequestParam(required = false) Long appUserId) {
        try {
            Long realAppUserId = resolveAppUserId(appUserId);
            TutoringResumenDTO resumen = tutoringService.obtainResumen(tutorId, realAppUserId);
            return ResponseEntity.ok(ResponseWrapper.success(resumen));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * Fases registradas de una tutoría, con su estado y sus archivos.
     *
     * @param tutorId   tutoría consultada
     * @param appUserId appUser en cuyo nombre se consulta; sólo se respeta para ADMIN o COORDINADOR
     * @return 200 con las fases, o 400 si no hay acceso a esa tutoría
     */
    @GetMapping("/{tutorId}/fases")
    public ResponseEntity<?> obtainFases(@PathVariable Long tutorId,
                                          @RequestParam(required = false) Long appUserId) {
        try {
            Long realAppUserId = resolveAppUserId(appUserId);
            List<TutoringFaseDTO> fases = tutoringService.obtainFases(tutorId, realAppUserId);
            return ResponseEntity.ok(ResponseWrapper.success(fases));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    // ── Operaciones sobre fases ───────────────────────────────────────────────

    /**
     * Abre una nueva fase de tutoría con la observación del tutor.
     *
     * @param tutorId        tutoría a la que se agrega la fase
     * @param observacion    indicación del tutor para el student
     * @param tutorAppUserId ignorado; el tutor se resuelve siempre desde el token
     * @return 200 con la fase creada, o 400 con el motivo del rechazo
     */
    @PostMapping("/{tutorId}/nueva-fase")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'TUTORIA_GESTIONAR')")
    public ResponseEntity<?> createFaseConObservacion(@PathVariable Long tutorId,
                                                     @RequestParam String observacion,
                                                     @RequestParam(required = false) Long tutorAppUserId) {
        try {
            Long realTutorAppUserId = obtainAppUserAutenticado().getId();
            TutoringFaseDTO fase = tutoringService.createFaseConObservacion(tutorId, realTutorAppUserId, observacion);
            return ResponseEntity.ok(ResponseWrapper.success(fase, "Fase creada exitosamente"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * Sube el PDF corregido del student para una fase.
     *
     * @param faseId              fase a la que corresponde el archivo
     * @param archivo             PDF enviado como multipart
     * @param studentAppUserId ignorado; el student se resuelve desde el token
     * @return 200 con la fase actualizada, o 400 si el archivo no es válido
     */
    @PostMapping(value = "/fases/{faseId}/subir-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> uploadPdfCorregido(@PathVariable Long faseId,
                                               @RequestParam("archivo") MultipartFile archivo,
                                               @RequestParam(required = false) Long studentAppUserId) {
        try {
            Long realStudentAppUserId = obtainAppUserAutenticado().getId();
            TutoringFaseDTO fase = tutoringService.uploadPdfCorregido(faseId, archivo, realStudentAppUserId);
            return ResponseEntity.ok(ResponseWrapper.success(fase, "PDF subido exitosamente"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * Aprueba una fase, habilitando que el student avance a la siguiente.
     *
     * @param faseId         fase a approve
     * @param tutorAppUserId ignorado; el tutor se resuelve desde el token
     * @param comentario     comentario opcional del tutor
     * @return 200 con la fase aprobada, o 400 con el motivo del rechazo
     */
    @PostMapping("/fases/{faseId}/aprobar")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'TUTORIA_GESTIONAR')")
    public ResponseEntity<?> approveFase(@PathVariable Long faseId,
                                         @RequestParam(required = false) Long tutorAppUserId,
                                         @RequestParam(required = false) String comentario) {
        try {
            Long realTutorAppUserId = obtainAppUserAutenticado().getId();
            TutoringFaseDTO fase = tutoringService.approveFase(faseId, realTutorAppUserId, comentario);
            return ResponseEntity.ok(ResponseWrapper.success(fase, "Fase aprobada exitosamente"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * Envía un mensaje en el hilo de conversación de una fase.
     *
     * @param faseId      fase sobre la que se conversa
     * @param remitenteId ignorado; el remitente se resuelve desde el token
     * @param request     cuerpo con el contenido y el tipo de mensaje
     * @return 200 con el mensaje creado, o 400 con el motivo del rechazo
     */
    @PostMapping("/fases/{faseId}/mensaje")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> sendMensaje(@PathVariable Long faseId,
                                           @RequestParam(required = false) Long remitenteId,
                                           @RequestBody NuevoMensajeRequest request) {
        try {
            Long realRemitenteId = obtainAppUserAutenticado().getId();
            TutoringMensajeDTO mensaje = tutoringService.sendMensaje(
                    faseId, realRemitenteId, request.getContenido(), request.getTipo());
            return ResponseEntity.ok(ResponseWrapper.success(mensaje, "Mensaje enviado exitosamente"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * Marca como leídos los mensajes que el appUser autenticado tiene pendientes en la fase.
     *
     * @param faseId    fase cuyos mensajes se marcan
     * @param appUserId ignorado; el appUser se resuelve desde el token
     * @return 200 sin datos, o 400 con el motivo del rechazo
     */
    @PutMapping("/fases/{faseId}/leer")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> marcarMensajesLeidos(@PathVariable Long faseId,
                                                  @RequestParam(required = false) Long appUserId) {
        try {
            Long realAppUserId = obtainAppUserAutenticado().getId();
            tutoringService.marcarMensajesLeidos(faseId, realAppUserId);
            return ResponseEntity.ok(ResponseWrapper.success(null, "Mensajes marcados como leídos"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    // ── PDF ───────────────────────────────────────────────────────────────────

    /**
     * Descarga en línea el PDF asociado a una fase.
     *
     * @param faseId    fase consultada
     * @param appUserId appUser en cuyo nombre se consulta; sólo se respeta para ADMIN o COORDINADOR
     * @return 200 con el PDF y cabecera inline, o 400 si la fase no tiene archivo o no hay acceso
     */
    @GetMapping("/fases/{faseId}/pdf")
    public ResponseEntity<?> obtainPdfFase(@PathVariable Long faseId,
                                            @RequestParam(required = false) Long appUserId) {
        try {
            Long realAppUserId = resolveAppUserId(appUserId);
            Resource resource = tutoringService.obtainPdfFase(faseId, realAppUserId);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    // ── Helpers de seguridad ──────────────────────────────────────────────────

    /**
     * SP (Fase 3): Registra o actualiza el avance de una fase de tutoría vía stored procedure.
     * Llama a presus.sp_register_tutoring_avance(p_tutor_id, p_numero_fase, p_archivo_pdf, p_tamano_bytes, p_sha256)
     * Flujo: POST → TutoringController → TutoringService → TutoringFaseRepository → SP → PostgreSQL
     *
     * Body: { "numeroFase": 1, "archivoPdf": "archivo.pdf", "tamanoBytes": 12345, "sha256": "abc..." }
     *
     * @param tutorId tutoría sobre la que se registra el avance
     * @param body    numeroFase y archivoPdf son obligatorios; tamanoBytes y sha256 son
     *                opcionales y viajan como números/cadenas JSON
     * @return 200 con el tutor y la fase registrados; 400 si faltan los campos obligatorios
     *         o si el procedimiento rechaza el avance (por ejemplo, cuando la fase anterior
     *         todavía no está aprobada)
     */
    @PostMapping("/{tutorId}/registrar-avance")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'TUTORIA_AVANCE_ESTUDIANTE')")
    public ResponseEntity<?> registerAvanceSP(
            @PathVariable Long tutorId,
            @RequestBody Map<String, Object> body) {
        try {
            Integer numeroFase = (Integer) body.get("numeroFase");
            String archivoPdf = (String) body.get("archivoPdf");
            Long tamanoBytes = body.get("tamanoBytes") instanceof Number
                    ? ((Number) body.get("tamanoBytes")).longValue() : null;
            String sha256 = (String) body.get("sha256");

            if (numeroFase == null || archivoPdf == null) {
                return ResponseEntity.badRequest()
                        .body(ResponseWrapper.error("Se requieren 'numeroFase' y 'archivoPdf'"));
            }

            Long realAppUserId = obtainAppUserAutenticado().getId();
            tutoringService.registerAvanceSP(tutorId, numeroFase, archivoPdf, tamanoBytes, sha256, realAppUserId);
            return ResponseEntity.ok(ResponseWrapper.success(Map.of(
                    "mensaje", "Avance de fase registrado correctamente vía stored procedure",
                    "tutorId", tutorId,
                    "numeroFase", numeroFase
            ), "Avance de fase registrado correctamente vía stored procedure"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * Resuelve el appUser de la sesión actual a partir del token.
     *
     * @return el {@link AppUser} autenticado
     * @throws RuntimeException si no hay sesión, es anónima, o el appUser del token ya no
     *                          existe en la base
     */
    private AppUser obtainAppUserAutenticado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new RuntimeException("Usuario no autenticado");
        }
        return appUserRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado en el sistema"));
    }

    /**
     * Decide sobre qué appUser se responde: ADMIN y COORDINADOR pueden consultar el de
     * otra persona, cualquier otro role queda restringido al suyo aunque mande otro id.
     *
     * @param appUserIdSolicitado appUser pedido por el cliente, puede ser null
     * @return el id sobre el que realmente se debe consultar
     * @throws RuntimeException si no hay un appUser autenticado válido
     */
    private Long resolveAppUserId(Long appUserIdSolicitado) {
        AppUser autenticado = obtainAppUserAutenticado();
        boolean esAdminOCoord = "ADMIN".equalsIgnoreCase(autenticado.getRole())
                || "COORDINADOR".equalsIgnoreCase(autenticado.getRole());
        if (esAdminOCoord && appUserIdSolicitado != null) {
            return appUserIdSolicitado;
        }
        return autenticado.getId();
    }
}
