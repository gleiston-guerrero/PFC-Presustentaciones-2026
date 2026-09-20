package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.Faculty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Contrato. Repositorio de acceso a datos de faculty.
 */
@Repository
public interface FacultyRepository extends JpaRepository<Faculty, Integer> {
    /**
     * Busca el/los registro(s) con codigo.
     * @param code código de negocio único del registro buscado
     * @return el registro si existe, vacío si no
     */
    Optional<Faculty> findByCode(String code);
}
