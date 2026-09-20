package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.ProgressStudent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Contrato. Repositorio de acceso a datos de progress student.
 */
@Repository
public interface ProgressStudentRepository extends JpaRepository<ProgressStudent, Integer> {

    /**
     * Busca el/los registro(s) con student id.
     * @param studentId identificador del estudiante
     * @return el registro si existe, vacío si no
     */
    Optional<ProgressStudent> findByStudentId(Long studentId);
}
