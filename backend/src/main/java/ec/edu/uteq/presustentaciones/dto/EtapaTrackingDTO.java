package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EtapaTrackingDTO {
    @JsonProperty("nombre")
    private String nombre;
    @JsonProperty("estadoVisual")
    private String estadoVisual; // COMPLETADO, EN_PROCESO, PENDIENTE, RECHAZADO
    @JsonProperty("fecha")
    private LocalDateTime fecha;
    @JsonProperty("descripcion")
    private String descripcion;
}
