package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class NuevoMensajeRequest {

    @JsonProperty("faseId")
    private Long faseId;
    @JsonProperty("contenido")
    private String contenido;
    @JsonProperty("tipo")
    private String tipo;
}
