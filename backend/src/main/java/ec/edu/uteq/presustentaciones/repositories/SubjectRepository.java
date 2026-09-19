package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Contrato. Repositorio de acceso a datos de subject.
 */
@Repository
public interface SubjectRepository extends JpaRepository<Subject, Integer> {
    /**
     * Busca el/los registro(s) con line investigacion id.
     * @param lineId lineId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<Subject> findByResearchLineId(Integer lineId);
}
