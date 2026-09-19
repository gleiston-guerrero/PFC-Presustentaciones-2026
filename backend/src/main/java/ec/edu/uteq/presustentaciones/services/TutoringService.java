package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.TutoringPhaseDTO;
import ec.edu.uteq.presustentaciones.dto.TutoringMessageDTO;
import ec.edu.uteq.presustentaciones.dto.TutoringSummaryDTO;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Contrato. Servicio de tutoring.
 */
public interface TutoringService {

    /**
     * Obtain summary.
     * @param tutorId   id del registro de tutoría
     * @param appUserId id del appUser que consulta (para resolve permissions de vista)
     * @return resumen de la tutoría: fase actual, progress y estado
     * @throws RuntimeException si la tutoría no existe
     */
    TutoringSummaryDTO obtainSummary(Long tutorId, Long appUserId);

    /**
     * Obtain phases.
     * @param tutorId id del registro de tutoría
     * @param appUserId id del appUser que consulta (para resolve permissions)
     * @return las fases registradas de esa tutoría, en orden
     */
    List<TutoringPhaseDTO> obtainPhases(Long tutorId, Long appUserId);

    /**
     * Create phase with observation.
     * @param tutorId        id del registro de tutoría
     * @param tutorAppUserId id del appUser teacher que crea la fase
     * @param observation    observación inicial del teacher para esta fase
     * @return la fase creada
     * @throws RuntimeException si la tutoría no existe
     */
    TutoringPhaseDTO createPhaseWithObservation(Long tutorId, Long tutorAppUserId, String observation);

    /**
     * Upload pdf corrected.
     * @param phaseId              id de la fase de tutoría
     * @param file             PDF corregido subido por el student
     * @param studentAppUserId id del appUser student que sube el archivo
     * @return la fase actualizada con el nuevo PDF
     * @throws RuntimeException si la fase no existe o el archivo no es un PDF válido
     */
    TutoringPhaseDTO uploadPdfCorrected(Long phaseId, MultipartFile file, Long studentAppUserId);

    /**
     * Approve phase.
     * @param phaseId         id de la fase a approve
     * @param tutorAppUserId id del appUser teacher que aprueba
     * @param comment     comentario opcional de aprobación
     * @return la fase actualizada en estado aprobado
     * @throws RuntimeException si la fase no existe
     */
    TutoringPhaseDTO approvePhase(Long phaseId, Long tutorAppUserId, String comment);

    /**
     * Send message.
     * @param phaseId      id de la fase de tutoría
     * @param senderId id del appUser que envía el mensaje
     * @param contenido   texto del mensaje
     * @param kind        tipo de mensaje (p. ej. comentario, corrección)
     * @return el mensaje creado
     * @throws RuntimeException si la fase no existe
     */
    TutoringMessageDTO sendMessage(Long phaseId, Long senderId, String contenido, String kind);

    /**
     * Mark messages read.
     * @param phaseId    id de la fase de tutoría
     * @param appUserId id del appUser que marca los mensajes como leídos
     */
    void markMessagesRead(Long phaseId, Long appUserId);

    /**
     * Obtain pdf phase.
     * @param phaseId id de la fase de tutoría
     * @param appUserId id del appUser que solicita el PDF
     * @return el resource PDF de esa fase, para descarga
     * @throws RuntimeException si la fase no existe o no tiene PDF
     */
    Resource obtainPdfPhase(Long phaseId, Long appUserId);

    /**
     * Obtain tutorings student.
     * @param studentAppUserId id del appUser student
     * @return resúmenes de todas las tutorías de ese student
     */
    List<TutoringSummaryDTO> obtainTutoringsStudent(Long studentAppUserId);

    /**
     * Obtain tutorings teacher.
     * @param teacherAppUserId id del appUser teacher
     * @return resúmenes de todas las tutorías a cargo de ese teacher
     */
    List<TutoringSummaryDTO> obtainTutoringsTeacher(Long teacherAppUserId);

    /**
     * Registra el avance de una fase de tutoría vía procedimiento almacenado.
     *
     * @param tutorId     id del registro de tutoría
     * @param numeroPhase  número de fase que avanza
     * @param filePdf  nombre del archivo PDF asociado al avance
     * @param sizeBytes tamaño en bytes del archivo
     * @param sha256      hash SHA-256 del archivo, para verificación de integridad posterior
     * @param appUserId   id del appUser student que registra el avance (para validacion)
     */
    void registerProgressSP(Long tutorId, Integer numeroPhase, String filePdf, Long sizeBytes, String sha256, Long appUserId);
}
