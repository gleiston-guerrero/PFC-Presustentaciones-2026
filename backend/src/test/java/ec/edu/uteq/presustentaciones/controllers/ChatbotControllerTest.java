package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.dto.ChatRequest;
import ec.edu.uteq.presustentaciones.dto.ChatResponse;
import ec.edu.uteq.presustentaciones.dto.ResponseWrapper;
import ec.edu.uteq.presustentaciones.services.ChatbotService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ChatbotController y ChatbotService entraron al backend después del último reporte
 * de cobertura y quedaron con cero pruebas de ningún tipo -- brecha señalada
 * explícitamente en la auditoría de la entrega final.
 */
@ExtendWith(MockitoExtension.class)
class ChatbotControllerTest {

    @Mock private ChatbotService chatbotService;

    @InjectMocks
    private ChatbotController controller;

    @Test
    void askChatbotReturnsResponseOfServiceWrappedWithMessageOfSuccess() {
        ChatRequest request = new ChatRequest();
        request.setMessage("¿Cómo subo mi anteproyecto?");
        ChatResponse esperada = ChatResponse.builder()
                .response("Ve a 'Cargar Anteproyecto' desde tu panel de estudiante.")
                .options(List.of("Ver mis trámites", "Ver mi horario"))
                .route("/estudiante/anteproyecto")
                .build();
        when(chatbotService.processMessage(request)).thenReturn(esperada);

        ResponseEntity<ResponseWrapper<ChatResponse>> response = controller.askChatbot(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertSame(esperada, response.getBody().getData());
        assertEquals("Consulta procesada correctamente", response.getBody().getMessage());
        verify(chatbotService).processMessage(request);
    }

    @Test
    void askChatbotPropagatesQuestionSuchWhichWithoutReinterpret() {
        ChatRequest request = new ChatRequest();
        request.setMessage("   ");
        when(chatbotService.processMessage(request))
                .thenReturn(ChatResponse.builder().response("No entendí tu consulta.").build());

        ResponseEntity<ResponseWrapper<ChatResponse>> response = controller.askChatbot(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("No entendí tu consulta.", response.getBody().getData().getResponse());
    }
}
