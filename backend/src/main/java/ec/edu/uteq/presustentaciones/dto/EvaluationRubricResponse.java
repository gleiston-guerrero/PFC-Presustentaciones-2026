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
    @JsonProperty("nombreJurado")
    private String nombrePanelist;
    @JsonProperty("rolJurado")
    private String rolePanelist;
    /** Detalle por criterio */
    @JsonProperty("detalles")
    private List<CriterioResultado> detalles;
    /** Nota total de este panelist (suma de notaObtenida de cada criterio) */
    @JsonProperty("notaTotalJurado")
    private Double notaTotalPanelist;
    /** Nota promedio del tribunal completo (todos los panelists), sobre 10 */
    @JsonProperty("notaPromedioTribunal")
    private Double notaPromedioTribunal;
    /** true si todos los panelists asignados ya evaluaron */
    @JsonProperty("tribunalCompleto")
    private boolean tribunalCompleto;

    @Data @Builder
    public static class CriterioResultado {
        @JsonProperty("criterioId")
        private Long criterioId;
        @JsonProperty("nombreCriterio")
        private String nombreCriterio;
        @JsonProperty("ponderacion")
        private Double ponderacion;
        @JsonProperty("escala")
        private Integer scale;
        @JsonProperty("rangoDescripcion")
        private String rangoDescripcion;
        @JsonProperty("notaObtenida")
        private Double notaObtenida;
        @JsonProperty("observacionAuto")
        private String observacionAuto;
        @JsonProperty("observacionManual")
        private String observacionManual;
        @JsonProperty("observaciones")
        private String observaciones;
    }
}
