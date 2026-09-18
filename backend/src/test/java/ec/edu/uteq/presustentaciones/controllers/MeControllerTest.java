package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.config.SecurityConfig;
import ec.edu.uteq.presustentaciones.security.RateLimiterService;
import ec.edu.uteq.presustentaciones.security.jwt.JwtTokenProvider;
import ec.edu.uteq.presustentaciones.services.PermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cubre GET /api/me/permissions: el panel lo consulta en caliente para saber qué módulos
 * mostrar (ver comentario de clase en {@code MeController}). Mismo patrón
 * @WebMvcTest + SecurityConfig real que {@code TeacherControllerTest}.
 */
@WebMvcTest(controllers = MeController.class)
@Import(SecurityConfig.class)
class MeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PermissionService permissionService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private RateLimiterService rateLimiterService;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @MockBean
    private AuthenticationManager authenticationManager;

    @MockBean
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @MockBean
    private ec.edu.uteq.presustentaciones.repositories.RoleAppUserRepository roleAppUserRepository;

    @MockBean
    private ec.edu.uteq.presustentaciones.repositories.AppUserRepository appUserRepository;

    @Test
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/me/permisos").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void devuelveLosPermissionsDelAppUserAuthenticated() throws Exception {
        String email = "docente@uteq.edu.ec";
        String token = "token-" + email;
        UserDetails userDetails = new User(email, "x",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_DOCENTE")));
        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken(token)).thenReturn(email);
        when(userDetailsService.loadUserByUsername(email)).thenReturn(userDetails);
        when(permissionService.permissionsOf(any(Authentication.class)))
                .thenReturn(List.of("SOLICITUDES_VER", "ACTAS_VER_PROPIAS"));

        mockMvc.perform(get("/api/v1/me/permisos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("SOLICITUDES_VER")));
    }
}
