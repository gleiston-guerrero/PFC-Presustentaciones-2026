package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GenerateTopicRequest {
    @NotNull(message = "El ID de la carrera es obligatorio")
    @JsonProperty("carreraId")
    private Integer programId;
    
    @JsonProperty("lineaInvestigacionId")
    private Integer lineInvestigacionId;
    private Integer areaId;
    private String areaInteres;
    private String tipoProblema;
    private String poblacion;
}
