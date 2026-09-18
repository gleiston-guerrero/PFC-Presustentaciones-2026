package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data @Builder
public class TutoringSummaryDTO {

    @JsonProperty("tutorId")
    private Long tutorId;
    @JsonProperty("solicitudId")
    private Long submissionId;
    @JsonProperty("tituloTema")
    private String tituloTopic;
    @JsonProperty("nombreEstudiante")
    private String nombreStudent;
    @JsonProperty("nombreTutor")
    private String nombreTutor;
    @JsonProperty("totalFases")
    private long totalPhases;
    @JsonProperty("fasesAprobadas")
    private long phasesAprobadas;
    @JsonProperty("estadoTutoria")
    private String statusTutoring;
    @JsonProperty("mensajesNoLeidos")
    private long messagesNoRead;
    @JsonProperty("solicitudSuspendida")
    private boolean submissionSuspendida;
}
