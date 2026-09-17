package ec.edu.uteq.presustentaciones.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    /**
     * Custom open a p i.
     * @return el OpenAPI correspondiente
     */
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("Sistema de Gestión de Pre-Sustentaciones UTEQ - API OpenAPI 3.0")
                        .version("0.9.0-rc")
                        .description("Documentación oficial de los endpoints RESTful para la gestión de solicitudes, anteproyectos, jurados, cronogramas, evaluaciones y actas de pre-sustentación en la Universidad Técnica Estatal de Quevedo.")
                        .contact(new Contact()
                                .name("Equipo de Desarrollo PFC-UTEQ")
                                .email("soporte-presustentaciones@uteq.edu.ec")
                                .url("https://www.uteq.edu.ec"))
                        .license(new License()
                                .name("MIT Open Source License")
                                .url("https://opensource.org/licenses/MIT")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Ingrese el token JWT obtenido en /api/auth/login")));
    }
}
