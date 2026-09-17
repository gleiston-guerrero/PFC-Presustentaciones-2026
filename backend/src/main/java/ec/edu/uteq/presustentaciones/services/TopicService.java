package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.GenerateTopicRequest;
import ec.edu.uteq.presustentaciones.dto.SaveTopicPropuestoRequest;
import ec.edu.uteq.presustentaciones.dto.TopicPropuestoDTO;
import java.util.List;

public interface TopicService {

    /**
     * Explora el catálogo de topics propuestos con filtros opcionales.
     * @param studentId si no es null, cada topic se marca con {@code guardado} según
     *                     los topics que ya guardó ese student.
     */
    List<TopicPropuestoDTO> explorar(Integer programId, Integer lineInvestigacionId,
                                    Integer areaId, String nivelDificultad, Long studentId);

    /** Sugiere ideas de topic a partir de la program / línea del student. */
    List<TopicPropuestoDTO> generateIdeas(GenerateTopicRequest request);

    /** Detalle de un topic propuesto. */
    TopicPropuestoDTO obtainDetalle(Integer topicPropuestoId);

    /**
     * Save topic student.
     * @param studentId studentId
     * @param topicPropuestoId topicPropuestoId
     */
    void saveTopicStudent(Long studentId, Integer topicPropuestoId);

    /**
     * Remove topic guardado.
     * @param studentId studentId
     * @param topicPropuestoId topicPropuestoId
     */
    void removeTopicGuardado(Long studentId, Integer topicPropuestoId);

    /**
     * Obtain topics guardados.
     * @param studentId studentId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<TopicPropuestoDTO> obtainTopicsGuardados(Long studentId);

    // ── Gestión del catálogo (permission ORIENTACION_CATALOGO_GESTIONAR) ─────────

    /**
     * Create.
     * @param request request
     * @return el TopicPropuestoDTO correspondiente
     */
    TopicPropuestoDTO create(SaveTopicPropuestoRequest request);

    /**
     * Update.
     * @param topicPropuestoId topicPropuestoId
     * @param request request
     * @return el TopicPropuestoDTO correspondiente
     */
    TopicPropuestoDTO update(Integer topicPropuestoId, SaveTopicPropuestoRequest request);

    /**
     * Delete.
     * @param topicPropuestoId topicPropuestoId
     */
    void delete(Integer topicPropuestoId);
}
