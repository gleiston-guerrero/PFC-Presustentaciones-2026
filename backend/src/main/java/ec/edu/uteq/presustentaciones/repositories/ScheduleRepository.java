package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    /**
     * Busca el/los registro(s) con student id.
     * @param studentId studentId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    @Query("SELECT c FROM Schedule c JOIN c.submission s JOIN s.student e WHERE e.id = :studentId")
    List<Schedule> findByStudentId(@Param("studentId") Long studentId);

    /**
     * Busca el/los registro(s) con app user id.
     * @param appUserId appUserId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    @Query("SELECT c FROM Schedule c JOIN c.submission s JOIN s.student e JOIN e.appUser u WHERE u.id = :appUserId")
    List<Schedule> findByAppUserId(@Param("appUserId") Long appUserId);

    /**
     * Busca el/los registro(s) con submission id.
     * @param submissionId submissionId
     * @return el registro si existe, vacío si no
     */
    Optional<Schedule> findBySubmissionId(Long submissionId);

    /** RF-04: Conflictos en room: cualquier schedule que se solape con la franja propuesta */
    @Query("""
        SELECT c FROM Schedule c
        WHERE c.room.id = :roomId
          AND c.status.code = 'PROGRAMADO'
          AND c.dateStart < :fin
          /**
           * F u n c t i o n.
           * @param 'TIMESTAMPADD' 'TIMESTAMPADD'
           * @param MINUTE MINUTE
           * @param c.duracionMin c.duracionMin
           * @param end end
           * @return el AND correspondiente
           */
          AND FUNCTION('TIMESTAMPADD', MINUTE, c.duracionMin, c.dateStart) > :inicio
    """)
    List<Schedule> findConflictos(@Param("roomId") Long roomId,
                                    @Param("inicio") LocalDateTime start,
                                    @Param("fin") LocalDateTime end);

    /** Todos los schedules activos de una fecha */
    @Query("SELECT c FROM Schedule c WHERE c.status.code = 'PROGRAMADO' AND CAST(c.dateStart AS date) = CAST(:fecha AS date)")
    List<Schedule> findActiveByDate(@Param("fecha") LocalDateTime date);

    /**
     * Find reporte schedule.
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    @Query("SELECT c FROM Schedule c " +
           "JOIN FETCH c.submission s " +
           "JOIN FETCH s.student e " +
           "JOIN FETCH e.appUser u " +
           "JOIN FETCH c.room sa " +
           "JOIN FETCH c.status es " +
           "JOIN FETCH c.announcement co " +
           "LEFT JOIN FETCH c.block b " +
           "WHERE es.code = 'PROGRAMADO' " +
           "ORDER BY c.dateStart ASC")
    List<Schedule> findReportSchedule();
}
