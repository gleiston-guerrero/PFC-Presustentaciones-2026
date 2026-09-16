package ec.edu.uteq.presustentaciones.dto;

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
    private String nombre;
    private String estadoVisual; // COMPLETADO, EN_PROCESO, PENDIENTE, RECHAZADO
    private LocalDateTime fecha;
    private String descripcion;
}
