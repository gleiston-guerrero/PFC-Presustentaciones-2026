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
    @JsonProperty("tituloTema")
    private String tituloTopic;
    @JsonProperty("nombreEstudiante")
    private String nombreStudent;
    @JsonProperty("tutor")
    private ObservacionesTutorDTO tutor;
    @JsonProperty("jurados")
    private List<ObservacionesPanelistDTO> panelists;
    @JsonProperty("coordinador")
    private ObservacionesCoordinadorDTO coordinador;

    @Data @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ObservacionesTutorDTO {
        @JsonProperty("tutorId")
        private Long tutorId;
        @JsonProperty("nombreTutor")
        private String nombreTutor;
        @JsonProperty("observaciones")
        private String observaciones;
        @JsonProperty("fechaRegistro")
        private String fechaRegistro;
    }

    @Data @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ObservacionesPanelistDTO {
        @JsonProperty("juradoId")
        private Long panelistId;
        @JsonProperty("nombreJurado")
        private String nombrePanelist;
        @JsonProperty("rol")
        private String role;
        @JsonProperty("criterios")
        private List<CriterioObservacionDTO> criterios;
        @JsonProperty("notaJurado")
        private Double notaPanelist;
        @JsonProperty("observaciones")
        private String observaciones;
        @JsonProperty("resultado")
        private String resultado;
        @JsonProperty("comentarioPreestablecido")
        private String comentarioPreestablecido;
    }

    @Data @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CriterioObservacionDTO {
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
    }

    @Data @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ObservacionesCoordinadorDTO {
        @JsonProperty("observaciones")
        private String observaciones;
        @JsonProperty("notaInstructor")
        private Double notaInstructor;
        @JsonProperty("notaFinal")
        private Double notaFinal;
        @JsonProperty("resultado")
        private String resultado;
    }
}
