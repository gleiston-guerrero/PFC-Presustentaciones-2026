package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.BackupPruebaRestauracion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BackupPruebaRestauracionRepository extends JpaRepository<BackupPruebaRestauracion, Long> {

    /**
     * Find top50 by order by fecha desc.
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<BackupPruebaRestauracion> findTop50ByOrderByFechaDesc();

    /**
     * Devuelve el primer registro con o der by fecha desc.
     * @return el BackupPruebaRestauracion correspondiente
     */
    BackupPruebaRestauracion findFirstByOrderByFechaDesc();
}
