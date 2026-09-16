package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data @Builder
public class EvaluationRubricResponse {
    @JsonProperty("solicitudId")
    private Long submissionId;
    @JsonProperty("juradoId")
    private Long panelistId;
    private String nombrePanelist;
    @JsonProperty("rolPanelist")
    private String rolePanelist;
    /** Detalle por criterio */
    private List<CriterioResultado> detalles;
    /** Nota total de este panelist (suma de notaObtenida de cada criterio) */
    private Double notaTotalPanelist;
    /** Nota promedio del tribunal completo (todos los panelists), sobre 10 */
    private Double notaPromedioTribunal;
    /** true si todos los panelists asignados ya evaluaron */
    private boolean tribunalCompleto;

    @Data @Builder
    public static class CriterioResultado {
        private Long criterioId;
        private String nombreCriterio;
        private Double ponderacion;
        @JsonProperty("escala")
        private Integer scale;
        private String rangoDescripcion;
        private Double notaObtenida;
        private String observacionAuto;
        private String observacionManual;
        private String observaciones;
    }
}
