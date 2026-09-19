package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.entities.Teacher;
import ec.edu.uteq.presustentaciones.repositories.TeacherRepository;
import ec.edu.uteq.presustentaciones.security.service.CurrentAppUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST de teacher.
 */
@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/docentes")
@PreAuthorize("isAuthenticated()")
public class TeacherController {

    private final TeacherRepository teacherRepository;
    private final CurrentAppUserService currentAppUserService;

    /**
     * Construye TeacherController, inyectando teacherRepository, appUserActualService.
     * @param teacherRepository teacherRepository
     * @param currentAppUserService currentAppUserService
     */
    public TeacherController(TeacherRepository teacherRepository, CurrentAppUserService currentAppUserService) {
        this.teacherRepository = teacherRepository;
        this.currentAppUserService = currentAppUserService;
    }

    /**
     * List.
     * @return todos los teachers registrados; para el panel de administración conviene usar
     *         la versión paginada
     */
    @GetMapping
    public List<Teacher> list() {
        return teacherRepository.findAll();
    }

    /**
     * Versión paginada -- misma convención que /api/v1/submissions/paginado y
     * /api/v1/appUsers/paginado. ERR-02: agrega búsqueda de texto libre ("q") para
     * alimentar un combobox con typeahead en vez de list los 9,807 teachers de una vez.
     *
     * @param q        búsqueda de texto libre sobre el teacher, opcional
     * @param pageable página y tamaño solicitados
     * @return página de teachers que cumplen el filtro
     */
    @GetMapping("/paginado")
    public Page<Teacher> listPaged(@RequestParam(name = "q", required = false) String q, Pageable pageable) {
        return teacherRepository.searchPaged(q, pageable);
    }

    /**
     * Available.
     * @return teachers disponibles para asignación de tribunal o tutoría
     */
    @GetMapping("/disponibles")
    public List<Teacher> available() {
        return teacherRepository.findByAvailableTrue();
    }

    /**
     * Directorio interno por id de Teacher (no de AppUser): mismo dato ya expuesto en block
     * por {@code /}, {@code /paginado} y {@code /disponibles} (selección de panelist/tutor,
     * asignación de room, etc.), así que no aplica un control de propiedad aquí -- restringirlo
     * rompería esos flujos sin cerrar ninguna fuga real, ya que el mismo teacher ya es visible
     * listando todos.
     *
     * @param id identificador del Teacher (no del AppUser)
     * @return 200 con el teacher, o 404 si no existe
     */
    @GetMapping("/{id}")
    public ResponseEntity<Teacher> obtain(@PathVariable("id") Long id) {
        return teacherRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * A diferencia de {@code /{id}}, este endpoint resuelve el perfil de Teacher a partir de un
     * appUserId -- pensado para que un teacher autenticado consulte su propio perfil (así lo usa
     * el frontend: sign-minutes-teacher y mis-asignaciones siempre pasan authService.getUserId()).
     * Sin control de propiedad, cualquier autenticado podía enumerar appUserId ajenos. ADMIN y
     * COORDINADOR conservan acceso completo (mismo criterio administrativo que el resto del
     * sistema); cualquier otro appUser solo puede consultar su propio appUserId.
     *
     * @param appUserId identificador del AppUser cuyo perfil de Teacher se busca
     * @return 200 con el teacher, o 404 si ese appUser no tiene perfil de teacher
     * @throws AccessDeniedException si un appUser sin role administrativo pide un appUserId
     *                               distinto del suyo
     */
    @GetMapping("/usuario/{appUserId}")
    public ResponseEntity<Teacher> obtainByAppUser(@PathVariable("appUserId") Long appUserId) {
        validateAccessOwnOrAdministrative(appUserId);
        return teacherRepository.findByAppUserId(appUserId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Exige que quien consulta sea ADMIN/COORDINADOR o el dueño del resource.
     *
     * @param appUserIdObjetivo appUser cuyo perfil se quiere consultar
     * @throws AccessDeniedException si no se cumple ninguna de las dos condiciones
     */
    private void validateAccessOwnOrAdministrative(Long appUserIdObjetivo) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdminOrCoordinator = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_COORDINADOR"));
        if (isAdminOrCoordinator) {
            return;
        }
        Long currentAppUserId = currentAppUserService.appUser().getId();
        if (!currentAppUserId.equals(appUserIdObjetivo)) {
            throw new AccessDeniedException("No tienes permiso para consultar el perfil de otro docente");
        }
    }
}