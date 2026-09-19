package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.BackupDrillRestore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Contrato. Repositorio de acceso a datos de backup drill restore.
 */
@Repository
public interface BackupDrillRestoreRepository extends JpaRepository<BackupDrillRestore, Long> {

    /**
     * Find top50 by order by fecha desc.
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<BackupDrillRestore> findTop50ByOrderByDateDesc();

    /**
     * Devuelve el primer registro con o der by fecha desc.
     * @return el BackupPruebaRestauracion correspondiente
     */
    BackupDrillRestore findFirstByOrderByDateDesc();
}
