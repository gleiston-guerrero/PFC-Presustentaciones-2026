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
public class ReporteDefensaResult {
    @JsonProperty("solicitudId")
    private Long submissionId;
    @JsonProperty("estudianteNombre")
    private String studentNombre;
    private String expediente;
    private String tituloTopic;
    private String estadoSubmission;
    private LocalDateTime fechaDefensa;
    @JsonProperty("salaNombre")
    private String roomNombre;
    private Double notaFinal;
}
