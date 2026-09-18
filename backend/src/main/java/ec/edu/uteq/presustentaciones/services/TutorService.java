package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.MyStudentTuteeDTO;
import ec.edu.uteq.presustentaciones.entities.Tutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface TutorService {

    /**
     * @param submissionId id de la submission
     * @param teacherId   id del teacher que actuará como tutor
     * @return el registro de tutoría creado
     */
    Tutor assignTutor(Long submissionId, Long teacherId);

    /**
     * @param submissionId id de la submission
     * @return el tutor asignado, si existe
     */
    Optional<Tutor> searchBySubmission(Long submissionId);

    /**
     * @param pageable configuración de paginación
     * @return página de todos los registros de tutoría del sistema
     */
    Page<Tutor> listAll(Pageable pageable);

    /** @param tutorId id del registro de tutoría a delete */
    void deleteTutor(Long tutorId);

    /**
     * Invoca el procedimiento almacenado de estadísticas de carga de tutores.
     *
     * @return una fila por teacher con su carga actual de tutorías
     */
    List<Map<String, Object>> obtainStatsTutorsSP();

    /**
     * @param appUserIdTeacher id del appUser teacher
     * @return los students tutorados actualmente por ese teacher
     */
    List<MyStudentTuteeDTO> myStudents(Long appUserIdTeacher);
}
