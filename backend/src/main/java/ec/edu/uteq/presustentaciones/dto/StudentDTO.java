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
    @JsonProperty("id")
    private Long id;
    @JsonProperty("usuarioId")
    private Long appUserId;
    @JsonProperty("nombre")
    private String nombre;
    @JsonProperty("apellido")
    private String apellido;
    @JsonProperty("email")
    private String email;
    @JsonProperty("activo")
    private Boolean activo;
    @JsonProperty("telefono")
    private String telefono;
    @JsonProperty("expedienteCodigo")
    private String expedienteCodigo;
    @JsonProperty("carreraId")
    private Integer programId;
    @JsonProperty("carreraNombre")
    private String programNombre;
    @JsonProperty("periodoIngresoId")
    private Integer periodIngresoId;
    @JsonProperty("periodoIngresoNombre")
    private String periodIngresoNombre;
    @JsonProperty("semestreActual")
    private Short semestreActual;
    @JsonProperty("estadoAcademicoCodigo")
    private String estadoAcademicoCodigo;
    @JsonProperty("estadoAcademicoNombre")
    private String estadoAcademicoNombre;
    /** Topic de la submission más reciente del student, si tiene alguna. */
    @JsonProperty("proyectoTitulo")
    private String proyectoTitulo;
    @JsonProperty("proyectoEstado")
    private String proyectoEstado;
}
