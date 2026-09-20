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
 * classpath real) y el caso de integración sobre {@code POST /api/v1/auth/register} con la política REAL activa
 * vive en {@link RegisterPasswordPolicyIntegrationTest}.
 */
class PasswordPolicyValidatorTest {

    private final PasswordPolicyValidator validador = new PasswordPolicyValidator();

    @Test
    void rejectsSevenCharactersByBeLessThatMinimum() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> validador.validate("Ab1cd2f")); // 7 caracteres, no está en la lista de comunes
        assertTrue(ex.getMessage().contains("8 caracteres"));
        assertFalse(ex.getMessage().contains("Ab1cd2f"), "el mensaje no debe revelar la contraseña");
    }

    @Test
    void acceptsEightCharactersThatNotAreInListOfCommon() {
        assertDoesNotThrow(() -> validador.validate("Xq7#mZ9d"));
        assertTrue(validador.meets("Xq7#mZ9d"));
    }

    @Test
    void rejectsPasswordOfListAlthoughHasEightCharactersOrMore() {
        // "password123" (11 caracteres) esta en common-passwords.txt: cumple la longitud
        // minima y aun asi debe rejectse por estar en la lista de comunes.
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> validador.validate("password123"));
        assertTrue(ex.getMessage().toLowerCase().contains("común"));
        assertFalse(ex.getMessage().contains("password123"));
    }

    @Test
    void checkOfCommonIsInsensitiveToUppercase() {
        assertThrows(IllegalArgumentException.class, () -> validador.validate("Admin123"));
    }

    @Test
    void meetsReturnsFalseWithoutThrowException() {
        assertFalse(validador.meets("corto1"));
        assertFalse(validador.meets("password123"));
        assertTrue(validador.meets("Zk4#pQ8w"));
    }
}
