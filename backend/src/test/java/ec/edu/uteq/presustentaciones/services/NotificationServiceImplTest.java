package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.Notification;
import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.repositories.NotificationRepository;
import ec.edu.uteq.presustentaciones.repositories.AppUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.springframework.security.access.AccessDeniedException;

/**
 * NotificationServiceImpl.createNotification() resuelve el remitente desde el contexto de
 * seguridad y solo envia email si el receptor configuro emailNotifications -- sin test
 * dedicado pese a ser invocado desde practicamente todos los otros servicios (Submission,
 * Minutes, Tutoring, Panelist...) para notify eventos del flujo.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private EmailService emailService;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @AfterEach
    void cleanContextoDeSeguridad() {
        SecurityContextHolder.clearContext();
    }

    private AppUser receiverWith(String emailNotifications) {
        return AppUser.builder().id(1L).nombre("Ana").apellido("Torres")
                .email("atorres@uteq.edu.ec").emailNotifications(emailNotifications).build();
    }

    @Test
    void createNotificationThrowsExceptionIfAppUserNotExists() {
        when(appUserRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> notificationService.createNotification(99L, "hola"));
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void createNotificationSavesNotificationStillWithoutEmailConfigured() {
        AppUser receiver = receiverWith(null);
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(receiver));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        Notification result = notificationService.createNotification(1L, "Tu solicitud fue aprobada");

        assertEquals("Tu solicitud fue aprobada", result.getMessage());
        assertFalse(result.isRead());
        verify(emailService, never()).sendNotification(any(), any(), any(), any());
    }

    @Test
    void createNotificationSendsEmailIfReceiverHasEmailNotificationsConfigured() {
        AppUser receiver = receiverWith("atorres.notif@gmail.com");
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(receiver));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        // Sin autenticacion en el contexto -> remitente generico.
        SecurityContextHolder.clearContext();

        notificationService.createNotification(1L, "Tu solicitud fue aprobada");

        verify(emailService).sendNotification(
                "atorres.notif@gmail.com", "Tu solicitud fue aprobada",
                "Sistema de Pre-Sustentaciones", "noreply@uteq.edu.ec");
    }

    @Test
    void createNotificationNotSendsEmailIfEmailNotificationsIsInBlank() {
        AppUser receiver = receiverWith("   ");
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(receiver));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.createNotification(1L, "hola");

        verify(emailService, never()).sendNotification(any(), any(), any(), any());
    }

    @Test
    void createNotificationUsesNameAndEmailOfAppUserAuthenticatedAsSender() {
        AppUser receiver = receiverWith("atorres.notif@gmail.com");
        AppUser coordinator = AppUser.builder().id(2L).nombre("Jorge").apellido("Coordinador")
                .email("jcoordinador@uteq.edu.ec").build();
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(receiver));
        when(appUserRepository.findByEmail("jcoordinador@uteq.edu.ec")).thenReturn(Optional.of(coordinator));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("jcoordinador@uteq.edu.ec", null,
                        AuthorityUtils.createAuthorityList("ROLE_COORDINADOR")));

        notificationService.createNotification(1L, "Solicitud aprobada");

        verify(emailService).sendNotification(
                "atorres.notif@gmail.com", "Solicitud aprobada", "Jorge Coordinador", "jcoordinador@uteq.edu.ec");
    }

    @Test
    void markAsReadThrowsExceptionIfNotificationNotExists() {
        when(notificationRepository.findById(5L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> notificationService.markAsRead(5L));
    }

    // Hallazgo real (2026-09-01): NotificationServiceImpl.validateAcceso(Long) es codigo nuevo
    // (control de acceso real agregado por el equipo) que estas pruebas, escritas antes del
    // cambio, no ejercitaban -- exige SecurityContextHolder autenticado, y si no es ADMIN,
    // busca al appUser actual por email (appUserRepository.findByEmail) para comparar su ID
    // contra el appUser objetivo. Las pruebas de "delegacion simple" (no prueban autorizacion
    // en si) se autentican como ADMIN para saltarse esa busqueda, igual que ya hacia el patron
    // ADMIN en MinutesServiceImplTest.

    @Test
    void markAsReadUpdatesFlagAndSaves() {
        AppUser propietario = AppUser.builder().id(1L).email("atorres@uteq.edu.ec").build();
        Notification n = Notification.builder().id(5L).message("x").read(false).appUser(propietario).build();
        when(notificationRepository.findById(5L)).thenReturn(Optional.of(n));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@uteq.edu.ec", null,
                        AuthorityUtils.createAuthorityList("ROLE_ADMIN")));

        Notification result = notificationService.markAsRead(5L);

        assertTrue(result.isRead());
        verify(notificationRepository).save(n);
    }

    @Test
    void countUnreadDelegatesToRepository() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@uteq.edu.ec", null,
                        AuthorityUtils.createAuthorityList("ROLE_ADMIN")));
        when(notificationRepository.countByAppUserIdAndReadFalse(1L)).thenReturn(3L);
        assertEquals(3L, notificationService.countUnread(1L));
    }

    @Test
    void markAllReadDelegatesToRepository() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@uteq.edu.ec", null,
                        AuthorityUtils.createAuthorityList("ROLE_ADMIN")));
        notificationService.markAllRead(1L);
        verify(notificationRepository).markAllReadByAppUser(1L);
    }

    @Test
    void deleteNotificationSuccessfulIfIsOwner() {
        AppUser receiver = AppUser.builder().id(1L).email("atorres@uteq.edu.ec").build();
        Notification n = Notification.builder().id(5L).appUser(receiver).build();
        when(notificationRepository.findById(5L)).thenReturn(Optional.of(n));
        when(appUserRepository.findByEmail("atorres@uteq.edu.ec")).thenReturn(Optional.of(receiver));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("atorres@uteq.edu.ec", null,
                        AuthorityUtils.createAuthorityList("ROLE_ESTUDIANTE")));

        assertDoesNotThrow(() -> notificationService.deleteNotification(5L));
        verify(notificationRepository).delete(n);
    }

    @Test
    void deleteNotificationThrowsAccessDeniedIfNotIsOwner() {
        AppUser receiver = AppUser.builder().id(1L).email("atorres@uteq.edu.ec").build();
        AppUser otro = AppUser.builder().id(2L).email("otro@uteq.edu.ec").build();
        Notification n = Notification.builder().id(5L).appUser(receiver).build();
        when(notificationRepository.findById(5L)).thenReturn(Optional.of(n));
        when(appUserRepository.findByEmail("otro@uteq.edu.ec")).thenReturn(Optional.of(otro));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("otro@uteq.edu.ec", null,
                        AuthorityUtils.createAuthorityList("ROLE_ESTUDIANTE")));

        assertThrows(AccessDeniedException.class, () -> notificationService.deleteNotification(5L));
        verify(notificationRepository, never()).delete(any());
    }
}
