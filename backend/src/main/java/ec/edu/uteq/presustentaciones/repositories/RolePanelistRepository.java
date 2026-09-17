package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.RolePanelist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RolePanelistRepository extends JpaRepository<RolePanelist, Short> {
    /**
     * Busca el/los registro(s) con codigo.
     * @param codigo codigo
     * @return el registro si existe, vacío si no
     */
    Optional<RolePanelist> findByCodigo(String codigo);
}
