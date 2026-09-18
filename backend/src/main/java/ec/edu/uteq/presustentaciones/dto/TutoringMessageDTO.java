package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data @Builder
public class TutoringMessageDTO {

    @JsonProperty("id")
    private Long id;
    @JsonProperty("faseId")
    private Long phaseId;
    @JsonProperty("remitenteId")
    private Long senderId;
    @JsonProperty("nombreRemitente")
    private String nombreSender;
    @JsonProperty("contenido")
    private String contenido;
    @JsonProperty("fechaEnvio")
    private LocalDateTime dateEnvio;
    @JsonProperty("tipo")
    private String kind;
    @JsonProperty("leido")
    private Boolean leido;
}
