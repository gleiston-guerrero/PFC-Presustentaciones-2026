package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.StatusSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Contrato. Repositorio de acceso a datos de status schedule.
 */
@Repository
public interface StatusScheduleRepository extends JpaRepository<StatusSchedule, Short> {
    /**
     * Busca el/los registro(s) con codigo.
     * @param code code
     * @return el registro si existe, vacío si no
     */
    Optional<StatusSchedule> findByCode(String code);
}
