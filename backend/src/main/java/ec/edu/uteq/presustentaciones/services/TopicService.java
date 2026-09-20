package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.GenerateTopicRequest;
import ec.edu.uteq.presustentaciones.dto.SaveTopicProposedRequest;
import ec.edu.uteq.presustentaciones.dto.TopicProposedDTO;
import java.util.List;

/**
 * Contrato. Servicio de topic.
 */
public interface TopicService {

    /**
     * Explora el catálogo de topics propuestos con filtros opcionales.
     * @param studentId si no es null, cada topic se marca con {@code saved} según
     *                     los topics que ya guardó ese student.
     * @param programId identificador del programa académico (carrera)
     * @param researchLineId identificador de la línea de investigación
     * @param areaId identificador del área temática
     * @param nivelDificultad nivel de dificultad declarado, o {@code null}
     * @return los temas propuestos que cumplen el filtro
     */
    List<TopicProposedDTO> explore(Integer programId, Integer researchLineId,
                                    Integer areaId, String nivelDificultad, Long studentId);

    /**
     * Sugiere ideas de topic a partir de la program / línea del student.
     * @param request datos con los que se crea o actualiza el registro
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<TopicProposedDTO> generateSuggestions(GenerateTopicRequest request);

    /**
     * Detalle de un topic propuesto.
     * @param topicProposedId identificador del tema propuesto
     * @return el valor de tipo {@code TopicProposedDTO} correspondiente
     */
    TopicProposedDTO obtainDetail(Integer topicProposedId);

    /**
     * Save topic student.
     * @param studentId identificador del estudiante
     * @param topicProposedId identificador del tema propuesto
     */
    void saveTopicStudent(Long studentId, Integer topicProposedId);

    /**
     * Remove topic guardado.
     * @param studentId identificador del estudiante
     * @param topicProposedId identificador del tema propuesto
     */
    void removeTopicSaved(Long studentId, Integer topicProposedId);

    /**
     * Obtain topics guardados.
     * @param studentId identificador del estudiante
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<TopicProposedDTO> obtainTopicsSaved(Long studentId);

    // ── Gestión del catálogo (permission ORIENTACION_CATALOGO_GESTIONAR) ─────────

    /**
     * Create.
     * @param request datos con los que se crea o actualiza el registro
     * @return el TopicPropuestoDTO correspondiente
     */
    TopicProposedDTO create(SaveTopicProposedRequest request);

    /**
     * Update.
     * @param topicProposedId identificador del tema propuesto
     * @param request datos con los que se crea o actualiza el registro
     * @return el TopicPropuestoDTO correspondiente
     */
    TopicProposedDTO update(Integer topicProposedId, SaveTopicProposedRequest request);

    /**
     * Delete.
     * @param topicProposedId identificador del tema propuesto
     */
    void delete(Integer topicProposedId);
}
