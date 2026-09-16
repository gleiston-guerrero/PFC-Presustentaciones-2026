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
    private Long tutorId;
    @JsonProperty("solicitudId")
    private Long submissionId;
    @JsonProperty("estudianteUsuarioId")
    private Long studentAppUserId;
    private String nombre;
    private String apellido;
    private String email;
    private String telefono;
    private String expedienteCodigo;
    @JsonProperty("carreraNombre")
    private String programNombre;
    private Short semestreActual;
    private String estadoAcademicoCodigo;
    private String estadoAcademicoNombre;
    private String tituloTopic;
    private String estadoSubmissionCodigo;
    private String estadoSubmissionNombre;
    private String estadoTutoring;
    private LocalDateTime fechaAsignacion;
}
