package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.AreaTematica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AreaTematicaRepository extends JpaRepository<AreaTematica, Integer> {
    List<AreaTematica> findByLineInvestigacionId(Integer lineId);
}
