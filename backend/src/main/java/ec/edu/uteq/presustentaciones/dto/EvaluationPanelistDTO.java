package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationPanelistDTO {
    private Long id;
    @JsonProperty("solicitudId")
    private Long submissionId;
    @JsonProperty("juradoId")
    private Long panelistId;
    private Double notaPanelist;
    private String observaciones;
    private String resultado;
    private String comentarioPreestablecido;
    private String nombrePanelist;
    @JsonProperty("rolPanelist")
    private String rolePanelist;
}
