package ec.edu.uteq.presustentaciones.security.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** RF-05: submission de recuperación de contraseña. */
@Data
public class RecoverPasswordRequest {

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo debe ser válido")
    private String email;
}
