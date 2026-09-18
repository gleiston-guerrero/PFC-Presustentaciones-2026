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
class CurrentAppUserServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private StudentRepository studentRepository;

    @InjectMocks
    private CurrentAppUserService service;

    private void authenticateAs(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null,
                        AuthorityUtils.createAuthorityList("ROLE_ESTUDIANTE")));
    }

    @AfterEach
    void cleanContexto() {
        SecurityContextHolder.clearContext();
    }

    // ── appUser() ────────────────────────────────────────────────────────────

    @Test
    void appUserLanzaExcepcionWithoutAuthenticationEnElContexto() {
        assertThrows(IllegalStateException.class, () -> service.appUser());
    }

    @Test
    void appUserLanzaExcepcionSiNoIsAuthenticated() {
        Authentication auth = new UsernamePasswordAuthenticationToken("x@uteq.edu.ec", "pass");
        auth.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThrows(IllegalStateException.class, () -> service.appUser());
    }

    @Test
    void appUserLanzaExcepcionForAppUserAnonimo() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThrows(IllegalStateException.class, () -> service.appUser());
    }

    @Test
    void appUserLanzaExcepcionSiElAuthenticatedNoIsEnLaBase() {
        authenticateAs("fantasma@uteq.edu.ec");
        when(appUserRepository.findByEmail("fantasma@uteq.edu.ec")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> service.appUser());
    }

    @Test
    void appUserDevuelveElAppUserAuthenticated() {
        authenticateAs("estudiante@uteq.edu.ec");
        AppUser u = new AppUser();
        u.setId(5L);
        u.setEmail("estudiante@uteq.edu.ec");
        when(appUserRepository.findByEmail("estudiante@uteq.edu.ec")).thenReturn(Optional.of(u));

        assertEquals(5L, service.appUser().getId());
    }

    // ── student() ─────────────────────────────────────────────────────────

    @Test
    void studentLanzaExcepcionSiElAppUserNoTieneProfileDeStudent() {
        authenticateAs("docente@uteq.edu.ec");
        AppUser u = new AppUser();
        u.setId(9L);
        u.setEmail("docente@uteq.edu.ec");
        when(appUserRepository.findByEmail("docente@uteq.edu.ec")).thenReturn(Optional.of(u));
        when(studentRepository.findByAppUserId(9L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.student());
    }

    @Test
    void studentDevuelveElProfileAsociado() {
        authenticateAs("estudiante@uteq.edu.ec");
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
    void studentIdOrNullDevuelveNullSiNoTieneProfileDeStudent() {
        authenticateAs("docente@uteq.edu.ec");
        AppUser u = new AppUser();
        u.setId(9L);
        u.setEmail("docente@uteq.edu.ec");
        when(appUserRepository.findByEmail("docente@uteq.edu.ec")).thenReturn(Optional.of(u));
        when(studentRepository.findByAppUserId(9L)).thenReturn(Optional.empty());

        assertNull(service.studentIdOrNull());
    }

    @Test
    void studentIdOrNullDevuelveElIdReal() {
        authenticateAs("estudiante@uteq.edu.ec");
        AppUser u = new AppUser();
        u.setId(5L);
        u.setEmail("estudiante@uteq.edu.ec");
        when(appUserRepository.findByEmail("estudiante@uteq.edu.ec")).thenReturn(Optional.of(u));
        Student e = Student.builder().id(2L).appUser(u).build();
        when(studentRepository.findByAppUserId(5L)).thenReturn(Optional.of(e));

        assertEquals(2L, service.studentIdOrNull());
    }
}
