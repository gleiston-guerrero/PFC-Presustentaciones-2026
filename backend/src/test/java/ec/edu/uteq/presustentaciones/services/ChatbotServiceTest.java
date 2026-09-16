package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.ChatRequest;
import ec.edu.uteq.presustentaciones.dto.ChatResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatbotService no tenia ninguna prueba (3.85% lines, 0% ramas antes de este archivo) --
 * era el unico servicio del proyecto sin cero pruebas de ChatbotController, y con el propio
 * servicio tambien sin cubrir.
 */
class ChatbotServiceTest {

    private final ChatbotService chatbotService = new ChatbotService();

    private ChatRequest req(String mensaje) {
        ChatRequest r = new ChatRequest();
        r.setMessage(mensaje);
        return r;
    }

    private void autenticar() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("estudiante@uteq.edu.ec", null,
                        AuthorityUtils.createAuthorityList("ROLE_ESTUDIANTE")));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sinAutenticacionPideIniciarSesion() {
        ChatResponse resp = chatbotService.processMessage(req("hola"));
        assertTrue(resp.getResponse().contains("iniciar sesión"));
        assertNull(resp.getOptions());
    }

    @Test
    void appUserAnonimoPideIniciarSesion() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        ChatResponse resp = chatbotService.processMessage(req("hola"));
        assertTrue(resp.getResponse().contains("iniciar sesión"));
    }

    @Test
    void mensajeNuloDevuelveRespuestaPorDefecto() {
        autenticar();
        ChatResponse resp = chatbotService.processMessage(req(null));
        assertTrue(resp.getResponse().contains("No estoy seguro"));
        assertEquals(7, resp.getOptions().size());
    }

    @Test
    void preguntaSobreSubmission() {
        autenticar();
        assertTrue(chatbotService.processMessage(req("¿Cómo veo mi SOLICITUD?")).getResponse().contains("Solicitudes"));
    }

    @Test
    void preguntaSobreProposal() {
        autenticar();
        assertTrue(chatbotService.processMessage(req("dudas del anteproyecto")).getResponse().contains("anteproyecto"));
    }

    @Test
    void preguntaSobreNotifications() {
        autenticar();
        assertTrue(chatbotService.processMessage(req("tengo notificaciones")).getResponse().contains("notificaciones"));
        assertTrue(chatbotService.processMessage(req("una notificación")).getResponse().contains("notificaciones"));
    }

    @Test
    void preguntaSobrePerfil() {
        autenticar();
        assertTrue(chatbotService.processMessage(req("quiero ver mi perfil")).getResponse().contains("Mi Perfil"));
        assertTrue(chatbotService.processMessage(req("mis datos personales")).getResponse().contains("Mi Perfil"));
    }

    @Test
    void preguntaSobreSustentacion() {
        autenticar();
        assertTrue(chatbotService.processMessage(req("cuando es mi sustentacion")).getResponse().contains("sustentación"));
        assertTrue(chatbotService.processMessage(req("mi sustentación")).getResponse().contains("sustentación"));
    }

    @Test
    void preguntaSobreContrasena() {
        autenticar();
        assertTrue(chatbotService.processMessage(req("olvide mi contraseña")).getResponse().contains("contraseña"));
        assertTrue(chatbotService.processMessage(req("cambiar contrasena")).getResponse().contains("contraseña"));
        assertTrue(chatbotService.processMessage(req("cual es mi clave")).getResponse().contains("contraseña"));
    }

    @Test
    void preguntaDeAyuda() {
        autenticar();
        assertTrue(chatbotService.processMessage(req("ayuda")).getResponse().contains("Puedo ayudarte"));
        assertTrue(chatbotService.processMessage(req("no sé qué hacer")).getResponse().contains("Puedo ayudarte"));
        assertTrue(chatbotService.processMessage(req("no se que hacer")).getResponse().contains("Puedo ayudarte"));
        assertTrue(chatbotService.processMessage(req("qué puedo hacer aquí")).getResponse().contains("Puedo ayudarte"));
        assertTrue(chatbotService.processMessage(req("que puedo hacer")).getResponse().contains("Puedo ayudarte"));
    }

    @Test
    void mensajeSinCoincidenciasDevuelveRespuestaPorDefectoConOpciones() {
        autenticar();
        ChatResponse resp = chatbotService.processMessage(req("cuál es el sentido de la vida"));
        assertTrue(resp.getResponse().contains("No estoy seguro"));
        assertEquals(7, resp.getOptions().size());
        assertTrue(resp.getOptions().contains("Solicitudes"));
    }
}
