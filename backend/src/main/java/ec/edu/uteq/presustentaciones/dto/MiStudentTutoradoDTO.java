package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** "Mis Estudiantes" (teacher): roster de los students que el teacher tiene
 * asignados como tutor, con sus datos académicos -- distinto de TutoringResumenDTO,
 * que está enfocado en el progress de fases/mensajes de la tutoría en sí. */
@Data
@Builder
public class MiStudentTutoradoDTO {
    @JsonProperty("tutorId")
    private Long tutorId;
    @JsonProperty("solicitudId")
    private Long submissionId;
    @JsonProperty("estudianteUsuarioId")
    private Long studentAppUserId;
    @JsonProperty("nombre")
    private String nombre;
    @JsonProperty("apellido")
    private String apellido;
    @JsonProperty("email")
    private String email;
    @JsonProperty("telefono")
    private String telefono;
    @JsonProperty("expedienteCodigo")
    private String expedienteCodigo;
    @JsonProperty("carreraNombre")
    private String programNombre;
    @JsonProperty("semestreActual")
    private Short semestreActual;
    @JsonProperty("estadoAcademicoCodigo")
    private String estadoAcademicoCodigo;
    @JsonProperty("estadoAcademicoNombre")
    private String estadoAcademicoNombre;
    @JsonProperty("tituloTema")
    private String tituloTopic;
    @JsonProperty("estadoSolicitudCodigo")
    private String estadoSubmissionCodigo;
    @JsonProperty("estadoSolicitudNombre")
    private String estadoSubmissionNombre;
    @JsonProperty("estadoTutoria")
    private String estadoTutoring;
    @JsonProperty("fechaAsignacion")
    private LocalDateTime fechaAsignacion;
}
