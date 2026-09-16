package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.BackupPruebaRestauracion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BackupPruebaRestauracionRepository extends JpaRepository<BackupPruebaRestauracion, Long> {

    List<BackupPruebaRestauracion> findTop50ByOrderByFechaDesc();

    BackupPruebaRestauracion findFirstByOrderByFechaDesc();
}
