package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Save program request.
 */
@Data
public class SaveProgramRequest {
    @JsonProperty("codigo")
    private String code;
    @JsonProperty("nombre")
    private String nombre;
    @JsonProperty("facultadId")
    private Integer facultyId;
    @JsonProperty("modalidadEstudio")
    private String modalityEstudio;
}
