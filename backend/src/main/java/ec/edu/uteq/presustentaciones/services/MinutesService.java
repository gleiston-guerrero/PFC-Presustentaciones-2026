package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.Minutes;
import ec.edu.uteq.presustentaciones.dto.MinutesDetailDTO;
import ec.edu.uteq.presustentaciones.dto.MinutesSummaryDTO;
import ec.edu.uteq.presustentaciones.dto.HistoryMinutesDTO;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Contrato. Servicio de minutes.
 */
public interface MinutesService {

    /** RF-11: Genera el minutes y crea el PDF real en disco
     * @param submissionId id de la submission a la que pertenece el minutes
     * @return el minutes existente si ya se había generado, o la recién creada (con su PDF)
     * @throws RuntimeException si la submission no existe o no se pudo create el directorio
     *                          de minutes en disco
     */
    Minutes generateMinutes(Long submissionId);

    /** RF-08: Firma el minutes por un actor específico (PRESIDENTE, VOCAL_1, VOCAL_2, TUTOR)
     * @param minutesId      id del minutes a sign
     * @param role         role que firma ({@code PRESIDENTE}, {@code VOCAL_1}, {@code VOCAL_2}
     *                    o {@code TUTOR}); no distingue mayúsculas/minúsculas
     * @param observation observación opcional del firmante, o {@code null}
     * @return el minutes actualizada; si con esta firma quedan las 4 completas, la submission pasa
     *         a "COMPLETADA" y el PDF se regenera con el estado final de las firmas
     * @throws RuntimeException si el minutes no existe o {@code role} no es uno de los 4 válidos
     */
    Minutes signMinutes(Long minutesId, String role, String observation);

    /** Retorna el path del PDF generado para descarga
     * @param minutesId id del minutes
     * @return los bytes del PDF generado para esa minutes
     * @throws RuntimeException si el minutes no existe, o si todavía no tiene PDF generado
     */
    byte[] obtainPdfBytes(Long minutesId);

    /**
     * List minutes.
     * @param pageable configuración de paginación
     * @return página de todas las minutes del sistema
     */
    Page<Minutes> listMinutes(Pageable pageable);

    /**
     * Search by submission.
     * @param submissionId id de la submission
     * @return el minutes de esa submission, si ya fue generada
     */
    Optional<Minutes> searchBySubmission(Long submissionId);

    /**
     * Elimina un minutes si el appUser tiene permission.
     * @param minutesId id del minutes
     */
    void deleteMinutes(Long minutesId);

    // ── Módulo 2: gestión e history de minutes ───────────────────────────────

    /**
     * "Mis actas" del teacher: minutes de las pre-sustentaciones en las que es tutor o panelist.
     * @param email email del appUser autenticado
     * @param pageable pageable
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    Page<MinutesSummaryDTO> listMyMinutes(String email, Pageable pageable);

    /**
     * Búsqueda/filtrado administrativo de minutes. Parámetros nulos/vacíos no filtran.
     * @param status status
     * @param program program
     * @param from fecha minima a incluir, o {@code null} para no acotar
     * @param to fecha maxima a incluir, o {@code null} para no acotar
     * @param q texto libre de busqueda, o {@code null}
     * @param pageable pagina y tamano solicitados
     * @return la pagina de actas que cumplen el filtro
     */
    Page<MinutesSummaryDTO> searchMinutes(String status, String program, LocalDate from, LocalDate to,
                                     String q, Pageable pageable);

    /**
     * Detalle de un minutes. Aplica control de acceso: ADMIN/COORDINADOR (permission ACTAS_VER),
     * o el student dueño / panelist / tutor de la submission. Lanza excepción si el appUser
     * no participa en esa minutes (previene IDOR/BOLA).
     * @param minutesId minutes id
     * @return el valor de tipo {@code MinutesDetailDTO} correspondiente
     */
    MinutesDetailDTO obtainDetail(Long minutesId);

    /**
     * History de trazabilidad (timeline) del minutes, más reciente primero. Mismo control
     * de acceso que {@link #obtainDetail(Long)}.
     * @param minutesId minutes id
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<HistoryMinutesDTO> obtainHistory(Long minutesId);

    /**
     * Cambia el estado del minutes (GENERADA -> REVISADA -> FINALIZADA, u OBSERVADA/ANULADA)
     * validando la transición y registrando el cambio en history_estados_minutes con el
     * appUser, su role, el estado anterior/nuevo y el motivo.
     * @param minutesId            id del minutes
     * @param targetStatusCode código del catálogo estados_minutes
     * @param motivo            motivo/observación (obligatorio para OBSERVADA y ANULADA)
     * @return el valor de tipo {@code Minutes} correspondiente
     */
    Minutes changeStatus(Long minutesId, String targetStatusCode, String motivo);
}
