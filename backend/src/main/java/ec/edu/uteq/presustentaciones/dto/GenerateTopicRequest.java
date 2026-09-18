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
    @JsonProperty("areaId")
    private Integer areaId;
    @JsonProperty("areaInteres")
    private String areaInteres;
    @JsonProperty("tipoProblema")
    private String tipoProblema;
    @JsonProperty("poblacion")
    private String poblacion;
}
