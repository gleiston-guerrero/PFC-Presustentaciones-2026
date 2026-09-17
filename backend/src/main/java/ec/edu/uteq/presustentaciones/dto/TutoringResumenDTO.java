package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data @Builder
public class TutoringResumenDTO {

    private Long tutorId;
    @JsonProperty("solicitudId")
    private Long submissionId;
    @JsonProperty("tituloTema")
    private String tituloTopic;
    @JsonProperty("nombreEstudiante")
    private String nombreStudent;
    private String nombreTutor;
    private long totalFases;
    private long fasesAprobadas;
    @JsonProperty("estadoTutoria")
    private String estadoTutoring;
    private long mensajesNoLeidos;
    @JsonProperty("solicitudSuspendida")
    private boolean submissionSuspendida;
}
