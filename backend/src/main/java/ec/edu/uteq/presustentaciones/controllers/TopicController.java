package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.dto.GenerateTopicRequest;
import ec.edu.uteq.presustentaciones.dto.SaveTopicPropuestoRequest;
import ec.edu.uteq.presustentaciones.dto.TopicPropuestoDTO;
import ec.edu.uteq.presustentaciones.entities.Student;
import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.repositories.StudentRepository;
import ec.edu.uteq.presustentaciones.repositories.AppUserRepository;
import ec.edu.uteq.presustentaciones.services.TopicService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Centro de Orientación y Titulación — catálogo de topics propuestos y la lista de
 * topics que cada student guarda para sí mismo.
 *
 * Autorización: la exploración del catálogo usa el permission dinámico
 * {@code ORIENTACION_TEMAS_VER} (gestionable desde "Gestionar Permisos", igual que
 * el resto del sistema — ver PermissionService). Las acciones sobre la lista personal
 * (save / remove / list guardados) son exclusivas del role ESTUDIANTE y operan
 * SIEMPRE sobre el student autenticado: el id se resuelve desde el JWT, nunca se
 * recibe por la URL (evita IDOR).
 */
@RestController
@RequestMapping("/api/v1/orientacion/temas")
@RequiredArgsConstructor
public class TopicController {

    private final TopicService topicService;
    private final AppUserRepository appUserRepository;
    private final StudentRepository studentRepository;

    // ── Catálogo (cualquier appUser con permission ORIENTACION_TEMAS_VER) ────────

    /**
     * Explora el catalogo de topics propuestos con filtros combinables. Si quien consulta es
     * student, el resultado marca ademas cuales tiene ya guardados.
     *
     * @param programId            filtra por program, opcional
     * @param lineInvestigacionId filtra por line de investigacion, opcional
     * @param areaId               filtra por area tematica, opcional
     * @param nivelDificultad      filtra por nivel (BASICO, INTERMEDIO, AVANZADO), opcional
     * @return 200 con los topics que cumplen los filtros
     */
    // "Quien puede gestionar el catálogo, puede verlo": la pantalla "Gestionar Temas
    // Propuestos" necesita list para poder editar, así que ORIENTACION_CATALOGO_GESTIONAR
    // también autoriza la lectura — de lo contrario ese permission por sí solo es inútil.
    @GetMapping
    @PreAuthorize("@permissionService.tienePermission(authentication, 'ORIENTACION_TEMAS_VER') " +
            "or @permissionService.tienePermission(authentication, 'ORIENTACION_CATALOGO_GESTIONAR')")
    /**
     * Explorar.
     * @param programId programId
     * @param lineInvestigacionId lineInvestigacionId
     * @param areaId areaId
     * @param nivelDificultad nivelDificultad
     * @return el ResponseEntity<List<TopicPropuestoDTO>> correspondiente
     */
    public ResponseEntity<List<TopicPropuestoDTO>> explorar(
            @RequestParam(name = "carreraId", required = false) Integer programId,
            @RequestParam(name = "lineaInvestigacionId", required = false) Integer lineInvestigacionId,
            @RequestParam(name = "areaId", required = false) Integer areaId,
            @RequestParam(name = "nivelDificultad", required = false) String nivelDificultad) {
        Long studentId = studentActualIdOrNull();
        return ResponseEntity.ok(topicService.explorar(
                programId, lineInvestigacionId, areaId, nivelDificultad, studentId));
    }

    /**
     * @param topicId topic consultado
     * @return 200 con el detalle del topic
     */
    @GetMapping("/{topicId}")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'ORIENTACION_TEMAS_VER') " +
            "or @permissionService.tienePermission(authentication, 'ORIENTACION_CATALOGO_GESTIONAR')")
    /**
     * Detalle.
     * @param topicId topicId
     * @return el ResponseEntity<TopicPropuestoDTO> correspondiente
     */
    public ResponseEntity<TopicPropuestoDTO> detalle(@PathVariable("topicId") Integer topicId) {
        return ResponseEntity.ok(topicService.obtainDetalle(topicId));
    }

    /**
     * Sugiere ideas de topic a partir de la program, line y area que indique el student.
     *
     * @param request criterios sobre los que generate las sugerencias
     * @return 200 con las ideas propuestas
     */
    @PostMapping("/generar")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'ORIENTACION_TEMAS_VER')")
    public ResponseEntity<List<TopicPropuestoDTO>> generateIdeas(@RequestBody @Valid GenerateTopicRequest request) {
        return ResponseEntity.ok(topicService.generateIdeas(request));
    }

    // ── Lista personal del student (solo ESTUDIANTE, siempre sobre sí mismo) ─

    /**
     * Lista personal de topics guardados. Opera siempre sobre el student autenticado; el id
     * sale del JWT y nunca de la URL.
     *
     * @return 200 con los topics que el student autenticado guardo
     */
    @GetMapping("/guardados")
    @PreAuthorize("hasRole('ESTUDIANTE')")
    public ResponseEntity<List<TopicPropuestoDTO>> misTopicsGuardados() {
        return ResponseEntity.ok(topicService.obtainTopicsGuardados(studentActual().getId()));
    }

    /**
     * Guarda un topic en la lista personal del student autenticado.
     *
     * @param topicId topic a save
     * @return 200 sin cuerpo
     */
    @PostMapping("/{topicId}/guardar")
    @PreAuthorize("hasRole('ESTUDIANTE')")
    public ResponseEntity<Void> save(@PathVariable("topicId") Integer topicId) {
        topicService.saveTopicStudent(studentActual().getId(), topicId);
        return ResponseEntity.status(201).build();
    }

    /**
     * Quita un topic de la lista personal del student autenticado.
     *
     * @param topicId topic a remove
     * @return 200 sin cuerpo
     */
    @DeleteMapping("/{topicId}/guardar")
    @PreAuthorize("hasRole('ESTUDIANTE')")
    public ResponseEntity<Void> removeGuardado(@PathVariable("topicId") Integer topicId) {
        topicService.removeTopicGuardado(studentActual().getId(), topicId);
        return ResponseEntity.noContent().build();
    }

    // ── Gestión del catálogo (permission ORIENTACION_CATALOGO_GESTIONAR) ────────

    /**
     * Publica un topic nuevo en el catalogo (gestion, no lista personal).
     *
     * @param request datos del topic, validados con Bean Validation
     * @return 200 con el topic creado
     */
    @PostMapping
    @PreAuthorize("@permissionService.tienePermission(authentication, 'ORIENTACION_CATALOGO_GESTIONAR')")
    public ResponseEntity<TopicPropuestoDTO> create(@RequestBody @Valid SaveTopicPropuestoRequest request) {
        return ResponseEntity.status(201).body(topicService.create(request));
    }

    /**
     * Edita un topic del catalogo.
     *
     * @param topicId  topic a update
     * @param request nuevos datos del topic
     * @return 200 con el topic actualizado
     */
    @PutMapping("/{topicId}")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'ORIENTACION_CATALOGO_GESTIONAR')")
    public ResponseEntity<TopicPropuestoDTO> update(@PathVariable("topicId") Integer topicId,
                                                       @RequestBody @Valid SaveTopicPropuestoRequest request) {
        return ResponseEntity.ok(topicService.update(topicId, request));
    }

    /**
     * Retira un topic del catalogo.
     *
     * @param topicId topic a delete
     * @return 204 sin cuerpo
     */
    @DeleteMapping("/{topicId}")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'ORIENTACION_CATALOGO_GESTIONAR')")
    public ResponseEntity<Void> delete(@PathVariable("topicId") Integer topicId) {
        topicService.delete(topicId);
        return ResponseEntity.noContent().build();
    }

    // ── Helpers de identidad ─────────────────────────────────────────────────

    /**
     * Resuelve el appUser de la sesion actual a partir del token.
     *
     * @return el appUser autenticado
     * @throws IllegalStateException si no hay sesion, es anonima, o el appUser del token ya
     *                               no existe en la base
     */
    private AppUser appUserActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            throw new IllegalStateException("Usuario no autenticado");
        }
        return appUserRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalStateException("Usuario autenticado no encontrado en el sistema"));
    }

    /**
     * Resuelve el perfil de student del appUser autenticado.
     *
     * @return el student autenticado
     * @throws IllegalArgumentException si el appUser autenticado no tiene perfil de student
     */
    private Student studentActual() {
        return studentRepository.findByAppUserId(appUserActual().getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "El usuario autenticado no tiene un perfil de estudiante asociado"));
    }

    /**
     * Variante tolerante de {@link #studentActual()} para el catalogo publico: permite que
     * un teacher o coordinador explore los topics sin perfil de student asociado.
     *
     * @return id del student autenticado, o null si quien consulta no es student
     */
    private Long studentActualIdOrNull() {
        try {
            return studentRepository.findByAppUserId(appUserActual().getId())
                    .map(Student::getId)
                    .orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
