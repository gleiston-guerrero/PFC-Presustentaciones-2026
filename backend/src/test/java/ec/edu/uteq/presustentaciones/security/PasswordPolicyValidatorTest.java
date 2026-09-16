package ec.edu.uteq.presustentaciones.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.edu.uteq.presustentaciones.config.SecurityConfig;
import ec.edu.uteq.presustentaciones.controllers.AuthController;
import ec.edu.uteq.presustentaciones.controllers.AppUserController;
import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.repositories.RoleAppUserRepository;
import ec.edu.uteq.presustentaciones.repositories.AppUserRepository;
import ec.edu.uteq.presustentaciones.security.jwt.JwtTokenProvider;
import ec.edu.uteq.presustentaciones.services.IAppUserService;
import ec.edu.uteq.presustentaciones.services.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RNF-06: longitud mínima de 8 y rechazo de contraseñas comunes
 * ({@code backend/src/main/resources/security/common-passwords.txt}). Casos de unidad sobre
 * {@link PasswordPolicyValidator} directamente (sin contexto de Spring, la lista se carga del
 * classpath real) más un caso de integración sobre {@code POST /api/v1/auth/register}, con la
 * política REAL activa -- a diferencia de {@code AuthControllerIntegrationTest}, que la
 * mockea a propósito para no acoplar sus fixtures a esta lista.
 */
class PasswordPolicyValidatorTest {

    private final PasswordPolicyValidator validador = new PasswordPolicyValidator();

    @Test
    void rechazaSieteCaracteresPorSerMenorQueElMinimo() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> validador.validate("Ab1cd2f")); // 7 caracteres, no está en la lista de comunes
        assertTrue(ex.getMessage().contains("8 caracteres"));
        assertFalse(ex.getMessage().contains("Ab1cd2f"), "el mensaje no debe revelar la contraseña");
    }

    @Test
    void aceptaOchoCaracteresQueNoEstanEnLaListaDeComunes() {
        assertDoesNotThrow(() -> validador.validate("Xq7#mZ9d"));
        assertTrue(validador.cumple("Xq7#mZ9d"));
    }

    @Test
    void rechazaUnaContrasenaDeLaListaAunqueTengaOchoCaracteresOMas() {
        // "password123" (11 caracteres) esta en common-passwords.txt: cumple la longitud
        // minima y aun asi debe rejectse por estar en la lista de comunes.
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> validador.validate("password123"));
        assertTrue(ex.getMessage().toLowerCase().contains("común"));
        assertFalse(ex.getMessage().contains("password123"));
    }

    @Test
    void laComprobacionDeComunesEsInsensibleAMayusculas() {
        assertThrows(IllegalArgumentException.class, () -> validador.validate("Admin123"));
    }

    @Test
    void cumpleDevuelveFalsoSinLanzarExcepcion() {
        assertFalse(validador.cumple("corto1"));
        assertFalse(validador.cumple("password123"));
        assertTrue(validador.cumple("Zk4#pQ8w"));
    }

    // ── Caso de integración: POST /api/v1/auth/register con la política REAL activa ────────

    @WebMvcTest(controllers = {AuthController.class, AppUserController.class})
    @Import({SecurityConfig.class, PasswordPolicyValidator.class})
    static class RegisterIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @MockBean private AuthenticationManager authenticationManager;
        @MockBean private AppUserRepository appUserRepository;
        @MockBean private PasswordEncoder passwordEncoder;
        @MockBean private JwtTokenProvider jwtTokenProvider;
        @MockBean private UserDetailsService userDetailsService;
        @MockBean private ec.edu.uteq.presustentaciones.security.RateLimiterService rateLimiterService;
        @MockBean private ec.edu.uteq.presustentaciones.security.PasswordRecoveryService passwordRecoveryService;
        @MockBean private ec.edu.uteq.presustentaciones.services.SupresionDatosService supresionDatosService;
        @MockBean private IAppUserService appUserService;
        @MockBean private JdbcTemplate jdbcTemplate;
        @MockBean private RoleAppUserRepository roleAppUserRepository;
        @MockBean(name = "permissionService") private PermissionService permissionService;

        private void autenticarComoAdmin() {
            String token = "adminToken";
            UserDetails adminDetails = new User("admin@uteq.edu.ec", "x",
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));
            when(jwtTokenProvider.validateToken(token)).thenReturn(true);
            when(jwtTokenProvider.getUsernameFromToken(token)).thenReturn("admin@uteq.edu.ec");
            when(userDetailsService.loadUserByUsername("admin@uteq.edu.ec")).thenReturn(adminDetails);
            when(permissionService.tienePermission(any(), any())).thenReturn(true);
        }

        @Test
        void registerConContrasenaDeLaListaDeComunesDevuelve400SinCreateAppUser() throws Exception {
            autenticarComoAdmin();
            String body = "{\"nombre\":\"Carlos\",\"apellido\":\"Mendoza\",\"email\":\"cmendoza@uteq.edu.ec\"," +
                    "\"password\":\"password123\",\"rol\":\"ESTUDIANTE\"}"; // esta en common-passwords.txt

            mockMvc.perform(post("/api/v1/auth/register")
                            .header("Authorization", "Bearer adminToken")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("común")));

            verify(appUserService, never()).create(any());
        }

        @Test
        void registerConContrasenaCortaDevuelve400PorBeanValidationAntesDeLlegarAlValidador() throws Exception {
            autenticarComoAdmin();
            String body = "{\"nombre\":\"Carlos\",\"apellido\":\"Mendoza\",\"email\":\"cmendoza@uteq.edu.ec\"," +
                    "\"password\":\"Ab1cd2f\",\"rol\":\"ESTUDIANTE\"}"; // 7 caracteres

            mockMvc.perform(post("/api/v1/auth/register")
                            .header("Authorization", "Bearer adminToken")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());

            verify(appUserService, never()).create(any());
        }

        @Test
        void registerConContrasenaQueCumpleLaPoliticaLlegaAAppUserService() throws Exception {
            autenticarComoAdmin();
            String body = "{\"nombre\":\"Carlos\",\"apellido\":\"Mendoza\",\"email\":\"cmendoza@uteq.edu.ec\"," +
                    "\"password\":\"Xq7#mZ9d\",\"rol\":\"ESTUDIANTE\"}"; // 8 caracteres, no comun

            when(appUserService.create(any(AppUser.class))).thenReturn(new AppUser());

            mockMvc.perform(post("/api/v1/auth/register")
                            .header("Authorization", "Bearer adminToken")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true));

            verify(appUserService).create(any(AppUser.class));
        }
    }
}
