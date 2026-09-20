package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Contrato. Servicio de notification.
 */
public interface NotificationService {

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
    Notification createNotification(Long appUserId, String message);

    /**
     * List notifications.
     * @param pageable configuración de paginación
     * @return página de todas las notifications del sistema
     */
    Page<Notification> listNotifications(Pageable pageable);

    /**
     * List by app user.
     * @param appUserId identificador del usuario del sistema
     * @param pageable  configuración de paginación
     * @return página de notifications de ese appUser, más recientes primero
     */
    Page<Notification> listByAppUser(Long appUserId, Pageable pageable);

    /**
     * Cuenta los registros con unread.
     * @param appUserId identificador del usuario del sistema
     * @return cantidad de notifications no leídas de ese appUser
     */
    long countUnread(Long appUserId);

    /**
     * Mark as read.
     * @param notificationId id de la notificación a marcar
     * @return la notificación actualizada con {@code leida = true}
     * @throws RuntimeException si la notificación no existe
     */
    Notification markAsRead(Long notificationId);

    /**
     * Mark all read.
     * @param appUserId id del appUser cuyas notifications se marcan todas como leídas
     */
    void markAllRead(Long appUserId);

    /**
     * Elimina una notificación específica
     * @param notificationId id de la notificación a delete
     * @throws RuntimeException si la notificación no existe
     */
    void deleteNotification(Long notificationId);
}
