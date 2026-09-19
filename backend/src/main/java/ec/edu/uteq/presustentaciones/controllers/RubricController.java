package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.entities.CriterionRubric;
import ec.edu.uteq.presustentaciones.entities.Rubric;
import ec.edu.uteq.presustentaciones.repositories.CriterionRubricRepository;
import ec.edu.uteq.presustentaciones.repositories.RubricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Controlador REST de rubric.
 */
@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/rubricas")
@RequiredArgsConstructor
public class RubricController {

    private final RubricRepository rubricRepository;
    private final CriterionRubricRepository criterionRepository;

    /**
     * List.
     * @param pageable pagina y tamano solicitados
     * @return pagina de rubrics de evaluation
     */
    @GetMapping
    public Page<Rubric> list(Pageable pageable) { return rubricRepository.findAll(pageable); }

    /**
     * Obtain.
     * @param id rubric consultada
     * @return 200 con la rubric, o 404 si no existe
     */
    @GetMapping("/{id}")
    public ResponseEntity<Rubric> obtain(@PathVariable("id") Long id) {
        return rubricRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Crea una rubric de evaluation.
     *
     * @param rubric datos de la rubric
     * @return la rubric persistida con su id asignado
     */
    @PostMapping
    @PreAuthorize("@permissionService.hasPermission(authentication, 'RUBRICA_GESTIONAR')")
    public Rubric create(@RequestBody Rubric rubric) { return rubricRepository.save(rubric); }

    /**
     * Elimina los registros.
     * @param id rubric a delete
     * @return 204 sin cuerpo
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'RUBRICA_GESTIONAR')")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        rubricRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Agrega un criterio a una rubric existente.
     *
     * @param rubricId rubric a la que se agrega el criterio
     * @param criterion  criterio con su descripcion y peso
     * @return 200 con el criterio creado, o el error si la rubric no existe
     */
    @PostMapping("/{rubricId}/criterios")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'RUBRICA_GESTIONAR')")
    public ResponseEntity<?> addCriterion(@PathVariable("rubricId") Long rubricId,
                                              @RequestBody CriterionRubric criterion) {
        Rubric rubric = rubricRepository.findById(rubricId)
                .orElseThrow(() -> new RuntimeException("Rúbrica no encontrada"));
        criterion.setRubric(rubric);
        return ResponseEntity.ok(criterionRepository.save(criterion));
    }

    /**
     * Criteria.
     * @param rubricId rubric consultada
     * @return criterios que componen esa rubric
     */
    @GetMapping("/{rubricId}/criterios")
    public List<CriterionRubric> criteria(@PathVariable("rubricId") Long rubricId) {
        return criterionRepository.findByRubricIdOrderByOrdenAsc(rubricId);
    }

    /**
     * Inicializa los 3 criterios institucionales UTEQ:
     *   Propuesta  → máx 6 pts (100%=6, 67%=4, 33%=2, 0%=0)
     *   Documento  → máx 3 pts (100%=3, 67%=2, 33%=1, 0%=0)
     *   Exposición → máx 1 pt  (100%=1, 67%=0.7, 33%=0.3, 0%=0)
     *   Total máximo = 10 pts
     *
     * @param rubricId id de la rúbrica a inicializar
     * @return 200 con los criterios creados, o 400 si la rúbrica ya tiene criterios
     */
    @PostMapping("/{rubricId}/inicializar-criterios")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'RUBRICA_GESTIONAR')")
    public ResponseEntity<?> initializeCriteriaInstitutional(@PathVariable("rubricId") Long rubricId) {
        Rubric rubric = rubricRepository.findById(rubricId)
                .orElseThrow(() -> new RuntimeException("Rúbrica no encontrada"));

        List<CriterionRubric> existentes = criterionRepository.findByRubricIdOrderByOrdenAsc(rubricId);
        if (!existentes.isEmpty()) {
            // Si ya existen, devolver los existentes (no error, para re-uso en frontend)
            return ResponseEntity.ok(Map.of(
                "mensaje", "La rúbrica ya tiene criterios.",
                "criterios", existentes
            ));
        }

        criterionRepository.save(CriterionRubric.builder()
                .rubric(rubric)
                .nombre("Propuesta")
                .description("La propuesta (software, algoritmos, dispositivos, etc.) está completamente desarrollada siguiendo buenas prácticas de Ingeniería de Software, cumpliendo los requisitos establecidos.")
                .ponderacion(6.0).orden(1).build());

        criterionRepository.save(CriterionRubric.builder()
                .rubric(rubric)
                .nombre("Documento")
                .description("El contenido del documento (informe) es de alta calidad, bien estructurado y redactado con claridad, cumpliendo buenas prácticas en la elaboración de informes técnicos.")
                .ponderacion(3.0).orden(2).build());

        criterionRepository.save(CriterionRubric.builder()
                .rubric(rubric)
                .nombre("Exposición")
                .description("La exposición es clara, bien estructurada y adecuada para una defensa de titulación, demostrando dominio del tema.")
                .ponderacion(1.0).orden(3).build());

        rubric.setPuntajeMaximo(10.0);
        rubricRepository.save(rubric);

        return ResponseEntity.ok(Map.of(
                "mensaje", "Criterios institucionales UTEQ inicializados correctamente.",
                "criterios", criterionRepository.findByRubricIdOrderByOrdenAsc(rubricId)
        ));
    }
}
