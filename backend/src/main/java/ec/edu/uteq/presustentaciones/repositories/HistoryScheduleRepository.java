package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.HistorySchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HistoryScheduleRepository extends JpaRepository<HistorySchedule, Long> {
    List<HistorySchedule> findByScheduleIdOrderByFechaCambioDesc(Long scheduleId);
}
