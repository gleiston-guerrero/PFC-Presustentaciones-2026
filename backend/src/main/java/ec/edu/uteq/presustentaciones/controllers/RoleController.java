package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.dto.RoleDTO;
import ec.edu.uteq.presustentaciones.entities.RoleAppUser;
import ec.edu.uteq.presustentaciones.repositories.PermissionRepository;
import ec.edu.uteq.presustentaciones.repositories.RoleAppUserRepository;
import ec.edu.uteq.presustentaciones.repositories.AppUserRepository;
import ec.edu.uteq.presustentaciones.services.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CRUD de roles del sistema. La asignación de permissions a cada role vive en
 * PermissionController -- aquí solo se administra el catálogo de roles en sí
 * (codigo, nombre) y su eliminación.
 */
@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@PreAuthorize("@permissionService.tienePermission(authentication, 'ROLES_PERMISOS_GESTIONAR')")
public class RoleController {

    private final RoleAppUserRepository roleAppUserRepository;
    private final PermissionRepository permissionRepository;
    private final AppUserRepository appUserRepository;
    private final AuditService auditService;

    /** Los 4 roles con los que arranca el sistema -- no se pueden delete ni rename
     * el código porque el resto de la aplicación (frontend roleeGuard, AppUser.role,
     * flujos de negocio) todavía distingue casos por estos 4 nombres exactos. Roles
     * nuevos que se creen desde aquí sí se pueden delete libremente. */
    private static final Set<String> ROLES_PROTEGIDOS = Set.of("ADMIN", "DOCENTE", "COORDINADOR", "ESTUDIANTE");

    /**
     * @return los roles del sistema, cada uno con su count de appUsers asignados y la lista
     *         de codigos de permission que tiene concedidos
     */
    @GetMapping
    public List<RoleDTO> list() {
        return roleAppUserRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Crea un role nuevo. El codigo se normaliza a mayusculas con guiones bajos, y el id se
     * calcula como el mayor existente mas uno.
     *
     * @param body codigo y nombre del role; ambos obligatorios
     * @return 200 con el role creado, o 400 si falta un campo o el codigo ya existe
     */
    @PostMapping
    @Transactional
    public ResponseEntity<?> create(@RequestBody Map<String, String> body) {
        String codigo = body.getOrDefault("codigo", "").trim().toUpperCase().replaceAll("\\s+", "_");
        String nombre = body.getOrDefault("nombre", "").trim();
        if (codigo.isEmpty() || nombre.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Código y nombre son obligatorios."));
        }
        if (roleAppUserRepository.findByCodigo(codigo).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Ya existe un rol con ese código."));
        }
        short siguienteId = (short) (roleAppUserRepository.findAll().stream()
                .mapToInt(RoleAppUser::getId).max().orElse(0) + 1);
        auditService.marcarActorActual();
        RoleAppUser role = roleAppUserRepository.save(
                RoleAppUser.builder().id(siguienteId).codigo(codigo).nombre(nombre).build());
        return ResponseEntity.ok(toDto(role));
    }

    /**
     * Renombra un role. El codigo no se toca porque lo usan las expresiones @PreAuthorize y el
     * campo heredado AppUser.role; renamelo dejaria esas referencias huerfanas.
     *
     * @param id   role a rename
     * @param body nuevo nombre visible
     * @return 200 con el role actualizado, 404 si no existe, o 400 si el nombre viene vacio
     */
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> rename(@PathVariable("id") Short id, @RequestBody Map<String, String> body) {
        auditService.marcarActorActual();
        RoleAppUser role = roleAppUserRepository.findById(id).orElse(null);
        if (role == null) {
            return ResponseEntity.notFound().build();
        }
        String nombre = body.getOrDefault("nombre", "").trim();
        if (nombre.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El nombre no puede estar vacío."));
        }
        // El código (usado por @PreAuthorize y por el role legado en AppUser.role) no se
        // renombra para no dejar huérfanas las referencias existentes -- solo el nombre visible.
        role.setNombre(nombre);
        return ResponseEntity.ok(toDto(roleAppUserRepository.save(role)));
    }

    /**
     * Elimina un role creado por el equipo. Los cuatro roles base (ADMIN, DOCENTE,
     * COORDINADOR, ESTUDIANTE) estan protegidos porque el frontend y AppUser.role todavia
     * distinguen casos por esos codigos exactos.
     *
     * @param id role a delete
     * @return 204 si se elimino; 404 si no existe; 400 si es un role base, si tiene appUsers
     *         asignados, o si la base rechaza el borrado por referencias
     */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> delete(@PathVariable("id") Short id) {
        RoleAppUser role = roleAppUserRepository.findById(id).orElse(null);
        if (role == null) {
            return ResponseEntity.notFound().build();
        }
        if (ROLES_PROTEGIDOS.contains(role.getCodigo())) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "El rol " + role.getCodigo() + " es uno de los 4 roles base del sistema y no se puede eliminar."));
        }
        long appUsersConEsteRole = appUserRepository.findByRole(role.getCodigo()).size();
        if (appUsersConEsteRole > 0) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "No se puede eliminar: hay " + appUsersConEsteRole + " usuario(s) con este rol asignado."));
        }
        try {
            auditService.marcarActorActual();
            roleAppUserRepository.delete(role);
            return ResponseEntity.noContent().build();
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "No se pudo eliminar el rol: tiene referencias asociadas."));
        }
    }

    /**
     * Arma el DTO de un role agregando datos que no viven en la entidad: cuantos appUsers lo
     * tienen asignado y que permissions concretos concede.
     *
     * @param role entidad de role a convertir
     * @return el DTO listo para el frontend
     */
    private RoleDTO toDto(RoleAppUser role) {
        return RoleDTO.builder()
                .id(role.getId())
                .codigo(role.getCodigo())
                .nombre(role.getNombre())
                .appUsersAsignados(appUserRepository.findByRole(role.getCodigo()).size())
                .permissions(permissionRepository.findCodigosPorRole(role.getId()))
                .build();
    }
}
