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

    /**
     * Busca el/los registro(s) con email.
     * @param email email
     * @return el registro si existe, vacío si no
     */
    Optional<AppUser> findByEmail(String email);

    /**
     * Indica si existe algún registro con email.
     * @param email email
     * @return true si se cumple la condición, false si no
     */
    boolean existsByEmail(String email);

    /**
     * Busca el/los registro(s) con activo true.
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<AppUser> findByActivoTrue();

    /** La tabla puede tener decenas de miles de filas (datos de carga k6) — el listado del panel de admin siempre pagina. */
    @Query("SELECT u FROM AppUser u WHERE :q IS NULL OR :q = '' " +
           "OR LOWER(u.nombre) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(u.apellido) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(u.role) LIKE LOWER(CONCAT('%', :q, '%'))")
    /**
     * Search paginado.
     * @param q q
     * @param pageable pageable
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    Page<AppUser> searchPaged(@Param("q") String q, Pageable pageable);

    @Modifying
    @Query("UPDATE AppUser u SET u.emailNotifications = :emailNoti, u.phone = :telefono WHERE u.id = :id")
    /**
     * Update perfil.
     * @param id id
     * @param emailNotifications emailNotifications
     * @param phone phone
     * @return la cantidad de registros
     */
    int updateProfile(@Param("id") Long id,
                         @Param("emailNoti") String emailNotifications,
                         @Param("telefono") String phone);

    /**
     * Busca el/los registro(s) con role.
     * @param role role
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<AppUser> findByRole(String role);
}