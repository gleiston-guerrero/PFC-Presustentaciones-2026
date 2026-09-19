package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Update student request.
 */
@Data
public class UpdateStudentRequest {
    @JsonProperty("carreraId")
    private Integer programId;
    @JsonProperty("periodoIngresoId")
    private Integer periodIngresoId;
    @JsonProperty("semestreActual")
    private Short semestreActual;
    @JsonProperty("telefono")
    private String phone;
    @JsonProperty("estadoAcademicoCodigo")
    private String statusAcademicCode;
}
