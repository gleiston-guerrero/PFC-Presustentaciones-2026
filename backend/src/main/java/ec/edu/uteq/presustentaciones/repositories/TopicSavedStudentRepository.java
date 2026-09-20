package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.TopicSavedStudent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Contrato. Repositorio de acceso a datos de topic saved student.
 */
@Repository
public interface TopicSavedStudentRepository extends JpaRepository<TopicSavedStudent, Integer> {

    /**
     * Busca el/los registro(s) con student id o der by fecha guardado desc.
     * @param studentId identificador del estudiante
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    @Query("""
            SELECT g FROM TopicSavedStudent g
            LEFT JOIN FETCH g.topicProposed t
            LEFT JOIN FETCH t.program
            LEFT JOIN FETCH t.researchLine
            LEFT JOIN FETCH t.area
            WHERE g.student.id = :studentId
            ORDER BY g.dateSaved DESC
            """)
    List<TopicSavedStudent> findByStudentIdOrderByDateSavedDesc(@Param("studentId") Long studentId);

    /**
     * Busca el/los registro(s) con student id.
     * @param studentId identificador del estudiante
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<TopicSavedStudent> findByStudentId(Long studentId);

    /**
     * Indica si existe algún registro con student id y topic propuesto id.
     * @param studentId identificador del estudiante
     * @param topicProposedId identificador del tema propuesto
     * @return true si se cumple la condición, false si no
     */
    boolean existsByStudentIdAndTopicProposedId(Long studentId, Integer topicProposedId);

    /**
     * Elimina los registros con student id y topic propuesto id.
     * @param studentId identificador del estudiante
     * @param topicProposedId identificador del tema propuesto
     * @return la cantidad de registros
     */
    @Modifying
    int deleteByStudentIdAndTopicProposedId(Long studentId, Integer topicProposedId);

    /**
     * Find topic ids by student id.
     * @param studentId identificador del estudiante
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    @Query("SELECT g.topicProposed.id FROM TopicSavedStudent g WHERE g.student.id = :studentId")
    List<Integer> findTopicIdsByStudentId(@Param("studentId") Long studentId);
}
