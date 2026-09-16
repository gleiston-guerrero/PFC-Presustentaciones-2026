package ec.edu.uteq.presustentaciones.security;

import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.repositories.AppUserRepository;
import ec.edu.uteq.presustentaciones.security.jwt.JwtTokenProvider;
import ec.edu.uteq.presustentaciones.services.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * RF-05: recuperación de contraseña. Los casos aquí ejercitan {@link PasswordRecoveryService}
 * directamente (sin MockMvc: la ausencia de enumeración de cuentas por el CUERPO de la
 * respuesta se prueba en {@code AuthControllerIntegrationTest}; aquí lo que importa es que el
 * SERVICIO haga el mismo trabajo -- save un token con TTL -- exista o no la cuenta).
 */
class PasswordRecoveryServiceTest {

    private static final String EMAIL = "estudiante@uteq.edu.ec";

    // Redis falso en memoria, con TTL real registrado (no solo ignorado) para poder asign
    // sobre él sin esperar 30 minutos de verdad.
    private final Map<String, String> valores = new ConcurrentHashMap<>();
    private final Map<String, Long> ttlsSegundos = new ConcurrentHashMap<>();

    private AppUserRepository appUserRepository;
    private PasswordEncoder passwordEncoder;
    private PasswordPolicyValidator passwordPolicyValidator;
    private EmailService emailService;
    private JwtTokenProvider jwtTokenProvider;
    private PasswordRecoveryService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        valores.clear();
        ttlsSegundos.clear();

        ValueOperations<String, String> valueOps = mock(ValueOperations.class, this::responderValueOps);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.delete(anyString())).thenAnswer(inv -> valores.remove((String) inv.getArgument(0)) != null);

        appUserRepository = mock(AppUserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        passwordPolicyValidator = mock(PasswordPolicyValidator.class); // no-op por omision (void)
        emailService = mock(EmailService.class);
        jwtTokenProvider = mock(JwtTokenProvider.class);

        service = new PasswordRecoveryService(appUserRepository, passwordEncoder,
                passwordPolicyValidator, emailService, jwtTokenProvider, redisTemplate);
    }

    private Object responderValueOps(InvocationOnMock inv) {
        switch (inv.getMethod().getName()) {
            case "set": {
                String key = inv.getArgument(0);
                String value = inv.getArgument(1);
                valores.put(key, value);
                if (inv.getArguments().length >= 4) {
                    long timeout = inv.getArgument(2);
                    TimeUnit unit = inv.getArgument(3);
                    ttlsSegundos.put(key, unit.toSeconds(timeout));
                }
                return null;
            }
            case "get":
                return valores.get((String) inv.getArgument(0));
            default:
                return null;
        }
    }

    private String capturarTokenEnviado() {
        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailService).sendRecuperacionPassword(eq(EMAIL), captor.capture());
        return captor.getValue();
    }

    @Test
    void solicitarConCuentaExistenteGuardaUnTokenConTtlDe30MinutosYEnviaElCorreo() {
        AppUser appUser = new AppUser();
        appUser.setId(7L);
        appUser.setEmail(EMAIL);
        when(appUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(appUser));

        service.solicitarRecuperacion(EMAIL);

        verify(emailService).sendRecuperacionPassword(eq(EMAIL), anyString());
        // Un solo valor guardado con TTL de 30 min (1800 s) -- el propio token, hasheado.
        assertEquals(1, ttlsSegundos.size());
        assertEquals(1800L, ttlsSegundos.values().iterator().next());
    }

    @Test
    void solicitarConCuentaInexistenteNoEnviaCorreoPeroHaceUnTrabajoEquivalenteEnRedis() {
        when(appUserRepository.findByEmail("nadie@uteq.edu.ec")).thenReturn(Optional.empty());

        service.solicitarRecuperacion("nadie@uteq.edu.ec");

        verify(emailService, never()).sendRecuperacionPassword(any(), any());
        // Sigue escribiendo en Redis (mismo tipo de operacion que la rama que si existe), para
        // no distinguirse por completo en el trabajo realizado.
        assertEquals(1, valores.size());
    }

    @Test
    void resetConTokenValidoAplicaLaNuevaContrasenaYRevocaTodasLasSesionesSinExcepcion() {
        AppUser appUser = new AppUser();
        appUser.setId(7L);
        appUser.setEmail(EMAIL);
        appUser.setPassword("hashViejo");
        when(appUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(appUser));
        when(passwordEncoder.encode("NuevaClave#2026")).thenReturn("hashNuevo");

        service.solicitarRecuperacion(EMAIL);
        String token = capturarTokenEnviado();

        service.reset(token, "NuevaClave#2026");

        verify(passwordPolicyValidator).validate("NuevaClave#2026");
        assertEquals("hashNuevo", appUser.getPassword());
        verify(appUserRepository).save(appUser);
        // A diferencia de RF-06 (cambio voluntario), aqui NO hay "salvo la sesion actual": se
        // revocan todas, sin excepcion -- una recuperacion puede originarse en un compromiso.
        verify(jwtTokenProvider).revokeAllUserTokens(EMAIL);
        verify(jwtTokenProvider, never()).revokeAllUserTokensExcept(any(), any());
    }

    @Test
    void elTokenNoPuedeUsarseDosVeces() {
        AppUser appUser = new AppUser();
        appUser.setId(7L);
        appUser.setEmail(EMAIL);
        appUser.setPassword("hashViejo");
        when(appUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(appUser));

        service.solicitarRecuperacion(EMAIL);
        String token = capturarTokenEnviado();

        service.reset(token, "NuevaClave#2026");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.reset(token, "OtraClave#2026"));
        assertTrue(ex.getMessage().toLowerCase().contains("inválido") || ex.getMessage().toLowerCase().contains("expiró"));
    }

    @Test
    void unTokenQueNuncaExistioEsInvalido() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.reset("token-inventado-por-un-atacante", "NuevaClave#2026"));
        assertFalse(ex.getMessage().contains("token-inventado-por-un-atacante"));
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void resetConNuevaContrasenaQueIncumpleLaPoliticaNoGuardaNadaYPropagaElMensajeDelValidador() {
        AppUser appUser = new AppUser();
        appUser.setId(7L);
        appUser.setEmail(EMAIL);
        appUser.setPassword("hashViejo");
        when(appUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(appUser));
        doThrow(new IllegalArgumentException("Esa contraseña es demasiado común. Elige una diferente."))
                .when(passwordPolicyValidator).validate("comun123");

        service.solicitarRecuperacion(EMAIL);
        String token = capturarTokenEnviado();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.reset(token, "comun123"));
        assertEquals("Esa contraseña es demasiado común. Elige una diferente.", ex.getMessage());
        verify(appUserRepository, never()).save(any());
        verify(jwtTokenProvider, never()).revokeAllUserTokens(any());
        // El token sigue siendo valido (no se consumio) porque el restablecimiento fallo antes
        // de aplicarse -- se puede reintentar con una contrasena que si cumpla.
        assertEquals("hashViejo", appUser.getPassword());
    }
}
