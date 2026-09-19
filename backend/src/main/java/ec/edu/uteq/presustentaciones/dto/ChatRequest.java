package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * Chat request.
 */
@Data
public class ChatRequest {
    @JsonProperty("message")
    private String message;
}
