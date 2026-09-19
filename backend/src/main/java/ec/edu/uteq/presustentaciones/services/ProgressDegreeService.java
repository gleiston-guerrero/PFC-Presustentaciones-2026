package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.ProgressDegreeDTO;

import java.util.Map;

/**
 * Contrato. Servicio de progress degree.
 */
public interface ProgressDegreeService {

    /**
     * Estado actual de la ruta de titulación del student (todos los pasos en falso si aún no guardó nada).
     * @param studentId student id
     * @return el valor de tipo {@code ProgressDegreeDTO} correspondiente
     */
    ProgressDegreeDTO obtain(Long studentId);

    /**
     * Fusiona los cambios recibidos con el estado guardado y devuelve el progress actualizado.
     * @param studentId student id
     * @param cambios cambios
     * @return el valor de tipo {@code ProgressDegreeDTO} correspondiente
     */
    ProgressDegreeDTO update(Long studentId, Map<String, Boolean> cambios);
}
