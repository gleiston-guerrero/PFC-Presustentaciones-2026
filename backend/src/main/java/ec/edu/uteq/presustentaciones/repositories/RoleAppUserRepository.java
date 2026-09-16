package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.RoleAppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleAppUserRepository extends JpaRepository<RoleAppUser, Short> {
    Optional<RoleAppUser> findByCodigo(String codigo);
}
