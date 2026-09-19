package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Profile request.
 */
@Data
public class ProfileRequest {
    @JsonProperty("emailNotificaciones")
    private String emailNotifications;
    @JsonProperty("telefono")
    private String phone;
}