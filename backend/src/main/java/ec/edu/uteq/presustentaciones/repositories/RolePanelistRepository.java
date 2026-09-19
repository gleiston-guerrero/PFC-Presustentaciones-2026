package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.RolePanelist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Contrato. Repositorio de acceso a datos de role panelist.
 */
@Repository
public interface RolePanelistRepository extends JpaRepository<RolePanelist, Short> {
    /**
     * Busca el/los registro(s) con codigo.
     * @param code code
     * @return el registro si existe, vacío si no
     */
    Optional<RolePanelist> findByCode(String code);
}
