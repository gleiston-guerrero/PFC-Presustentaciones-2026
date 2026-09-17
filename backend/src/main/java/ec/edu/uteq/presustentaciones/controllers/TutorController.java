package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.dto.MiStudentTutoradoDTO;
import ec.edu.uteq.presustentaciones.entities.Tutor;
import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.repositories.AppUserRepository;
import ec.edu.uteq.presustentaciones.services.TutorService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/tutores")
public class TutorController {

    private final TutorService tutorService;
    private final AppUserRepository appUserRepository;

    /**
     * Construye TutorController, inyectando tutorService, appUserRepository.
     * @param tutorService tutorService
     * @param appUserRepository appUserRepository
     */
    public TutorController(TutorService tutorService, AppUserRepository appUserRepository) {
        this.tutorService = tutorService;
        this.appUserRepository = appUserRepository;
    }

    /**
     * "Mis Estudiantes" (teacher): roster de los students que el teacher autenticado
     * tiene asignados como tutor. Sin permission dedicado porque cualquier DOCENTE debe poder
     * consultar sus propios students (mismo criterio que /api/tutorings/teacher/{id});
     * el appUser se resuelve desde el token, nunca desde un parámetro del cliente.
     *
     * @return 200 con el roster de students tutorados por el teacher autenticado
     * @throws RuntimeException si el token es válido pero su appUser ya no existe en la base
     */
    @GetMapping("/mis-estudiantes")
    public ResponseEntity<List<MiStudentTutoradoDTO>> misStudents() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        AppUser appUser = appUserRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado"));
        return ResponseEntity.ok(tutorService.misStudents(appUser.getId()));
    }

    /**
     * Asigna un teacher como tutor de una submission.
     *
     * @param submissionId submission a tutorar
     * @param teacherId   teacher que asumirá la tutoría
     * @return 200 con el {@link Tutor} creado, o 400 sin cuerpo si el servicio lo rechaza
     *         (por ejemplo, si la submission ya tiene tutor)
     */
    @PostMapping("/asignar")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'TRIBUNAL_TUTOR_ASIGNAR')")
    public ResponseEntity<Tutor> assign(@RequestParam(name = "solicitudId") Long submissionId,
                                         @RequestParam(name = "docenteId") Long teacherId) {
        try {
            return ResponseEntity.ok(tutorService.assignTutor(submissionId, teacherId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * @param submissionId submission consultada
     * @return 200 con el tutor asignado, o 404 si la submission aún no tiene tutor
     */
    @GetMapping("/solicitud/{submissionId}")
    public ResponseEntity<Tutor> porSubmission(@PathVariable Long submissionId) {
        return tutorService.searchPorSubmission(submissionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * @param pageable página y tamaño solicitados
     * @return 200 con la página de tutorías asignadas
     */
    @GetMapping
    public ResponseEntity<Page<Tutor>> list(Pageable pageable) {
        return ResponseEntity.ok(tutorService.listTodos(pageable));
    }

    /**
     * Retira la asignación de tutoría.
     *
     * @param id tutoría a delete
     * @return 204 sin cuerpo
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'TRIBUNAL_TUTOR_ASIGNAR')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        tutorService.deleteTutor(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * SP (Fase 3): Estadísticas consolidadas del desempeño de tutores.
     * Llama a presus.sp_obtain_estadisticas_tutores().
     * Flujo: GET → TutorController → TutorService → TutorRepository → SP → PostgreSQL
     *
     * @return 200 con una fila por teacher (id, nombre, tutorías activas, completadas y
     *         fases aprobadas), o 400 con el error si el procedimiento falla en la base
     */
    @GetMapping("/estadisticas")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'EVALUACION_RUBRICA_REGISTRAR')")
    public ResponseEntity<?> estadisticas() {
        try {
            List<Map<String, Object>> stats = tutorService.obtainEstadisticasTutoresSP();
            return ResponseEntity.ok(stats);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
