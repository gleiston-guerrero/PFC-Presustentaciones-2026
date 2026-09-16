package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.ProgressStudent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProgressStudentRepository extends JpaRepository<ProgressStudent, Integer> {

    Optional<ProgressStudent> findByStudentId(Long studentId);
}
