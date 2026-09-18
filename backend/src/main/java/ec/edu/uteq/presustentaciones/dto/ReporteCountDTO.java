package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Par etiqueta/cantidad genérico para los reportes agregados (submissions por estado,
 * sustentaciones por período, actividad por teacher, por program, ...). Lo llenan
 * consultas JPQL {@code GROUP BY} — nunca se carga la tabla completa en memoria.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ReporteCountDTO {
    @JsonProperty("etiqueta")
    private String etiqueta;
    @JsonProperty("cantidad")
    private long cantidad;
}
