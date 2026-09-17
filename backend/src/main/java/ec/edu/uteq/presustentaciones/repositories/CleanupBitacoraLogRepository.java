package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.CleanupBitacoraLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CleanupBitacoraLogRepository extends JpaRepository<CleanupBitacoraLog, Long> {
    /**
     * Find top50 by order by fecha ejecucion desc.
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<CleanupBitacoraLog> findTop50ByOrderByFechaEjecucionDesc();
}
