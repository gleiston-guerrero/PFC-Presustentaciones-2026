package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.dto.SaveResourceRequest;
import ec.edu.uteq.presustentaciones.dto.ResourceDegreeDTO;
import ec.edu.uteq.presustentaciones.entities.Student;
import ec.edu.uteq.presustentaciones.repositories.StudentRepository;
import ec.edu.uteq.presustentaciones.security.service.CurrentAppUserService;
import ec.edu.uteq.presustentaciones.services.ResourceDegreeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Centro de Resources de Titulación. La consulta está abierta a cualquier appUser
 * autenticado (si es student, por defecto ve los resources generales + los de su
 * program); la gestión requiere el permission {@code ORIENTACION_CATALOGO_GESTIONAR}.
 */
@RestController
@RequestMapping("/api/v1/orientacion/recursos")
@RequiredArgsConstructor
public class ResourceDegreeController {

    private final ResourceDegreeService resourceService;
    private final CurrentAppUserService currentAppUser;
    private final StudentRepository studentRepository;

    /**
     * Resources de apoyo a la titulación. Si no se indica program, se resuelve la del
     * student autenticado, de modo que cada quien ve los materiales de su propia program
     * sin tener que pasarla explícitamente.
     *
     * @param programId program de la que se quieren los resources; si es null se usa la del
     *                  student autenticado
     * @return 200 con los resources correspondientes
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ResourceDegreeDTO>> list(
            @RequestParam(name = "carreraId", required = false) Integer programId) {
        Integer efectivo = programId;
        if (efectivo == null) {
            Long studentId = currentAppUser.studentIdOrNull();
            if (studentId != null) {
                efectivo = studentRepository.findById(studentId)
                        .map(Student::getProgramEntidad)
                        .map(c -> c.getId())
                        .orElse(null);
            }
        }
        return ResponseEntity.ok(resourceService.list(efectivo));
    }

    /**
     * Publica un resource nuevo en el Centro de Orientación.
     *
     * @param request datos del resource, validados con Bean Validation
     * @return 200 con el resource creado
     */
    @PostMapping
    @PreAuthorize("@permissionService.hasPermission(authentication, 'ORIENTACION_CATALOGO_GESTIONAR')")
    public ResponseEntity<ResourceDegreeDTO> create(@RequestBody @Valid SaveResourceRequest request) {
        return ResponseEntity.status(201).body(resourceService.create(request));
    }

    /**
     * Edita un resource publicado.
     *
     * @param id      resource a update
     * @param request nuevos datos del resource
     * @return 200 con el resource actualizado
     */
    @PutMapping("/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'ORIENTACION_CATALOGO_GESTIONAR')")
    public ResponseEntity<ResourceDegreeDTO> update(@PathVariable("id") Integer id,
                                                           @RequestBody @Valid SaveResourceRequest request) {
        return ResponseEntity.ok(resourceService.update(id, request));
    }

    /**
     * Retira un resource del Centro de Orientación.
     *
     * @param id resource a delete
     * @return 204 sin cuerpo
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'ORIENTACION_CATALOGO_GESTIONAR')")
    public ResponseEntity<Void> delete(@PathVariable("id") Integer id) {
        resourceService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
