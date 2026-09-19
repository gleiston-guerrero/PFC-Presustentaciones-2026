package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Objeto de transferencia de stage tracking.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StageTrackingDTO {
    @JsonProperty("nombre")
    private String nombre;
    @JsonProperty("estadoVisual")
    private String statusVisual; // COMPLETADO, EN_PROCESO, PENDIENTE, RECHAZADO
    @JsonProperty("fecha")
    private LocalDateTime date;
    @JsonProperty("descripcion")
    private String description;
}
