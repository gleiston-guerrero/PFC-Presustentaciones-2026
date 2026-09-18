package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.ResourceDegree;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResourceDegreeRepository extends JpaRepository<ResourceDegree, Integer> {

    @Query("""
            SELECT r FROM ResourceDegree r
            LEFT JOIN FETCH r.program c
            WHERE :programId IS NULL OR c IS NULL OR c.id = :programId
            ORDER BY r.categoria ASC, r.titulo ASC
            """)
    /**
     * List visibles para program.
     * @param programId programId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<ResourceDegree> listVisiblesForProgram(@Param("programId") Integer programId);

    @Query("SELECT r FROM ResourceDegree r LEFT JOIN FETCH r.program ORDER BY r.categoria ASC, r.titulo ASC")
    /**
     * List todos.
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<ResourceDegree> listAll();
}
