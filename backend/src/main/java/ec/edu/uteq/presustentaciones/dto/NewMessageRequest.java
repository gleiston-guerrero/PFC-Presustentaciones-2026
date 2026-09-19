package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * New message request.
 */
@Data
public class NewMessageRequest {

    @JsonProperty("faseId")
    private Long phaseId;
    @JsonProperty("contenido")
    private String contenido;
    @JsonProperty("tipo")
    private String kind;
}
