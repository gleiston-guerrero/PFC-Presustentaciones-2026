package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/** RNF-19: resolución de una submission de supresión de datos personales. */
@Data
public class ResolveErasureRequest {
    @JsonProperty("aceptar")
    private boolean aceptar;
    @JsonProperty("notas")
    private String notas;
}
