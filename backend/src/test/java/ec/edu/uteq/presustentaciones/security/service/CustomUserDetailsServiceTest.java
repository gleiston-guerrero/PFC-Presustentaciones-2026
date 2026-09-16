package ec.edu.uteq.presustentaciones.security.service;

import ec.edu.uteq.presustentaciones.entities.Usuario;
import ec.edu.uteq.presustentaciones.repositories.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/** Sin ningun test dedicado pese a ser el bean que autentica cada login. */
@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private CustomUserDetailsService service;

    @Test
    void lanzaUsernameNotFoundSiElUsuarioNoExiste() {
        when(usuarioRepository.findByEmail("nadie@uteq.edu.ec")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("nadie@uteq.edu.ec"));
    }

    @Test
    void devuelveUserDetailsHabilitadoParaUnUsuarioActivo() {
        Usuario u = new Usuario();
        u.setEmail("docente@uteq.edu.ec");
        u.setPassword("hash-bcrypt");
        u.setRol("DOCENTE");
        u.setActivo(true);
        when(usuarioRepository.findByEmail("docente@uteq.edu.ec")).thenReturn(Optional.of(u));

        UserDetails details = service.loadUserByUsername("docente@uteq.edu.ec");

        assertEquals("docente@uteq.edu.ec", details.getUsername());
        assertEquals("hash-bcrypt", details.getPassword());
        assertTrue(details.isEnabled());
        assertTrue(details.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_DOCENTE")));
    }

    @Test
    void devuelveUserDetailsDeshabilitadoParaUnUsuarioInactivo() {
        Usuario u = new Usuario();
        u.setEmail("retirado@uteq.edu.ec");
        u.setPassword("hash");
        u.setRol("ESTUDIANTE");
        u.setActivo(false);
        when(usuarioRepository.findByEmail("retirado@uteq.edu.ec")).thenReturn(Optional.of(u));

        UserDetails details = service.loadUserByUsername("retirado@uteq.edu.ec");

        assertFalse(details.isEnabled());
    }
}
