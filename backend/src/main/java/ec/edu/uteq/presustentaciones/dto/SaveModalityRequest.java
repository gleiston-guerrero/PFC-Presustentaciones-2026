package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class SaveModalityRequest {
    @JsonProperty("codigo")
    private String codigo;
    @JsonProperty("nombre")
    private String nombre;
}
