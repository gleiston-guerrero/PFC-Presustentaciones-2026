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
 * RNF-06, caso de integracion: {@code POST /api/v1/auth/register} con la politica de contrasenas
 * REAL activa (a diferencia de {@code AuthControllerIntegrationTest}, que la mockea a proposito).
 *
 * <p>Estaba como clase {@code static} anidada dentro de {@link PasswordPolicyValidatorTest}, sin
 * {@code @Nested}: JUnit no descubre una clase estatica anidada y Surefire excluye por defecto las
 * clases internas, asi que estas tres pruebas EXISTIAN (809 anotaciones {@code @Test}) y NUNCA SE
 * EJECUTABAN (806 corridas). La revision final del 19-sep lo descubrio al contar. Es una clase
 * propia para que Surefire la ejecute.
 */
@WebMvcTest(controllers = {AuthController.class, AppUserController.class})
@Import({SecurityConfig.class, PasswordPolicyValidator.class})
class RegisterPasswordPolicyIntegrationTest {

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
    @MockBean private ec.edu.uteq.presustentaciones.services.ErasureDataService erasureDataService;
    @MockBean private IAppUserService appUserService;
    @MockBean private JdbcTemplate jdbcTemplate;
    @MockBean private RoleAppUserRepository roleAppUserRepository;
    @MockBean(name = "permissionService") private PermissionService permissionService;

    private void authenticateAsAdmin() {
        String token = "adminToken";
        UserDetails adminDetails = new User("admin@uteq.edu.ec", "x",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken(token)).thenReturn("admin@uteq.edu.ec");
        when(userDetailsService.loadUserByUsername("admin@uteq.edu.ec")).thenReturn(adminDetails);
        when(permissionService.hasPermission(any(), any())).thenReturn(true);
    }

    @Test
    void registerWithPasswordOfListOfCommonReturns400WithoutCreateAppUser() throws Exception {
        authenticateAsAdmin();
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
    void registerWithPasswordShortReturns400ByBeanValidationBeforeOfReachToValidator() throws Exception {
        authenticateAsAdmin();
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
    void registerWithPasswordThatMeetsPolicyArrivesToAppUserService() throws Exception {
        authenticateAsAdmin();
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
