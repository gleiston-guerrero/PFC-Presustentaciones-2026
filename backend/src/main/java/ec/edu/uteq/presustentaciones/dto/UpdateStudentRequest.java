package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class UpdateStudentRequest {
    @JsonProperty("carreraId")
    private Integer programId;
    @JsonProperty("periodoIngresoId")
    private Integer periodIngresoId;
    private Short semestreActual;
    private String telefono;
    private String estadoAcademicoCodigo;
}
