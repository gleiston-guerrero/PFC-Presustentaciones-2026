package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.ChatRequest;
import ec.edu.uteq.presustentaciones.dto.ChatResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class ChatbotService {

    /**
     * Process message.
     * @param request request
     * @return el ChatResponse correspondiente
     */
    public ChatResponse processMessage(ChatRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            return ChatResponse.builder()
                    .response("Lo siento, debes iniciar sesión para interactuar con el asistente virtual.")
                    .build();
        }

        String msg = request.getMessage() != null ? request.getMessage().toLowerCase() : "";

        if (msg.contains("solicitud")) {
            return buildResponse("Puedes consultar tus solicitudes desde el módulo de Solicitudes. Allí podrás revisar el estado y la información registrada.");
        } else if (msg.contains("anteproyecto")) {
            return buildResponse("El anteproyecto es parte del proceso de pre-sustentación. Puedes revisar y gestionar la información disponible desde el módulo correspondiente.");
        } else if (msg.contains("notificacion") || msg.contains("notificaciones") || msg.contains("notificación")) {
            return buildResponse("Puedes revisar tus notificaciones desde el centro de notificaciones de la aplicación.");
        } else if (msg.contains("perfil") || msg.contains("mis datos")) {
            return buildResponse("Puedes acceder a Mi Perfil desde el menú de usuario para consultar y actualizar la información permitida.");
        } else if (msg.contains("sustentacion") || msg.contains("sustentación")) {
            return buildResponse("Puedes consultar la información disponible de tu sustentación desde el módulo correspondiente. La información dependerá de tu rol y del estado de tu proceso.");
        } else if (msg.contains("contraseña") || msg.contains("contrasena") || msg.contains("clave")) {
            return buildResponse("Para cambiar o recuperar tu contraseña, utiliza las opciones de seguridad disponibles en tu cuenta.");
        } else if (msg.contains("ayuda") || msg.contains("qué puedo hacer") || msg.contains("que puedo hacer") || msg.contains("no sé qué hacer") || msg.contains("no se que hacer")) {
            return buildResponse("Puedo ayudarte con Solicitudes, Anteproyecto, Notificaciones, Mi Perfil, Sustentación y Contraseña.");
        }

        return buildResponse("No estoy seguro de cómo responder esa pregunta. Puedes consultar las opciones de ayuda disponibles o seleccionar una de las preguntas frecuentes.");
    }

    private ChatResponse buildResponse(String text) {
        return ChatResponse.builder()
                .response(text)
                .options(Arrays.asList("Solicitudes", "Anteproyecto", "Notificaciones", "Mi Perfil", "Sustentación", "Contraseña", "Ayuda"))
                .build();
    }
}
