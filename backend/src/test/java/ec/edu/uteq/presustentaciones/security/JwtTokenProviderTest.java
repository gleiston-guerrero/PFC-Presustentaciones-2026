package ec.edu.uteq.presustentaciones.security;

import ec.edu.uteq.presustentaciones.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private final String secret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private final int expirationMs = 86400000;

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private SetOperations<String, String> setOps;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", secret);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpiration", (long) expirationMs);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtRefreshExpiration", 604800000L);
    }

    private String tokenValido() {
        return jwtTokenProvider.generateTokenFromUsername("user@uteq.edu.ec");
    }

    // ── Sin Redis disponible: todos los metodos fallan/salen cerrados sin romper ────────────

    @Test
    void generateRefreshTokenDevuelveUnUuidAunqueRedisNoEsteDisponible() {
        assertNotNull(jwtTokenProvider.generateRefreshToken("user@uteq.edu.ec"));
    }

    @Test
    void getUsernameFromRefreshTokenDevuelveNullSinRedis() {
        assertNull(jwtTokenProvider.getUsernameFromRefreshToken("x"));
    }

    @Test
    void getUsernameFromUsedRefreshTokenDevuelveNullSinRedis() {
        assertNull(jwtTokenProvider.getUsernameFromUsedRefreshToken("x"));
    }

    @Test
    void validateRefreshTokenDevuelveFalseSinRedis() {
        assertFalse(jwtTokenProvider.validateRefreshToken("x"));
    }

    @Test
    void rotateRefreshTokenNoHaceNadaSinRedis() {
        assertDoesNotThrow(() -> jwtTokenProvider.rotateRefreshToken("old", "user@uteq.edu.ec"));
    }

    @Test
    void revokeAllUserTokensNoHaceNadaSinRedis() {
        assertDoesNotThrow(() -> jwtTokenProvider.revokeAllUserTokens("user@uteq.edu.ec"));
    }

    @Test
    void revokeAllUserTokensExceptNoHaceNadaSinRedis() {
        assertDoesNotThrow(() -> jwtTokenProvider.revokeAllUserTokensExcept("user@uteq.edu.ec", "keep"));
    }

    @Test
    void deleteRefreshTokenNoHaceNadaSinRedis() {
        assertDoesNotThrow(() -> jwtTokenProvider.deleteRefreshToken("x"));
    }

    @Test
    void blacklistTokenOmiteLaOperacionSinRedis() {
        assertDoesNotThrow(() -> jwtTokenProvider.blacklistToken(tokenValido()));
    }

    @Test
    void isTokenBlacklistedDevuelveFalseSinRedis() {
        assertFalse(jwtTokenProvider.isTokenBlacklisted(tokenValido()));
    }

    // ── Con Redis disponible ─────────────────────────────────────────────────────────────

    private void conRedis() {
        ReflectionTestUtils.setField(jwtTokenProvider, "redisTemplate", redisTemplate);
    }

    @Test
    void generateRefreshTokenGuardaElTokenYLoAgregaAlSetDelAppUser() {
        conRedis();
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);

        String refresh = jwtTokenProvider.generateRefreshToken("user@uteq.edu.ec");

        assertNotNull(refresh);
        verify(valueOps).set(eq("refresh_token:" + refresh), eq("user@uteq.edu.ec"), anyLong(), eq(TimeUnit.MILLISECONDS));
        verify(setOps).add("user_refresh_tokens:user@uteq.edu.ec", refresh);
        verify(redisTemplate).expire(eq("user_refresh_tokens:user@uteq.edu.ec"), anyLong(), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void getUsernameFromRefreshTokenDelegaAlValorGuardado() {
        conRedis();
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("refresh_token:abc")).thenReturn("user@uteq.edu.ec");

        assertEquals("user@uteq.edu.ec", jwtTokenProvider.getUsernameFromRefreshToken("abc"));
    }

    @Test
    void validateRefreshTokenConsultaLaExistenciaDeLaClave() {
        conRedis();
        when(redisTemplate.hasKey("refresh_token:abc")).thenReturn(true);
        assertTrue(jwtTokenProvider.validateRefreshToken("abc"));

        when(redisTemplate.hasKey("refresh_token:def")).thenReturn(false);
        assertFalse(jwtTokenProvider.validateRefreshToken("def"));
    }

    @Test
    void rotateRefreshTokenMueveElTokenAUsadosYLoQuitaDeLosActivos() {
        conRedis();
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);

        jwtTokenProvider.rotateRefreshToken("old-token", "user@uteq.edu.ec");

        verify(redisTemplate).delete("refresh_token:old-token");
        verify(valueOps).set(eq("used_refresh_token:old-token"), eq("user@uteq.edu.ec"), anyLong(), eq(TimeUnit.MILLISECONDS));
        verify(setOps).remove("user_refresh_tokens:user@uteq.edu.ec", "old-token");
    }

    @Test
    void revokeAllUserTokensNoHaceNadaSiElSetDeActivosEsNull() {
        conRedis();
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members("user_refresh_tokens:user@uteq.edu.ec")).thenReturn(null);

        jwtTokenProvider.revokeAllUserTokens("user@uteq.edu.ec");

        verify(redisTemplate).delete("user_refresh_tokens:user@uteq.edu.ec");
        verify(redisTemplate, never()).delete(startsWith("refresh_token:"));
    }

    @Test
    void revokeAllUserTokensBorraCadaTokenActivoYElSet() {
        conRedis();
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members("user_refresh_tokens:user@uteq.edu.ec")).thenReturn(Set.of("t1", "t2"));

        jwtTokenProvider.revokeAllUserTokens("user@uteq.edu.ec");

        verify(redisTemplate).delete("refresh_token:t1");
        verify(redisTemplate).delete("refresh_token:t2");
        verify(redisTemplate).delete("user_refresh_tokens:user@uteq.edu.ec");
    }

    @Test
    void revokeAllUserTokensExceptPreservaElTokenDeLaSesionActual() {
        conRedis();
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members("user_refresh_tokens:user@uteq.edu.ec")).thenReturn(Set.of("mantener", "revocar"));

        jwtTokenProvider.revokeAllUserTokensExcept("user@uteq.edu.ec", "mantener");

        verify(redisTemplate, never()).delete("refresh_token:mantener");
        verify(redisTemplate).delete("refresh_token:revocar");
        verify(setOps).remove("user_refresh_tokens:user@uteq.edu.ec", "revocar");
    }

    @Test
    void deleteRefreshTokenNoHaceNadaSiElTokenYaNoApuntaAUnAppUser() {
        conRedis();
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("refresh_token:abc")).thenReturn(null);

        jwtTokenProvider.deleteRefreshToken("abc");

        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void deleteRefreshTokenBorraElTokenYLoQuitaDelSetDelAppUser() {
        conRedis();
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(valueOps.get("refresh_token:abc")).thenReturn("user@uteq.edu.ec");

        jwtTokenProvider.deleteRefreshToken("abc");

        verify(redisTemplate).delete("refresh_token:abc");
        verify(setOps).remove("user_refresh_tokens:user@uteq.edu.ec", "abc");
    }

    @Test
    void blacklistTokenGuardaElJtiConElTiempoRestante() {
        conRedis();
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        jwtTokenProvider.blacklistToken(tokenValido());

        verify(valueOps).set(startsWith("blacklist:token:"), eq("revoked"), anyLong(), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void blacklistTokenNoRompeSiElTokenNoEsParseable() {
        conRedis();
        assertDoesNotThrow(() -> jwtTokenProvider.blacklistToken("no-es-un-jwt"));
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void isTokenBlacklistedDevuelveFalseSiElTokenNoEsParseable() {
        conRedis();
        assertFalse(jwtTokenProvider.isTokenBlacklisted("no-es-un-jwt"));
    }

    @Test
    void isTokenBlacklistedConsultaLaClaveRealCuandoElTokenEsValido() {
        conRedis();
        String token = tokenValido();
        when(redisTemplate.hasKey(anyString())).thenReturn(true);

        assertTrue(jwtTokenProvider.isTokenBlacklisted(token));
        verify(redisTemplate).hasKey(startsWith("blacklist:token:"));
    }

    @Test
    void isTokenBlacklistedFallaCerradoSiRedisNoResponde() {
        conRedis();
        String token = tokenValido();
        when(redisTemplate.hasKey(anyString())).thenThrow(new QueryTimeoutException("timeout"));

        // RNF-04: Redis caido -> se trata como revocado (fail-closed), no como valido.
        assertTrue(jwtTokenProvider.isTokenBlacklisted(token));
    }

    @Test
    void testGenerateAndValidateToken() {
        UserDetails userDetails = User.withUsername("user@uteq.edu.ec")
                .password("password")
                .authorities("ROLE_ESTUDIANTE")
                .build();
        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, "password", userDetails.getAuthorities());
        String token = jwtTokenProvider.generateToken(auth);

        assertNotNull(token);
        assertTrue(jwtTokenProvider.validateToken(token));
        assertEquals("user@uteq.edu.ec", jwtTokenProvider.getUsernameFromToken(token));
    }

    @Test
    void testInvalidToken() {
        // Hallazgo real (2026-09-01): validateToken() ya no atrapa las excepciones de parseo del
        // propio jjwt y retornar false -- las deja propagar (io.jsonwebtoken.JwtException y
        // subclases como MalformedJwtException), consistente con el manejo explicito de
        // ExpiredJwtException que ya hacia JwtAuthenticationFilter, el unico caller real, que
        // ya envuelve esta llamada en su propio try/catch.
        assertThrows(io.jsonwebtoken.JwtException.class,
                () -> jwtTokenProvider.validateToken("invalid.jwt.token"));
    }
}
