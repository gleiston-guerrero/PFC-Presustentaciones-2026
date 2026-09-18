package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.dto.UpdateStudentRequest;
import ec.edu.uteq.presustentaciones.dto.CreateStudentRequest;
import ec.edu.uteq.presustentaciones.dto.StudentDTO;
import ec.edu.uteq.presustentaciones.entities.StatusAcademic;
import ec.edu.uteq.presustentaciones.services.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Gestión de students: register (appUser + perfil académico) y editar program,
 * semestre, período de ingreso y estado académico. */
@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/estudiantes")
@RequiredArgsConstructor
@PreAuthorize("@permissionService.hasPermission(authentication, 'ESTUDIANTES_GESTIONAR')")
public class StudentController {

    private final StudentService studentService;

    /**
     * Listado paginado de students con búsqueda de texto libre.
     *
     * @param page número de página (0 por defecto)
     * @param size filas por página (20 por defecto)
     * @param q    búsqueda sobre nombre, apellido, correo o expediente, opcional
     * @return 200 con la página de students
     */
    @GetMapping("/paginado")
    public ResponseEntity<Page<StudentDTO>> listPaged(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "q", required = false) String q) {
        return ResponseEntity.ok(studentService.listPaged(page, size, q));
    }

    /**
     * @param id perfil de student consultado
     * @return 200 con la ficha del student, o el error correspondiente si no existe
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> obtainById(@PathVariable("id") Long id) {
        try {
            return ResponseEntity.ok(studentService.obtainById(id));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Registra un student creando de una vez su appUser autenticable y su perfil académico
     * (program, período de ingreso, semestre, expediente).
     *
     * @param req datos del appUser y del perfil académico
     * @return 200 con el student creado, o el error de validación correspondiente
     */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateStudentRequest req) {
        try {
            return ResponseEntity.ok(studentService.create(req));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Edita los datos académicos de un student (program, semestre, estado académico).
     *
     * @param id  perfil de student a update
     * @param req campos a modificar
     * @return 200 con el student actualizado, o el error correspondiente
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable("id") Long id, @RequestBody UpdateStudentRequest req) {
        try {
            return ResponseEntity.ok(studentService.update(id, req));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * @return catálogo de estados académicos posibles, para poblar el selector del formulario
     */
    @GetMapping("/estados-academicos")
    public List<StatusAcademic> statusesAcademic() {
        return studentService.listStatusesAcademic();
    }
}
