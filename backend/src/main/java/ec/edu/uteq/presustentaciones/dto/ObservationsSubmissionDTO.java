package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Objeto de transferencia de observations submission.
 */
@Data @Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObservationsSubmissionDTO {
    @JsonProperty("solicitudId")
    private Long submissionId;
    @JsonProperty("tituloTema")
    private String tituloTopic;
    @JsonProperty("nombreEstudiante")
    private String nombreStudent;
    @JsonProperty("tutor")
    private ObservationsTutorDTO tutor;
    @JsonProperty("jurados")
    private List<ObservationsPanelistDTO> panelists;
    @JsonProperty("coordinador")
    private ObservationsCoordinatorDTO coordinator;

    /**
     * Objeto de transferencia de observations tutor.
     */
    @Data @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ObservationsTutorDTO {
        @JsonProperty("tutorId")
        private Long tutorId;
        @JsonProperty("nombreTutor")
        private String nombreTutor;
        @JsonProperty("observaciones")
        private String observations;
        @JsonProperty("fechaRegistro")
        private String dateRecord;
    }

    /**
     * Objeto de transferencia de observations panelist.
     */
    @Data @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ObservationsPanelistDTO {
        @JsonProperty("juradoId")
        private Long panelistId;
        @JsonProperty("nombreJurado")
        private String nombrePanelist;
        @JsonProperty("rol")
        private String role;
        @JsonProperty("criterios")
        private List<CriterionObservationDTO> criteria;
        @JsonProperty("notaJurado")
        private Double gradePanelist;
        @JsonProperty("observaciones")
        private String observations;
        @JsonProperty("resultado")
        private String result;
        @JsonProperty("comentarioPreestablecido")
        private String commentPreestablecido;
    }

    /**
     * Objeto de transferencia de criterion observation.
     */
    @Data @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CriterionObservationDTO {
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
    }

    /**
     * Objeto de transferencia de observations coordinator.
     */
    @Data @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ObservationsCoordinatorDTO {
        @JsonProperty("observaciones")
        private String observations;
        @JsonProperty("notaInstructor")
        private Double gradeInstructor;
        @JsonProperty("notaFinal")
        private Double gradeFinal;
        @JsonProperty("resultado")
        private String result;
    }
}
