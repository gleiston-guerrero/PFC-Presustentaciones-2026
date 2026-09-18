package ec.edu.uteq.presustentaciones.security.service;

import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.repositories.AppUserRepository;
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
    private AppUserRepository appUserRepository;

    @InjectMocks
    private CustomUserDetailsService service;

    @Test
    void lanzaUsernameNotFoundSiElAppUserNoExists() {
        when(appUserRepository.findByEmail("nadie@uteq.edu.ec")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("nadie@uteq.edu.ec"));
    }

    @Test
    void devuelveUserDetailsHabilitadoForUnAppUserActivo() {
        AppUser u = new AppUser();
        u.setEmail("docente@uteq.edu.ec");
        u.setPassword("hash-bcrypt");
        u.setRole("DOCENTE");
        u.setActivo(true);
        when(appUserRepository.findByEmail("docente@uteq.edu.ec")).thenReturn(Optional.of(u));

        UserDetails details = service.loadUserByUsername("docente@uteq.edu.ec");

        assertEquals("docente@uteq.edu.ec", details.getUsername());
        assertEquals("hash-bcrypt", details.getPassword());
        assertTrue(details.isEnabled());
        assertTrue(details.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_DOCENTE")));
    }

    @Test
    void devuelveUserDetailsDeshabilitadoForUnAppUserInactivo() {
        AppUser u = new AppUser();
        u.setEmail("retirado@uteq.edu.ec");
        u.setPassword("hash");
        u.setRole("ESTUDIANTE");
        u.setActivo(false);
        when(appUserRepository.findByEmail("retirado@uteq.edu.ec")).thenReturn(Optional.of(u));

        UserDetails details = service.loadUserByUsername("retirado@uteq.edu.ec");

        assertFalse(details.isEnabled());
    }
}
