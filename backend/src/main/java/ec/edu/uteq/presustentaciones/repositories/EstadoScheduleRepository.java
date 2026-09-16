package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.EstadoSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EstadoScheduleRepository extends JpaRepository<EstadoSchedule, Short> {
    Optional<EstadoSchedule> findByCodigo(String codigo);
}
