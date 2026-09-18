package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Alta / edición de un topic propuesto desde el panel de administración del
 * Centro de Orientación (permission ORIENTACION_CATALOGO_GESTIONAR).
 */
@Data
public class SaveTopicProposedRequest {

    @NotBlank(message = "El título es obligatorio")
    @Size(max = 500, message = "El título no puede superar los 500 caracteres")
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

    @Size(max = 50, message = "El nivel de dificultad no puede superar los 50 caracteres")
    @JsonProperty("nivelDificultad")
    private String nivelDificultad;

    @JsonProperty("carreraId")
    private Integer programId;
    @JsonProperty("lineaInvestigacionId")
    private Integer researchLineId;
    @JsonProperty("areaId")
    private Integer areaId;
}
