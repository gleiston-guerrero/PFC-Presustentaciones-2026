package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.dto.ReporteDefensaResult;
import ec.edu.uteq.presustentaciones.entities.Submission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    /**
     * Invoca sp_generate_reporte_defensas (JPA 2.1 @NamedStoredProcedureQuery declarada en
     * Submission.java) -- reporte consolidado multi-tabla por program. Fase 3 / Criterio P1.
     */
    @Procedure(name = "Solicitud.generarReporteDefensas")
    List<ReporteDefensaResult> generateReporteDefensas(@Param("p_carrera") String program);

    /** Carga submissions con student+appUser en un solo query — evita LazyInitializationException */
    @Query("SELECT s FROM Submission s JOIN FETCH s.student e JOIN FETCH e.appUser u ORDER BY s.fechaRegistro DESC")
    List<Submission> findAllWithStudent();

    /**
     * Igual que {@link #findAllWithStudent()} pero acotada por {@link Pageable}. El endpoint
     * sin paginar ({@code GET /api/v1/submissions}) NUNCA debe devolver las 44k+ filas del volumen
     * real de una sola vez: son ~93 MB de JSON que congelan el navegador y llenan Redis (la lista
     * está cacheada). El listado completo navegable es {@code GET /api/v1/submissions/paginado}.
     * Como {@code student} y {@code appUser} son asociaciones @ManyToOne (a-uno), Hibernate
     * pagina en SQL sin el warning de "collection fetch + firstResult/maxResults en memoria".
     */
    @Query("SELECT s FROM Submission s JOIN FETCH s.student e JOIN FETCH e.appUser u ORDER BY s.fechaRegistro DESC")
    List<Submission> findAllWithStudent(Pageable pageable);

    /**
     * Página de submissions con student+appUser precargados — evita traer las 44k filas de
     * una vez. Combina el filtro de estado (pestañas), búsqueda de texto libre por título
     * del topic o nombre/apellido del student (ERR-01: el listado del coordinador solo
     * filtraba por estado, sin buscador), y un rango de fechaRegistro (para poder acotar a
     * "hoy" o a un día concreto desde el frontend). Todos los parámetros son opcionales e
     * independientes entre sí -- mismo patrón que AppUserRepository.searchPaginado.
     */
    @Query(value = "SELECT s FROM Submission s JOIN FETCH s.student e JOIN FETCH e.appUser u " +
           "WHERE (:estado IS NULL OR :estado = '' OR s.estado.codigo = :estado) " +
           "AND (:texto IS NULL OR :texto = '' " +
           "     OR LOWER(s.tituloTopic) LIKE LOWER(CONCAT('%', :texto, '%')) " +
           "     OR LOWER(u.nombre) LIKE LOWER(CONCAT('%', :texto, '%')) " +
           "     OR LOWER(u.apellido) LIKE LOWER(CONCAT('%', :texto, '%'))) " +
           "AND s.fechaRegistro >= :fechaDesde AND s.fechaRegistro <= :fechaHasta",
           countQuery = "SELECT count(s) FROM Submission s JOIN s.student e JOIN e.appUser u " +
           "WHERE (:estado IS NULL OR :estado = '' OR s.estado.codigo = :estado) " +
           "AND (:texto IS NULL OR :texto = '' " +
           "     OR LOWER(s.tituloTopic) LIKE LOWER(CONCAT('%', :texto, '%')) " +
           "     OR LOWER(u.nombre) LIKE LOWER(CONCAT('%', :texto, '%')) " +
           "     OR LOWER(u.apellido) LIKE LOWER(CONCAT('%', :texto, '%'))) " +
           "AND s.fechaRegistro >= :fechaDesde AND s.fechaRegistro <= :fechaHasta")
    /**
     * Search con filtros.
     * @param estado estado
     * @param texto texto
     * @param fechaDesde fechaDesde
     * @param fechaHasta fechaHasta
     * @param pageable pageable
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    Page<Submission> searchConFiltros(@Param("estado") String estado, @Param("texto") String texto,
                                      @Param("fechaDesde") LocalDateTime fechaDesde,
                                      @Param("fechaHasta") LocalDateTime fechaHasta,
                                      Pageable pageable);

    @Query("SELECT s FROM Submission s JOIN FETCH s.student e JOIN FETCH e.appUser u WHERE e.id = :studentId ORDER BY s.fechaRegistro DESC")
    /**
     * Busca el/los registro(s) con student id.
     * @param studentId studentId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<Submission> findByStudentId(@Param("studentId") Long studentId);

    @Query("SELECT s FROM Submission s JOIN FETCH s.student e JOIN FETCH e.appUser u WHERE u.id = :appUserId ORDER BY s.fechaRegistro DESC")
    /**
     * Busca el/los registro(s) con app user id.
     * @param appUserId appUserId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<Submission> findByAppUserId(@Param("appUserId") Long appUserId);

    @Query("SELECT s FROM Submission s JOIN FETCH s.student e JOIN FETCH e.appUser u WHERE s.id = :id")
    /**
     * Busca el/los registro(s) con id with student.
     * @param id id
     * @return el registro si existe, vacío si no
     */
    Optional<Submission> findByIdWithStudent(@Param("id") Long id);

    /**
     * Cuenta los registros con estado codigo.
     * @param codigo codigo
     * @return la cantidad de registros
     */
    long countByEstadoCodigo(String codigo);

    @Query("SELECT s.estado.codigo AS codigo, COUNT(s) AS total FROM Submission s GROUP BY s.estado.codigo")
    /**
     * Count agrupado por estado.
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<EstadoCount> countAgrupadoPorEstado();

    interface EstadoCount {
        /**
         * Get codigo.
         * @return el valor encontrado, o null si no existe
         */
        String getCodigo();
        /**
         * Get total.
         * @return la cantidad de registros
         */
        Long getTotal();
    }

    // ── Agregados para el módulo de reportes (COORDINADOR / ADMINISTRADOR) ────
    // Todos resuelven con GROUP BY en Postgres: nunca cargan la tabla en memoria.

    /**
     * Cantidad de submissions por estado, filtrable por rango de fechaRegistro y program.
     * El rango de fechas NO es opcional a nivel de query (mismo criterio que
     * searchConFiltros): el service pasa sentinelas MIN/MAX cuando el appUser no filtra --
     * un {@code :fecha IS NULL} deja a Postgres sin tipo para el bind ("could not determine
     * data type of parameter").
     */
    @Query("SELECT s.estado.codigo, COUNT(s) FROM Submission s " +
           "WHERE s.fechaRegistro >= :desde AND s.fechaRegistro <= :hasta " +
           "AND (:program IS NULL OR :program = '' OR LOWER(s.student.program) LIKE LOWER(CONCAT('%', :program, '%'))) " +
           "GROUP BY s.estado.codigo ORDER BY s.estado.codigo")
    /**
     * Count por estado.
     * @param desde desde
     * @param hasta hasta
     * @param program program
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<Object[]> countPorEstado(@Param("desde") LocalDateTime desde,
                                   @Param("hasta") LocalDateTime hasta,
                                   @Param("program") String program);

    /** Cantidad de pre-sustentaciones (submissions) por período académico de su announcement. */
    @Query("SELECT p.codigo, COUNT(s) FROM Submission s JOIN s.announcement c JOIN c.periodAcademico p " +
           "WHERE s.fechaRegistro >= :desde AND s.fechaRegistro <= :hasta " +
           "GROUP BY p.codigo ORDER BY p.codigo")
    /**
     * Count por period.
     * @param desde desde
     * @param hasta hasta
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<Object[]> countPorPeriod(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    /** Estadísticas por program: total, completadas y rechazadas. */
    @Query("SELECT s.student.program, COUNT(s), " +
           "SUM(CASE WHEN s.estado.codigo = 'COMPLETADA' THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN s.estado.codigo = 'RECHAZADA' THEN 1 ELSE 0 END) " +
           "FROM Submission s GROUP BY s.student.program ORDER BY COUNT(s) DESC")
    /**
     * Estadisticas por program.
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<Object[]> estadisticasPorProgram();

    /**
     * Cuenta los registros con fecha registro between.
     * @param desde desde
     * @param hasta hasta
     * @return la cantidad de registros
     */
    long countByFechaRegistroBetween(LocalDateTime desde, LocalDateTime hasta);

    @Query(value = "SELECT * FROM presus.sp_generar_reporte_defensas(:program)", nativeQuery = true)
    /**
     * Generate reporte defensas sp.
     * @param program program
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<Object[]> generateReporteDefensasSp(@Param("program") String program);
}
