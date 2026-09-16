package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.ModalityTitulacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ModalityTitulacionRepository extends JpaRepository<ModalityTitulacion, Short> {
    Optional<ModalityTitulacion> findByCodigo(String codigo);
}
