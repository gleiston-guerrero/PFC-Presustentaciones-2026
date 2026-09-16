package ec.edu.uteq.presustentaciones.security.service;

import ec.edu.uteq.presustentaciones.entities.Student;
import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.repositories.StudentRepository;
import ec.edu.uteq.presustentaciones.repositories.AppUserRepository;
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
 * TutoringController y TopicController (nunca confiar en un id de la URL para "yo mismo").
 */
@ExtendWith(MockitoExtension.class)
class AppUserActualServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private StudentRepository studentRepository;

    @InjectMocks
    private AppUserActualService service;

    private void autenticarComo(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null,
                        AuthorityUtils.createAuthorityList("ROLE_ESTUDIANTE")));
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    // ── appUser() ────────────────────────────────────────────────────────────

    @Test
    void appUserLanzaExcepcionSinAuthenticationEnElContexto() {
        assertThrows(IllegalStateException.class, () -> service.appUser());
    }

    @Test
    void appUserLanzaExcepcionSiNoEstaAutenticado() {
        Authentication auth = new UsernamePasswordAuthenticationToken("x@uteq.edu.ec", "pass");
        auth.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThrows(IllegalStateException.class, () -> service.appUser());
    }

    @Test
    void appUserLanzaExcepcionParaAppUserAnonimo() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThrows(IllegalStateException.class, () -> service.appUser());
    }

    @Test
    void appUserLanzaExcepcionSiElAutenticadoNoEstaEnLaBase() {
        autenticarComo("fantasma@uteq.edu.ec");
        when(appUserRepository.findByEmail("fantasma@uteq.edu.ec")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> service.appUser());
    }

    @Test
    void appUserDevuelveElAppUserAutenticado() {
        autenticarComo("estudiante@uteq.edu.ec");
        AppUser u = new AppUser();
        u.setId(5L);
        u.setEmail("estudiante@uteq.edu.ec");
        when(appUserRepository.findByEmail("estudiante@uteq.edu.ec")).thenReturn(Optional.of(u));

        assertEquals(5L, service.appUser().getId());
    }

    // ── student() ─────────────────────────────────────────────────────────

    @Test
    void studentLanzaExcepcionSiElAppUserNoTienePerfilDeStudent() {
        autenticarComo("docente@uteq.edu.ec");
        AppUser u = new AppUser();
        u.setId(9L);
        u.setEmail("docente@uteq.edu.ec");
        when(appUserRepository.findByEmail("docente@uteq.edu.ec")).thenReturn(Optional.of(u));
        when(studentRepository.findByAppUserId(9L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.student());
    }

    @Test
    void studentDevuelveElPerfilAsociado() {
        autenticarComo("estudiante@uteq.edu.ec");
        AppUser u = new AppUser();
        u.setId(5L);
        u.setEmail("estudiante@uteq.edu.ec");
        when(appUserRepository.findByEmail("estudiante@uteq.edu.ec")).thenReturn(Optional.of(u));
        Student e = Student.builder().id(2L).appUser(u).build();
        when(studentRepository.findByAppUserId(5L)).thenReturn(Optional.of(e));

        assertEquals(2L, service.student().getId());
    }

    // ── studentIdOrNull() ─────────────────────────────────────────────────

    @Test
    void studentIdOrNullDevuelveNullSiNoHayAutenticacion() {
        assertNull(service.studentIdOrNull());
    }

    @Test
    void studentIdOrNullDevuelveNullSiNoTienePerfilDeStudent() {
        autenticarComo("docente@uteq.edu.ec");
        AppUser u = new AppUser();
        u.setId(9L);
        u.setEmail("docente@uteq.edu.ec");
        when(appUserRepository.findByEmail("docente@uteq.edu.ec")).thenReturn(Optional.of(u));
        when(studentRepository.findByAppUserId(9L)).thenReturn(Optional.empty());

        assertNull(service.studentIdOrNull());
    }

    @Test
    void studentIdOrNullDevuelveElIdReal() {
        autenticarComo("estudiante@uteq.edu.ec");
        AppUser u = new AppUser();
        u.setId(5L);
        u.setEmail("estudiante@uteq.edu.ec");
        when(appUserRepository.findByEmail("estudiante@uteq.edu.ec")).thenReturn(Optional.of(u));
        Student e = Student.builder().id(2L).appUser(u).build();
        when(studentRepository.findByAppUserId(5L)).thenReturn(Optional.of(e));

        assertEquals(2L, service.studentIdOrNull());
    }
}
