package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Cuerpo de PATCH /api/v1/minutes/{id}/estado. {@code nuevoEstado} es el código del
 * catálogo estados_minutes (GENERADA, REVISADA, OBSERVADA, FINALIZADA, ANULADA);
 * {@code motivo} es obligatorio en la práctica para OBSERVADA/ANULADA (lo valida
 * el service) y queda como comentario en history_estados_minutes.
 */
@Data
public class ChangeEstadoMinutesRequest {

    @NotBlank(message = "El nuevo estado es obligatorio")
    @JsonProperty("nuevoEstado")
    private String nuevoEstado;

    @Size(max = 2000, message = "El motivo no puede superar los 2000 caracteres")
    @JsonProperty("motivo")
    private String motivo;
}
