package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.ProgressStudent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProgressStudentRepository extends JpaRepository<ProgressStudent, Integer> {

    /**
     * Busca el/los registro(s) con student id.
     * @param studentId studentId
     * @return el registro si existe, vacío si no
     */
    Optional<ProgressStudent> findByStudentId(Long studentId);
}
