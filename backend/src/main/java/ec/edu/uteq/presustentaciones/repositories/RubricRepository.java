package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.Rubric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Contrato. Repositorio de acceso a datos de rubric.
 */
@Repository
public interface RubricRepository extends JpaRepository<Rubric, Long> {
}
