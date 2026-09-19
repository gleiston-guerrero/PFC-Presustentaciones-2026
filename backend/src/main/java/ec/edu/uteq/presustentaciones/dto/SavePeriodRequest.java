package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

import java.time.LocalDate;

/**
 * Save period request.
 */
@Data
public class SavePeriodRequest {
    @JsonProperty("codigo")
    private String code;
    @JsonProperty("nombre")
    private String nombre;
    @JsonProperty("fechaInicio")
    private LocalDate dateStart;
    @JsonProperty("fechaFin")
    private LocalDate dateEnd;
    @JsonProperty("activo")
    private Boolean activo;
}
