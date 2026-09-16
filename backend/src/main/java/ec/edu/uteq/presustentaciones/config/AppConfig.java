package ec.edu.uteq.presustentaciones.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.cache.annotation.EnableCaching;

@Configuration
@EnableJpaRepositories(basePackages = "ec.edu.uteq.presustentaciones.repositories")
@EnableAsync
@EnableCaching
@Slf4j
public class AppConfig implements CachingConfigurer {

    /**
     * RNF-04/RNF-03: a diferencia de la revocación (JwtTokenProvider) y el límite de tasa
     * (RateLimiterService), la caché de lectura es rendimiento, no seguridad -- con Redis
     * caído, un {@code @Cacheable} debe resolver contra el origen (la base de datos) y servir
     * igual, no fallar. Sin este {@code CacheErrorHandler}, el comportamiento por omisión de
     * Spring ({@code SimpleCacheErrorHandler}) relanza cualquier excepción de la caché y el
     * método anotado nunca llega a ejecutarse -- degradación de Redis tumbaría endpoints que
     * no dependen de Redis para nada más que el caché de lectura.
     *
     * @return un manejador que registra (log) los fallos de caché y deja continuar la
     *         ejecución en vez de propagarlos
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            /**
             * @param exception fallo ocurrido al leer de la caché
             * @param cache     caché afectada
             * @param key       llave que se intentaba leer
             */
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("DEGRADACION (RNF-03): fallo al leer de la cache '{}' (Redis caído); "
                        + "se resuelve contra el origen. causa={}", cache.getName(), exception.getMessage());
            }

            /**
             * @param exception fallo ocurrido al escribir en la caché
             * @param cache     caché afectada
             * @param key       llave que se intentaba escribir
             * @param value     valor que se intentaba cachear
             */
            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("DEGRADACION (RNF-03): fallo al escribir en la cache '{}' (Redis caído); "
                        + "se continúa sin cachear. causa={}", cache.getName(), exception.getMessage());
            }

            /**
             * @param exception fallo ocurrido al invalidar una entrada de la caché
             * @param cache     caché afectada
             * @param key       llave que se intentaba invalidar
             */
            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("DEGRADACION (RNF-03): fallo al invalidar la cache '{}' (Redis caído). causa={}",
                        cache.getName(), exception.getMessage());
            }

            /**
             * @param exception fallo ocurrido al limpiar la caché
             * @param cache     caché afectada
             */
            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("DEGRADACION (RNF-03): fallo al limpiar la cache '{}' (Redis caído). causa={}",
                        cache.getName(), exception.getMessage());
            }
        };
    }
}
