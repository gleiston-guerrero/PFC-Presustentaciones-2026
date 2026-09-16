package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.EstadoProcess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EstadoProcessRepository extends JpaRepository<EstadoProcess, Short> {
    Optional<EstadoProcess> findByCodigo(String codigo);
}
