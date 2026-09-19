package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.CriterionRubric;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * Contrato. Repositorio de acceso a datos de criterion rubric.
 */
public interface CriterionRubricRepository extends JpaRepository<CriterionRubric, Long> {
    /**
     * Busca el/los registro(s) con rubric id o der by o den asc.
     * @param rubricId rubricId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<CriterionRubric> findByRubricIdOrderByOrdenAsc(Long rubricId);
}
