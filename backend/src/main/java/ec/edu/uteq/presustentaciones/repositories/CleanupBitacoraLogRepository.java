package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.CleanupBitacoraLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CleanupBitacoraLogRepository extends JpaRepository<CleanupBitacoraLog, Long> {
    List<CleanupBitacoraLog> findTop50ByOrderByFechaEjecucionDesc();
}
