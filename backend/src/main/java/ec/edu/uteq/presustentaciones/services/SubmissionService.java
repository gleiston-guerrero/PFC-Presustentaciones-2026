package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.Submission;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface SubmissionService {

    /**
     * Crea una nueva submission de pre-sustentación para un student ya existente, en estado
     * inicial "CREADA".
     *
     * @param studentId id del {@code Student} propietario de la submission
     * @param datos        datos de la submission a create (título del topic, modality, etc.)
     * @return la submission creada y persistida, con su estado y student asociados
     * @throws RuntimeException si el student no existe o falta la modality de titulación
     */
    Submission createSubmission(Long studentId, Submission datos);

    /**
     * Crea una submission a partir del appUser autenticado, creando automáticamente su perfil de
     * {@code Student} (vía {@code sp_generate_codigo_expediente}) si todavía no existe.
     *
     * @param appUserId id del {@code AppUser} autenticado (role ESTUDIANTE)
     * @param datos     datos de la submission a create
     * @return la submission creada
     * @throws RuntimeException si el appUser no existe, no tiene role ESTUDIANTE, o no hay
     *                          programs configuradas para create el perfil automáticamente
     */
    Submission createSubmissionPorAppUser(Long appUserId, Submission datos);

    /**
     * Transiciona una submission de "CREADA" a "ENVIADA", validando que ya tenga un proposal
     * en PDF adjunto.
     *
     * @param submissionId id de la submission a send
     * @return la submission actualizada en estado "ENVIADA"
     * @throws RuntimeException si la submission no existe o no tiene proposal adjunto
     */
    Submission sendSubmission(Long submissionId);

    /**
     * Aprueba una submission enviada, transicionándola a estado "APROBADA".
     *
     * @param submissionId id de la submission a approve
     * @return la submission actualizada
     */
    Submission approveSubmission(Long submissionId);

    /**
     * Rechaza una submission sin register un motivo explícito.
     *
     * @param submissionId id de la submission a reject
     * @return la submission actualizada en estado "RECHAZADA"
     */
    Submission rejectSubmission(Long submissionId);

    /**
     * Rechaza una submission registrando el motivo del rechazo en sus observaciones.
     *
     * @param submissionId  id de la submission a reject
     * @param observacion  motivo del rechazo, visible luego para el student
     * @return la submission actualizada en estado "RECHAZADA" con la observación guardada
     */
    Submission rejectConObservacion(Long submissionId, String observacion);

    /** @return todas las submissions del sistema, sin paginar */
    List<Submission> listSubmissions();

    /**
     * Lista submissions con paginación y filtros opcionales, usada por la vista administrativa
     * de gestión de submissions (evita load el dataset completo, ver hallazgo real documentado
     * en la memoria del proyecto sobre {@code /submissions} sin paginar).
     *
     * @param pagina       número de página, base 0
     * @param tamanio      tamaño de página
     * @param estado       código de estado por el que filtrar, o {@code null} para no filtrar
     * @param texto        texto libre de búsqueda (título/student), o {@code null}
     * @param fechaDesde   fecha mínima de registro, o {@code null} para no acotar
     * @param fechaHasta   fecha máxima de registro, o {@code null} para no acotar
     * @return página de submissions que cumplen los filtros
     */
    Page<Submission> listSubmissionsPaginado(int pagina, int tamanio, String estado, String texto,
                                               LocalDate fechaDesde, LocalDate fechaHasta);

    /** @return count de submissions agrupado por código de estado, para el dashboard */
    Map<String, Long> countPorEstado();

    /**
     * @param studentId id del student
     * @return todas las submissions registradas por ese student
     */
    List<Submission> listPorStudent(Long studentId);

    /**
     * @param appUserId id del appUser (se resuelve a su perfil de student internamente)
     * @return las submissions del student asociado a ese appUser, o lista vacía si no tiene
     *         perfil de student todavía
     */
    List<Submission> listPorAppUser(Long appUserId);

    /**
     * @param id id de la submission
     * @return la submission si existe, o {@link Optional#empty()} en caso contrario
     */
    Optional<Submission> obtainPorId(Long id);

    /**
     * Suspende una submission que ya está en trámite (no permitido si está en "CREADA",
     * "RECHAZADA" o ya "SUSPENDIDA"), registrando el motivo y la fecha de suspensión.
     *
     * @param submissionId id de la submission a suspender
     * @param motivo      motivo de la suspensión; no puede estar vacío
     * @return la submission actualizada en estado "SUSPENDIDA"
     * @throws RuntimeException si la submission no existe, su estado actual no permite
     *                          suspensión, o el motivo está vacío
     */
    Submission suspenderSubmission(Long submissionId, String motivo);

    /**
     * Invoca {@code sp_generate_reporte_defensas} (procedimiento almacenado, Criterio P1) para
     * obtain el reporte consolidado de defensas de una program, cruzando submission, student,
     * schedule, room y evaluación.
     *
     * @param program nombre (o coincidencia parcial, {@code ILIKE}) de la program a filtrar
     * @return una fila por defensa, con las claves declaradas en
     *         {@code docs/basedatos/CATALOGO-SP.md} (submissionId, studentNombre, expediente,
     *         tituloTopic, estadoSubmission, fechaDefensa, roomNombre, notaFinal)
     */
    List<Map<String, Object>> generateReporteDefensasSP(String program);

    /**
     * Obtiene el tracking visual de la pre-sustentación.
     * @param submissionId el ID de la submission
     * @return un DTO con el progress y etapas del process
     */
    ec.edu.uteq.presustentaciones.dto.TrackingDTO obtainTracking(Long submissionId);
}