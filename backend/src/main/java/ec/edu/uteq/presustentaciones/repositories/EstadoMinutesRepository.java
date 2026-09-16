package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.EstadoMinutes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EstadoMinutesRepository extends JpaRepository<EstadoMinutes, Short> {
    Optional<EstadoMinutes> findByCodigo(String codigo);
}
