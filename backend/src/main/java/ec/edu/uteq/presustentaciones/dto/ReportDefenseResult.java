package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Proyección del resultado de sp_generate_reporte_defensas (función SQL, RETURNS TABLE),
 * invocada vía @NamedStoredProcedureQuery + @SqlResultSetMapping desde SubmissionRepository.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ReportDefenseResult {
    @JsonProperty("solicitudId")
    private Long submissionId;
    @JsonProperty("estudianteNombre")
    private String studentNombre;
    @JsonProperty("expediente")
    private String expediente;
    @JsonProperty("tituloTopic")
    private String tituloTopic;
    @JsonProperty("estadoSubmission")
    private String statusSubmission;
    @JsonProperty("fechaDefensa")
    private LocalDateTime dateDefense;
    @JsonProperty("salaNombre")
    private String roomNombre;
    @JsonProperty("notaFinal")
    private Double gradeFinal;
}
