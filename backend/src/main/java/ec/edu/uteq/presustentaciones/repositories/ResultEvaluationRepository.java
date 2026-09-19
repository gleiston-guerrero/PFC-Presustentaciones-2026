package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.ResultEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Contrato. Repositorio de acceso a datos de result evaluation.
 */
@Repository
public interface ResultEvaluationRepository extends JpaRepository<ResultEvaluation, Short> {
    /**
     * Busca el/los registro(s) con codigo.
     * @param code code
     * @return el registro si existe, vacío si no
     */
    Optional<ResultEvaluation> findByCode(String code);
}
