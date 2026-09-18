package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data @Builder
public class TutoringMensajeDTO {

    @JsonProperty("id")
    private Long id;
    @JsonProperty("faseId")
    private Long faseId;
    @JsonProperty("remitenteId")
    private Long remitenteId;
    @JsonProperty("nombreRemitente")
    private String nombreRemitente;
    @JsonProperty("contenido")
    private String contenido;
    @JsonProperty("fechaEnvio")
    private LocalDateTime fechaEnvio;
    @JsonProperty("tipo")
    private String tipo;
    @JsonProperty("leido")
    private Boolean leido;
}
