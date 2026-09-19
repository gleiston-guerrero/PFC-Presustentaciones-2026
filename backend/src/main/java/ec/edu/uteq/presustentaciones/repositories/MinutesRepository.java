package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.Minutes;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MinutesRepository extends JpaRepository<Minutes, Long> {
    /**
     * Busca el/los registro(s) con submission id.
     * @param submissionId submissionId
     * @return el registro si existe, vacío si no
     */
    @Query("SELECT a FROM Minutes a JOIN FETCH a.submission s JOIN FETCH s.student e JOIN FETCH e.appUser u WHERE s.id = :submissionId")
    Optional<Minutes> findBySubmissionId(@Param("submissionId") Long submissionId);

    /**
     * Find all.
     * @param pageable pageable
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    @Query(value = "SELECT a FROM Minutes a JOIN FETCH a.submission s JOIN FETCH s.student e JOIN FETCH e.appUser u",
           countQuery = "SELECT COUNT(a) FROM Minutes a")
    Page<Minutes> findAll(Pageable pageable);

    /** Detalle de un minutes con submission + student + appUser + estado en un solo query. */
    @Query("SELECT a FROM Minutes a JOIN FETCH a.submission s JOIN FETCH s.student e JOIN FETCH e.appUser u JOIN FETCH a.status est WHERE a.id = :id")
    Optional<Minutes> findDetailById(@Param("id") Long id);

    /**
     * "Mis actas" del teacher: minutes de pre-sustentaciones en las que el appUser es
     * panelist (members_tribunal) o tutor (tutores). DISTINCT porque un teacher puede
     * ser panelist en más de un role de la misma submission.
     * @param email email
     * @param pageable pageable
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    @Query(value = "SELECT DISTINCT a FROM Minutes a " +
            "JOIN FETCH a.submission s JOIN FETCH s.student e JOIN FETCH e.appUser u JOIN FETCH a.status est " +
            "WHERE EXISTS (SELECT 1 FROM Panelist j WHERE j.submission = s AND j.teacher.appUser.email = :email) " +
            "   OR EXISTS (SELECT 1 FROM Tutor t WHERE t.submission = s AND t.teacher.appUser.email = :email)",
           countQuery = "SELECT COUNT(DISTINCT a) FROM Minutes a JOIN a.submission s " +
            "WHERE EXISTS (SELECT 1 FROM Panelist j WHERE j.submission = s AND j.teacher.appUser.email = :email) " +
            "   OR EXISTS (SELECT 1 FROM Tutor t WHERE t.submission = s AND t.teacher.appUser.email = :email)")
    Page<Minutes> findMyMinutes(@Param("email") String email, Pageable pageable);

    /**
     * ¿Es el appUser tutor o panelist de la submission de esta minutes? (control de acceso del teacher).
     * @param minutesId minutesId
     * @param email email
     * @return true si se cumple la condición, false si no
     */
    @Query("SELECT (COUNT(a) > 0) FROM Minutes a JOIN a.submission s WHERE a.id = :minutesId AND (" +
            "EXISTS (SELECT 1 FROM Panelist j WHERE j.submission = s AND j.teacher.appUser.email = :email) " +
            "OR EXISTS (SELECT 1 FROM Tutor t WHERE t.submission = s AND t.teacher.appUser.email = :email) " +
            "OR s.student.appUser.email = :email)")
    boolean esParticipante(@Param("minutesId") Long minutesId, @Param("email") String email);

    /**
     * Búsqueda/filtrado administrativo. Los parámetros String nulos/vacíos no filtran; el
     * {@code OR :param = ''} extra le da a Hibernate la pista de tipo String para el bind
     * (sin él, un parámetro null se enlaza como bytea y Postgres falla en LOWER()).
     *
     * Las fechas NUNCA llegan null: {@code MinutesServiceImpl.searchMinutes} sustituye por
     * sentinelas (1900-01-01 / 9999-12-31) cuando el appUser no filtra por fecha. Un
     * {@code :desde IS NULL} dejaba a Postgres sin tipo para el bind ("could not determine
     * data type of parameter") — mismo motivo por el que SubmissionRepository.searchConFiltros
     * usa sentinelas en vez de comprobar null.
     */
    @Query(value = "SELECT a FROM Minutes a " +
            "JOIN FETCH a.submission s JOIN FETCH s.student e JOIN FETCH e.appUser u JOIN FETCH a.status est " +
            "WHERE (:estado IS NULL OR :estado = '' OR est.code = :estado) " +
            "AND (:program IS NULL OR :program = '' OR LOWER(e.program) LIKE LOWER(CONCAT('%', :program, '%'))) " +
            "AND a.dateGeneracion >= :desde AND a.dateGeneracion <= :hasta " +
            "AND (:q IS NULL OR :q = '' OR LOWER(u.nombre) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "     OR LOWER(u.apellido) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "     OR LOWER(s.tituloTopic) LIKE LOWER(CONCAT('%', :q, '%')))",
           countQuery = "SELECT COUNT(a) FROM Minutes a JOIN a.submission s JOIN s.student e JOIN e.appUser u JOIN a.status est " +
            "WHERE (:estado IS NULL OR :estado = '' OR est.code = :estado) " +
            "AND (:program IS NULL OR :program = '' OR LOWER(e.program) LIKE LOWER(CONCAT('%', :program, '%'))) " +
            "AND a.dateGeneracion >= :desde AND a.dateGeneracion <= :hasta " +
            "AND (:q IS NULL OR :q = '' OR LOWER(u.nombre) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "     OR LOWER(u.apellido) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "     OR LOWER(s.tituloTopic) LIKE LOWER(CONCAT('%', :q, '%')))")
    /**
     * Search con filtros.
     * @param status status
     * @param program program
     * @param from from
     * @param to to
     * @param q q
     * @param pageable pageable
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    Page<Minutes> searchWithFiltros(@Param("estado") String status,
                                @Param("program") String program,
                                @Param("desde") LocalDate from,
                                @Param("hasta") LocalDate to,
                                @Param("q") String q,
                                Pageable pageable);

    // ── Agregados para reportes (COUNT en la base, nunca en memoria) ──────────
    /**
     * Cuenta los registros con estado codigo.
     * @param code code
     * @return la cantidad de registros
     */
    long countByStatusCode(String code);

    /**
     * Cuenta los registros con firmada false.
     * @return la cantidad de registros
     */
    long countByFirmadaFalse();

    /**
     * Count por estado.
     * @param from from
     * @param to to
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    @Query("SELECT a.status.code AS code, COUNT(a) AS total FROM Minutes a " +
           "WHERE a.dateGeneracion >= :desde AND a.dateGeneracion <= :hasta " +
           "GROUP BY a.status.code")
    List<Object[]> countByStatus(@Param("desde") LocalDate from, @Param("hasta") LocalDate to);

    /** Invoca sp_sign_minutes_digital (PROCEDURE). Fase 3 / Criterio P1. */
    @Procedure(procedureName = "presus.sp_firmar_acta_digital")
    void signMinutesDigital(@Param("p_acta_id") Long minutesId,
                            @Param("p_rol") String role,
                            @Param("p_observacion") String observation);
}
