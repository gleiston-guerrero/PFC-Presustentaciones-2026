package ec.edu.uteq.presustentaciones.security;

import ec.edu.uteq.presustentaciones.config.AppConfig;
import ec.edu.uteq.presustentaciones.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RNF-04: con el almacén de Redis caído, los tres mecanismos que dependen de él deben
 * comportarse distinto a propósito -- revocación y límite de tasa son controles de
 * SEGURIDAD (fail-closed: reject), la caché de lectura es RENDIMIENTO (fail-open: resolve
 * contra el origen). El doble de {@link StringRedisTemplate} lanza
 * {@link RedisConnectionFailureException} (una {@code DataAccessException} real, no un mock
 * genérico) para que la prueba ejercite exactamente la rama que el código distingue.
 */
class RedisDegradacionTest {

    private static RedisConnectionFailureException redisCaido() {
        return new RedisConnectionFailureException("Redis no responde (simulado)");
    }

    // ── 1) Revocación: fail-CLOSED -- con Redis caído, un token se trata como revocado ─────

    @Test
    @SuppressWarnings("unchecked")
    void isTokenBlacklistedConRedisCaidoTrataElTokenComoRevocado() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.hasKey(anyString())).thenThrow(redisCaido());

        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider();
        String secret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", secret);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpiration", 86400000L);
        ReflectionTestUtils.setField(jwtTokenProvider, "redisTemplate", redisTemplate);

        String token = jwtTokenProvider.generateTokenFromUsername("docente@uteq.edu.ec");

        // Con Redis arriba este token es valido (jamas se puso en la blacklist); con Redis
        // caido debe tratarse como revocado -- exactamente el escenario que el hallazgo describe.
        assertTrue(jwtTokenProvider.isTokenBlacklisted(token),
                "con el almacen de revocacion caido, el token debe tratarse como revocado (fail-closed)");

        // Y por lo tanto validateToken() debe rejectlo, no aceptarlo silenciosamente.
        assertThrows(io.jsonwebtoken.JwtException.class, () -> jwtTokenProvider.validateToken(token));
    }

    @Test
    @SuppressWarnings("unchecked")
    void isTokenBlacklistedConTokenMalformadoNoLoConfundeConFalloDeRedis() {
        // Un token malformado no debe disparar el fail-closed de availability: Redis nunca
        // se llega a consultar porque el parseo del token falla antes.
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret",
                "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        ReflectionTestUtils.setField(jwtTokenProvider, "redisTemplate", redisTemplate);

        assertFalse(jwtTokenProvider.isTokenBlacklisted("esto.no.es-un-jwt-valido"));
        org.mockito.Mockito.verifyNoInteractions(redisTemplate);
    }

    // ── 2) Límite de tasa: ni 500 ni acceso sin límite -- señal explícita de degradación ───

    @Test
    @SuppressWarnings("unchecked")
    void rateLimiterConRedisCaidoLanzaExcepcionDeDegradacionEnVezDePermitirODevolver500Crudo() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenThrow(redisCaido());

        RateLimiterService rateLimiterService = new RateLimiterService(redisTemplate);

        assertThrows(RateLimiterUnavailableException.class,
                () -> rateLimiterService.isAllowed("203.0.113.7"));
    }

    // ── 3) Caché de lectura: fail-OPEN -- se resuelve contra el origen, no se corta la petición ──

    @Test
    void cacheErrorHandlerNoPropagaLaExcepcionDeLecturaDeCache() {
        AppConfig appConfig = new AppConfig();
        CacheErrorHandler errorHandler = appConfig.errorHandler();
        Cache cache = mock(Cache.class);
        when(cache.getName()).thenReturn("solicitudes");

        // El contrato de CacheErrorHandler es justamente ese: si no relanza, Spring trata la
        // lectura como un cache-miss y el metodo @Cacheable sigue adelante contra el origen.
        assertDoesNotThrow(() -> errorHandler.handleCacheGetError(redisCaido(), cache, "clave-cualquiera"));
        assertDoesNotThrow(() -> errorHandler.handleCachePutError(redisCaido(), cache, "clave-cualquiera", "valor"));
        assertDoesNotThrow(() -> errorHandler.handleCacheEvictError(redisCaido(), cache, "clave-cualquiera"));
        assertDoesNotThrow(() -> errorHandler.handleCacheClearError(redisCaido(), cache));
    }

    @Test
    void otrasExcepcionesDeAccesoADatosTambienDisparanElMismoComportamientoPorSerDataAccessException() {
        // QueryTimeoutException es otra subclase real de DataAccessException (no
        // necesariamente de Redis) -- confirma que la distincion es por tipo de excepcion
        // ("y afines", como pide el encargo), no por un chequeo especifico de Redis.
        AppConfig appConfig = new AppConfig();
        Cache cache = mock(Cache.class);
        assertDoesNotThrow(() -> appConfig.errorHandler()
                .handleCacheGetError(new QueryTimeoutException("timeout simulado"), cache, "k"));
    }
}
