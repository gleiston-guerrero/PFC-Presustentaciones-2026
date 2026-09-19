package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

/**
 * Evaluation rubric request.
 */
@Data
public class EvaluationRubricRequest {
    @JsonProperty("solicitudId")
    private Long submissionId;
    @JsonProperty("juradoId")
    private Long panelistId;
    @JsonProperty("rubricaId")
    private Long rubricId;
    /** Una entrada por cada criterio de la rúbrica */
    @JsonProperty("criterios")
    private List<ScaleCriterionDTO> criteria;
    @JsonProperty("observaciones")
    private String observations;
}
