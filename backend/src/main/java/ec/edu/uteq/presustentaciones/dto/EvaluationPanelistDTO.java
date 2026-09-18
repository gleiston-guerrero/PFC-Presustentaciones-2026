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
    @JsonProperty("id")
    private Long id;
    @JsonProperty("solicitudId")
    private Long submissionId;
    @JsonProperty("juradoId")
    private Long panelistId;
    @JsonProperty("notaJurado")
    private Double notaPanelist;
    @JsonProperty("observaciones")
    private String observaciones;
    @JsonProperty("resultado")
    private String resultado;
    @JsonProperty("comentarioPreestablecido")
    private String comentarioPreestablecido;
    @JsonProperty("nombreJurado")
    private String nombrePanelist;
    @JsonProperty("rolJurado")
    private String rolePanelist;
}
