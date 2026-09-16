package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicPropuestoDTO {
    private Integer id;
    private String titulo;
    private String problema;
    private String objetivoGeneral;
    private String objetivosEspecificos;
    private String justificacion;
    private String beneficiarios;
    private String nivelDificultad;
    @JsonProperty("carreraId")
    private Integer programId;
    @JsonProperty("carreraNombre")
    private String programNombre;
    @JsonProperty("lineaInvestigacionId")
    private Integer lineInvestigacionId;
    @JsonProperty("lineaInvestigacionNombre")
    private String lineInvestigacionNombre;
    private Integer areaId;
    private String areaNombre;
    /** true si el student autenticado ya guardó este topic (solo se rellena en listados del student). */
    private Boolean guardado;
}
