package ec.edu.uteq.presustentaciones.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    /**
     * Endpoints de autenticación que nunca deben pasar por el filtro JWT.
     *
     * El interceptor del frontend adjunta el header {@code Authorization: Bearer <token>} a
     * TODAS las peticiones mientras exista un token en localStorage, incluida la de login. Si
     * ese token está caducado, este filtro respondía 401 "Token expirado" y hacía {@code return}
     * antes de que se executea el handler de login: el appUser quedaba blockado sin poder
     * volver a entrar (solo se recuperaba borrando el localStorage a mano). Estos endpoints no
     * requieren autenticación previa, así que se saltan el filtro por completo.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.contains("/auth/login")
                || path.contains("/auth/refresh")
                || path.contains("/auth/register")
                // RF-05 (fase 6): mismo motivo que login -- quien pide recuperar o reset
                // su contraseña puede tener un token vencido (o ninguno) en localStorage, y el
                // interceptor del frontend lo adjunta igual a esta peticion.
                || path.contains("/auth/recuperar")
                || path.contains("/auth/restablecer");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt)) {
                try {
                    if (jwtTokenProvider.validateToken(jwt)) {
                        String username = jwtTokenProvider.getUsernameFromToken(jwt);
                        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails,
                                        null,
                                        userDetails.getAuthorities()
                                );

                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } catch (io.jsonwebtoken.ExpiredJwtException ex) {
                    log.warn("Token expirado: {}", ex.getMessage());
                    sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Token expirado");
                    return;
                } catch (Exception ex) {
                    log.error("Token inválido: {}", ex.getMessage());
                    sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Token inválido");
                    return;
                }
            }
        } catch (Exception e) {
            log.error("No se pudo establecer la autenticación: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        // Soporte para Cookie de seguridad HTTP-Only
        if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                if ("jwtToken".equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private void sendErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(String.format("{\"success\": false, \"message\": \"%s\"}", message));
    }
}
