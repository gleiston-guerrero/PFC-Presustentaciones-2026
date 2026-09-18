package ec.edu.uteq.presustentaciones.security.jwt;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * RNF-07: un access token vigente pero expirado debe responder 401 con un mensaje
 * distinguible de "sin permiso" (403 de {@code GlobalExceptionHandler.handleAccessDenied}) --
 * el frontend necesita saber si debe reautenticar (expirado) o simplemente no mostrar la
 * acción (sin permission). El comportamiento ya existía en {@code JwtAuthenticationFilter}; esta
 * prueba solo lo cubre, sin cambiarlo.
 */
class JwtAuthenticationFilterTest {

    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
    private final UserDetailsService userDetailsService = mock(UserDetailsService.class);
    private final JwtAuthenticationFilter filtro = new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService);

    @Test
    void tokenExpiradoResponde401WithMessageDistinguibleDeWithoutPermission() throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-expirado");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(jwtTokenProvider.validateToken("token-expirado"))
                .thenThrow(new ExpiredJwtException(null, null, "expirado"));

        filtro.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        String cuerpo = response.getContentAsString();
        assertTrue(cuerpo.contains("Token expirado"), "debe declarar expiracion: " + cuerpo);
        assertFalse(cuerpo.toLowerCase().contains("permiso"),
                "el mensaje de un token expirado no debe confundirse con el de falta de permiso: " + cuerpo);
        // No debe delegar al resto de la cadena: la peticion se corta aqui, con el 401 ya escrito.
        verifyNoInteractions(chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void tokenMalformadoRespondeWithMessageDistintoAlDeExpirado() throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-basura");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(jwtTokenProvider.validateToken("token-basura"))
                .thenThrow(new io.jsonwebtoken.MalformedJwtException("malformado"));

        filtro.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        String cuerpo = response.getContentAsString();
        assertTrue(cuerpo.contains("Token inválido"), "debe distinguirse del mensaje de expirado: " + cuerpo);
        assertFalse(cuerpo.contains("Token expirado"));
    }

    @Test
    void sinTokenDejaPasarLaRequestWithoutEstablecerAutenticacion() throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filtro.doFilter(request, response, chain);

        // Sin token no hay nada que reject aqui -- SecurityConfig decide mas adelante si la
        // ruta requiere autenticacion. El filtro solo debe dejar pasar, no fijar Authentication.
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
