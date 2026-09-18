package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CreateStudentRequest {
    @JsonProperty("nombre")
    private String nombre;
    @JsonProperty("apellido")
    private String apellido;
    @JsonProperty("email")
    private String email;
    @JsonProperty("password")
    private String password;
    @JsonProperty("telefono")
    private String phone;
    @JsonProperty("carreraId")
    private Integer programId;
    @JsonProperty("periodoIngresoId")
    private Integer periodIngresoId;
    @JsonProperty("semestreActual")
    private Short semestreActual;
}
