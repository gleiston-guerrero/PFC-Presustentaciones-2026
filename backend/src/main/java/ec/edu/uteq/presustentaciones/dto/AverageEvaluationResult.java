package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Proyección del resultado de sp_calculate_promedio_evaluation (función SQL, RETURNS TABLE),
 * invocada vía @NamedStoredProcedureQuery + @SqlResultSetMapping desde EvaluationRepository.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AverageEvaluationResult {
    @JsonProperty("solicitudId")
    private Long submissionId;
    @JsonProperty("notaFinal")
    private Double gradeFinal;
    @JsonProperty("estadoResultado")
    private String statusResult;
}
