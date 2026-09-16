package ec.edu.uteq.presustentaciones.security.service;

import ec.edu.uteq.presustentaciones.entities.Estudiante;
import ec.edu.uteq.presustentaciones.entities.Usuario;
import ec.edu.uteq.presustentaciones.repositories.EstudianteRepository;
import ec.edu.uteq.presustentaciones.repositories.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Sin ningun test dedicado pese a centralizar el patron anti-IDOR que usan
 * TutoriaController y TemaController (nunca confiar en un id de la URL para "yo mismo").
 */
@ExtendWith(MockitoExtension.class)
class UsuarioActualServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private EstudianteRepository estudianteRepository;

    @InjectMocks
    private UsuarioActualService service;

    private void autenticarComo(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null,
                        AuthorityUtils.createAuthorityList("ROLE_ESTUDIANTE")));
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    // ── usuario() ────────────────────────────────────────────────────────────

    @Test
    void usuarioLanzaExcepcionSinAuthenticationEnElContexto() {
        assertThrows(IllegalStateException.class, () -> service.usuario());
    }

    @Test
    void usuarioLanzaExcepcionSiNoEstaAutenticado() {
        Authentication auth = new UsernamePasswordAuthenticationToken("x@uteq.edu.ec", "pass");
        auth.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThrows(IllegalStateException.class, () -> service.usuario());
    }

    @Test
    void usuarioLanzaExcepcionParaUsuarioAnonimo() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThrows(IllegalStateException.class, () -> service.usuario());
    }

    @Test
    void usuarioLanzaExcepcionSiElAutenticadoNoEstaEnLaBase() {
        autenticarComo("fantasma@uteq.edu.ec");
        when(usuarioRepository.findByEmail("fantasma@uteq.edu.ec")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> service.usuario());
    }

    @Test
    void usuarioDevuelveElUsuarioAutenticado() {
        autenticarComo("estudiante@uteq.edu.ec");
        Usuario u = new Usuario();
        u.setId(5L);
        u.setEmail("estudiante@uteq.edu.ec");
        when(usuarioRepository.findByEmail("estudiante@uteq.edu.ec")).thenReturn(Optional.of(u));

        assertEquals(5L, service.usuario().getId());
    }

    // ── estudiante() ─────────────────────────────────────────────────────────

    @Test
    void estudianteLanzaExcepcionSiElUsuarioNoTienePerfilDeEstudiante() {
        autenticarComo("docente@uteq.edu.ec");
        Usuario u = new Usuario();
        u.setId(9L);
        u.setEmail("docente@uteq.edu.ec");
        when(usuarioRepository.findByEmail("docente@uteq.edu.ec")).thenReturn(Optional.of(u));
        when(estudianteRepository.findByUsuarioId(9L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.estudiante());
    }

    @Test
    void estudianteDevuelveElPerfilAsociado() {
        autenticarComo("estudiante@uteq.edu.ec");
        Usuario u = new Usuario();
        u.setId(5L);
        u.setEmail("estudiante@uteq.edu.ec");
        when(usuarioRepository.findByEmail("estudiante@uteq.edu.ec")).thenReturn(Optional.of(u));
        Estudiante e = Estudiante.builder().id(2L).usuario(u).build();
        when(estudianteRepository.findByUsuarioId(5L)).thenReturn(Optional.of(e));

        assertEquals(2L, service.estudiante().getId());
    }

    // ── estudianteIdOrNull() ─────────────────────────────────────────────────

    @Test
    void estudianteIdOrNullDevuelveNullSiNoHayAutenticacion() {
        assertNull(service.estudianteIdOrNull());
    }

    @Test
    void estudianteIdOrNullDevuelveNullSiNoTienePerfilDeEstudiante() {
        autenticarComo("docente@uteq.edu.ec");
        Usuario u = new Usuario();
        u.setId(9L);
        u.setEmail("docente@uteq.edu.ec");
        when(usuarioRepository.findByEmail("docente@uteq.edu.ec")).thenReturn(Optional.of(u));
        when(estudianteRepository.findByUsuarioId(9L)).thenReturn(Optional.empty());

        assertNull(service.estudianteIdOrNull());
    }

    @Test
    void estudianteIdOrNullDevuelveElIdReal() {
        autenticarComo("estudiante@uteq.edu.ec");
        Usuario u = new Usuario();
        u.setId(5L);
        u.setEmail("estudiante@uteq.edu.ec");
        when(usuarioRepository.findByEmail("estudiante@uteq.edu.ec")).thenReturn(Optional.of(u));
        Estudiante e = Estudiante.builder().id(2L).usuario(u).build();
        when(estudianteRepository.findByUsuarioId(5L)).thenReturn(Optional.of(e));

        assertEquals(2L, service.estudianteIdOrNull());
    }
}
