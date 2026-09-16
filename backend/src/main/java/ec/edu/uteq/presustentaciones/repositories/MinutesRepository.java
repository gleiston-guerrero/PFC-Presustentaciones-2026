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
    @Query("SELECT a FROM Minutes a JOIN FETCH a.submission s JOIN FETCH s.student e JOIN FETCH e.appUser u WHERE s.id = :submissionId")
    Optional<Minutes> findBySubmissionId(@Param("submissionId") Long submissionId);

    @Query(value = "SELECT a FROM Minutes a JOIN FETCH a.submission s JOIN FETCH s.student e JOIN FETCH e.appUser u",
           countQuery = "SELECT COUNT(a) FROM Minutes a")
    Page<Minutes> findAll(Pageable pageable);

    /** Detalle de un minutes con submission + student + appUser + estado en un solo query. */
    @Query("SELECT a FROM Minutes a JOIN FETCH a.submission s JOIN FETCH s.student e JOIN FETCH e.appUser u JOIN FETCH a.estado est WHERE a.id = :id")
    Optional<Minutes> findDetalleById(@Param("id") Long id);

    /**
     * "Mis actas" del teacher: minutes de pre-sustentaciones en las que el appUser es
     * panelist (members_tribunal) o tutor (tutores). DISTINCT porque un teacher puede
     * ser panelist en más de un role de la misma submission.
     */
    @Query(value = "SELECT DISTINCT a FROM Minutes a " +
            "JOIN FETCH a.submission s JOIN FETCH s.student e JOIN FETCH e.appUser u JOIN FETCH a.estado est " +
            "WHERE EXISTS (SELECT 1 FROM Panelist j WHERE j.submission = s AND j.teacher.appUser.email = :email) " +
            "   OR EXISTS (SELECT 1 FROM Tutor t WHERE t.submission = s AND t.teacher.appUser.email = :email)",
           countQuery = "SELECT COUNT(DISTINCT a) FROM Minutes a JOIN a.submission s " +
            "WHERE EXISTS (SELECT 1 FROM Panelist j WHERE j.submission = s AND j.teacher.appUser.email = :email) " +
            "   OR EXISTS (SELECT 1 FROM Tutor t WHERE t.submission = s AND t.teacher.appUser.email = :email)")
    Page<Minutes> findMisMinutes(@Param("email") String email, Pageable pageable);

    /** ¿Es el appUser tutor o panelist de la submission de esta minutes? (control de acceso del teacher). */
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
            "JOIN FETCH a.submission s JOIN FETCH s.student e JOIN FETCH e.appUser u JOIN FETCH a.estado est " +
            "WHERE (:estado IS NULL OR :estado = '' OR est.codigo = :estado) " +
            "AND (:program IS NULL OR :program = '' OR LOWER(e.program) LIKE LOWER(CONCAT('%', :program, '%'))) " +
            "AND a.fechaGeneracion >= :desde AND a.fechaGeneracion <= :hasta " +
            "AND (:q IS NULL OR :q = '' OR LOWER(u.nombre) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "     OR LOWER(u.apellido) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "     OR LOWER(s.tituloTopic) LIKE LOWER(CONCAT('%', :q, '%')))",
           countQuery = "SELECT COUNT(a) FROM Minutes a JOIN a.submission s JOIN s.student e JOIN e.appUser u JOIN a.estado est " +
            "WHERE (:estado IS NULL OR :estado = '' OR est.codigo = :estado) " +
            "AND (:program IS NULL OR :program = '' OR LOWER(e.program) LIKE LOWER(CONCAT('%', :program, '%'))) " +
            "AND a.fechaGeneracion >= :desde AND a.fechaGeneracion <= :hasta " +
            "AND (:q IS NULL OR :q = '' OR LOWER(u.nombre) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "     OR LOWER(u.apellido) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "     OR LOWER(s.tituloTopic) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Minutes> searchConFiltros(@Param("estado") String estado,
                                @Param("program") String program,
                                @Param("desde") LocalDate desde,
                                @Param("hasta") LocalDate hasta,
                                @Param("q") String q,
                                Pageable pageable);

    // ── Agregados para reportes (COUNT en la base, nunca en memoria) ──────────
    long countByEstadoCodigo(String codigo);

    long countByFirmadaFalse();

    @Query("SELECT a.estado.codigo AS codigo, COUNT(a) AS total FROM Minutes a " +
           "WHERE a.fechaGeneracion >= :desde AND a.fechaGeneracion <= :hasta " +
           "GROUP BY a.estado.codigo")
    List<Object[]> countPorEstado(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    /** Invoca sp_sign_minutes_digital (PROCEDURE). Fase 3 / Criterio P1. */
    @Procedure(procedureName = "presus.sp_firmar_acta_digital")
    void signMinutesDigital(@Param("p_acta_id") Long minutesId,
                            @Param("p_rol") String role,
                            @Param("p_observacion") String observacion);
}
