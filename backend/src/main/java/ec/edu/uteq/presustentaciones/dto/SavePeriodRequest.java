package ec.edu.uteq.presustentaciones.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class SavePeriodRequest {
    private String codigo;
    private String nombre;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private Boolean activo;
}
