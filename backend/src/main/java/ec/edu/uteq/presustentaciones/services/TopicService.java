package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.GenerateTopicRequest;
import ec.edu.uteq.presustentaciones.dto.SaveTopicProposedRequest;
import ec.edu.uteq.presustentaciones.dto.TopicProposedDTO;
import java.util.List;

public interface TopicService {

    /**
     * Explora el catálogo de topics propuestos con filtros opcionales.
     * @param studentId si no es null, cada topic se marca con {@code saved} según
     *                     los topics que ya guardó ese student.
     */
    List<TopicProposedDTO> explore(Integer programId, Integer researchLineId,
                                    Integer areaId, String nivelDificultad, Long studentId);

    /** Sugiere ideas de topic a partir de la program / línea del student. */
    List<TopicProposedDTO> generateSuggestions(GenerateTopicRequest request);

    /** Detalle de un topic propuesto. */
    TopicProposedDTO obtainDetail(Integer topicProposedId);

    /**
     * Save topic student.
     * @param studentId studentId
     * @param topicProposedId topicProposedId
     */
    void saveTopicStudent(Long studentId, Integer topicProposedId);

    /**
     * Remove topic guardado.
     * @param studentId studentId
     * @param topicProposedId topicProposedId
     */
    void removeTopicSaved(Long studentId, Integer topicProposedId);

    /**
     * Obtain topics guardados.
     * @param studentId studentId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<TopicProposedDTO> obtainTopicsSaved(Long studentId);

    // ── Gestión del catálogo (permission ORIENTACION_CATALOGO_GESTIONAR) ─────────

    /**
     * Create.
     * @param request request
     * @return el TopicPropuestoDTO correspondiente
     */
    TopicProposedDTO create(SaveTopicProposedRequest request);

    /**
     * Update.
     * @param topicProposedId topicProposedId
     * @param request request
     * @return el TopicPropuestoDTO correspondiente
     */
    TopicProposedDTO update(Integer topicProposedId, SaveTopicProposedRequest request);

    /**
     * Delete.
     * @param topicProposedId topicProposedId
     */
    void delete(Integer topicProposedId);
}
