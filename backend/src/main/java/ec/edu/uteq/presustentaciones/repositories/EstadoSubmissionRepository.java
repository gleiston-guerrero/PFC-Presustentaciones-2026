package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.EstadoSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EstadoSubmissionRepository extends JpaRepository<EstadoSubmission, Short> {
    Optional<EstadoSubmission> findByCodigo(String codigo);
}
