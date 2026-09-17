package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.entities.Teacher;
import ec.edu.uteq.presustentaciones.entities.Panelist;
import ec.edu.uteq.presustentaciones.entities.Tutor;
import ec.edu.uteq.presustentaciones.services.PanelistService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import ec.edu.uteq.presustentaciones.dto.ResponseWrapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/jurados")
public class PanelistController {

    private final PanelistService panelistService;

    /**
     * Construye PanelistController, inyectando panelistService.
     * @param panelistService panelistService
     */
    public PanelistController(PanelistService panelistService) {
        this.panelistService = panelistService;
    }

    // ── Panelists ───────────────────────────────────────────────────────────────

    /**
     * Asigna manualmente un teacher como panelist de una submission, con un role concreto.
     *
     * @param submissionId submission a la que se asigna el tribunal
     * @param teacherId   teacher que actuará como panelist
     * @param role         código de role en el tribunal (PRESIDENTE, VOCAL_1, VOCAL_2)
     * @return 200 con el {@link Panelist} asignado, o 400 con el motivo si el servicio lo
     *         rechaza (teacher ya asignado, role inexistente, etc.)
     */
    @PostMapping("/asignar")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'TRIBUNAL_TUTOR_ASIGNAR')")
    public ResponseEntity<?> assignPanelist(
            @RequestParam(name = "solicitudId") Long submissionId,
            @RequestParam(name = "docenteId") Long teacherId,
            @RequestParam(name = "rol") String role) {
        try {
            Panelist j = panelistService.assignPanelist(submissionId, teacherId, role);
            return ResponseEntity.ok(ResponseWrapper.success(j, "Jurado asignado exitosamente"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * Asigna automáticamente los tres panelists del tribunal repartiendo carga entre los
     * teachers disponibles.
     *
     * @param submissionId submission a la que se asigna el tribunal
     * @return 200 con la lista de panelists resultante, o 400 con el motivo si no hay
     *         suficientes teachers disponibles
     */
    @PostMapping("/asignar-automatico/{submissionId}")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'TRIBUNAL_TUTOR_ASIGNAR')")
    public ResponseEntity<?> assignAutomaticamente(@PathVariable Long submissionId) {
        try {
            panelistService.assignPanelistsAutomaticamente(submissionId);
            List<Panelist> panelists = panelistService.listPorSubmission(submissionId);
            return ResponseEntity.ok(ResponseWrapper.success(panelists, "Jurados asignados automáticamente"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * @param submissionId submission consultada
     * @return 200 con los panelists asignados a esa submission
     */
    @GetMapping("/solicitud/{submissionId}")
    public ResponseEntity<?> listPorSubmission(@PathVariable Long submissionId) {
        return ResponseEntity.ok(ResponseWrapper.success(panelistService.listPorSubmission(submissionId)));
    }

    /**
     * @param pageable página y tamaño solicitados
     * @return 200 con la página de todas las asignaciones de tribunal
     */
    @GetMapping
    @PreAuthorize("@permissionService.tienePermission(authentication, 'TRIBUNAL_TUTOR_ASIGNAR')")
    public ResponseEntity<?> listTodos(Pageable pageable) {
        return ResponseEntity.ok(ResponseWrapper.success(panelistService.listTodos(pageable)));
    }

    /**
     * Retira a un teacher del tribunal.
     *
     * @param panelistId asignación de panelist a delete
     * @return 204 sin cuerpo
     */
    @DeleteMapping("/{panelistId}")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'TRIBUNAL_TUTOR_ASIGNAR')")
    public ResponseEntity<Void> deletePanelist(@PathVariable Long panelistId) {
        panelistService.deletePanelist(panelistId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Sugiere teachers candidatos para completar el tribunal, excluyendo a los ya asignados.
     *
     * @param submissionId submission para la que se buscan candidatos
     * @param cantidad    número máximo de sugerencias (5 por defecto)
     * @return 200 con la lista de teachers sugeridos
     */
    @GetMapping("/sugerencias/{submissionId}")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'TRIBUNAL_TUTOR_ASIGNAR')")
    public ResponseEntity<?> sugerirTeachers(
            @PathVariable Long submissionId,
            @RequestParam(defaultValue = "5") int cantidad) {
        return ResponseEntity.ok(ResponseWrapper.success(panelistService.sugerirTeachers(submissionId, cantidad)));
    }

    // ── Tutor ─────────────────────────────────────────────────────────────────

    /**
     * Asigna un teacher como tutor de la submission.
     *
     * @param submissionId submission a tutorar
     * @param teacherId   teacher que asumirá la tutoría
     * @return 200 con el {@link Tutor} creado, o 400 con el motivo si ya tiene tutor
     */
    @PostMapping("/tutor/asignar")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'TRIBUNAL_TUTOR_ASIGNAR')")
    public ResponseEntity<?> assignTutor(
            @RequestParam(name = "solicitudId") Long submissionId,
            @RequestParam(name = "docenteId") Long teacherId) {
        try {
            Tutor t = panelistService.assignTutor(submissionId, teacherId);
            return ResponseEntity.ok(ResponseWrapper.success(t, "Tutor asignado exitosamente"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * @param submissionId submission consultada
     * @return 200 con el tutor activo, o 404 si la submission no tiene tutor asignado
     */
    @GetMapping("/tutor/solicitud/{submissionId}")
    public ResponseEntity<?> obtainTutor(@PathVariable Long submissionId) {
        return panelistService.obtainTutorDeSubmission(submissionId)
                .map(t -> ResponseEntity.ok(ResponseWrapper.success(t)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Retira la tutoría asignada.
     *
     * @param tutorId tutoría a delete
     * @return 204 sin cuerpo
     */
    @DeleteMapping("/tutor/{tutorId}")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'TRIBUNAL_TUTOR_ASIGNAR')")
    public ResponseEntity<Void> deleteTutor(@PathVariable Long tutorId) {
        panelistService.deleteTutor(tutorId);
        return ResponseEntity.noContent().build();
    }

    // ── Vistas del teacher como panelist ────────────────────────────────────────

    /**
     * @param teacherId teacher consultado
     * @return 200 con todas las submissions en las que ese teacher es panelist
     */
    @GetMapping("/docente/{teacherId}")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'TRIBUNAL_TUTOR_ASIGNAR') or @permissionService.esPropioTeacher(authentication, #teacherId)")
    public ResponseEntity<?> listPorTeacher(@PathVariable Long teacherId) {
        return ResponseEntity.ok(ResponseWrapper.success(panelistService.listPorTeacher(teacherId)));
    }

    /**
     * @param teacherId teacher consultado
     * @return 200 con todas las tutorías a cargo de ese teacher
     */
    @GetMapping("/tutor/docente/{teacherId}")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'TRIBUNAL_TUTOR_ASIGNAR') or @permissionService.esPropioTeacher(authentication, #teacherId)")
    public ResponseEntity<?> listTutoringsPorTeacher(@PathVariable Long teacherId) {
        return ResponseEntity.ok(ResponseWrapper.success(panelistService.listTutoringsPorTeacher(teacherId)));
    }

    /**
     * Datos del panelist que un appUser concreto ocupa en una submission, usados por la
     * pantalla de calificación para saber con qué role firma quien está viendo la página.
     *
     * @param submissionId submission consultada
     * @param appUserId   appUser del que se quiere conocer su role en ese tribunal
     * @return 200 con id, role, confirmación y nombre del teacher; o 200 con datos nulos
     *         si ese appUser no es panelist de la submission
     */
    @GetMapping("/info/{submissionId}/{appUserId}")
    public ResponseEntity<?> obtainInfoPanelist(@PathVariable Long submissionId, @PathVariable Long appUserId) {
        Optional<Panelist> panelistOpt = panelistService.obtainInfoPanelist(submissionId, appUserId);
        if (panelistOpt.isPresent()) {
            Panelist panelist = panelistOpt.get();
            String nombreTeacher = "";
            if (panelist.getTeacher() != null && panelist.getTeacher().getAppUser() != null) {
                nombreTeacher = panelist.getTeacher().getAppUser().getNombre() + " " 
                        + panelist.getTeacher().getAppUser().getApellido();
            }
            return ResponseEntity.ok(ResponseWrapper.success(Map.of(
                    "id", panelist.getId(),
                    "rol", panelist.getRole() != null ? panelist.getRole() : "",
                    "confirmado", panelist.isConfirmado(),
                    "nombreDocente", nombreTeacher
            )));
        }
        return ResponseEntity.ok(ResponseWrapper.success(null));
    }

    /**
     * SP (Fase 3): Asignación masiva de panelists por role.
     * Llama a presus.sp_assign_panelist_masivo(p_submission_ids, p_teacher_ids, p_rol)
     * Flujo: POST → PanelistController → PanelistService → PanelistRepository → SP → PostgreSQL
     *
     * Body esperado: { "solicitudIds": [1,2,3], "docenteIds": [4,5,6], "rol": "PRESIDENTE" }
     *
     * @param body mapa con submissionIds, teacherIds y role; los ids llegan como enteros JSON
     *             y se convierten a Long para el procedimiento
     * @return 200 con el número de asignaciones ejecutadas y el role aplicado; 400 si falta
     *         alguna de las tres claves o si el procedimiento rechaza el lote (en cuyo caso
     *         la transacción revierte el lote completo)
     */
    @PostMapping("/asignar-masivo")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'TRIBUNAL_TUTOR_ASIGNAR')")
    public ResponseEntity<?> assignMasivo(@RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<Integer> submissionIdsList = (List<Integer>) body.get("solicitudIds");
            @SuppressWarnings("unchecked")
            List<Integer> teacherIdsList   = (List<Integer>) body.get("docenteIds");
            String role = (String) body.get("rol");

            if (submissionIdsList == null || teacherIdsList == null || role == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Se requieren 'solicitudIds', 'docenteIds' y 'rol'"));
            }
            Long[] submissionIds = submissionIdsList.stream().map(i -> i.longValue()).toArray(Long[]::new);
            Long[] teacherIds   = teacherIdsList.stream().map(i -> i.longValue()).toArray(Long[]::new);

            panelistService.assignPanelistMasivoSP(submissionIds, teacherIds, role);
            return ResponseEntity.ok(ResponseWrapper.success(Map.of(
                    "mensaje", "Asignación masiva ejecutada correctamente",
                    "asignados", submissionIds.length,
                    "rol", role
            ), "Asignación masiva ejecutada correctamente"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }
}
