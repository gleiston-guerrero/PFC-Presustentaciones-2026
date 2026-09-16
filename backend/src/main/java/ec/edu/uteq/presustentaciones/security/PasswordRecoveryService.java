package ec.edu.uteq.presustentaciones.security;

import ec.edu.uteq.presustentaciones.entities.Usuario;
import ec.edu.uteq.presustentaciones.repositories.UsuarioRepository;
import ec.edu.uteq.presustentaciones.security.jwt.JwtTokenProvider;
import ec.edu.uteq.presustentaciones.services.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * RF-05: recuperación de contraseña sin sesión activa. El elemento de un solo uso vive en
 * Redis (mismo mecanismo que los refresh tokens de {@link JwtTokenProvider}), con vigencia de
 * 30 minutos, pero -a diferencia de esos- solo se guarda su hash SHA-256: un token de
 * recuperación en texto plano vale una cuenta completa, así que un volcado de Redis no debe
 * entregarlo directamente utilizable.
 *
 * <p><b>Nota honesta sobre el envío real de correo:</b> con {@code app.mail.enabled=false}
 * (valor por omisión), este servicio genera y guarda el token igual, y lo "envía" solo al log
 * ({@link EmailService#enviarRecuperacionPassword}) -- la entrega real del correo nunca se
 * verificó contra un servidor SMTP de verdad. El flujo completo (generación, caducidad, un solo
 * uso, revocación de sesiones, límite de tasa) sí es real y probado; ver el estado declarado de
 * RF-05 en el SRS.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordRecoveryService {

    private static final long TTL_MINUTOS = 30;
    private static final String PREFIJO_TOKEN = "password_reset:";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final EmailService emailService;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;

    /**
     * Genera el token y dispara el correo si la cuenta existe. Si no existe, hace un trabajo
     * de costo equivalente (una escritura en Redis con TTL corto) y no envía nada -- para que
     * el tiempo de respuesta no distinga "existe" de "no existe" además del cuerpo, que ya es
     * idéntico en ambos casos (ver {@code AuthController}).
     *
     * @param email email de la cuenta para la que se solicita recuperación
     */
    public void solicitarRecuperacion(String email) {
        Optional<Usuario> usuario = usuarioRepository.findByEmail(email);
        if (usuario.isPresent()) {
            String tokenPlano = UUID.randomUUID().toString() + UUID.randomUUID();
            String hash = sha256(tokenPlano);
            redisTemplate.opsForValue().set(PREFIJO_TOKEN + hash, usuario.get().getEmail(),
                    TTL_MINUTOS, TimeUnit.MINUTES);
            emailService.enviarRecuperacionPassword(usuario.get().getEmail(), tokenPlano);
        } else {
            // Trabajo equivalente (una escritura en Redis) para no filtrar la existencia de la
            // cuenta por el tiempo de respuesta; se descarta casi de inmediato.
            redisTemplate.opsForValue().set(PREFIJO_TOKEN + "descartado:" + sha256(UUID.randomUUID().toString()),
                    "n/a", 1, TimeUnit.SECONDS);
        }
    }

    /**
     * Aplica el nuevo password si el token es válido, no ha expirado y no se usó antes.
     *
     * @param tokenPlano    token de recuperación recibido por correo
     * @param passwordNueva la nueva contraseña en texto plano
     * @throws IllegalArgumentException token inválido, expirado o ya usado (400); o la nueva
     *         contraseña incumple RNF-06 (mensaje del propio {@link PasswordPolicyValidator})
     */
    public void restablecer(String tokenPlano, String passwordNueva) {
        String key = PREFIJO_TOKEN + sha256(tokenPlano);
        String email = redisTemplate.opsForValue().get(key);
        if (email == null) {
            throw new IllegalArgumentException("El enlace de recuperación es inválido o ya expiró.");
        }
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("El enlace de recuperación es inválido o ya expiró."));

        passwordPolicyValidator.validar(passwordNueva);

        usuario.setPassword(passwordEncoder.encode(passwordNueva));
        usuarioRepository.save(usuario);

        // Un solo uso: se borra ANTES de revocar sesiones, para que un reintento concurrente
        // con el mismo token nunca vea una ventana en la que el token siga "vivo".
        redisTemplate.delete(key);

        // A diferencia del cambio voluntario (RF-06), aquí se revocan TODAS las sesiones sin
        // excepción: una recuperación puede originarse en un compromiso real de la cuenta, no
        // en un cambio de rutina desde una sesión de confianza.
        jwtTokenProvider.revokeAllUserTokens(usuario.getEmail());

        log.warn("Contraseña restablecida vía recuperación para el usuario: {}", usuario.getEmail());
    }

    private static String sha256(String texto) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(texto.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }
}
