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

    @Query("SELECT c FROM Schedule c JOIN c.submission s JOIN s.student e WHERE e.id = :studentId")
    List<Schedule> findByStudentId(@Param("studentId") Long studentId);

    @Query("SELECT c FROM Schedule c JOIN c.submission s JOIN s.student e JOIN e.appUser u WHERE u.id = :appUserId")
    List<Schedule> findByAppUserId(@Param("appUserId") Long appUserId);

    Optional<Schedule> findBySubmissionId(Long submissionId);

    /** RF-04: Conflictos en room: cualquier schedule que se solape con la franja propuesta */
    @Query("""
        SELECT c FROM Schedule c
        WHERE c.room.id = :roomId
          AND c.estado.codigo = 'PROGRAMADO'
          AND c.fechaInicio < :fin
          AND FUNCTION('TIMESTAMPADD', MINUTE, c.duracionMin, c.fechaInicio) > :inicio
    """)
    List<Schedule> findConflictos(@Param("roomId") Long roomId,
                                    @Param("inicio") LocalDateTime inicio,
                                    @Param("fin") LocalDateTime fin);

    /** Todos los schedules activos de una fecha */
    @Query("SELECT c FROM Schedule c WHERE c.estado.codigo = 'PROGRAMADO' AND CAST(c.fechaInicio AS date) = CAST(:fecha AS date)")
    List<Schedule> findActivosPorFecha(@Param("fecha") LocalDateTime fecha);

    @Query("SELECT c FROM Schedule c " +
           "JOIN FETCH c.submission s " +
           "JOIN FETCH s.student e " +
           "JOIN FETCH e.appUser u " +
           "JOIN FETCH c.room sa " +
           "JOIN FETCH c.estado es " +
           "JOIN FETCH c.announcement co " +
           "LEFT JOIN FETCH c.block b " +
           "WHERE es.codigo = 'PROGRAMADO' " +
           "ORDER BY c.fechaInicio ASC")
    List<Schedule> findReporteSchedule();
}
