package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.BackupConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Contrato. Repositorio de acceso a datos de backup config.
 */
@Repository
public interface BackupConfigRepository extends JpaRepository<BackupConfig, Short> {
}
