package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.TipoEvaluator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TipoEvaluatorRepository extends JpaRepository<TipoEvaluator, Short> {
    Optional<TipoEvaluator> findByCodigo(String codigo);
}
