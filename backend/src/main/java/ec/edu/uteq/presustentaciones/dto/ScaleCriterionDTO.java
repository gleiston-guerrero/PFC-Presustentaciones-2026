package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ScaleCriterionDTO {
    @JsonProperty("criterioId")
    private Long criterionId;
    /** Scale: 1-100 (% del puntaje máximo del criterio) */
    @JsonProperty("escala")
    private Integer scale;
    @JsonProperty("observaciones")
    private String observations;
    /** Observación automática según el rango (generada en backend) */
    @JsonProperty("observacionAuto")
    private String observationAuto;
    /** Observación manual ingresada por el panelist */
    @JsonProperty("observacionManual")
    private String observationManual;
}
