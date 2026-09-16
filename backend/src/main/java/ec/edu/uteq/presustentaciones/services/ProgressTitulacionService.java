package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.ProgressTitulacionDTO;

import java.util.Map;

public interface ProgressTitulacionService {

    /** Estado actual de la ruta de titulación del student (todos los pasos en falso si aún no guardó nada). */
    ProgressTitulacionDTO obtain(Long studentId);

    /** Fusiona los cambios recibidos con el estado guardado y devuelve el progress actualizado. */
    ProgressTitulacionDTO update(Long studentId, Map<String, Boolean> cambios);
}
