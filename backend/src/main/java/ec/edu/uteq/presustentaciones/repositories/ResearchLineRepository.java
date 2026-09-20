package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.ResearchLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Contrato. Repositorio de acceso a datos de research line.
 */
@Repository
public interface ResearchLineRepository extends JpaRepository<ResearchLine, Integer> {
    /**
     * Busca el/los registro(s) con codigo.
     * @param code código de negocio único del registro buscado
     * @return el registro si existe, vacío si no
     */
    Optional<ResearchLine> findByCode(String code);
    /**
     * Busca el/los registro(s) con faculty id.
     * @param facultyId identificador de la facultad
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<ResearchLine> findByFacultyId(Integer facultyId);
}
