package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.AreaTematica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AreaTematicaRepository extends JpaRepository<AreaTematica, Integer> {
    /**
     * Busca el/los registro(s) con line investigacion id.
     * @param lineId lineId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<AreaTematica> findByLineInvestigacionId(Integer lineId);
}
