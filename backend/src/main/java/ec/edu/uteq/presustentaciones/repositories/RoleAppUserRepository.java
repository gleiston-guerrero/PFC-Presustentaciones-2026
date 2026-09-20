package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.RoleAppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Contrato. Repositorio de acceso a datos de role app user.
 */
@Repository
public interface RoleAppUserRepository extends JpaRepository<RoleAppUser, Short> {
    /**
     * Busca el/los registro(s) con codigo.
     * @param code código de negocio único del registro buscado
     * @return el registro si existe, vacío si no
     */
    Optional<RoleAppUser> findByCode(String code);
}
