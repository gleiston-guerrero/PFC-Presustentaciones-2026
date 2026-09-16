package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.dto.ChatRequest;
import ec.edu.uteq.presustentaciones.dto.ChatResponse;
import ec.edu.uteq.presustentaciones.services.ChatbotService;
import lombok.RequiredArgsConstructor;
import ec.edu.uteq.presustentaciones.dto.ResponseWrapper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Asistente virtual de ayuda: responde preguntas frecuentes sobre cómo usar los módulos
 * del sistema. No consulta la base de datos ni devuelve datos personales, sólo texto guía.
 *
 * Hallazgo de auditoría (2026-09-05): era el único controlador del proyecto sin ninguna
 * anotación de autorización, ni de clase ni de método -- quedaba protegido sólo por la
 * regla global de autenticación de SecurityConfig. Se hace explícita con
 * {@code isAuthenticated()}, el mismo nivel que ya usan otros controladores de consulta
 * general, porque las respuestas no dependen del role. La comprobación equivalente que el
 * servicio hace por su cuenta queda como defensa en profundidad, no como única barrera.
 */
@RestController
@RequestMapping("/api/v1/chatbot")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ChatbotController {

    private final ChatbotService chatbotService;

    /**
     * Responde una consulta en lenguaje natural con la guía correspondiente al topic detectado.
     *
     * @param request cuerpo con el mensaje del appUser
     * @return 200 con la respuesta del asistente y, cuando aplica, las opciones sugeridas
     *         y la ruta del módulo relacionado
     */
    @PostMapping("/ask")
    public ResponseEntity<ResponseWrapper<ChatResponse>> askChatbot(@RequestBody ChatRequest request) {
        return ResponseEntity.ok(ResponseWrapper.success(chatbotService.processMessage(request), "Consulta procesada correctamente"));
    }
}
