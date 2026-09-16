package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.TopicGuardadoStudent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TopicGuardadoStudentRepository extends JpaRepository<TopicGuardadoStudent, Integer> {

    @Query("""
            SELECT g FROM TopicGuardadoStudent g
            LEFT JOIN FETCH g.topicPropuesto t
            LEFT JOIN FETCH t.program
            LEFT JOIN FETCH t.lineInvestigacion
            LEFT JOIN FETCH t.area
            WHERE g.student.id = :studentId
            ORDER BY g.fechaGuardado DESC
            """)
    List<TopicGuardadoStudent> findByStudentIdOrderByFechaGuardadoDesc(@Param("studentId") Long studentId);

    List<TopicGuardadoStudent> findByStudentId(Long studentId);

    boolean existsByStudentIdAndTopicPropuestoId(Long studentId, Integer topicPropuestoId);

    @Modifying
    int deleteByStudentIdAndTopicPropuestoId(Long studentId, Integer topicPropuestoId);

    @Query("SELECT g.topicPropuesto.id FROM TopicGuardadoStudent g WHERE g.student.id = :studentId")
    List<Integer> findTopicIdsByStudentId(@Param("studentId") Long studentId);
}
