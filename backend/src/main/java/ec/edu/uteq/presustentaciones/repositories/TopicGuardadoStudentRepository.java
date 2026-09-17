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
    /**
     * Busca el/los registro(s) con student id o der by fecha guardado desc.
     * @param studentId studentId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<TopicGuardadoStudent> findByStudentIdOrderByFechaGuardadoDesc(@Param("studentId") Long studentId);

    /**
     * Busca el/los registro(s) con student id.
     * @param studentId studentId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<TopicGuardadoStudent> findByStudentId(Long studentId);

    /**
     * Indica si existe algún registro con student id y topic propuesto id.
     * @param studentId studentId
     * @param topicPropuestoId topicPropuestoId
     * @return true si se cumple la condición, false si no
     */
    boolean existsByStudentIdAndTopicPropuestoId(Long studentId, Integer topicPropuestoId);

    @Modifying
    /**
     * Elimina los registros con student id y topic propuesto id.
     * @param studentId studentId
     * @param topicPropuestoId topicPropuestoId
     * @return la cantidad de registros
     */
    int deleteByStudentIdAndTopicPropuestoId(Long studentId, Integer topicPropuestoId);

    @Query("SELECT g.topicPropuesto.id FROM TopicGuardadoStudent g WHERE g.student.id = :studentId")
    /**
     * Find topic ids by student id.
     * @param studentId studentId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<Integer> findTopicIdsByStudentId(@Param("studentId") Long studentId);
}
