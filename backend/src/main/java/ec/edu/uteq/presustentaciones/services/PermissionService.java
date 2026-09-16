package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.repositories.PermissionRepository;
import ec.edu.uteq.presustentaciones.repositories.TeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Bean invocado desde @PreAuthorize("@permissionService.tienePermission(authentication, 'CODIGO')")
 * en cada controlador protegido. Reemplaza los hasRole/hasAnyRole fijos en código: el role
 * del appUser autenticado y sus permissions viven en la base de datos (roles_appUser,
 * permissions, role_permissions) y son editables desde "Gestionar Roles" / "Gestionar Permisos".
 */
@Service("permissionService")
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final TeacherRepository teacherRepository;

    /**
     * @param authentication autenticación del appUser a evaluar
     * @param codigoPermission  código del permission requerido
     * @return {@code true} si el appUser autenticado tiene ese permission vía su role
     */
    public boolean tienePermission(Authentication authentication, String codigoPermission) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        String email = authentication.getName();
        if (email == null || "anonymousUser".equals(email)) {
            return false;
        }
        return permissionRepository.appUserTienePermission(email, codigoPermission);
    }

    /**
     * Códigos de permission del appUser autenticado. El frontend los usa para ocultar los
     * módulos cuyo permission se ha retirado al role (sin re-login).
     *
     * @param authentication autenticación del appUser
     * @return los códigos de permission de ese appUser, o lista vacía si no está autenticado
     */
    public java.util.List<String> permissionsDe(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return java.util.List.of();
        }
        String email = authentication.getName();
        if (email == null || "anonymousUser".equals(email)) {
            return java.util.List.of();
        }
        return permissionRepository.findCodigosPorEmail(email);
    }

    /**
     * @param authentication autenticación del appUser a evaluar
     * @param teacherId      id del teacher a comparar
     * @return {@code true} si el appUser autenticado es el teacher vinculado a ese id
     */
    public boolean esPropioTeacher(Authentication authentication, Long teacherId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        String email = authentication.getName();
        if (email == null || "anonymousUser".equals(email)) {
            return false;
        }
        return teacherRepository.findById(teacherId)
                .map(teacher -> teacher.getAppUser() != null && email.equals(teacher.getAppUser().getEmail()))
                .orElse(false);
    }
}
