package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.config.SecurityConfig;
import ec.edu.uteq.presustentaciones.security.RateLimiterService;
import ec.edu.uteq.presustentaciones.security.jwt.JwtTokenProvider;
import ec.edu.uteq.presustentaciones.services.MinutesService;
import ec.edu.uteq.presustentaciones.services.PermissionService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Corrección de autorización (auditoría 2026-09-04): DELETE /api/v1/minutes/{id} solo exigía
 * isAuthenticated(), así que MinutesServiceImpl.deleteMinutes() -- que reutiliza validateAcceso(),
 * pensado para LECTURA (admin, panelist, tutor o el propio student dueño) -- terminaba
 * autorizando también el borrado permanente del minutes a cualquiera de esos participantes. Ahora
 * exige el permission ACTAS_GESTIONAR (hoy solo ADMIN), igual que el resto de acciones
 * administrativas del controlador (generate/sign/changeEstado).
 */
@WebMvcTest(controllers = MinutesController.class)
@Import(SecurityConfig.class)
class MinutesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MinutesService minutesService;

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

    @MockBean(name = "permissionService")
    private PermissionService permissionService;

    @MockBean
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @MockBean
    private ec.edu.uteq.presustentaciones.repositories.RoleAppUserRepository roleAppUserRepository;

    @MockBean
    private ec.edu.uteq.presustentaciones.repositories.AppUserRepository appUserRepository;

    private void authenticateAs(String email, String role, boolean hasPermission) {
        String token = "token-" + email;
        UserDetails userDetails = new User(email, "x",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)));
        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken(token)).thenReturn(email);
        when(userDetailsService.loadUserByUsername(email)).thenReturn(userDetails);
        when(permissionService.hasPermission(any(), any())).thenReturn(hasPermission);
    }

    @Test
    void deleteRejectsToStudentAlthoughIsOwnerOfSubmission() throws Exception {
        // Caso que exponía la vulnerabilidad: antes, ser el student dueño/panelist/tutor
        // (via validateAcceso en el service) bastaba para erase el minutes. Ahora ni siquiera
        // llega al service: @PreAuthorize lo rechaza antes.
        authenticateAs("estudiante@uteq.edu.ec", "ESTUDIANTE", false);

        mockMvc.perform(delete("/api/v1/actas/1")
                        .header("Authorization", "Bearer token-estudiante@uteq.edu.ec")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        verify(minutesService, never()).deleteMinutes(any());
    }

    @Test
    void deleteRejectsToTeacherPanelistOrTutorWithoutMinutesManage() throws Exception {
        authenticateAs("docente@uteq.edu.ec", "DOCENTE", false);

        mockMvc.perform(delete("/api/v1/actas/1")
                        .header("Authorization", "Bearer token-docente@uteq.edu.ec")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        verify(minutesService, never()).deleteMinutes(any());
    }

    @Test
    void deleteAllowsToAdminWithMinutesManage() throws Exception {
        authenticateAs("admin@uteq.edu.ec", "ADMIN", true);

        mockMvc.perform(delete("/api/v1/actas/1")
                        .header("Authorization", "Bearer token-admin@uteq.edu.ec")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(minutesService).deleteMinutes(1L);
    }

    @Test
    void deleteWithoutTokenReturns401() throws Exception {
        mockMvc.perform(delete("/api/v1/actas/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
