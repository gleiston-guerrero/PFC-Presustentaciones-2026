package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.ResultadoEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ResultadoEvaluationRepository extends JpaRepository<ResultadoEvaluation, Short> {
    /**
     * Busca el/los registro(s) con codigo.
     * @param codigo codigo
     * @return el registro si existe, vacío si no
     */
    Optional<ResultadoEvaluation> findByCodigo(String codigo);
}
