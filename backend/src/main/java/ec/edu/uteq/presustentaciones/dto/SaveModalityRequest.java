package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * Save modality request.
 */
@Data
public class SaveModalityRequest {
    @JsonProperty("codigo")
    private String code;
    @JsonProperty("nombre")
    private String nombre;
}
