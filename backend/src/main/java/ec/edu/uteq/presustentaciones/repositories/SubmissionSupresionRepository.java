package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.SubmissionSupresion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubmissionSupresionRepository extends JpaRepository<SubmissionSupresion, Long> {
    List<SubmissionSupresion> findByAppUserId(Long appUserId);
    List<SubmissionSupresion> findAllByOrderByFechaSubmissionDesc();
    boolean existsByAppUserIdAndEstado(Long appUserId, String estado);
}
