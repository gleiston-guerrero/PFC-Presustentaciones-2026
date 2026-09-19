package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.ResourceDegree;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Contrato. Repositorio de acceso a datos de resource degree.
 */
@Repository
public interface ResourceDegreeRepository extends JpaRepository<ResourceDegree, Integer> {

    /**
     * List visibles para program.
     * @param programId programId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    @Query("""
            SELECT r FROM ResourceDegree r
            LEFT JOIN FETCH r.program c
            WHERE :programId IS NULL OR c IS NULL OR c.id = :programId
            ORDER BY r.categoria ASC, r.titulo ASC
            """)
    List<ResourceDegree> listVisiblesForProgram(@Param("programId") Integer programId);

    /**
     * List todos.
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    @Query("SELECT r FROM ResourceDegree r LEFT JOIN FETCH r.program ORDER BY r.categoria ASC, r.titulo ASC")
    List<ResourceDegree> listAll();
}
