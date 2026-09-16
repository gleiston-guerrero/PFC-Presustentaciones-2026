package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObservacionesSubmissionDTO {
    @JsonProperty("solicitudId")
    private Long submissionId;
    private String tituloTopic;
    private String nombreStudent;
    private ObservacionesTutorDTO tutor;
    @JsonProperty("jurados")
    private List<ObservacionesPanelistDTO> panelists;
    private ObservacionesCoordinadorDTO coordinador;

    @Data @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ObservacionesTutorDTO {
        private Long tutorId;
        private String nombreTutor;
        private String observaciones;
        private String fechaRegistro;
    }

    @Data @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ObservacionesPanelistDTO {
        @JsonProperty("juradoId")
        private Long panelistId;
        private String nombrePanelist;
        @JsonProperty("rol")
        private String role;
        private List<CriterioObservacionDTO> criterios;
        private Double notaPanelist;
        private String observaciones;
        private String resultado;
        private String comentarioPreestablecido;
    }

    @Data @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CriterioObservacionDTO {
        private String nombreCriterio;
        private Double ponderacion;
        @JsonProperty("escala")
        private Integer scale;
        private String rangoDescripcion;
        private Double notaObtenida;
        private String observacionAuto;
        private String observacionManual;
    }

    @Data @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ObservacionesCoordinadorDTO {
        private String observaciones;
        private Double notaInstructor;
        private Double notaFinal;
        private String resultado;
    }
}
