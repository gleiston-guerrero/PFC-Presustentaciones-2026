package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.GenerateTopicRequest;
import ec.edu.uteq.presustentaciones.dto.SaveTopicProposedRequest;
import ec.edu.uteq.presustentaciones.dto.TopicProposedDTO;
import ec.edu.uteq.presustentaciones.entities.Subject;
import ec.edu.uteq.presustentaciones.entities.Program;
import ec.edu.uteq.presustentaciones.entities.Student;
import ec.edu.uteq.presustentaciones.entities.ResearchLine;
import ec.edu.uteq.presustentaciones.entities.TopicSavedStudent;
import ec.edu.uteq.presustentaciones.entities.TopicProposed;
import ec.edu.uteq.presustentaciones.repositories.SubjectRepository;
import ec.edu.uteq.presustentaciones.repositories.ProgramRepository;
import ec.edu.uteq.presustentaciones.repositories.StudentRepository;
import ec.edu.uteq.presustentaciones.repositories.ResearchLineRepository;
import ec.edu.uteq.presustentaciones.repositories.TopicSavedStudentRepository;
import ec.edu.uteq.presustentaciones.repositories.TopicProposedRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TopicServiceImpl implements TopicService {

    private final TopicProposedRepository topicProposedRepository;
    private final TopicSavedStudentRepository topicSavedStudentRepository;
    private final StudentRepository studentRepository;
    private final ProgramRepository programRepository;
    private final ResearchLineRepository researchLineRepository;
    private final SubjectRepository subjectRepository;

    /**
     * Explora el catálogo de topics propuestos con filtros opcionales.
     *
     * @param programId            id de program a filtrar, o {@code null} para no filtrar
     * @param researchLineId id de línea de investigación a filtrar, o {@code null}
     * @param areaId               id de área temática a filtrar, o {@code null}
     * @param nivelDificultad      nivel de dificultad a filtrar, o {@code null}/vacío
     * @param studentId         si no es {@code null}, cada topic se marca con {@code saved}
     *                             según los topics que ya guardó ese student
     * @return los topics propuestos que cumplen los filtros
     */
    @Override
    @Transactional(readOnly = true)
    public List<TopicProposedDTO> explore(Integer programId, Integer researchLineId,
                                           Integer areaId, String nivelDificultad, Long studentId) {
        String nivel = (nivelDificultad != null && !nivelDificultad.isBlank()) ? nivelDificultad.trim() : null;
        List<TopicProposed> topics = topicProposedRepository.searchWithFiltros(
                programId, researchLineId, areaId, nivel);

        Set<Integer> saved = studentId == null
                ? Set.of()
                : Set.copyOf(topicSavedStudentRepository.findTopicIdsByStudentId(studentId));

        return topics.stream()
                .map(t -> mapToDTO(t, studentId != null ? saved.contains(t.getId()) : null))
                .collect(Collectors.toList());
    }

    /**
     * Sugiere ideas de topic a partir de la program / línea del student.
     *
     * @param request program (obligatoria) y línea de investigación (opcional) a partir de
     *                las cuales sugerir topics
     * @return los topics propuestos del catálogo que coinciden con la program/línea indicadas
     */
    @Override
    @Transactional(readOnly = true)
    public List<TopicProposedDTO> generateSuggestions(GenerateTopicRequest request) {
        List<TopicProposed> topics;
        if (request.getResearchLineId() != null) {
            topics = topicProposedRepository.findByProgramIdAndResearchLineId(
                    request.getProgramId(), request.getResearchLineId());
        } else {
            topics = topicProposedRepository.findByProgramId(request.getProgramId());
        }
        return topics.stream().map(t -> mapToDTO(t, null)).collect(Collectors.toList());
    }

    /**
     * Detalle de un topic propuesto.
     *
     * @param topicProposedId id del topic propuesto
     * @return el detalle del topic, con sus catálogos (program/línea/área) resueltos
     * @throws IllegalArgumentException si el topic no existe
     */
    @Override
    @Transactional(readOnly = true)
    public TopicProposedDTO obtainDetail(Integer topicProposedId) {
        TopicProposed topic = topicProposedRepository.findByIdWithCatalogs(topicProposedId)
                .orElseThrow(() -> new IllegalArgumentException("Tema propuesto no encontrado"));
        return mapToDTO(topic, null);
    }

    /**
     * Guarda un topic propuesto en la lista de favoritos del student.
     *
     * @param studentId    id del student
     * @param topicProposedId id del topic propuesto a save
     * @throws IllegalStateException   si el student ya había guardado ese topic
     * @throws IllegalArgumentException si el student o el topic no existen
     */
    @Override
    @Transactional
    public void saveTopicStudent(Long studentId, Integer topicProposedId) {
        if (topicSavedStudentRepository.existsByStudentIdAndTopicProposedId(studentId, topicProposedId)) {
            throw new IllegalStateException("El tema ya está guardado por el estudiante");
        }

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Estudiante no encontrado"));

        TopicProposed topic = topicProposedRepository.findById(topicProposedId)
                .orElseThrow(() -> new IllegalArgumentException("Tema propuesto no encontrado"));

        TopicSavedStudent topicSaved = TopicSavedStudent.builder()
                .student(student)
                .topicProposed(topic)
                .build();

        topicSavedStudentRepository.save(topicSaved);
    }

    /**
     * Quita un topic de la lista de favoritos del student.
     *
     * @param studentId    id del student
     * @param topicProposedId id del topic propuesto a remove
     * @throws IllegalArgumentException si el topic no estaba guardado por ese student
     */
    @Override
    @Transactional
    public void removeTopicSaved(Long studentId, Integer topicProposedId) {
        int eliminados = topicSavedStudentRepository
                .deleteByStudentIdAndTopicProposedId(studentId, topicProposedId);
        if (eliminados == 0) {
            throw new IllegalArgumentException("El tema no estaba en la lista de guardados del estudiante");
        }
    }

    /**
     * @param studentId id del student
     * @return los topics que ese student tiene guardados, del más reciente al más antiguo
     */
    @Override
    @Transactional(readOnly = true)
    public List<TopicProposedDTO> obtainTopicsSaved(Long studentId) {
        return topicSavedStudentRepository.findByStudentIdOrderByDateSavedDesc(studentId).stream()
                .map(TopicSavedStudent::getTopicProposed)
                .map(t -> mapToDTO(t, true))
                .collect(Collectors.toList());
    }

    /**
     * Crea un topic propuesto en el catálogo (permission ORIENTACION_CATALOGO_GESTIONAR).
     *
     * @param request datos del topic a create
     * @return el topic creado
     * @throws IllegalArgumentException si la program, línea de investigación o área indicadas
     *                                  no existen, o el área no pertenece a la línea indicada
     */
    @Override
    @Transactional
    public TopicProposedDTO create(SaveTopicProposedRequest request) {
        TopicProposed topic = new TopicProposed();
        apply(topic, request);
        return mapToDTO(topicProposedRepository.save(topic), null);
    }

    /**
     * Actualiza un topic propuesto del catálogo (permission ORIENTACION_CATALOGO_GESTIONAR).
     *
     * @param topicProposedId id del topic a update
     * @param request         datos nuevos del topic
     * @return el topic actualizado
     * @throws IllegalArgumentException si el topic no existe, o la program/línea/área indicadas
     *                                  no existen o son inconsistentes entre sí
     */
    @Override
    @Transactional
    public TopicProposedDTO update(Integer topicProposedId, SaveTopicProposedRequest request) {
        TopicProposed topic = topicProposedRepository.findById(topicProposedId)
                .orElseThrow(() -> new IllegalArgumentException("Tema propuesto no encontrado"));
        apply(topic, request);
        return mapToDTO(topicProposedRepository.save(topic), null);
    }

    /**
     * Elimina un topic del catálogo (permission ORIENTACION_CATALOGO_GESTIONAR). Los topics
     * guardados por students que apunten a él se eliminan en cascada (FK V20).
     *
     * @param topicProposedId id del topic a delete
     * @throws IllegalArgumentException si el topic no existe
     */
    @Override
    @Transactional
    public void delete(Integer topicProposedId) {
        if (!topicProposedRepository.existsById(topicProposedId)) {
            throw new IllegalArgumentException("Tema propuesto no encontrado");
        }
        // topics_guardados tiene FK ON DELETE CASCADE (V20): al erase el topic del
        // catálogo también se quita de la lista de los students que lo guardaron.
        topicProposedRepository.deleteById(topicProposedId);
    }

    private void apply(TopicProposed topic, SaveTopicProposedRequest r) {
        topic.setTitulo(r.getTitulo().trim());
        topic.setProblema(trimOrNull(r.getProblema()));
        topic.setObjetivoGeneral(trimOrNull(r.getObjetivoGeneral()));
        topic.setObjetivosEspecificos(trimOrNull(r.getObjetivosEspecificos()));
        topic.setJustificacion(trimOrNull(r.getJustificacion()));
        topic.setBeneficiarios(trimOrNull(r.getBeneficiarios()));
        topic.setNivelDificultad(trimOrNull(r.getNivelDificultad()));

        Program program = r.getProgramId() == null ? null : programRepository.findById(r.getProgramId())
                .orElseThrow(() -> new IllegalArgumentException("Carrera no encontrada"));
        ResearchLine line = r.getResearchLineId() == null ? null
                : researchLineRepository.findById(r.getResearchLineId())
                .orElseThrow(() -> new IllegalArgumentException("Línea de investigación no encontrada"));
        Subject area = r.getAreaId() == null ? null : subjectRepository.findById(r.getAreaId())
                .orElseThrow(() -> new IllegalArgumentException("Área temática no encontrada"));

        if (area != null && line != null && area.getResearchLine() != null
                && !area.getResearchLine().getId().equals(line.getId())) {
            throw new IllegalArgumentException("El área temática no pertenece a la línea de investigación indicada");
        }

        topic.setProgram(program);
        topic.setResearchLine(line);
        topic.setArea(area);
    }

    private static String trimOrNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private TopicProposedDTO mapToDTO(TopicProposed entity, Boolean saved) {
        return TopicProposedDTO.builder()
                .id(entity.getId())
                .titulo(entity.getTitulo())
                .problema(entity.getProblema())
                .objetivoGeneral(entity.getObjetivoGeneral())
                .objetivosEspecificos(entity.getObjetivosEspecificos())
                .justificacion(entity.getJustificacion())
                .beneficiarios(entity.getBeneficiarios())
                .nivelDificultad(entity.getNivelDificultad())
                .programId(entity.getProgram() != null ? entity.getProgram().getId() : null)
                .programNombre(entity.getProgram() != null ? entity.getProgram().getNombre() : null)
                .researchLineId(entity.getResearchLine() != null ? entity.getResearchLine().getId() : null)
                .researchLineNombre(entity.getResearchLine() != null ? entity.getResearchLine().getNombre() : null)
                .areaId(entity.getArea() != null ? entity.getArea().getId() : null)
                .areaNombre(entity.getArea() != null ? entity.getArea().getNombre() : null)
                .saved(saved)
                .build();
    }
}
