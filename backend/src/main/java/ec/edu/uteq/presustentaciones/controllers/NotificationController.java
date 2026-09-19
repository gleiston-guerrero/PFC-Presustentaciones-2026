package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.entities.Notification;
import ec.edu.uteq.presustentaciones.services.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import ec.edu.uteq.presustentaciones.dto.ResponseWrapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Controlador REST de notification.
 */
@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/v1/notificaciones")
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Construye NotificationController, inyectando notificationService.
     * @param notificationService notificationService
     */
    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Crea una notification dirigida a un appUser concreto.
     *
     * @param appUserId destinatario de la notification
     * @param message   texto que vera el appUser
     * @return 200 con la notification creada
     */
    @PostMapping("/crear")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'NOTIFICACIONES_ENVIAR')")
    public ResponseEntity<?> create(@RequestParam("appUserId") Long appUserId, @RequestParam("mensaje") String message) {
        try {
            return ResponseEntity.ok(ResponseWrapper.success(notificationService.createNotification(appUserId, message)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * List.
     * @param pageable pagina y tamano solicitados
     * @return 200 con la pagina de notifications del sistema
     */
    @GetMapping
    @PreAuthorize("@permissionService.hasPermission(authentication, 'NOTIFICACIONES_GLOBAL_VER')")
    public ResponseEntity<?> list(Pageable pageable) {
        try {
            return ResponseEntity.ok(ResponseWrapper.success(notificationService.listNotifications(pageable)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * List by app user.
     * @param appUserId destinatario cuyas notifications se consultan
     * @param pageable  pagina y tamano solicitados
     * @return 200 con la pagina de notifications de ese appUser
     */
    @GetMapping("/usuario/{appUserId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> listByAppUser(@PathVariable("appUserId") Long appUserId, Pageable pageable) {
        try {
            return ResponseEntity.ok(ResponseWrapper.success(notificationService.listByAppUser(appUserId, pageable)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * Contador para el badge de la campana del frontend.
     *
     * @param appUserId appUser consultado
     * @return 200 con la cantidad de notifications sin leer
     */
    @GetMapping("/usuario/{appUserId}/no-leidas")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> countUnread(@PathVariable("appUserId") Long appUserId) {
        try {
            return ResponseEntity.ok(ResponseWrapper.success(java.util.Map.of("total", notificationService.countUnread(appUserId))));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * Mark read.
     * @param id notification a marcar como leida
     * @return 200 con la notification actualizada
     */
    @PatchMapping("/{id}/marcar-leida")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> markRead(@PathVariable("id") Long id) {
        try {
            return ResponseEntity.ok(ResponseWrapper.success(notificationService.markAsRead(id), "Notificación marcada como leída"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * Mark all read.
     * @param appUserId appUser cuyas notifications se marcan todas como leidas
     * @return 200 al confirmar la operacion
     */
    @PatchMapping("/usuario/{appUserId}/marcar-todas-leidas")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> markAllRead(@PathVariable("appUserId") Long appUserId) {
        try {
            notificationService.markAllRead(appUserId);
            return ResponseEntity.ok(ResponseWrapper.success(null, "Todas las notificaciones marcadas como leídas"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * Elimina los registros.
     * @param id notification a delete
     * @return 200 al confirmar el borrado
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> delete(@PathVariable("id") Long id) {
        try {
            notificationService.deleteNotification(id);
            return ResponseEntity.ok(ResponseWrapper.success(null, "Notificación eliminada exitosamente"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }
}
