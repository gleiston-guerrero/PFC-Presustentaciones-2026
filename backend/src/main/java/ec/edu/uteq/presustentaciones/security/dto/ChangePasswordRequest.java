package ec.edu.uteq.presustentaciones.security.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** RF-06: cambio de contraseña propia. */
@Data
public class ChangePasswordRequest {

    @NotBlank(message = "La contraseña actual es obligatoria")
    private String passwordActual;

    @NotBlank(message = "La nueva contraseña es obligatoria")
    private String passwordNueva;
}
