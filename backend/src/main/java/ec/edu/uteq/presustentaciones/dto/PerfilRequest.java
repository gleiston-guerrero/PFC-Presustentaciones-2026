package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PerfilRequest {
    @JsonProperty("emailNotificaciones")
    private String emailNotifications;
    @JsonProperty("telefono")
    private String telefono;
}