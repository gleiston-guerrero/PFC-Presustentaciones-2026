package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.config.SecurityConfig;
import ec.edu.uteq.presustentaciones.dto.UniversityDto;
import ec.edu.uteq.presustentaciones.security.RateLimiterService;
import ec.edu.uteq.presustentaciones.security.jwt.JwtTokenProvider;
import ec.edu.uteq.presustentaciones.services.ExternalApiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cubre GET /api/universidades: sin test dedicado hasta ahora (el único controlador,
 * junto con {@code MeController}, sin ninguna cobertura). Mismo patrón @WebMvcTest +
 * SecurityConfig real que {@code TeacherControllerTest}.
 */
@WebMvcTest(controllers = ExternalApiController.class)
@Import(SecurityConfig.class)
class ExternalApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExternalApiService externalApiService;

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
        mockMvc.perform(get("/api/v1/universidades").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void devuelveLasUniversidadesDesdeElServicio() throws Exception {
        String email = "docente@uteq.edu.ec";
        String token = "token-" + email;
        UserDetails userDetails = new User(email, "x",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_DOCENTE")));
        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken(token)).thenReturn(email);
        when(userDetailsService.loadUserByUsername(email)).thenReturn(userDetails);

        UniversityDto uteq = new UniversityDto();
        uteq.setName("Universidad Tecnica Estatal de Quevedo");
        when(externalApiService.getUniversitiesOfEcuador()).thenReturn(List.of(uteq));

        mockMvc.perform(get("/api/v1/universidades")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Quevedo")));
    }
}
