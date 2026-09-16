package ec.edu.uteq.presustentaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Estado de la ruta de titulación de un student: la lista de pasos del catálogo
 * (fija, definida en el servicio) con el flag de completado de cada uno y el
 * porcentaje global de avance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgressTitulacionDTO {

    private int porcentaje;
    private int completados;
    private int total;
    private List<PasoDTO> pasos;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PasoDTO {
        private String clave;
        private int orden;
        private String titulo;
        private String descripcion;
        private boolean completado;
    }
}
