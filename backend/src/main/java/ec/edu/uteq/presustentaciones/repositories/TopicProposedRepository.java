package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.TopicProposed;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TopicProposedRepository extends JpaRepository<TopicProposed, Integer> {

    /**
     * Busca el/los registro(s) con program id.
     * @param programId programId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<TopicProposed> findByProgramId(Integer programId);

    /**
     * Busca el/los registro(s) con program id y line investigacion id.
     * @param programId programId
     * @param researchLineId researchLineId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<TopicProposed> findByProgramIdAndResearchLineId(Integer programId, Integer researchLineId);

    /**
     * Búsqueda flexible para el explorador de topics propuestos: cada filtro es opcional
     * (null = no filtra). Se hace un solo query con LEFT JOIN FETCH de los catálogos para
     * poblar los nombres en el DTO sin incurrir en N+1.
     *
     * El CAST(:nivel AS string) es necesario: sin él, cuando nivel es null el driver de
     * Postgres no tiene pista de tipo para el parámetro dentro de LOWER(?) y lo envía como
     * bytea sin tipo, y "function lower(bytea) does not exist" -- hallazgo real al probar
     * el endpoint contra Postgres de verdad (los tests con repositorio mockeado nunca
     * ejecutan el SQL real y no lo detectan).
     */
    @Query("""
            SELECT t FROM TopicProposed t
            LEFT JOIN FETCH t.program c
            LEFT JOIN FETCH t.researchLine l
            LEFT JOIN FETCH t.area a
            WHERE (:programId IS NULL OR c.id = :programId)
              AND (:lineId IS NULL OR l.id = :lineId)
              AND (:areaId IS NULL OR a.id = :areaId)
              AND (:nivel IS NULL OR LOWER(t.nivelDificultad) = LOWER(CAST(:nivel AS string)))
            ORDER BY t.titulo ASC
            """)
    /**
     * Search con filtros.
     * @param programId programId
     * @param lineId lineId
     * @param areaId areaId
     * @param nivelDificultad nivelDificultad
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<TopicProposed> searchWithFiltros(@Param("programId") Integer programId,
                                         @Param("lineId") Integer lineId,
                                         @Param("areaId") Integer areaId,
                                         @Param("nivel") String nivelDificultad);

    @Query("""
            SELECT t FROM TopicProposed t
            LEFT JOIN FETCH t.program
            LEFT JOIN FETCH t.researchLine
            LEFT JOIN FETCH t.area
            WHERE t.id = :id
            """)
    /**
     * Busca el/los registro(s) con id con catalogos.
     * @param id id
     * @return el java.util.Optional<TopicPropuesto> correspondiente
     */
    java.util.Optional<TopicProposed> findByIdWithCatalogs(@Param("id") Integer id);
}
