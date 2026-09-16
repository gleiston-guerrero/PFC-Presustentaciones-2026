package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.TutoringFaseDTO;
import ec.edu.uteq.presustentaciones.dto.TutoringMensajeDTO;
import ec.edu.uteq.presustentaciones.dto.TutoringResumenDTO;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface TutoringService {

    /**
     * @param tutorId   id del registro de tutoría
     * @param appUserId id del appUser que consulta (para resolve permissions de vista)
     * @return resumen de la tutoría: fase actual, progress y estado
     * @throws RuntimeException si la tutoría no existe
     */
    TutoringResumenDTO obtainResumen(Long tutorId, Long appUserId);

    /**
     * @param tutorId id del registro de tutoría
     * @param appUserId id del appUser que consulta (para resolve permissions)
     * @return las fases registradas de esa tutoría, en orden
     */
    List<TutoringFaseDTO> obtainFases(Long tutorId, Long appUserId);

    /**
     * @param tutorId        id del registro de tutoría
     * @param tutorAppUserId id del appUser teacher que crea la fase
     * @param observacion    observación inicial del teacher para esta fase
     * @return la fase creada
     * @throws RuntimeException si la tutoría no existe
     */
    TutoringFaseDTO createFaseConObservacion(Long tutorId, Long tutorAppUserId, String observacion);

    /**
     * @param faseId              id de la fase de tutoría
     * @param archivo             PDF corregido subido por el student
     * @param studentAppUserId id del appUser student que sube el archivo
     * @return la fase actualizada con el nuevo PDF
     * @throws RuntimeException si la fase no existe o el archivo no es un PDF válido
     */
    TutoringFaseDTO uploadPdfCorregido(Long faseId, MultipartFile archivo, Long studentAppUserId);

    /**
     * @param faseId         id de la fase a approve
     * @param tutorAppUserId id del appUser teacher que aprueba
     * @param comentario     comentario opcional de aprobación
     * @return la fase actualizada en estado aprobado
     * @throws RuntimeException si la fase no existe
     */
    TutoringFaseDTO approveFase(Long faseId, Long tutorAppUserId, String comentario);

    /**
     * @param faseId      id de la fase de tutoría
     * @param remitenteId id del appUser que envía el mensaje
     * @param contenido   texto del mensaje
     * @param tipo        tipo de mensaje (p. ej. comentario, corrección)
     * @return el mensaje creado
     * @throws RuntimeException si la fase no existe
     */
    TutoringMensajeDTO sendMensaje(Long faseId, Long remitenteId, String contenido, String tipo);

    /**
     * @param faseId    id de la fase de tutoría
     * @param appUserId id del appUser que marca los mensajes como leídos
     */
    void marcarMensajesLeidos(Long faseId, Long appUserId);

    /**
     * @param faseId id de la fase de tutoría
     * @param appUserId id del appUser que solicita el PDF
     * @return el resource PDF de esa fase, para descarga
     * @throws RuntimeException si la fase no existe o no tiene PDF
     */
    Resource obtainPdfFase(Long faseId, Long appUserId);

    /**
     * @param studentAppUserId id del appUser student
     * @return resúmenes de todas las tutorías de ese student
     */
    List<TutoringResumenDTO> obtainTutoringsStudent(Long studentAppUserId);

    /**
     * @param teacherAppUserId id del appUser teacher
     * @return resúmenes de todas las tutorías a cargo de ese teacher
     */
    List<TutoringResumenDTO> obtainTutoringsTeacher(Long teacherAppUserId);

    /**
     * Registra el avance de una fase de tutoría vía procedimiento almacenado.
     *
     * @param tutorId     id del registro de tutoría
     * @param numeroFase  número de fase que avanza
     * @param archivoPdf  nombre del archivo PDF asociado al avance
     * @param tamanoBytes tamaño en bytes del archivo
     * @param sha256      hash SHA-256 del archivo, para verificación de integridad posterior
     * @param appUserId   id del appUser student que registra el avance (para validacion)
     */
    void registerAvanceSP(Long tutorId, Integer numeroFase, String archivoPdf, Long tamanoBytes, String sha256, Long appUserId);
}
