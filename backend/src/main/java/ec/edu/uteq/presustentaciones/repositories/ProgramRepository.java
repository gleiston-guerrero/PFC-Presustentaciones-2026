package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.Program;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProgramRepository extends JpaRepository<Program, Integer> {
    /**
     * Busca el/los registro(s) con codigo.
     * @param codigo codigo
     * @return el registro si existe, vacío si no
     */
    Optional<Program> findByCodigo(String codigo);
    /**
     * Busca el/los registro(s) con faculty id.
     * @param facultyId facultyId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<Program> findByFacultyId(Integer facultyId);
}
