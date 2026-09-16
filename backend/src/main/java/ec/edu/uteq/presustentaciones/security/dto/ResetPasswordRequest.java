package ec.edu.uteq.presustentaciones.security.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** RF-05: aplicación del restablecimiento con el token de un solo uso recibido por correo. */
@Data
public class ResetPasswordRequest {

    @NotBlank(message = "El token es obligatorio")
    private String token;

    @NotBlank(message = "La nueva contraseña es obligatoria")
    private String passwordNueva;
}
