package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SaveResourceRequest {

    @NotBlank(message = "El título es obligatorio")
    @Size(max = 255, message = "El título no puede superar los 255 caracteres")
    private String titulo;

    @NotBlank(message = "La categoría es obligatoria")
    @Size(max = 100, message = "La categoría no puede superar los 100 caracteres")
    private String categoria;

    @NotBlank(message = "La URL del archivo es obligatoria")
    @Size(max = 500, message = "La URL no puede superar los 500 caracteres")
    private String urlArchivo;

    /** Opcional: null = resource general para todas las programs. */
    @JsonProperty("carreraId")
    private Integer programId;
}
