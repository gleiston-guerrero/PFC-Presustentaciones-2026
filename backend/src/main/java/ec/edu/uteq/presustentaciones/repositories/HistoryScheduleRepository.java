package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.HistorySchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Contrato. Repositorio de acceso a datos de history schedule.
 */
@Repository
public interface HistoryScheduleRepository extends JpaRepository<HistorySchedule, Long> {
    /**
     * Busca el/los registro(s) con schedule id o der by fecha cambio desc.
     * @param scheduleId identificador de la programación
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<HistorySchedule> findByScheduleIdOrderByDateCambioDesc(Long scheduleId);
}
