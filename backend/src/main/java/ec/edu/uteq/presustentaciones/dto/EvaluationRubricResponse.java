package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * Evaluation rubric response.
 */
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
    private List<CriterionResult> detalles;
    /** Nota total de este panelist (suma de notaObtenida de cada criterio) */
    @JsonProperty("notaTotalJurado")
    private Double gradeTotalPanelist;
    /** Nota promedio del tribunal completo (todos los panelists), sobre 10 */
    @JsonProperty("notaPromedioTribunal")
    private Double gradeAveragePanel;
    /** true si todos los panelists asignados ya evaluaron */
    @JsonProperty("tribunalCompleto")
    private boolean panelComplete;

    /**
     * Criterion result.
     */
    @Data @Builder
    public static class CriterionResult {
        @JsonProperty("criterioId")
        private Long criterionId;
        @JsonProperty("nombreCriterio")
        private String nombreCriterion;
        @JsonProperty("ponderacion")
        private Double ponderacion;
        @JsonProperty("escala")
        private Integer scale;
        @JsonProperty("rangoDescripcion")
        private String rangeDescription;
        @JsonProperty("notaObtenida")
        private Double gradeObtenida;
        @JsonProperty("observacionAuto")
        private String observationAuto;
        @JsonProperty("observacionManual")
        private String observationManual;
        @JsonProperty("observaciones")
        private String observations;
    }
}
