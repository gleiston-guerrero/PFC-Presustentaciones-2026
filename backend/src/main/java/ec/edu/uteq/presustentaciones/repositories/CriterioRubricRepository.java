package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.CriterioRubric;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CriterioRubricRepository extends JpaRepository<CriterioRubric, Long> {
    List<CriterioRubric> findByRubricIdOrderByOrdenAsc(Long rubricId);
}
