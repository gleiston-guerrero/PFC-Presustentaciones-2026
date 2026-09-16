package ec.edu.uteq.presustentaciones.services;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EmailService no tenia ningun test (0% de ramas) pese a tener la logica real de cuando
 * un correo sale o no (feature flag app.mail.enabled + availability del JavaMailSender).
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailService service;

    @BeforeEach
    void setUp() {
        service = new EmailService();
        ReflectionTestUtils.setField(service, "mailSender", mailSender);
        ReflectionTestUtils.setField(service, "smtpUsername", "noreply@uteq.edu.ec");
    }

    private MimeMessage mimeReal() {
        return new MimeMessage(Session.getDefaultInstance(new Properties()));
    }

    // ── sendNotification(4 args) ───────────────────────────────────────────

    @Test
    void sendNotificationNoEnviaNadaSiElFeatureFlagEstaDesactivado() {
        ReflectionTestUtils.setField(service, "enabled", false);

        service.sendNotification("x@uteq.edu.ec", "hola", "Ana", "ana@uteq.edu.ec");

        verify(mailSender, never()).createMimeMessage();
    }

    @Test
    void sendNotificationNoEnviaNadaSiNoHayMailSenderAunqueElFlagEsteActivo() {
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "mailSender", null);

        service.sendNotification("x@uteq.edu.ec", "hola", "Ana", "ana@uteq.edu.ec");
        // no debe lanzar NPE ni intentar nada; nada que verify sobre un mock que no existe
    }

    @Test
    void sendNotificationEnviaElCorreoRealCuandoEstaHabilitado() throws Exception {
        ReflectionTestUtils.setField(service, "enabled", true);
        when(mailSender.createMimeMessage()).thenReturn(mimeReal());

        service.sendNotification("x@uteq.edu.ec", "hola", "Ana", "ana@uteq.edu.ec");

        verify(mailSender).send((MimeMessage) org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sendNotificationSinRemitenteUsaValorGenerico() {
        ReflectionTestUtils.setField(service, "enabled", true);
        when(mailSender.createMimeMessage()).thenReturn(mimeReal());

        service.sendNotification("x@uteq.edu.ec", "hola");

        verify(mailSender).send((MimeMessage) org.mockito.ArgumentMatchers.any());
    }

    // ── sendRecuperacionPassword ───────────────────────────────────────────

    @Test
    void sendRecuperacionPasswordNoEnviaNadaSiElFeatureFlagEstaDesactivado() {
        ReflectionTestUtils.setField(service, "enabled", false);

        service.sendRecuperacionPassword("x@uteq.edu.ec", "token-123");

        verify(mailSender, never()).createMimeMessage();
    }

    @Test
    void sendRecuperacionPasswordEnviaElCorreoRealCuandoEstaHabilitado() {
        ReflectionTestUtils.setField(service, "enabled", true);
        when(mailSender.createMimeMessage()).thenReturn(mimeReal());

        service.sendRecuperacionPassword("x@uteq.edu.ec", "token-123");

        verify(mailSender).send((MimeMessage) org.mockito.ArgumentMatchers.any());
    }
}
