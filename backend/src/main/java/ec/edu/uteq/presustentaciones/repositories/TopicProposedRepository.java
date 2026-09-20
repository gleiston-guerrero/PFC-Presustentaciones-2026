package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.TopicProposed;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Contrato. Repositorio de acceso a datos de topic proposed.
 */
@Repository
public interface TopicProposedRepository extends JpaRepository<TopicProposed, Integer> {

    /**
     * Busca el/los registro(s) con program id.
     * @param programId identificador del programa académico (carrera)
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<TopicProposed> findByProgramId(Integer programId);

    /**
     * Busca el/los registro(s) con program id y line investigacion id.
     * @param programId identificador del programa académico (carrera)
     * @param researchLineId identificador de la línea de investigación
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
     *
     * @param programId identificador del programa académico (carrera)
     * @param lineId identificador de la línea de investigación
     * @param areaId identificador del área temática
     * @param nivelDificultad nivel de dificultad del tema; nulo no filtra
     * @return los resultados encontrados (vacío si no hay coincidencias)
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
    List<TopicProposed> searchWithFiltros(@Param("programId") Integer programId,
                                         @Param("lineId") Integer lineId,
                                         @Param("areaId") Integer areaId,
                                         @Param("nivel") String nivelDificultad);

    /**
     * Busca el/los registro(s) con id con catalogos.
     * @param id identificador del registro
     * @return el {@code java.util.Optional<TopicPropuesto>} correspondiente
     */
    @Query("""
            SELECT t FROM TopicProposed t
            LEFT JOIN FETCH t.program
            LEFT JOIN FETCH t.researchLine
            LEFT JOIN FETCH t.area
            WHERE t.id = :id
            """)
    java.util.Optional<TopicProposed> findByIdWithCatalogs(@Param("id") Integer id);
}
