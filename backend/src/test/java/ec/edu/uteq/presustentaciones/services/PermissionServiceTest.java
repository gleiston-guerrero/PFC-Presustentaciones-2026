package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.Teacher;
import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.repositories.TeacherRepository;
import ec.edu.uteq.presustentaciones.repositories.PermissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PermissionService es el punto unico de autorizacion referenciado desde
 * @PreAuthorize("@permissionService.hasPermission(...)") en todos los controllers protegidos
 * (ver AppUserController y AuthControllerIntegrationTest). Sin tests dedicados pese a ser
 * el bean de seguridad mas invocado del backend.
 */
@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private TeacherRepository teacherRepository;

    @InjectMocks
    private PermissionService permissionService;

    @Test
    void hasPermissionRetornaFalseSiAuthenticationEsNull() {
        assertFalse(permissionService.hasPermission(null, "USUARIOS_GESTIONAR"));
        verify(permissionRepository, never()).appUserTienePermission(anyString(), anyString());
    }

    @Test
    void hasPermissionRetornaFalseSiNoIsAuthenticated() {
        Authentication auth = new UsernamePasswordAuthenticationToken("user@uteq.edu.ec", "pass");
        auth.setAuthenticated(false);

        assertFalse(permissionService.hasPermission(auth, "USUARIOS_GESTIONAR"));
        verify(permissionRepository, never()).appUserTienePermission(anyString(), anyString());
    }

    @Test
    void hasPermissionRetornaFalseForAppUserAnonimo() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "anonymousUser", null, AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        assertFalse(permissionService.hasPermission(auth, "USUARIOS_GESTIONAR"));
        verify(permissionRepository, never()).appUserTienePermission(anyString(), anyString());
    }

    @Test
    void hasPermissionDelegaAlRepositorioWithElEmailDelAppUserAuthenticated() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "coordinador@uteq.edu.ec", null, AuthorityUtils.createAuthorityList("ROLE_COORDINADOR"));
        when(permissionRepository.appUserTienePermission("coordinador@uteq.edu.ec", "USUARIOS_GESTIONAR"))
                .thenReturn(true);

        assertTrue(permissionService.hasPermission(auth, "USUARIOS_GESTIONAR"));
        verify(permissionRepository).appUserTienePermission("coordinador@uteq.edu.ec", "USUARIOS_GESTIONAR");
    }

    @Test
    void hasPermissionRetornaFalseSiElRepositorioNoEncuentraElPermission() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "estudiante@uteq.edu.ec", null, AuthorityUtils.createAuthorityList("ROLE_ESTUDIANTE"));
        when(permissionRepository.appUserTienePermission("estudiante@uteq.edu.ec", "USUARIOS_GESTIONAR"))
                .thenReturn(false);

        assertFalse(permissionService.hasPermission(auth, "USUARIOS_GESTIONAR"));
    }

    // ── permissionsDe ───────────────────────────────────────────────────────────

    @Test
    void permissionsOfRetornaListaVaciaSiAuthenticationEsNull() {
        assertEquals(List.of(), permissionService.permissionsOf(null));
        verify(permissionRepository, never()).findCodigosByEmail(anyString());
    }

    @Test
    void permissionsOfRetornaListaVaciaSiNoIsAuthenticated() {
        Authentication auth = new UsernamePasswordAuthenticationToken("user@uteq.edu.ec", "pass");
        auth.setAuthenticated(false);

        assertEquals(List.of(), permissionService.permissionsOf(auth));
    }

    @Test
    void permissionsOfRetornaListaVaciaForAppUserAnonimo() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "anonymousUser", null, AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        assertEquals(List.of(), permissionService.permissionsOf(auth));
    }

    @Test
    void permissionsOfDelegaAlRepositorioWithElEmailDelAppUserAuthenticated() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "docente@uteq.edu.ec", null, AuthorityUtils.createAuthorityList("ROLE_DOCENTE"));
        when(permissionRepository.findCodigosByEmail("docente@uteq.edu.ec"))
                .thenReturn(List.of("SOLICITUDES_VER", "ACTAS_VER_PROPIAS"));

        assertEquals(List.of("SOLICITUDES_VER", "ACTAS_VER_PROPIAS"), permissionService.permissionsOf(auth));
    }

    // ── isOwnTeacher ──────────────────────────────────────────────────────

    @Test
    void isOwnTeacherRetornaFalseSiAuthenticationEsNull() {
        assertFalse(permissionService.isOwnTeacher(null, 1L));
        verify(teacherRepository, never()).findById(anyLong());
    }

    @Test
    void isOwnTeacherRetornaFalseSiNoIsAuthenticated() {
        Authentication auth = new UsernamePasswordAuthenticationToken("user@uteq.edu.ec", "pass");
        auth.setAuthenticated(false);

        assertFalse(permissionService.isOwnTeacher(auth, 1L));
    }

    @Test
    void isOwnTeacherRetornaFalseForAppUserAnonimo() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "anonymousUser", null, AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        assertFalse(permissionService.isOwnTeacher(auth, 1L));
    }

    @Test
    void isOwnTeacherRetornaFalseSiElTeacherNoExists() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "docente@uteq.edu.ec", null, AuthorityUtils.createAuthorityList("ROLE_DOCENTE"));
        when(teacherRepository.findById(99L)).thenReturn(Optional.empty());

        assertFalse(permissionService.isOwnTeacher(auth, 99L));
    }

    @Test
    void isOwnTeacherRetornaFalseSiElTeacherNoTieneAppUserAsociado() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "docente@uteq.edu.ec", null, AuthorityUtils.createAuthorityList("ROLE_DOCENTE"));
        Teacher teacher = Teacher.builder().id(7L).appUser(null).build();
        when(teacherRepository.findById(7L)).thenReturn(Optional.of(teacher));

        assertFalse(permissionService.isOwnTeacher(auth, 7L));
    }

    @Test
    void isOwnTeacherRetornaFalseSiElEmailNoCoincide() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "docente@uteq.edu.ec", null, AuthorityUtils.createAuthorityList("ROLE_DOCENTE"));
        AppUser otroAppUser = new AppUser();
        otroAppUser.setEmail("otro@uteq.edu.ec");
        Teacher teacher = Teacher.builder().id(7L).appUser(otroAppUser).build();
        when(teacherRepository.findById(7L)).thenReturn(Optional.of(teacher));

        assertFalse(permissionService.isOwnTeacher(auth, 7L));
    }

    @Test
    void isOwnTeacherRetornaTrueSiElEmailCoincide() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "docente@uteq.edu.ec", null, AuthorityUtils.createAuthorityList("ROLE_DOCENTE"));
        AppUser mismoAppUser = new AppUser();
        mismoAppUser.setEmail("docente@uteq.edu.ec");
        Teacher teacher = Teacher.builder().id(7L).appUser(mismoAppUser).build();
        when(teacherRepository.findById(7L)).thenReturn(Optional.of(teacher));

        assertTrue(permissionService.isOwnTeacher(auth, 7L));
    }
}
