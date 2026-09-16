package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.MiStudentTutoradoDTO;
import ec.edu.uteq.presustentaciones.entities.Teacher;
import ec.edu.uteq.presustentaciones.entities.Student;
import ec.edu.uteq.presustentaciones.entities.Submission;
import ec.edu.uteq.presustentaciones.entities.Tutor;
import ec.edu.uteq.presustentaciones.repositories.TeacherRepository;
import ec.edu.uteq.presustentaciones.repositories.SubmissionRepository;
import ec.edu.uteq.presustentaciones.repositories.TutorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
@Slf4j
public class TutorServiceImpl implements TutorService {

    private final TutorRepository tutorRepository;
    private final SubmissionRepository submissionRepository;
    private final TeacherRepository teacherRepository;
    private final NotificationService notificationService;
    private final ec.edu.uteq.presustentaciones.repositories.EstadoSubmissionRepository estadoSubmissionRepository;

    /**
     * @param submissionId id de la submission
     * @param teacherId   id del teacher que actuará como tutor
     * @return el registro de tutoría creado
     */
    @Override
    @Transactional
    public Tutor assignTutor(Long submissionId, Long teacherId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada: " + submissionId));
        Teacher teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new RuntimeException("Docente no encontrado: " + teacherId));

        tutorRepository.findBySubmissionId(submissionId).ifPresent(t -> tutorRepository.delete(t));

        Tutor tutor = Tutor.builder()
                .submission(submission)
                .teacher(teacher)
                .estado("ACTIVO")
                .build();
        Tutor guardado = tutorRepository.save(tutor);

        ec.edu.uteq.presustentaciones.entities.EstadoSubmission estadoTutoring = estadoSubmissionRepository.findByCodigo("TUTORIA")
                .orElseGet(() -> estadoSubmissionRepository.save(ec.edu.uteq.presustentaciones.entities.EstadoSubmission.builder()
                        .codigo("TUTORIA").nombre("Tutoria").build()));

        submission.setEstado(estadoTutoring);
        submissionRepository.save(submission);

        // Notify al teacher asignado
        try {
            notificationService.createNotification(teacher.getAppUser().getId(),
                    String.format("📚 Has sido asignado como tutor del anteproyecto \"%s\" " +
                                    "del estudiante %s %s.",
                            submission.getTituloTopic(),
                            submission.getStudent().getAppUser().getNombre(),
                            submission.getStudent().getAppUser().getApellido()));
        } catch (Exception e) {
            log.warn("No se pudo notificar al docente tutor: {}", e.getMessage());
        }

        // Notify al student
        try {
            notificationService.createNotification(submission.getStudent().getAppUser().getId(),
                    String.format("🎓 El docente %s %s ha sido asignado como tu tutor para \"%s\". Tu solicitud ahora está en fase de tutoría.",
                            teacher.getAppUser().getNombre(),
                            teacher.getAppUser().getApellido(),
                            submission.getTituloTopic()));
        } catch (Exception e) {
            log.warn("No se pudo notificar al estudiante sobre tutor: {}", e.getMessage());
        }

        return guardado;
    }

    /**
     * @param submissionId id de la submission
     * @return el tutor asignado, si existe
     */
    @Override
    public Optional<Tutor> searchPorSubmission(Long submissionId) {
        return tutorRepository.findBySubmissionId(submissionId);
    }

    /**
     * @param pageable configuración de paginación
     * @return página de todos los registros de tutoría del sistema
     */
    @Override
    public Page<Tutor> listTodos(Pageable pageable) {
        return tutorRepository.findAll(pageable);
    }

    /** @param tutorId id del registro de tutoría a delete */
    @Override
    public void deleteTutor(Long tutorId) {
        tutorRepository.deleteById(tutorId);
    }

    /**
     * @param appUserIdTeacher id del appUser teacher
     * @return los students tutorados actualmente por ese teacher
     */
    @Override
    public List<MiStudentTutoradoDTO> misStudents(Long appUserIdTeacher) {
        return tutorRepository.findByTeacherAppUserId(appUserIdTeacher).stream()
                .map(tutor -> {
                    Submission submission = tutor.getSubmission();
                    Student student = submission.getStudent();
                    return MiStudentTutoradoDTO.builder()
                            .tutorId(tutor.getId())
                            .submissionId(submission.getId())
                            .studentAppUserId(student.getAppUser().getId())
                            .nombre(student.getAppUser().getNombre())
                            .apellido(student.getAppUser().getApellido())
                            .email(student.getAppUser().getEmail())
                            .telefono(student.getTelefono())
                            .expedienteCodigo(student.getExpedienteCodigo())
                            .programNombre(student.getProgramEntidad() != null ? student.getProgramEntidad().getNombre() : student.getProgram())
                            .semestreActual(student.getSemestreActual())
                            .estadoAcademicoCodigo(student.getEstadoAcademico() != null ? student.getEstadoAcademico().getCodigo() : null)
                            .estadoAcademicoNombre(student.getEstadoAcademico() != null ? student.getEstadoAcademico().getNombre() : null)
                            .tituloTopic(submission.getTituloTopic())
                            .estadoSubmissionCodigo(submission.getEstado() != null ? submission.getEstado().getCodigo() : submission.getEstadoCodigo())
                            .estadoSubmissionNombre(submission.getEstado() != null ? submission.getEstado().getNombre() : null)
                            .estadoTutoring(tutor.getEstado())
                            .fechaAsignacion(tutor.getFechaAsignacion())
                            .build();
                })
                .toList();
    }

    /**
     * Invoca el procedimiento almacenado de estadísticas de carga de tutores.
     *
     * @return una fila por teacher con su carga actual de tutorías
     */
    @Override
    public List<Map<String, Object>> obtainEstadisticasTutoresSP() {
        List<Object[]> res = tutorRepository.obtainEstadisticasTutoresSp();
        List<Map<String, Object>> list = new java.util.ArrayList<>();
        for (Object[] row : res) {
            Map<String, Object> map = new HashMap<>();
            map.put("tutorDocenteId", row[0]);
            map.put("tutorNombre", row[1]);
            map.put("tutoriasActivas", row[2]);
            map.put("tutoriasCompletadas", row[3]);
            map.put("totalFasesAprobadas", row[4]);
            list.add(map);
        }
        return list;
    }
}