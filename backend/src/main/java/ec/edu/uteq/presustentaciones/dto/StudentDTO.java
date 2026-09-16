package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentDTO {
    private Long id;
    @JsonProperty("usuarioId")
    private Long appUserId;
    private String nombre;
    private String apellido;
    private String email;
    private Boolean activo;
    private String telefono;
    private String expedienteCodigo;
    @JsonProperty("carreraId")
    private Integer programId;
    @JsonProperty("carreraNombre")
    private String programNombre;
    @JsonProperty("periodoIngresoId")
    private Integer periodIngresoId;
    @JsonProperty("periodoIngresoNombre")
    private String periodIngresoNombre;
    private Short semestreActual;
    private String estadoAcademicoCodigo;
    private String estadoAcademicoNombre;
    /** Topic de la submission más reciente del student, si tiene alguna. */
    private String proyectoTitulo;
    private String proyectoEstado;
}
