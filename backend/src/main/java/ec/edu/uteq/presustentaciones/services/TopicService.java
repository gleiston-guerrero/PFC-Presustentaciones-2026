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

    void saveTopicStudent(Long studentId, Integer topicPropuestoId);

    void removeTopicGuardado(Long studentId, Integer topicPropuestoId);

    List<TopicPropuestoDTO> obtainTopicsGuardados(Long studentId);

    // ── Gestión del catálogo (permission ORIENTACION_CATALOGO_GESTIONAR) ─────────

    TopicPropuestoDTO create(SaveTopicPropuestoRequest request);

    TopicPropuestoDTO update(Integer topicPropuestoId, SaveTopicPropuestoRequest request);

    void delete(Integer topicPropuestoId);
}
