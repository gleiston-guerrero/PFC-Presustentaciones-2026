package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.Program;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Contrato. Repositorio de acceso a datos de program.
 */
@Repository
public interface ProgramRepository extends JpaRepository<Program, Integer> {
    /**
     * Busca el/los registro(s) con codigo.
     * @param code code
     * @return el registro si existe, vacío si no
     */
    Optional<Program> findByCode(String code);
    /**
     * Busca el/los registro(s) con faculty id.
     * @param facultyId facultyId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<Program> findByFacultyId(Integer facultyId);
}
