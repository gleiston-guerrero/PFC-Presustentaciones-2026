package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    List<AppUser> findByActivoTrue();

    /** La tabla puede tener decenas de miles de filas (datos de carga k6) — el listado del panel de admin siempre pagina. */
    @Query("SELECT u FROM AppUser u WHERE :q IS NULL OR :q = '' " +
           "OR LOWER(u.nombre) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(u.apellido) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(u.role) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<AppUser> searchPaginado(@Param("q") String q, Pageable pageable);

    @Modifying
    @Query("UPDATE AppUser u SET u.emailNotifications = :emailNoti, u.telefono = :telefono WHERE u.id = :id")
    int updatePerfil(@Param("id") Long id,
                         @Param("emailNoti") String emailNotifications,
                         @Param("telefono") String telefono);

    List<AppUser> findByRole(String role);
}