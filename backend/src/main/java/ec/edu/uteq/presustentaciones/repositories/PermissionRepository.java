package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Short> {

    /**
     * Devuelve todos los registros con o der by categoria asc nombre asc.
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<Permission> findAllByOrderByCategoriaAscNombreAsc();

    /**
     * Busca el/los registro(s) con codigo in.
     * @param codigos codigos
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<Permission> findByCodeIn(List<String> codigos);

    /**
     * Find role ids con permission.
     * @param code code
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    @Query(value = "SELECT rp.rol_id FROM presus.rol_permisos rp " +
            "JOIN presus.permisos p ON p.id = rp.permiso_id " +
            "WHERE p.codigo = :codigo", nativeQuery = true)
    List<Short> findRoleIdsWithPermission(@Param("codigo") String code);

    /**
     * Punto único de verificación de acceso -- reemplaza los @PreAuthorize("hasRole(...)")
     * fijos en código. Consulta directa contra la tabla de sesión (appUsers.role_id) unida
     * a role_permissions, así que un cambio en "Gestionar Permisos" aplica de inmediato, sin
     * esperar a que el appUser vuelva a iniciar sesión (el JWT no lleva permissions, solo
     * identidad -- por diseño, para que esto sea realmente dinámico).
     * @param email email
     * @param code code
     * @return true si se cumple la condición, false si no
     */
    @Query(value = "SELECT EXISTS (" +
            "  SELECT 1 FROM presus.rol_permisos rp " +
            "  JOIN presus.permisos p ON p.id = rp.permiso_id " +
            "  JOIN presus.usuarios u ON u.rol_id = rp.rol_id " +
            "  WHERE u.email = :email AND p.codigo = :codigo" +
            ")", nativeQuery = true)
    boolean appUserTienePermission(@Param("email") String email, @Param("codigo") String code);

    /**
     * Find codigos por role.
     * @param roleId roleId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    @Query(value = "SELECT p.codigo FROM presus.permisos p " +
            "JOIN presus.rol_permisos rp ON rp.permiso_id = p.id " +
            "WHERE rp.rol_id = :roleId", nativeQuery = true)
    List<String> findCodigosByRole(@Param("roleId") Short roleId);

    /**
     * Todos los códigos de permission del appUser (vía su role). Lo usa el frontend para
     * mostrar/ocultar módulos: al remove un permission a un role, el módulo desaparece del
     * panel sin necesidad de que el appUser vuelva a iniciar sesión. Misma unión que
     * {@link #appUserTienePermission}, pero devolviendo la lista completa.
     * @param email email
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    @Query(value = "SELECT p.codigo FROM presus.permisos p " +
            "JOIN presus.rol_permisos rp ON rp.permiso_id = p.id " +
            "JOIN presus.usuarios u ON u.rol_id = rp.rol_id " +
            "WHERE u.email = :email", nativeQuery = true)
    List<String> findCodigosByEmail(@Param("email") String email);

    /**
     * Delete permissions de role.
     * @param roleId roleId
     */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM presus.rol_permisos WHERE rol_id = :roleId", nativeQuery = true)
    void deletePermissionsDeRole(@Param("roleId") Short roleId);

    /**
     * Assign permission.
     * @param roleId roleId
     * @param permissionId permissionId
     */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO presus.rol_permisos (rol_id, permiso_id) VALUES (:roleId, :permissionId) ON CONFLICT DO NOTHING", nativeQuery = true)
    void assignPermission(@Param("roleId") Short roleId, @Param("permissionId") Short permissionId);
}
