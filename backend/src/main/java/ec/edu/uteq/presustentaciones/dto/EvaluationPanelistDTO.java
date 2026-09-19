package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Objeto de transferencia de evaluation panelist.
 */
@Data @Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationPanelistDTO {
    @JsonProperty("id")
    private Long id;
    @JsonProperty("solicitudId")
    private Long submissionId;
    @JsonProperty("juradoId")
    private Long panelistId;
    @JsonProperty("notaJurado")
    private Double gradePanelist;
    @JsonProperty("observaciones")
    private String observations;
    @JsonProperty("resultado")
    private String result;
    @JsonProperty("comentarioPreestablecido")
    private String commentPreestablecido;
    @JsonProperty("nombreJurado")
    private String nombrePanelist;
    @JsonProperty("rolJurado")
    private String rolePanelist;
}
