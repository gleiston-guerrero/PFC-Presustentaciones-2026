package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ScaleCriterioDTO {
    @JsonProperty("criterioId")
    private Long criterioId;
    /** Scale: 1-100 (% del puntaje máximo del criterio) */
    @JsonProperty("escala")
    private Integer scale;
    @JsonProperty("observaciones")
    private String observaciones;
    /** Observación automática según el rango (generada en backend) */
    @JsonProperty("observacionAuto")
    private String observacionAuto;
    /** Observación manual ingresada por el panelist */
    @JsonProperty("observacionManual")
    private String observacionManual;
}
