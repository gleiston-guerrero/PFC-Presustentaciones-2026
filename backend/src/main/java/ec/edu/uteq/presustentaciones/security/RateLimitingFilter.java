package ec.edu.uteq.presustentaciones.security;

import ec.edu.uteq.presustentaciones.dto.ResponseWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro de rate limiting.
 */
@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        
        // Aplica únicamente a la ruta de autenticación de login (POST /api/v1/auth/login)
        if (path.equals("/api/v1/auth/login") && request.getMethod().equalsIgnoreCase("POST")) {
            String ip = getClientIp(request);
            boolean permitido;
            try {
                permitido = rateLimiterService.isAllowed(ip);
            } catch (RateLimiterUnavailableException e) {
                // RNF-04: ni 500 (excepcion sin manejar) ni dejar pasar sin limite -- 503
                // declarando la degradacion explicitamente.
                response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(objectMapper.writeValueAsString(ResponseWrapper.error(
                        "Servicio de límite de intentos no disponible temporalmente. Intenta de nuevo en un momento.")));
                return;
            }
            if (!permitido) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");

                ResponseWrapper<Object> errorResponse = ResponseWrapper.error(
                        "Límite de intentos excedido. Por favor, intente de nuevo en un minuto."
                );
                response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isBlank()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
