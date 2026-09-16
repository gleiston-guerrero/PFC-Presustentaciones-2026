package ec.edu.uteq.presustentaciones.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;

    /**
     * @param ipAddress dirección IP del intento de login a limitar
     * @return {@code true} si la IP todavía no alcanzó el máximo de intentos permitidos
     * @throws RateLimiterUnavailableException RNF-04: si Redis no responde, esto NO es "sin
     *         límite" (dejaría pasar fuerza bruta) ni un 500 sin explicación -- es una
     *         degradación identificable que {@code RateLimitingFilter} convierte en 503.
     */
    public boolean isAllowed(String ipAddress) {
        return isAllowed("ratelimit:login:" + ipAddress, 6, 60);
    }

    /**
     * Versión general, reutilizada por RF-05 (submissions de recuperación de contraseña, fase
     * 6) para no duplicar la lógica de ventana deslizante ni el fail-closed de RNF-04 con una
     * clave/ventana propia.
     *
     * @param key            clave completa en Redis (ya prefijada por el llamador)
     * @param maxIntentos    máximo de intentos permitidos dentro de la ventana
     * @param ventanaSegundos duración de la ventana, en segundos
     * @return {@code true} si la clave todavía no alcanzó el máximo de intentos en la ventana
     * @throws RateLimiterUnavailableException RNF-04: mismo fail-closed que {@link #isAllowed(String)}
     */
    public boolean isAllowed(String key, int maxIntentos, long ventanaSegundos) {
        try {
            String currentVal = redisTemplate.opsForValue().get(key);

            if (currentVal == null) {
                redisTemplate.opsForValue().set(key, "1", ventanaSegundos, TimeUnit.SECONDS);
                return true;
            }

            int attempts = Integer.parseInt(currentVal);
            if (attempts >= maxIntentos) {
                return false;
            }

            redisTemplate.opsForValue().increment(key);
            return true;
        } catch (DataAccessException e) {
            log.error("DEGRADACION (RNF-04): no se pudo consultar el limite de tasa en Redis para la clave "
                    + "'{}'; se rechaza la peticion como degradada (503), no se deja pasar sin limite. causa={}",
                    key, e.getMessage());
            throw new RateLimiterUnavailableException("Almacén de límite de tasa no disponible", e);
        }
    }
}
