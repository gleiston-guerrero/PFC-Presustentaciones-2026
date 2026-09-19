package ec.edu.uteq.presustentaciones.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ec.edu.uteq.presustentaciones.dto.ProgressDegreeDTO;
import ec.edu.uteq.presustentaciones.entities.Student;
import ec.edu.uteq.presustentaciones.entities.ProgressStudent;
import ec.edu.uteq.presustentaciones.repositories.StudentRepository;
import ec.edu.uteq.presustentaciones.repositories.ProgressStudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProgressDegreeServiceImpl implements ProgressDegreeService {

    private final ProgressStudentRepository progressRepository;
    private final StudentRepository studentRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Catálogo fijo de pasos de la ruta de titulación, en orden. */
    private static final List<ProgressDegreeDTO.StepDTO> CATALOGO = List.of(
            step("tema_definido", 1, "Definir el tema de titulación",
                    "Tienes claro el problema, el objetivo general y la línea de investigación."),
            step("tutor_asignado", 2, "Tener un tutor asignado",
                    "La coordinación te asignó un docente tutor para tu anteproyecto."),
            step("anteproyecto_elaborado", 3, "Elaborar el anteproyecto",
                    "Redactaste el documento del anteproyecto siguiendo la plantilla oficial."),
            step("anteproyecto_aprobado", 4, "Anteproyecto aprobado por el tutor",
                    "El tutor revisó y aprobó tu anteproyecto."),
            step("solicitud_registrada", 5, "Registrar la solicitud de pre-sustentación",
                    "Enviaste la solicitud desde 'Nueva Solicitud' con toda la información requerida."),
            step("correcciones_aplicadas", 6, "Aplicar las correcciones de tutoría",
                    "Subiste las correcciones de cada fase de tutoría y el tutor las aprobó."),
            step("documento_final", 7, "Subir el documento final del anteproyecto",
                    "Cargaste la versión final en PDF lista para el tribunal."),
            step("pre_sustentacion_programada", 8, "Pre-sustentación programada",
                    "Ya tienes fecha, hora y sala asignadas para tu pre-sustentación.")
    );

    private static ProgressDegreeDTO.StepDTO step(String clave, int orden, String titulo, String desc) {
        return ProgressDegreeDTO.StepDTO.builder()
                .clave(clave).orden(orden).titulo(titulo).description(desc).completado(false).build();
    }

    /**
     * Obtain.
     * @param studentId studentId
     * @return el ProgressTitulacionDTO correspondiente
     */
    @Override
    @Transactional(readOnly = true)
    public ProgressDegreeDTO obtain(Long studentId) {
        String json = progressRepository.findByStudentId(studentId)
                .map(ProgressStudent::getPasosJson)
                .orElse("{}");
        return buildDTO(readStatus(json));
    }

    /**
     * Update.
     * @param studentId studentId
     * @param cambios cambios
     * @return el ProgressTitulacionDTO correspondiente
     */
    @Override
    @Transactional
    public ProgressDegreeDTO update(Long studentId, Map<String, Boolean> cambios) {
        ProgressStudent progress = progressRepository.findByStudentId(studentId)
                .orElseGet(() -> createEmpty(studentId));

        Map<String, Boolean> status = readStatus(progress.getPasosJson());
        for (ProgressDegreeDTO.StepDTO p : CATALOGO) {
            if (cambios.containsKey(p.getClave()) && cambios.get(p.getClave()) != null) {
                status.put(p.getClave(), cambios.get(p.getClave()));
            }
        }
        progress.setPasosJson(writeStatus(status));
        progressRepository.save(progress);
        return buildDTO(status);
    }

    private ProgressStudent createEmpty(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Estudiante no encontrado"));
        return ProgressStudent.builder()
                .student(student)
                .pasosJson("{}")
                .build();
    }

    private Map<String, Boolean> readStatus(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Boolean> parsed = objectMapper.readValue(json, new TypeReference<>() {});
            return parsed != null ? parsed : new LinkedHashMap<>();
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private String writeStatus(Map<String, Boolean> status) {
        try {
            return objectMapper.writeValueAsString(status);
        } catch (Exception e) {
            return "{}";
        }
    }

    private ProgressDegreeDTO buildDTO(Map<String, Boolean> status) {
        List<ProgressDegreeDTO.StepDTO> pasos = CATALOGO.stream()
                .map(p -> ProgressDegreeDTO.StepDTO.builder()
                        .clave(p.getClave())
                        .orden(p.getOrden())
                        .titulo(p.getTitulo())
                        .description(p.getDescription())
                        .completado(Boolean.TRUE.equals(status.get(p.getClave())))
                        .build())
                .toList();

        int completados = (int) pasos.stream().filter(ProgressDegreeDTO.StepDTO::isCompletado).count();
        int total = pasos.size();
        int porcentaje = total == 0 ? 0 : Math.round((completados * 100f) / total);

        return ProgressDegreeDTO.builder()
                .pasos(pasos)
                .completados(completados)
                .total(total)
                .porcentaje(porcentaje)
                .build();
    }
}
