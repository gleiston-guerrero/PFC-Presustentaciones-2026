package ec.edu.uteq.presustentaciones.services;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Servicio de email.
 */
@Service
@Slf4j
public class EmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@uteq.edu.ec}")
    private String smtpUsername;

    @Value("${app.mail.enabled:false}")
    private boolean enabled;

    /**
     * Envía una notificación HTML con el nombre del remitente (appUser logueado).
     *
     * @param destinatario  correo del receptor
     * @param message       cuerpo del mensaje
     * @param senderNombre nombre completo del appUser que genera la notificación
     * @param senderEmail  correo del appUser que genera la notificación
     */
    @Async
    public void sendNotification(String destinatario, String message,
                                   String senderNombre, String senderEmail) {
        if (!enabled || mailSender == null) {
            log.info("[EMAIL DESHABILITADO] Para: {} | Mensaje: {}", destinatario, message);
            return;
        }
        try {
            MimeMessage mail = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mail, true, "UTF-8");

            // El correo sale desde la cuenta SMTP pero con el nombre del appUser
            helper.setFrom(smtpUsername, senderNombre + " (UTEQ - Pre-Sustentaciones)");
            helper.setTo(destinatario);
            helper.setSubject("📬 Nueva Notificación — Sistema de Pre-Sustentaciones UTEQ");
            helper.setText(buildHtml(message, senderNombre, senderEmail), true);

            mailSender.send(mail);
            log.info("Email enviado a: {} por: {}", destinatario, senderEmail);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("Error al enviar email a {}: {}", destinatario, e.getMessage());
        }
    }

    /**
     * Sobrecarga de compatibilidad para llamadas sin remitente (usa valor genérico).
     *
     * @param destinatario email del destinatario
     * @param message      cuerpo de la notificación
     */
    @Async
    public void sendNotification(String destinatario, String message) {
        sendNotification(destinatario, message, "Sistema de Pre-Sustentaciones", smtpUsername);
    }

    /**
     * RF-05: correo de recuperación de contraseña, con el enlace de un solo uso. Con
     * {@code app.mail.enabled=false} (valor por omisión hoy) el envío no se verifica de
     * verdad -- ver la nota del requisito en el SRS -- pero el flujo completo (generación,
     * caducidad a 30 min, un solo uso) es real independientemente de si el correo sale o no.
     *
     * @param destinatario correo del titular de la cuenta
     * @param tokenPlano   token de un solo uso, en texto plano (solo existe fuera de Redis aquí
     *                     y en la respuesta al enlace del correo; el almacén guarda su hash)
     */
    @Async
    public void sendRecoveryPassword(String destinatario, String tokenPlano) {
        String enlace = "http://localhost:4200/restablecer-password?token=" + tokenPlano;
        if (!enabled || mailSender == null) {
            log.info("[EMAIL DESHABILITADO] Recuperación de contraseña para: {} | enlace: {}", destinatario, enlace);
            return;
        }
        try {
            MimeMessage mail = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mail, true, "UTF-8");
            helper.setFrom(smtpUsername, "Sistema de Pre-Sustentaciones UTEQ");
            helper.setTo(destinatario);
            helper.setSubject("Restablecimiento de contraseña — Sistema de Pre-Sustentaciones UTEQ");
            helper.setText("<p>Se solicitó restablecer la contraseña de esta cuenta. El enlace vence en "
                    + "30 minutos y solo puede usarse una vez:</p><p><a href=\"" + enlace + "\">" + enlace
                    + "</a></p><p>Si no fuiste tú, ignora este correo.</p>", true);
            mailSender.send(mail);
            log.info("Correo de recuperación de contraseña enviado a: {}", destinatario);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("Error al enviar correo de recuperación a {}: {}", destinatario, e.getMessage());
        }
    }

    private String buildHtml(String message, String senderNombre, String senderEmail) {
        return """
            <!DOCTYPE html>
            <html lang="es">
            <head>
              <meta charset="UTF-8"/>
              <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
            </head>
            <body style="margin:0;padding:0;background-color:#f0f4f8;font-family:'Segoe UI',Arial,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f0f4f8;padding:40px 0;">
                <tr><td align="center">
                  <table width="600" cellpadding="0" cellspacing="0" style="max-width:600px;width:100%%;">

                    <!-- HEADER -->
                    <tr>
                      <td style="background:liner-gradient(135deg,#1a3c6e 0%%,#2563eb 100%%);
                                 border-radius:12px 12px 0 0;padding:32px 40px;text-align:center;">
                        <p style="margin:0;color:#93c5fd;font-size:13px;letter-spacing:2px;
                                  text-transform:uppercase;font-weight:600;">
                          Universidad Técnica Estatal de Quevedo
                        </p>
                        <h1 style="margin:8px 0 0;color:#ffffff;font-size:26px;font-weight:700;">
                          Sistema de Pre-Sustentaciones
                        </h1>
                      </td>
                    </tr>

                    <!-- BODY -->
                    <tr>
                      <td style="background:#ffffff;padding:40px;">

                        <p style="margin:0 0 6px;color:#6b7280;font-size:13px;font-weight:600;
                                  text-transform:uppercase;letter-spacing:1px;">
                          Nueva notificación
                        </p>
                        <h2 style="margin:0 0 24px;color:#111827;font-size:20px;font-weight:700;">
                          Tienes un nuevo mensaje
                        </h2>

                        <!-- Mensaje principal -->
                        <div style="background:#eff6ff;border-left:4px solid #2563eb;
                                    border-radius:0 8px 8px 0;padding:20px 24px;margin-bottom:28px;">
                          <p style="margin:0;color:#1e3a5f;font-size:15px;line-height:1.7;">
                            %s
                          </p>
                        </div>

                        <p style="margin:0 0 24px;color:#4b5563;font-size:14px;line-height:1.6;">
                          Por favor, ingresa al sistema para ver más detalles y tomar las acciones
                          correspondientes.
                        </p>

                        <!-- Botón CTA -->
                        <div style="text-align:center;margin-bottom:32px;">
                          <a href="http://localhost:4200"
                             style="display:inline-block;background:liner-gradient(135deg,#1a3c6e,#2563eb);
                                    color:#ffffff;text-decoration:none;padding:14px 36px;
                                    border-radius:8px;font-size:15px;font-weight:600;
                                    letter-spacing:0.5px;">
                            Ir al Sistema ➜
                          </a>
                        </div>

                        <!-- Remitente -->
                        <div style="background:#f9fafb;border:1px solid #e5e7eb;border-radius:8px;
                                    padding:16px 20px;">
                          <p style="margin:0;color:#6b7280;font-size:12px;font-weight:600;
                                    text-transform:uppercase;letter-spacing:1px;margin-bottom:6px;">
                            Enviado por
                          </p>
                          <p style="margin:0;color:#111827;font-size:14px;font-weight:700;">
                            %s
                          </p>
                          <p style="margin:2px 0 0;color:#6b7280;font-size:13px;">%s</p>
                        </div>
                      </td>
                    </tr>

                    <!-- FOOTER -->
                    <tr>
                      <td style="background:#f8fafc;border-top:1px solid #e5e7eb;
                                 border-radius:0 0 12px 12px;padding:20px 40px;text-align:center;">
                        <p style="margin:0;color:#9ca3af;font-size:12px;line-height:1.6;">
                          Este es un mensaje automático del Sistema de Pre-Sustentaciones UTEQ.<br/>
                          Por favor, no respondas directamente a este correo.
                        </p>
                      </td>
                    </tr>

                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(message, senderNombre, senderEmail);
    }
}