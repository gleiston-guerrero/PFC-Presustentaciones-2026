package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.CleanupLogLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Contrato. Repositorio de acceso a datos de cleanup log log.
 */
@Repository
public interface CleanupLogLogRepository extends JpaRepository<CleanupLogLog, Long> {
    /**
     * Find top50 by order by fecha ejecucion desc.
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<CleanupLogLog> findTop50ByOrderByDateEjecucionDesc();
}
