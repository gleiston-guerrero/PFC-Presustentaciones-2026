package ec.edu.uteq.presustentaciones.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class UpdateProgressRequest {

    /**
     * Mapa parcial {claveDelPaso: completado}. Solo se aplican las claves que
     * pertenezcan al catálogo de pasos; el resto se ignora. Las claves no
     * enviadas conservan su valor anterior.
     */
    @NotNull(message = "Debe enviar al menos un paso")
    private Map<String, Boolean> pasos;
}
