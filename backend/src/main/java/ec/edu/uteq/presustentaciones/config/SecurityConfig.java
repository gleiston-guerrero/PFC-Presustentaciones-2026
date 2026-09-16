package ec.edu.uteq.presustentaciones.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.edu.uteq.presustentaciones.dto.ResponseWrapper;
import ec.edu.uteq.presustentaciones.security.RateLimitingFilter;
import ec.edu.uteq.presustentaciones.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsService userDetailsService;
    private final RateLimitingFilter rateLimitingFilter;

    // Hallazgo real (despliegue en Railway, 2026-09-06): la lista de origenes CORS estaba fija
    // en codigo y solo incluia localhost -- el login fallaba con "Invalid CORS request" desde
    // el dominio publico real del frontend, aunque la peticion es same-origin desde el
    // navegador (Spring Security igual exige que el header Origin este en la lista). Se agrega
    // via variable de entorno (coma-separado) en vez de hardcodear el dominio de Railway, para
    // no tener que volver a compilar si el dominio cambia.
    @Value("${cors.allowed-origins-extra:}")
    private String corsAllowedOriginsExtra;

    /**
     * Configura la cadena de filtros de seguridad: CORS, CSRF, sesiones stateless, cabeceras
     * de seguridad (HSTS, CSP, X-Frame-Options, X-Content-Type-Options), autorización de rutas
     * bajo {@code /api/v1/}, y el orden de los filtros de rate limiting y JWT.
     *
     * @param http builder de configuración HTTP de Spring Security
     * @return la cadena de filtros de seguridad construida
     * @throws Exception si Spring Security no puede construir la configuración indicada
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Cabeceras de Seguridad (Requisito E14)
                .headers(headers -> headers
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)) // 1 año de HSTS
                        // script-src sin 'unsafe-inline'/'unsafe-eval': el build de produccion de
                        // Angular es AOT y su index.html solo referencia bundles externos (cero
                        // scripts inline), asi que no los necesita -- mantenerlos anulaba buena parte
                        // de la proteccion contra XSS que da esta cabecera (alerta 10055 de ZAP).
                        // connect-src 'self' a secas: el frontend llama al backend con rutas
                        // relativas (/api/...) a traves del proxy de nginx, siempre mismo origen.
                        // Se retiran los origenes localhost (que en un despliegue real bloquearian
                        // las llamadas del frontend), los ws:// (no hay WebSocket: el estado en
                        // tiempo real es polling) y universities.hipolabs.com, que consume el
                        // backend server-side via ExternalApiServiceImpl, nunca el navegador.
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://cdn.jsdelivr.net; font-src 'self' https://fonts.gstatic.com https://cdn.jsdelivr.net data:; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none';"))
                        .frameOptions(frame -> frame.deny()) // X-Frame-Options: DENY
                        .contentTypeOptions(contentType -> {}) // X-Content-Type-Options: nosniff
                )
                // Autorización de peticiones HTTP (Requisito Versionado /api/v1/)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/actuator/**"
                        ).permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().authenticated())
                .authenticationProvider(authenticationProvider())
                // Sin esto, Spring Security responde 403 (AccessDeniedException del usuario anónimo)
                // tanto para "no autenticado / token expirado" como para "autenticado pero sin permiso",
                // y el frontend solo sabe redirigir a /login cuando ve 401 -- con un JWT vencido la app
                // se quedaba mostrando un error genérico en vez de pedir iniciar sesión de nuevo.
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint()))
                // Añadir filtros de Rate Limiting y JWT (Requisitos Rate Limiting & Blacklist)
                .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Responde 401 con un JSON uniforme cuando la petición no está autenticada (token ausente
     * o expirado), en vez del 403 genérico de Spring Security -- así el frontend distingue
     * "hay que iniciar sesión de nuevo" de "estás autenticado pero sin permiso".
     *
     * @return el manejador de errores de autenticación de Spring Security
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        ObjectMapper objectMapper = new ObjectMapper();
        return (request, response, authException) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(
                    ResponseWrapper.error("No autenticado. Inicia sesión de nuevo.")));
        };
    }

    /**
     * Orígenes CORS permitidos: los de desarrollo local, más los que llegan por la variable
     * de entorno {@code cors.allowed-origins-extra} (coma-separado), para no tener que
     * recompilar si cambia el dominio público del frontend.
     *
     * @return la configuración CORS aplicada a todas las rutas
     */
    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        java.util.List<String> origins = new java.util.ArrayList<>(Arrays.asList(
                "http://localhost:4200",
                "http://127.0.0.1:4200",
                "http://localhost:3000",
                "http://localhost",
                "http://localhost:80"
        ));
        if (corsAllowedOriginsExtra != null && !corsAllowedOriginsExtra.isBlank()) {
            for (String origin : corsAllowedOriginsExtra.split(",")) {
                String trimmed = origin.trim();
                if (!trimmed.isEmpty()) {
                    origins.add(trimmed);
                }
            }
        }
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** @return el proveedor de autenticación DAO, con el {@link UserDetailsService} y el encoder de contraseñas del sistema */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /** @return el codificador de contraseñas (BCrypt) usado en todo el sistema */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * @param config configuración de autenticación de Spring Security
     * @return el {@link AuthenticationManager} resuelto por Spring, usado por el flujo de login
     * @throws Exception si Spring no puede resolver el manager de autenticación
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}