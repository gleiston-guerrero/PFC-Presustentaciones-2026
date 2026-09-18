package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.entities.Permission;
import ec.edu.uteq.presustentaciones.entities.RoleAppUser;
import ec.edu.uteq.presustentaciones.repositories.PermissionRepository;
import ec.edu.uteq.presustentaciones.repositories.RoleAppUserRepository;
import ec.edu.uteq.presustentaciones.services.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Catálogo de permissions del sistema y asignación de permissions a roles
 * ("Gestionar Permisos" en el panel de administrador). Reemplaza los
 * hasRole/hasAnyRole fijos en código -- ver PermissionService.tienePermission,
 * invocado desde @PreAuthorize en cada controlador protegido.
 */
@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/permisos")
@RequiredArgsConstructor
@PreAuthorize("@permissionService.tienePermission(authentication, 'ROLES_PERMISOS_GESTIONAR')")
public class PermissionController {

    private final PermissionRepository permissionRepository;
    private final RoleAppUserRepository roleAppUserRepository;
    private final AuditService auditService;

    /**
     * Catalogo completo de permissions disponibles, que el frontend agrupa por categoria.
     *
     * @return permissions ordenados por categoria y nombre
     */
    @GetMapping
    public List<Permission> list() {
        return permissionRepository.findAllByOrderByCategoriaAscNombreAsc();
    }

    /**
     * Reemplaza por completo el conjunto de permissions de un role (checkbox matrix en el
     * frontend: se envía la lista final de códigos marcados, no un delta).
     * Salvaguarda: si el resultado dejaría a NINGÚN role en el sistema con
     * ROLES_PERMISOS_GESTIONAR, se rechaza -- de lo contrario un admin podría removese
     * a sí mismo (y a todos) el acceso para volver a corregirlo, sin salida salvo tocar
     * la base de datos directamente.
     *
     * @param roleId           role cuyos permissions se reemplazan
     * @param codigosPermissions lista final de codigos marcados (no un delta); se admiten
     *                        repetidos, el count se hace sobre los distintos
     * @return 200 con los codigos resultantes del role; 404 si el role no existe; 400 si algun
     *         codigo no existe o si la operacion dejaria al sistema sin ningun role capaz de
     *         gestionar permissions
     */
    @PutMapping("/rol/{roleId}")
    @Transactional
    public ResponseEntity<?> updatePermissionsDeRole(@PathVariable("roleId") Short roleId, @RequestBody List<String> codigosPermissions) {
        RoleAppUser role = roleAppUserRepository.findById(roleId).orElse(null);
        if (role == null) {
            return ResponseEntity.notFound().build();
        }

        List<Permission> permissions = permissionRepository.findByCodigoIn(codigosPermissions);
        if (permissions.size() != codigosPermissions.stream().distinct().count()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Uno o más códigos de permiso no existen."));
        }

        boolean incluyeGestionPermissions = codigosPermissions.contains("ROLES_PERMISOS_GESTIONAR");
        if (!incluyeGestionPermissions) {
            List<Short> otrosRolesConEsePermission = permissionRepository.findRoleIdsConPermission("ROLES_PERMISOS_GESTIONAR")
                    .stream().filter(id -> !id.equals(roleId)).toList();
            if (otrosRolesConEsePermission.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error",
                        "No se puede quitar 'Gestionar roles y permisos' de este rol: ningún otro rol lo tendría, " +
                        "y nadie podría volver a asignarlo desde la interfaz."));
            }
        }

        auditService.marcarActorActual();
        permissionRepository.deletePermissionsDeRole(roleId);
        for (Permission p : permissions) {
            permissionRepository.assignPermission(roleId, p.getId());
        }
        return ResponseEntity.ok(permissionRepository.findCodigosPorRole(roleId));
    }
}
