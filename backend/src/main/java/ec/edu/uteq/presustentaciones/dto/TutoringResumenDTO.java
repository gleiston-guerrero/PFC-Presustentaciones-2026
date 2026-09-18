package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data @Builder
public class TutoringResumenDTO {

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
    private long totalFases;
    @JsonProperty("fasesAprobadas")
    private long fasesAprobadas;
    @JsonProperty("estadoTutoria")
    private String estadoTutoring;
    @JsonProperty("mensajesNoLeidos")
    private long mensajesNoLeidos;
    @JsonProperty("solicitudSuspendida")
    private boolean submissionSuspendida;
}
