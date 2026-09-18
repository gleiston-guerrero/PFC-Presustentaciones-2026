package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

import java.time.LocalDate;

@Data
public class SavePeriodRequest {
    @JsonProperty("codigo")
    private String codigo;
    @JsonProperty("nombre")
    private String nombre;
    @JsonProperty("fechaInicio")
    private LocalDate fechaInicio;
    @JsonProperty("fechaFin")
    private LocalDate fechaFin;
    @JsonProperty("activo")
    private Boolean activo;
}
