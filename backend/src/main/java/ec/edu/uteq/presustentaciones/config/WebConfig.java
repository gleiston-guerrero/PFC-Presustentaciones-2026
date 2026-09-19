package ec.edu.uteq.presustentaciones.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuracion de web.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Constructor sin argumentos: Spring instancia la configuracion web (CORS y recursos estaticos).
     * Se declara explicitamente porque javadoc avisa del constructor
     * por defecto, que no puede llevar comentario.
     */
    public WebConfig() {
        // sin estado que inicializar
    }

    /**
     * Add cors mappings.
     * @param registry registry
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(
                        "http://localhost:4200",
                        "http://localhost:3000",
                        "http://localhost",
                        "http://localhost:80"
                )
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}