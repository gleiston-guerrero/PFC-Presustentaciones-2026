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
    void limpiarContextoDeSeguridad() {
        SecurityContextHolder.clearContext();
    }

    private AppUser receptorCon(String emailNotifications) {
        return AppUser.builder().id(1L).nombre("Ana").apellido("Torres")
                .email("atorres@uteq.edu.ec").emailNotifications(emailNotifications).build();
    }

    @Test
    void createNotificationLanzaExcepcionSiElAppUserNoExiste() {
        when(appUserRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> notificationService.createNotification(99L, "hola"));
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void createNotificationGuardaLaNotificationAunSinEmailConfigurado() {
        AppUser receptor = receptorCon(null);
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(receptor));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        Notification resultado = notificationService.createNotification(1L, "Tu solicitud fue aprobada");

        assertEquals("Tu solicitud fue aprobada", resultado.getMensaje());
        assertFalse(resultado.isLeida());
        verify(emailService, never()).sendNotification(any(), any(), any(), any());
    }

    @Test
    void createNotificationEnviaEmailSiElReceptorTieneEmailNotificationsConfigurado() {
        AppUser receptor = receptorCon("atorres.notif@gmail.com");
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(receptor));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        // Sin autenticacion en el contexto -> remitente generico.
        SecurityContextHolder.clearContext();

        notificationService.createNotification(1L, "Tu solicitud fue aprobada");

        verify(emailService).sendNotification(
                "atorres.notif@gmail.com", "Tu solicitud fue aprobada",
                "Sistema de Pre-Sustentaciones", "noreply@uteq.edu.ec");
    }

    @Test
    void createNotificationNoEnviaEmailSiEmailNotificationsEstaEnBlanco() {
        AppUser receptor = receptorCon("   ");
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(receptor));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.createNotification(1L, "hola");

        verify(emailService, never()).sendNotification(any(), any(), any(), any());
    }

    @Test
    void createNotificationUsaNombreYEmailDelAppUserAutenticadoComoRemitente() {
        AppUser receptor = receptorCon("atorres.notif@gmail.com");
        AppUser coordinador = AppUser.builder().id(2L).nombre("Jorge").apellido("Coordinador")
                .email("jcoordinador@uteq.edu.ec").build();
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(receptor));
        when(appUserRepository.findByEmail("jcoordinador@uteq.edu.ec")).thenReturn(Optional.of(coordinador));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("jcoordinador@uteq.edu.ec", null,
                        AuthorityUtils.createAuthorityList("ROLE_COORDINADOR")));

        notificationService.createNotification(1L, "Solicitud aprobada");

        verify(emailService).sendNotification(
                "atorres.notif@gmail.com", "Solicitud aprobada", "Jorge Coordinador", "jcoordinador@uteq.edu.ec");
    }

    @Test
    void marcarComoLeidaLanzaExcepcionSiLaNotificationNoExiste() {
        when(notificationRepository.findById(5L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> notificationService.marcarComoLeida(5L));
    }

    // Hallazgo real (2026-09-01): NotificationServiceImpl.validateAcceso(Long) es codigo nuevo
    // (control de acceso real agregado por el equipo) que estas pruebas, escritas antes del
    // cambio, no ejercitaban -- exige SecurityContextHolder autenticado, y si no es ADMIN,
    // busca al appUser actual por email (appUserRepository.findByEmail) para comparar su ID
    // contra el appUser objetivo. Las pruebas de "delegacion simple" (no prueban autorizacion
    // en si) se autentican como ADMIN para saltarse esa busqueda, igual que ya hacia el patron
    // ADMIN en MinutesServiceImplTest.

    @Test
    void marcarComoLeidaActualizaElFlagYGuarda() {
        AppUser propietario = AppUser.builder().id(1L).email("atorres@uteq.edu.ec").build();
        Notification n = Notification.builder().id(5L).mensaje("x").leida(false).appUser(propietario).build();
        when(notificationRepository.findById(5L)).thenReturn(Optional.of(n));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@uteq.edu.ec", null,
                        AuthorityUtils.createAuthorityList("ROLE_ADMIN")));

        Notification resultado = notificationService.marcarComoLeida(5L);

        assertTrue(resultado.isLeida());
        verify(notificationRepository).save(n);
    }

    @Test
    void countNoLeidasDelegaAlRepositorio() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@uteq.edu.ec", null,
                        AuthorityUtils.createAuthorityList("ROLE_ADMIN")));
        when(notificationRepository.countByAppUserIdAndLeidaFalse(1L)).thenReturn(3L);
        assertEquals(3L, notificationService.countNoLeidas(1L));
    }

    @Test
    void marcarTodasLeidasDelegaAlRepositorio() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@uteq.edu.ec", null,
                        AuthorityUtils.createAuthorityList("ROLE_ADMIN")));
        notificationService.marcarTodasLeidas(1L);
        verify(notificationRepository).marcarTodasLeidasPorAppUser(1L);
    }

    @Test
    void deleteNotificationExitosoSiEsPropietario() {
        AppUser receptor = AppUser.builder().id(1L).email("atorres@uteq.edu.ec").build();
        Notification n = Notification.builder().id(5L).appUser(receptor).build();
        when(notificationRepository.findById(5L)).thenReturn(Optional.of(n));
        when(appUserRepository.findByEmail("atorres@uteq.edu.ec")).thenReturn(Optional.of(receptor));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("atorres@uteq.edu.ec", null,
                        AuthorityUtils.createAuthorityList("ROLE_ESTUDIANTE")));

        assertDoesNotThrow(() -> notificationService.deleteNotification(5L));
        verify(notificationRepository).delete(n);
    }

    @Test
    void deleteNotificationLanzaAccessDeniedSiNoEsPropietario() {
        AppUser receptor = AppUser.builder().id(1L).email("atorres@uteq.edu.ec").build();
        AppUser otro = AppUser.builder().id(2L).email("otro@uteq.edu.ec").build();
        Notification n = Notification.builder().id(5L).appUser(receptor).build();
        when(notificationRepository.findById(5L)).thenReturn(Optional.of(n));
        when(appUserRepository.findByEmail("otro@uteq.edu.ec")).thenReturn(Optional.of(otro));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("otro@uteq.edu.ec", null,
                        AuthorityUtils.createAuthorityList("ROLE_ESTUDIANTE")));

        assertThrows(AccessDeniedException.class, () -> notificationService.deleteNotification(5L));
        verify(notificationRepository, never()).delete(any());
    }
}
