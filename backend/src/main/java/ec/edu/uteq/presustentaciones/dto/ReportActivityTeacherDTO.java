package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Actividad de un teacher en el process de pre-sustentaciones (reporte). */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ReportActivityTeacherDTO {
    @JsonProperty("docenteId")
    private Long teacherId;
    @JsonProperty("docente")
    private String teacher;
    @JsonProperty("comoJurado")
    private long asPanelist;
    @JsonProperty("comoTutor")
    private long asTutor;
    @JsonProperty("actasFirmadas")
    private long minutesFirmadas;
}
