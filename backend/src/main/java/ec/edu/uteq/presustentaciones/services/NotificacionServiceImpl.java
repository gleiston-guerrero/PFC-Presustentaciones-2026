package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.Notificacion;
import ec.edu.uteq.presustentaciones.entities.Usuario;
import ec.edu.uteq.presustentaciones.repositories.NotificacionRepository;
import ec.edu.uteq.presustentaciones.repositories.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NotificacionServiceImpl implements NotificacionService {

    private final NotificacionRepository notificacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final EmailService emailService;

    private void validarAcceso(Long targetUsuarioId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("Usuario no autenticado");
        }
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) return;

        Usuario actual = usuarioRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("Usuario actual no encontrado"));

        if (!actual.getId().equals(targetUsuarioId)) {
            throw new org.springframework.security.access.AccessDeniedException("No tienes permiso para acceder a las notificaciones de este usuario");
        }
    }

    /**
     * Crea y persiste una notificación para un usuario, y adicionalmente le envía un correo si
     * tiene configurado un {@code emailNotificaciones}. El remitente que figura en el correo se
     * resuelve del usuario autenticado en el contexto de seguridad actual, o uno genérico si no
     * hay ninguno.
     *
     * @param usuarioId id del usuario receptor de la notificación
     * @param mensaje   texto de la notificación
     * @return la notificación creada
     * @throws RuntimeException si el usuario receptor no existe
     */
    @Override
    public Notificacion crearNotificacion(Long usuarioId, String mensaje) {
        Usuario receptor = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Notificacion notificacion = notificacionRepository.save(
                Notificacion.builder()
                        .usuario(receptor)
                        .mensaje(mensaje)
                        .fecha(LocalDateTime.now())
                        .leida(false)
                        .build());

        // Obtener remitente desde el contexto de seguridad (usuario logueado)
        String[] remitente = resolverRemitente();

        // Enviar email al correo de notificaciones del receptor (si está configurado)
        String destino = (receptor.getEmailNotificaciones() != null
                && !receptor.getEmailNotificaciones().isBlank())
                ? receptor.getEmailNotificaciones()
                : null;

        if (destino != null) {
            emailService.enviarNotificacion(destino, mensaje, remitente[0], remitente[1]);
        }

        return notificacion;
    }

    /**
     * Resuelve el nombre y email del usuario logueado para usarlo como remitente.
     * Retorna [nombre, email].
     */
    private String[] resolverRemitente() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()
                    && !"anonymousUser".equals(String.valueOf(auth.getPrincipal()))) {
                Optional<Usuario> opt = usuarioRepository.findByEmail(auth.getName());
                if (opt.isPresent()) {
                    Usuario u = opt.get();
                    return new String[]{
                            u.getNombre() + " " + u.getApellido(),
                            u.getEmail()
                    };
                }
            }
        } catch (Exception ignored) {
            // Sin contexto de seguridad: usar valor genérico
        }
        return new String[]{"Sistema de Pre-Sustentaciones", "noreply@uteq.edu.ec"};
    }

    /**
     * @param pageable configuración de paginación
     * @return página de todas las notificaciones del sistema
     */
    @Override
    public Page<Notificacion> listarNotificaciones(Pageable pageable) {
        return notificacionRepository.findAll(pageable);
    }

    /**
     * @param usuarioId id del usuario
     * @param pageable  configuración de paginación
     * @return página de notificaciones de ese usuario, más recientes primero
     */
    @Override
    public Page<Notificacion> listarPorUsuario(Long usuarioId, Pageable pageable) {
        validarAcceso(usuarioId);
        return notificacionRepository.findByUsuarioIdOrderByFechaDesc(usuarioId, pageable);
    }

    /**
     * @param usuarioId id del usuario
     * @return cantidad de notificaciones no leídas de ese usuario
     */
    @Override
    public long contarNoLeidas(Long usuarioId) {
        validarAcceso(usuarioId);
        return notificacionRepository.countByUsuarioIdAndLeidaFalse(usuarioId);
    }

    /**
     * @param id id de la notificación a marcar
     * @return la notificación actualizada con {@code leida = true}
     * @throws RuntimeException si la notificación no existe
     */
    @Override
    public Notificacion marcarComoLeida(Long id) {
        Notificacion n = notificacionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notificación no encontrada"));
        validarAcceso(n.getUsuario().getId());
        n.setLeida(true);
        return notificacionRepository.save(n);
    }

    /** @param usuarioId id del usuario cuyas notificaciones se marcan todas como leídas */
    @Override
    @org.springframework.transaction.annotation.Transactional
    public void marcarTodasLeidas(Long usuarioId) {
        validarAcceso(usuarioId);
        notificacionRepository.marcarTodasLeidasPorUsuario(usuarioId);
    }

    /**
     * Elimina una notificación específica.
     *
     * @param id id de la notificación a eliminar
     * @throws RuntimeException si la notificación no existe
     */
    @Override
    public void eliminarNotificacion(Long id) {
        Notificacion n = notificacionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notificación no encontrada"));
        validarAcceso(n.getUsuario().getId());
        notificacionRepository.delete(n);
    }
}
