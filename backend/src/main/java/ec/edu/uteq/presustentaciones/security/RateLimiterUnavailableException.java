package ec.edu.uteq.presustentaciones.security;

/**
 * RNF-04: señala que {@link RateLimiterService} no pudo consultar su almacén (Redis caído),
 * no que la petición esté permitida ni blockada. {@code RateLimitingFilter} la traduce en
 * {@code 503}, nunca en el {@code 500} genérico ni en dejar pasar la petición sin límite.
 */
public class RateLimiterUnavailableException extends RuntimeException {
    public RateLimiterUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
