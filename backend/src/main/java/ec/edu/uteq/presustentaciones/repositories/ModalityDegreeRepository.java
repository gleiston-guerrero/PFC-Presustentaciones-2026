package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.ModalityDegree;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Contrato. Repositorio de acceso a datos de modality degree.
 */
@Repository
public interface ModalityDegreeRepository extends JpaRepository<ModalityDegree, Short> {
    /**
     * Busca el/los registro(s) con codigo.
     * @param code code
     * @return el registro si existe, vacío si no
     */
    Optional<ModalityDegree> findByCode(String code);
}
