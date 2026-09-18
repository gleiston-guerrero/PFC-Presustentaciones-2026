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
    @JsonProperty("id")
    private Integer id;
    @JsonProperty("titulo")
    private String titulo;
    @JsonProperty("problema")
    private String problema;
    @JsonProperty("objetivoGeneral")
    private String objetivoGeneral;
    @JsonProperty("objetivosEspecificos")
    private String objetivosEspecificos;
    @JsonProperty("justificacion")
    private String justificacion;
    @JsonProperty("beneficiarios")
    private String beneficiarios;
    @JsonProperty("nivelDificultad")
    private String nivelDificultad;
    @JsonProperty("carreraId")
    private Integer programId;
    @JsonProperty("carreraNombre")
    private String programNombre;
    @JsonProperty("lineaInvestigacionId")
    private Integer lineInvestigacionId;
    @JsonProperty("lineaInvestigacionNombre")
    private String lineInvestigacionNombre;
    @JsonProperty("areaId")
    private Integer areaId;
    @JsonProperty("areaNombre")
    private String areaNombre;
    /** true si el student autenticado ya guardó este topic (solo se rellena en listados del student). */
    @JsonProperty("guardado")
    private Boolean guardado;
}
