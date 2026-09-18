package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Alta de una prueba de restauración en la bitácora. */
@Data
public class RegisterDrillRestoreRequest {

    @NotBlank(message = "Indica sobre qué respaldo se hizo la prueba")
    @JsonProperty("respaldoNombre")
    private String backupNombre;

    @NotBlank
    @Pattern(regexp = "EXITOSA|FALLIDA", message = "El resultado debe ser EXITOSA o FALLIDA")
    @JsonProperty("resultado")
    private String result;

    @Size(max = 200)
    @JsonProperty("responsable")
    private String responsable;

    @Size(max = 4000)
    @JsonProperty("notas")
    private String notas;
}
