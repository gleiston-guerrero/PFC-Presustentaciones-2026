package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CreateStudentRequest {
    private String nombre;
    private String apellido;
    private String email;
    private String password;
    private String telefono;
    @JsonProperty("carreraId")
    private Integer programId;
    @JsonProperty("periodoIngresoId")
    private Integer periodIngresoId;
    private Short semestreActual;
}
