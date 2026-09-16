package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Actividad de un teacher en el process de pre-sustentaciones (reporte). */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ReporteActividadTeacherDTO {
    @JsonProperty("docenteId")
    private Long teacherId;
    @JsonProperty("docente")
    private String teacher;
    private long comoPanelist;
    private long comoTutor;
    @JsonProperty("actasFirmadas")
    private long minutesFirmadas;
}
