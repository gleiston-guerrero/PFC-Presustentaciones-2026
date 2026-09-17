package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.entities.Teacher;
import ec.edu.uteq.presustentaciones.repositories.TeacherRepository;
import ec.edu.uteq.presustentaciones.security.service.AppUserActualService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/docentes")
@PreAuthorize("isAuthenticated()")
public class TeacherController {

    private final TeacherRepository teacherRepository;
    private final AppUserActualService appUserActualService;

    /**
     * Construye TeacherController, inyectando teacherRepository, appUserActualService.
     * @param teacherRepository teacherRepository
     * @param appUserActualService appUserActualService
     */
    public TeacherController(TeacherRepository teacherRepository, AppUserActualService appUserActualService) {
        this.teacherRepository = teacherRepository;
        this.appUserActualService = appUserActualService;
    }

    /**
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
    public Page<Teacher> listPaginado(@RequestParam(required = false) String q, Pageable pageable) {
        return teacherRepository.searchPaginado(q, pageable);
    }

    /**
     * @return teachers disponibles para asignación de tribunal o tutoría
     */
    @GetMapping("/disponibles")
    public List<Teacher> disponibles() {
        return teacherRepository.findByDisponibleTrue();
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
    public ResponseEntity<Teacher> obtain(@PathVariable Long id) {
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
    public ResponseEntity<Teacher> obtainPorAppUser(@PathVariable Long appUserId) {
        validateAccesoPropioOAdministrativo(appUserId);
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
    private void validateAccesoPropioOAdministrativo(Long appUserIdObjetivo) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean esAdminOCoordinador = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_COORDINADOR"));
        if (esAdminOCoordinador) {
            return;
        }
        Long appUserActualId = appUserActualService.appUser().getId();
        if (!appUserActualId.equals(appUserIdObjetivo)) {
            throw new AccessDeniedException("No tienes permiso para consultar el perfil de otro docente");
        }
    }
}