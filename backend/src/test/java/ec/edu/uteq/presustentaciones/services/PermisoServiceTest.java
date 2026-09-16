package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.Docente;
import ec.edu.uteq.presustentaciones.entities.Usuario;
import ec.edu.uteq.presustentaciones.repositories.DocenteRepository;
import ec.edu.uteq.presustentaciones.repositories.PermisoRepository;
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
 * PermisoService es el punto unico de autorizacion referenciado desde
 * @PreAuthorize("@permisoService.tienePermiso(...)") en todos los controllers protegidos
 * (ver UsuarioController y AuthControllerIntegrationTest). Sin tests dedicados pese a ser
 * el bean de seguridad mas invocado del backend.
 */
@ExtendWith(MockitoExtension.class)
class PermisoServiceTest {

    @Mock
    private PermisoRepository permisoRepository;

    @Mock
    private DocenteRepository docenteRepository;

    @InjectMocks
    private PermisoService permisoService;

    @Test
    void tienePermisoRetornaFalseSiAuthenticationEsNull() {
        assertFalse(permisoService.tienePermiso(null, "USUARIOS_GESTIONAR"));
        verify(permisoRepository, never()).usuarioTienePermiso(anyString(), anyString());
    }

    @Test
    void tienePermisoRetornaFalseSiNoEstaAutenticado() {
        Authentication auth = new UsernamePasswordAuthenticationToken("user@uteq.edu.ec", "pass");
        auth.setAuthenticated(false);

        assertFalse(permisoService.tienePermiso(auth, "USUARIOS_GESTIONAR"));
        verify(permisoRepository, never()).usuarioTienePermiso(anyString(), anyString());
    }

    @Test
    void tienePermisoRetornaFalseParaUsuarioAnonimo() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "anonymousUser", null, AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        assertFalse(permisoService.tienePermiso(auth, "USUARIOS_GESTIONAR"));
        verify(permisoRepository, never()).usuarioTienePermiso(anyString(), anyString());
    }

    @Test
    void tienePermisoDelegaAlRepositorioConElEmailDelUsuarioAutenticado() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "coordinador@uteq.edu.ec", null, AuthorityUtils.createAuthorityList("ROLE_COORDINADOR"));
        when(permisoRepository.usuarioTienePermiso("coordinador@uteq.edu.ec", "USUARIOS_GESTIONAR"))
                .thenReturn(true);

        assertTrue(permisoService.tienePermiso(auth, "USUARIOS_GESTIONAR"));
        verify(permisoRepository).usuarioTienePermiso("coordinador@uteq.edu.ec", "USUARIOS_GESTIONAR");
    }

    @Test
    void tienePermisoRetornaFalseSiElRepositorioNoEncuentraElPermiso() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "estudiante@uteq.edu.ec", null, AuthorityUtils.createAuthorityList("ROLE_ESTUDIANTE"));
        when(permisoRepository.usuarioTienePermiso("estudiante@uteq.edu.ec", "USUARIOS_GESTIONAR"))
                .thenReturn(false);

        assertFalse(permisoService.tienePermiso(auth, "USUARIOS_GESTIONAR"));
    }

    // ── permisosDe ───────────────────────────────────────────────────────────

    @Test
    void permisosDeRetornaListaVaciaSiAuthenticationEsNull() {
        assertEquals(List.of(), permisoService.permisosDe(null));
        verify(permisoRepository, never()).findCodigosPorEmail(anyString());
    }

    @Test
    void permisosDeRetornaListaVaciaSiNoEstaAutenticado() {
        Authentication auth = new UsernamePasswordAuthenticationToken("user@uteq.edu.ec", "pass");
        auth.setAuthenticated(false);

        assertEquals(List.of(), permisoService.permisosDe(auth));
    }

    @Test
    void permisosDeRetornaListaVaciaParaUsuarioAnonimo() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "anonymousUser", null, AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        assertEquals(List.of(), permisoService.permisosDe(auth));
    }

    @Test
    void permisosDeDelegaAlRepositorioConElEmailDelUsuarioAutenticado() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "docente@uteq.edu.ec", null, AuthorityUtils.createAuthorityList("ROLE_DOCENTE"));
        when(permisoRepository.findCodigosPorEmail("docente@uteq.edu.ec"))
                .thenReturn(List.of("SOLICITUDES_VER", "ACTAS_VER_PROPIAS"));

        assertEquals(List.of("SOLICITUDES_VER", "ACTAS_VER_PROPIAS"), permisoService.permisosDe(auth));
    }

    // ── esPropioDocente ──────────────────────────────────────────────────────

    @Test
    void esPropioDocenteRetornaFalseSiAuthenticationEsNull() {
        assertFalse(permisoService.esPropioDocente(null, 1L));
        verify(docenteRepository, never()).findById(anyLong());
    }

    @Test
    void esPropioDocenteRetornaFalseSiNoEstaAutenticado() {
        Authentication auth = new UsernamePasswordAuthenticationToken("user@uteq.edu.ec", "pass");
        auth.setAuthenticated(false);

        assertFalse(permisoService.esPropioDocente(auth, 1L));
    }

    @Test
    void esPropioDocenteRetornaFalseParaUsuarioAnonimo() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "anonymousUser", null, AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        assertFalse(permisoService.esPropioDocente(auth, 1L));
    }

    @Test
    void esPropioDocenteRetornaFalseSiElDocenteNoExiste() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "docente@uteq.edu.ec", null, AuthorityUtils.createAuthorityList("ROLE_DOCENTE"));
        when(docenteRepository.findById(99L)).thenReturn(Optional.empty());

        assertFalse(permisoService.esPropioDocente(auth, 99L));
    }

    @Test
    void esPropioDocenteRetornaFalseSiElDocenteNoTieneUsuarioAsociado() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "docente@uteq.edu.ec", null, AuthorityUtils.createAuthorityList("ROLE_DOCENTE"));
        Docente docente = Docente.builder().id(7L).usuario(null).build();
        when(docenteRepository.findById(7L)).thenReturn(Optional.of(docente));

        assertFalse(permisoService.esPropioDocente(auth, 7L));
    }

    @Test
    void esPropioDocenteRetornaFalseSiElEmailNoCoincide() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "docente@uteq.edu.ec", null, AuthorityUtils.createAuthorityList("ROLE_DOCENTE"));
        Usuario otroUsuario = new Usuario();
        otroUsuario.setEmail("otro@uteq.edu.ec");
        Docente docente = Docente.builder().id(7L).usuario(otroUsuario).build();
        when(docenteRepository.findById(7L)).thenReturn(Optional.of(docente));

        assertFalse(permisoService.esPropioDocente(auth, 7L));
    }

    @Test
    void esPropioDocenteRetornaTrueSiElEmailCoincide() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "docente@uteq.edu.ec", null, AuthorityUtils.createAuthorityList("ROLE_DOCENTE"));
        Usuario mismoUsuario = new Usuario();
        mismoUsuario.setEmail("docente@uteq.edu.ec");
        Docente docente = Docente.builder().id(7L).usuario(mismoUsuario).build();
        when(docenteRepository.findById(7L)).thenReturn(Optional.of(docente));

        assertTrue(permisoService.esPropioDocente(auth, 7L));
    }
}
