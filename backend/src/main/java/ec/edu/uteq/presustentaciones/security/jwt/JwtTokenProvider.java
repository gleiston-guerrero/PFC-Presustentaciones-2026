package ec.edu.uteq.presustentaciones.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Value("${jwt.refresh-expiration:604800000}")
    private long jwtRefreshExpiration; // 7 días por defecto

    @Autowired(required = false) // Hacemos opcional para tests unitarios simples
    private StringRedisTemplate redisTemplate;

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * @param authentication autenticación del appUser ya validada por Spring Security
     * @return un JWT de acceso firmado para el username del principal autenticado
     */
    public String generateToken(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return generateTokenFromUsername(userDetails.getUsername());
    }

    /**
     * @param username sujeto (username) del token a emitir
     * @return un JWT de acceso firmado, con expiración {@code jwt.expiration}
     */
    public String generateTokenFromUsername(String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);

        return Jwts.builder()
                .id(UUID.randomUUID().toString()) // jti
                .issuer("PFC-Presustentaciones-UTEQ") // iss
                .subject(username) // sub
                .audience().add("PFC-Frontend-Angular").and() // aud
                .issuedAt(now) // iat
                .notBefore(now) // nbf
                .expiration(expiryDate) // exp
                .signWith(getSigningKey())
                .compact();
    }

    // Generate y almacenar Refresh Token en Redis (Multi-device support)
    /**
     * @param username titular del refresh token a emitir
     * @return un refresh token opaco (UUID) registrado en Redis, o el UUID sin persistir si
     *         Redis no está disponible ({@code redisTemplate} es {@code null})
     */
    public String generateRefreshToken(String username) {
        String refreshToken = UUID.randomUUID().toString();
        if (redisTemplate != null) {
            String tokenKey = "refresh_token:" + refreshToken;
            String userSetKey = "user_refresh_tokens:" + username;
            
            // Save token -> username
            redisTemplate.opsForValue().set(tokenKey, username, jwtRefreshExpiration, TimeUnit.MILLISECONDS);
            // Add a la lista de tokens activos del appUser
            redisTemplate.opsForSet().add(userSetKey, refreshToken);
            redisTemplate.expire(userSetKey, jwtRefreshExpiration, TimeUnit.MILLISECONDS);
            
            log.info("Refresh token generado y guardado en Redis para el usuario: {}", username);
        }
        return refreshToken;
    }

    /**
     * @param token refresh token opaco a resolve
     * @return el username dueño del token, o {@code null} si no existe, expiró, o Redis no
     *         está disponible
     */
    public String getUsernameFromRefreshToken(String token) {
        if (redisTemplate == null) return null;
        return redisTemplate.opsForValue().get("refresh_token:" + token);
    }

    /**
     * @param token refresh token ya rotado (movido a "usados" por {@link #rotateRefreshToken})
     * @return el username dueño del token usado, o {@code null} si no está registrado como
     *         usado o Redis no está disponible
     */
    public String getUsernameFromUsedRefreshToken(String token) {
        if (redisTemplate == null) return null;
        return redisTemplate.opsForValue().get("used_refresh_token:" + token);
    }

    /**
     * @param token refresh token a validate
     * @return {@code true} si el token existe entre los activos en Redis; {@code false} si no
     *         existe, expiró, o Redis no está disponible
     */
    public boolean validateRefreshToken(String token) {
        if (redisTemplate == null) return false;
        return Boolean.TRUE.equals(redisTemplate.hasKey("refresh_token:" + token));
    }

    /**
     * Invalida {@code oldToken} y lo mueve a "usados" (detección de reutilización), quitándolo
     * de la lista de tokens activos del appUser. No emite un token nuevo.
     *
     * @param oldToken refresh token a rotar
     * @param username titular del token
     */
    public void rotateRefreshToken(String oldToken, String username) {
        if (redisTemplate == null) return;
        
        // Mover a "usados" para detectar reutilización
        redisTemplate.delete("refresh_token:" + oldToken);
        redisTemplate.opsForValue().set("used_refresh_token:" + oldToken, username, jwtRefreshExpiration, TimeUnit.MILLISECONDS);
        
        // Remove de la lista de activos
        redisTemplate.opsForSet().remove("user_refresh_tokens:" + username, oldToken);
    }
    
    /**
     * Revoca todos los refresh tokens activos del appUser (logout de todas las sesiones).
     *
     * @param username titular cuyas sesiones se revocan
     */
    public void revokeAllUserTokens(String username) {
        if (redisTemplate == null) return;
        String userSetKey = "user_refresh_tokens:" + username;
        java.util.Set<String> activeTokens = redisTemplate.opsForSet().members(userSetKey);
        if (activeTokens != null) {
            for (String t : activeTokens) {
                redisTemplate.delete("refresh_token:" + t);
            }
        }
        redisTemplate.delete(userSetKey);
        log.warn("Todos los refresh tokens han sido revocados para el usuario: {}", username);
    }

    /**
     * RF-06: igual que {@link #revokeAllUserTokens(String)}, pero preserva un token -- la
     * sesión desde la que se hizo el cambio de contraseña. Revocar también esa dejaría al
     * appUser fuera justo después de un cambio legítimo, obligándolo a iniciar sesión de
     * nuevo sin necesidad.
     *
     * @param username     titular cuyas sesiones se revocan
     * @param tokenAConservar refresh token de la sesión actual, que NO se revoca
     */
    public void revokeAllUserTokensExcept(String username, String tokenAConservar) {
        if (redisTemplate == null) return;
        String userSetKey = "user_refresh_tokens:" + username;
        java.util.Set<String> activeTokens = redisTemplate.opsForSet().members(userSetKey);
        if (activeTokens != null) {
            for (String t : activeTokens) {
                if (t.equals(tokenAConservar)) continue;
                redisTemplate.delete("refresh_token:" + t);
                redisTemplate.opsForSet().remove(userSetKey, t);
            }
        }
        log.warn("Todos los refresh tokens salvo el de la sesión actual han sido revocados para el usuario: {}", username);
    }

    /**
     * Elimina un refresh token puntual (logout de una sola sesión).
     *
     * @param token refresh token a delete
     */
    public void deleteRefreshToken(String token) {
        if (redisTemplate == null) return;
        String username = getUsernameFromRefreshToken(token);
        if (username != null) {
            redisTemplate.delete("refresh_token:" + token);
            redisTemplate.opsForSet().remove("user_refresh_tokens:" + username, token);
        }
    }

    // Invalidate token JWT (Blacklist en Redis - Requisito Blacklist)
    /**
     * Agrega un JWT de acceso a la blacklist de Redis hasta su expiración natural, para
     * invalidatelo antes de tiempo (p. ej. en logout). Si el token ya expiró o Redis no está
     * disponible, no hace nada.
     *
     * @param token JWT de acceso a invalidate
     */
    public void blacklistToken(String token) {
        if (redisTemplate == null) {
            log.warn("StringRedisTemplate no está disponible. Blacklist omitida.");
            return;
        }
        try {
            Claims claims = getClaimsFromToken(token);
            String jti = claims.getId();
            Date expiration = claims.getExpiration();
            long remainingTime = expiration.getTime() - System.currentTimeMillis();

            if (remainingTime > 0) {
                String key = "blacklist:token:" + jti;
                redisTemplate.opsForValue().set(key, "revoked", remainingTime, TimeUnit.MILLISECONDS);
                log.info("Token JWT blacklisted (JTI: {}) por los siguientes {} ms", jti, remainingTime);
            }
        } catch (Exception e) {
            log.error("No se pudo agregar el token a la blacklist: {}", e.getMessage());
        }
    }

    /**
     * RNF-04: si Redis no responde, este metodo debe fallar CERRADO (tratar el token como
     * revocado) en vez de silenciarse como "no revocado" -- lo contrario deja que un token
     * cerrado por logout vuelva a aceptarse justo cuando la infraestructura esta degradada.
     * Por eso el parseo del token (un problema del TOKEN) y la consulta a Redis (un problema
     * de DISPONIBILIDAD DEL ALMACEN) estan en blocks try/catch separados: solo la segunda
     * excepcion dispara el fail-closed. Un token malformado/invalido sigue sin blockar nada
     * aqui -- lo rechaza el parseo real de {@code validateToken()}, con su propio motivo.
     *
     * @param token JWT de acceso a comprobar
     * @return {@code true} si el token está en la blacklist o si Redis no responde
     *         (fail-closed); {@code false} en caso contrario
     */
    public boolean isTokenBlacklisted(String token) {
        if (redisTemplate == null) {
            return false;
        }
        String jti;
        try {
            Claims claims = getClaimsFromToken(token);
            jti = claims.getId();
            if (jti == null) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey("blacklist:token:" + jti));
        } catch (org.springframework.dao.DataAccessException e) {
            log.error("DEGRADACION (RNF-04): no se pudo consultar la blacklist de tokens en Redis; "
                    + "se trata el token como revocado (fail-closed), no como valido. jti={} causa={}",
                    jti, e.getMessage());
            return true;
        }
    }

    /**
     * @param token JWT firmado a parsear
     * @return los claims del token
     * @throws io.jsonwebtoken.JwtException si la firma no es válida o el token está mal formado
     */
    public Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * @param token JWT firmado a parsear
     * @return el subject (username) codificado en el token
     */
    public String getUsernameFromToken(String token) {
        return getClaimsFromToken(token).getSubject();
    }

    /**
     * @param token JWT de acceso a validate
     * @return {@code true} si el token tiene firma válida y no está en la blacklist
     * @throws io.jsonwebtoken.JwtException si el token está en la blacklist, mal formado, o su
     *                                       firma no es válida
     */
    public boolean validateToken(String token) {
        if (isTokenBlacklisted(token)) {
            log.warn("Token JWT rechazado: se encuentra en la blacklist.");
            throw new io.jsonwebtoken.JwtException("Token en blacklist");
        }
        Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token);
        return true;
    }
}
