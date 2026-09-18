package ec.edu.uteq.presustentaciones.security;

import ec.edu.uteq.presustentaciones.controllers.AuthController;
import ec.edu.uteq.presustentaciones.dto.ResponseWrapper;
import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.repositories.AppUserRepository;
import ec.edu.uteq.presustentaciones.security.jwt.JwtTokenProvider;
import ec.edu.uteq.presustentaciones.services.IAppUserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cubre RF-02 (rotación de refresh token) y RNF-08 en AuthController.refresh(), contra el
 * JwtTokenProvider real (generateRefreshToken/rotateRefreshToken/getUsernameFromUsedRefreshToken/
 * revokeAllUserTokens). StringRedisTemplate es @Autowired(required=false) en JwtTokenProvider
 * (ver JwtTokenProviderTest), así que en vez de levantar Redis se inyecta un doble en memoria
 * que replica el mismo esquema de claves (refresh_token:, used_refresh_token:,
 * user_refresh_tokens:) para que la rotación y la revocación se comporten igual que contra
 * Redis real, no solo simular una respuesta fija.
 */
class RefreshTokenRotationTest {

    private static final String EMAIL = "estudiante@uteq.edu.ec";

    private final Map<String, String> valores = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> conjuntos = new ConcurrentHashMap<>();

    private JwtTokenProvider jwtTokenProvider;
    private AuthController authController;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class, this::respondValueOps);
        SetOperations<String, String> setOps = mock(SetOperations.class, this::respondSetOps);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(redisTemplate.hasKey(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            return valores.containsKey(key) || conjuntos.containsKey(key);
        });
        when(redisTemplate.delete(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            boolean removido = valores.remove(key) != null;
            removido |= conjuntos.remove(key) != null;
            return removido;
        });
        when(redisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret",
                "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpiration", 86400000L);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtRefreshExpiration", 604800000L);
        ReflectionTestUtils.setField(jwtTokenProvider, "redisTemplate", redisTemplate);

        AppUser appUser = new AppUser();
        appUser.setId(1L);
        appUser.setEmail(EMAIL);
        appUser.setNombre("Ana");
        appUser.setApellido("Perez");
        appUser.setRole("ESTUDIANTE");

        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        when(appUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(appUser));

        authController = new AuthController(
                mock(AuthenticationManager.class),
                appUserRepository,
                mock(PasswordEncoder.class),
                jwtTokenProvider,
                mock(IAppUserService.class),
                mock(ec.edu.uteq.presustentaciones.security.PasswordPolicyValidator.class),
                mock(ec.edu.uteq.presustentaciones.security.PasswordRecoveryService.class),
                mock(ec.edu.uteq.presustentaciones.security.RateLimiterService.class));
    }

    /** Respuestas de ValueOperations respaldadas por {@code valores}, igual que refresh_token:/used_refresh_token: en Redis real. */
    private Object respondValueOps(InvocationOnMock inv) {
        switch (inv.getMethod().getName()) {
            case "set":
                valores.put(inv.getArgument(0), inv.getArgument(1));
                return null;
            case "get":
                return valores.get((String) inv.getArgument(0));
            default:
                return null;
        }
    }

    /** Respuestas de SetOperations respaldadas por {@code conjuntos}, igual que user_refresh_tokens:<email> en Redis real. */
    private Object respondSetOps(InvocationOnMock inv) {
        String key = inv.getArgument(0);
        Object[] args = inv.getArguments();
        switch (inv.getMethod().getName()) {
            case "add": {
                Set<String> conjunto = conjuntos.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet());
                for (int i = 1; i < args.length; i++) {
                    conjunto.add((String) args[i]);
                }
                return (long) (args.length - 1);
            }
            case "remove": {
                Set<String> conjunto = conjuntos.get(key);
                long eliminados = 0;
                if (conjunto != null) {
                    for (int i = 1; i < args.length; i++) {
                        if (conjunto.remove(args[i])) eliminados++;
                    }
                }
                return eliminados;
            }
            case "members":
                return conjuntos.get(key);
            default:
                return null;
        }
    }

    private MockHttpServletRequest requestWithRefresh(String refreshToken) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (refreshToken != null) {
            request.setCookies(new Cookie("refreshToken", refreshToken));
        }
        return request;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dataOf(ResponseEntity<?> response) {
        return (Map<String, Object>) ((ResponseWrapper<?>) response.getBody()).getData();
    }

    @Test
    void refreshVigenteDevuelveAccessNewYRefreshNew() {
        String refreshToken = jwtTokenProvider.generateRefreshToken(EMAIL);

        ResponseEntity<?> respuesta = authController.refresh(requestWithRefresh(refreshToken), new MockHttpServletResponse());

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        Map<String, Object> data = dataOf(respuesta);
        assertNotNull(data.get("token"));
        assertNotNull(data.get("refreshToken"));
        assertNotEquals(refreshToken, data.get("refreshToken"));
    }

    @Test
    void elRefreshUsadoDejaDeValerDeInmediato() {
        String refreshToken = jwtTokenProvider.generateRefreshToken(EMAIL);

        ResponseEntity<?> primerUso = authController.refresh(requestWithRefresh(refreshToken), new MockHttpServletResponse());
        assertEquals(HttpStatus.OK, primerUso.getStatusCode());

        ResponseEntity<?> segundoUso = authController.refresh(requestWithRefresh(refreshToken), new MockHttpServletResponse());
        assertEquals(HttpStatus.UNAUTHORIZED, segundoUso.getStatusCode());
    }

    @Test
    void reutilizarUnRefreshYaUsadoRevocaAllLasSesionesActiveDelAppUser() {
        String primerRefresh = jwtTokenProvider.generateRefreshToken(EMAIL);
        // Segunda sesión activa del mismo appUser (p.ej. otro dispositivo), sin relación con el ataque.
        String segundoRefresh = jwtTokenProvider.generateRefreshToken(EMAIL);

        // Uso legítimo: rota el primer refresh y emite uno nuevo.
        ResponseEntity<?> usoLegitimo = authController.refresh(requestWithRefresh(primerRefresh), new MockHttpServletResponse());
        assertEquals(HttpStatus.OK, usoLegitimo.getStatusCode());
        String refreshRotado = (String) dataOf(usoLegitimo).get("refreshToken");

        // El atacante reutiliza el refresh ya usado (robado antes de la rotación).
        ResponseEntity<?> reutilizacion = authController.refresh(requestWithRefresh(primerRefresh), new MockHttpServletResponse());

        assertEquals(HttpStatus.UNAUTHORIZED, reutilizacion.getStatusCode());
        // No basta con el 401: revokeAllUserTokens debe haber cerrado TODAS las sesiones activas,
        // incluida la que acababa de nacer de la rotación legítima y la del otro dispositivo.
        assertFalse(jwtTokenProvider.validateRefreshToken(refreshRotado),
                "El refresh emitido por la rotación legitima tambien debe quedar revocado");
        assertFalse(jwtTokenProvider.validateRefreshToken(segundoRefresh),
                "La sesion activa de otro dispositivo tambien debe quedar revocada");
    }

    @Test
    void sinRefreshTokenEnLaRequestDevuelve400() {
        ResponseEntity<?> respuesta = authController.refresh(requestWithRefresh(null), new MockHttpServletResponse());
        assertEquals(HttpStatus.BAD_REQUEST, respuesta.getStatusCode());
    }
}
