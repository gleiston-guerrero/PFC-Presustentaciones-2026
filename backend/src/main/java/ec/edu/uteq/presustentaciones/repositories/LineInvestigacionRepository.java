package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.LineInvestigacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LineInvestigacionRepository extends JpaRepository<LineInvestigacion, Integer> {
    Optional<LineInvestigacion> findByCodigo(String codigo);
    List<LineInvestigacion> findByFacultyId(Integer facultyId);
}
