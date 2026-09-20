package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.Notification;
import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.repositories.NotificationRepository;
import ec.edu.uteq.presustentaciones.repositories.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Implementacion del servicio de notification.
 */
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final AppUserRepository appUserRepository;
    private final EmailService emailService;

    private void validateAccess(Long targetAppUserId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("Usuario no autenticado");
        }
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) return;

        AppUser actual = appUserRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("Usuario actual no encontrado"));

        if (!actual.getId().equals(targetAppUserId)) {
            throw new org.springframework.security.access.AccessDeniedException("No tienes permiso para acceder a las notificaciones de este usuario");
        }
    }

    /**
     * Crea y persiste una notificación para un appUser, y adicionalmente le envía un correo si
     * tiene configurado un {@code emailNotifications}. El remitente que figura en el correo se
     * resuelve del appUser autenticado en el contexto de seguridad actual, o uno genérico si no
     * hay ninguno.
     *
     * @param appUserId id del appUser receptor de la notificación
     * @param message   texto de la notificación
     * @return la notificación creada
     * @throws RuntimeException si el appUser receptor no existe
     */
    @Override
    public Notification createNotification(Long appUserId, String message) {
        AppUser receiver = appUserRepository.findById(appUserId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Notification notification = notificationRepository.save(
                Notification.builder()
                        .appUser(receiver)
                        .message(message)
                        .date(LocalDateTime.now())
                        .read(false)
                        .build());

        // Obtain remitente desde el contexto de seguridad (appUser logueado)
        String[] sender = resolveSender();

        // Send email al correo de notifications del receptor (si está configurado)
        String destino = (receiver.getEmailNotifications() != null
                && !receiver.getEmailNotifications().isBlank())
                ? receiver.getEmailNotifications()
                : null;

        if (destino != null) {
            emailService.sendNotification(destino, message, sender[0], sender[1]);
        }

        return notification;
    }

    /**
     * Resuelve el nombre y email del appUser logueado para usarlo como remitente.
     * Retorna [nombre, email].
     */
    private String[] resolveSender() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()
                    && !"anonymousUser".equals(String.valueOf(auth.getPrincipal()))) {
                Optional<AppUser> opt = appUserRepository.findByEmail(auth.getName());
                if (opt.isPresent()) {
                    AppUser u = opt.get();
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
     * @return página de todas las notifications del sistema
     */
    @Override
    public Page<Notification> listNotifications(Pageable pageable) {
        return notificationRepository.findAll(pageable);
    }

    /**
     * @param appUserId identificador del usuario del sistema
     * @param pageable  configuración de paginación
     * @return página de notifications de ese appUser, más recientes primero
     */
    @Override
    public Page<Notification> listByAppUser(Long appUserId, Pageable pageable) {
        validateAccess(appUserId);
        return notificationRepository.findByAppUserIdOrderByDateDesc(appUserId, pageable);
    }

    /**
     * @param appUserId identificador del usuario del sistema
     * @return cantidad de notifications no leídas de ese appUser
     */
    @Override
    public long countUnread(Long appUserId) {
        validateAccess(appUserId);
        return notificationRepository.countByAppUserIdAndReadFalse(appUserId);
    }

    /**
     * @param id id de la notificación a marcar
     * @return la notificación actualizada con {@code leida = true}
     * @throws RuntimeException si la notificación no existe
     */
    @Override
    public Notification markAsRead(Long id) {
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notificación no encontrada"));
        validateAccess(n.getAppUser().getId());
        n.setRead(true);
        return notificationRepository.save(n);
    }

    /** @param appUserId id del appUser cuyas notifications se marcan todas como leídas */
    @Override
    @org.springframework.transaction.annotation.Transactional
    public void markAllRead(Long appUserId) {
        validateAccess(appUserId);
        notificationRepository.markAllReadByAppUser(appUserId);
    }

    /**
     * Elimina una notificación específica.
     *
     * @param id id de la notificación a delete
     * @throws RuntimeException si la notificación no existe
     */
    @Override
    public void deleteNotification(Long id) {
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notificación no encontrada"));
        validateAccess(n.getAppUser().getId());
        notificationRepository.delete(n);
    }
}
