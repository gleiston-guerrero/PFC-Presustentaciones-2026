package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.CriterioRubric;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CriterioRubricRepository extends JpaRepository<CriterioRubric, Long> {
    /**
     * Busca el/los registro(s) con rubric id o der by o den asc.
     * @param rubricId rubricId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<CriterioRubric> findByRubricIdOrderByOrdenAsc(Long rubricId);
}
