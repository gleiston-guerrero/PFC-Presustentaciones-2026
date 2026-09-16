package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.entities.CriterioRubrica;
import ec.edu.uteq.presustentaciones.entities.Rubrica;
import ec.edu.uteq.presustentaciones.repositories.CriterioRubricaRepository;
import ec.edu.uteq.presustentaciones.repositories.RubricaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/rubricas")
@RequiredArgsConstructor
public class RubricaController {

    private final RubricaRepository rubricaRepository;
    private final CriterioRubricaRepository criterioRepository;

    /**
     * @param pageable pagina y tamano solicitados
     * @return pagina de rubricas de evaluacion
     */
    @GetMapping
    public Page<Rubrica> listar(Pageable pageable) { return rubricaRepository.findAll(pageable); }

    /**
     * @param id rubrica consultada
     * @return 200 con la rubrica, o 404 si no existe
     */
    @GetMapping("/{id}")
    public ResponseEntity<Rubrica> obtener(@PathVariable Long id) {
        return rubricaRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Crea una rubrica de evaluacion.
     *
     * @param rubrica datos de la rubrica
     * @return la rubrica persistida con su id asignado
     */
    @PostMapping
    @PreAuthorize("@permisoService.tienePermiso(authentication, 'RUBRICA_GESTIONAR')")
    public Rubrica crear(@RequestBody Rubrica rubrica) { return rubricaRepository.save(rubrica); }

    /**
     * @param id rubrica a eliminar
     * @return 204 sin cuerpo
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@permisoService.tienePermiso(authentication, 'RUBRICA_GESTIONAR')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        rubricaRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Agrega un criterio a una rubrica existente.
     *
     * @param rubricaId rubrica a la que se agrega el criterio
     * @param criterio  criterio con su descripcion y peso
     * @return 200 con el criterio creado, o el error si la rubrica no existe
     */
    @PostMapping("/{rubricaId}/criterios")
    @PreAuthorize("@permisoService.tienePermiso(authentication, 'RUBRICA_GESTIONAR')")
    public ResponseEntity<?> agregarCriterio(@PathVariable Long rubricaId,
                                              @RequestBody CriterioRubrica criterio) {
        Rubrica rubrica = rubricaRepository.findById(rubricaId)
                .orElseThrow(() -> new RuntimeException("Rúbrica no encontrada"));
        criterio.setRubrica(rubrica);
        return ResponseEntity.ok(criterioRepository.save(criterio));
    }

    /**
     * @param rubricaId rubrica consultada
     * @return criterios que componen esa rubrica
     */
    @GetMapping("/{rubricaId}/criterios")
    public List<CriterioRubrica> criterios(@PathVariable Long rubricaId) {
        return criterioRepository.findByRubricaIdOrderByOrdenAsc(rubricaId);
    }

    /**
     * Inicializa los 3 criterios institucionales UTEQ:
     *   Propuesta  → máx 6 pts (100%=6, 67%=4, 33%=2, 0%=0)
     *   Documento  → máx 3 pts (100%=3, 67%=2, 33%=1, 0%=0)
     *   Exposición → máx 1 pt  (100%=1, 67%=0.7, 33%=0.3, 0%=0)
     *   Total máximo = 10 pts
     *
     * @param rubricaId id de la rúbrica a inicializar
     * @return 200 con los criterios creados, o 400 si la rúbrica ya tiene criterios
     */
    @PostMapping("/{rubricaId}/inicializar-criterios")
    @PreAuthorize("@permisoService.tienePermiso(authentication, 'RUBRICA_GESTIONAR')")
    public ResponseEntity<?> inicializarCriteriosInstitucionales(@PathVariable Long rubricaId) {
        Rubrica rubrica = rubricaRepository.findById(rubricaId)
                .orElseThrow(() -> new RuntimeException("Rúbrica no encontrada"));

        List<CriterioRubrica> existentes = criterioRepository.findByRubricaIdOrderByOrdenAsc(rubricaId);
        if (!existentes.isEmpty()) {
            // Si ya existen, devolver los existentes (no error, para re-uso en frontend)
            return ResponseEntity.ok(Map.of(
                "mensaje", "La rúbrica ya tiene criterios.",
                "criterios", existentes
            ));
        }

        criterioRepository.save(CriterioRubrica.builder()
                .rubrica(rubrica)
                .nombre("Propuesta")
                .descripcion("La propuesta (software, algoritmos, dispositivos, etc.) está completamente desarrollada siguiendo buenas prácticas de Ingeniería de Software, cumpliendo los requisitos establecidos.")
                .ponderacion(6.0).orden(1).build());

        criterioRepository.save(CriterioRubrica.builder()
                .rubrica(rubrica)
                .nombre("Documento")
                .descripcion("El contenido del documento (informe) es de alta calidad, bien estructurado y redactado con claridad, cumpliendo buenas prácticas en la elaboración de informes técnicos.")
                .ponderacion(3.0).orden(2).build());

        criterioRepository.save(CriterioRubrica.builder()
                .rubrica(rubrica)
                .nombre("Exposición")
                .descripcion("La exposición es clara, bien estructurada y adecuada para una defensa de titulación, demostrando dominio del tema.")
                .ponderacion(1.0).orden(3).build());

        rubrica.setPuntajeMaximo(10.0);
        rubricaRepository.save(rubrica);

        return ResponseEntity.ok(Map.of(
                "mensaje", "Criterios institucionales UTEQ inicializados correctamente.",
                "criterios", criterioRepository.findByRubricaIdOrderByOrdenAsc(rubricaId)
        ));
    }
}
