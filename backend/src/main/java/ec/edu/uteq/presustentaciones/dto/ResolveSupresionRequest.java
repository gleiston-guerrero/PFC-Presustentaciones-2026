package ec.edu.uteq.presustentaciones.dto;

import lombok.Data;

/** RNF-19: resolución de una submission de supresión de datos personales. */
@Data
public class ResolveSupresionRequest {
    private boolean aceptar;
    private String notas;
}
