package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

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
public class ProgressDegreeDTO {

    @JsonProperty("porcentaje")
    private int porcentaje;
    @JsonProperty("completados")
    private int completados;
    @JsonProperty("total")
    private int total;
    @JsonProperty("pasos")
    private List<StepDTO> pasos;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepDTO {
        @JsonProperty("clave")
        private String clave;
        @JsonProperty("orden")
        private int orden;
        @JsonProperty("titulo")
        private String titulo;
        @JsonProperty("descripcion")
        private String description;
        @JsonProperty("completado")
        private boolean completado;
    }
}
