package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.repositories.AppUserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuditService no tenia ningun test (0% de ramas) pese a ser el bean que fija el
 * "quien" que ven los triggers de audit (V15) en cada escritura auditable.
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    @InjectMocks
    private AuditService auditService;

    @BeforeEach
    void fijarEntityManager() {
        // @PersistenceContext no es del constructor de @RequiredArgsConstructor,
        // asi que @InjectMocks no lo inyecta solo.
        ReflectionTestUtils.setField(auditService, "entityManager", entityManager);
    }

    @AfterEach
    void cleanContexto() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null,
                        AuthorityUtils.createAuthorityList("ROLE_ESTUDIANTE")));
    }

    @Test
    void markActorActualFijaCadenaVaciaWithoutAuthenticationEnElContexto() {
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(eq("valor"), eq(""))).thenReturn(query);

        assertDoesNotThrow(() -> auditService.markActorActual());

        verify(query).setParameter("valor", "");
    }

    @Test
    void markActorActualFijaCadenaVaciaSiNoIsAuthenticated() {
        Authentication auth = new UsernamePasswordAuthenticationToken("x@uteq.edu.ec", "pass");
        auth.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(auth);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(eq("valor"), anyString())).thenReturn(query);

        auditService.markActorActual();

        verify(query).setParameter("valor", "");
    }

    @Test
    void markActorActualFijaCadenaVaciaForAppUserAnonimo() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(eq("valor"), anyString())).thenReturn(query);

        auditService.markActorActual();

        verify(query).setParameter("valor", "");
    }

    @Test
    void markActorActualFijaCadenaVaciaSiElAuthenticatedNoIsEnLaBase() {
        authenticateAs("fantasma@uteq.edu.ec");
        when(appUserRepository.findByEmail("fantasma@uteq.edu.ec")).thenReturn(Optional.empty());
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(eq("valor"), anyString())).thenReturn(query);

        auditService.markActorActual();

        verify(query).setParameter("valor", "");
    }

    @Test
    void markActorActualFijaElIdDelAppUserAuthenticated() {
        authenticateAs("docente@uteq.edu.ec");
        AppUser u = new AppUser();
        u.setId(42L);
        when(appUserRepository.findByEmail("docente@uteq.edu.ec")).thenReturn(Optional.of(u));
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(eq("valor"), anyString())).thenReturn(query);

        auditService.markActorActual();

        verify(query).setParameter("valor", "42");
    }

    @Test
    void markActorActualNoPropagaLaExcepcionSiFallaLaQueryNativa() {
        authenticateAs("docente@uteq.edu.ec");
        AppUser u = new AppUser();
        u.setId(42L);
        when(appUserRepository.findByEmail("docente@uteq.edu.ec")).thenReturn(Optional.of(u));
        when(entityManager.createNativeQuery(anyString())).thenThrow(new RuntimeException("conexión caída"));

        assertDoesNotThrow(() -> auditService.markActorActual());
    }
}
