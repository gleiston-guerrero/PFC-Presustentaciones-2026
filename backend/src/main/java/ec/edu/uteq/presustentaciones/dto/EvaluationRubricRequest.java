package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class EvaluationRubricRequest {
    @JsonProperty("solicitudId")
    private Long submissionId;
    @JsonProperty("juradoId")
    private Long panelistId;
    @JsonProperty("rubricaId")
    private Long rubricId;
    /** Una entrada por cada criterio de la rúbrica */
    private List<ScaleCriterioDTO> criterios;
    private String observaciones;
}
