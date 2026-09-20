package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.Teacher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Contrato. Repositorio de acceso a datos de teacher.
 */
@Repository
public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    /**
     * Busca el/los registro(s) con disponible true.
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<Teacher> findByAvailableTrue();

    /**
     * Busca el/los registro(s) con app user id.
     * @param appUserId identificador del usuario del sistema
     * @return el registro si existe, vacío si no
     */
    Optional<Teacher> findByAppUserId(Long appUserId);

    /**
     * ERR-02: la tabla teacher tiene 9,807 filas -- /api/teachers (findAll sin Pageable) traía
     * todo de una vez y el {@code <select>} con un {@code <option>} por fila congelaba el
     * navegador al abrirlo. Búsqueda de texto libre (nombre/apellido/área de especialidad) +
     * paginado, mismo patrón que AppUserRepository.searchPaginado, para alimentar un combobox
     * con typeahead en vez del {@code <select>} nativo.
     * @param q texto que se busca en el nombre, el apellido y el correo del docente
     * @param pageable página, tamaño y ordenamiento solicitados
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    @Query(value = "SELECT d FROM Teacher d JOIN FETCH d.appUser u " +
           "WHERE :q IS NULL OR :q = '' " +
           "OR LOWER(u.nombre) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(u.apellido) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(d.areaEspecialidad) LIKE LOWER(CONCAT('%', :q, '%'))",
           countQuery = "SELECT count(d) FROM Teacher d JOIN d.appUser u " +
           "WHERE :q IS NULL OR :q = '' " +
           "OR LOWER(u.nombre) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(u.apellido) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(d.areaEspecialidad) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<Teacher> searchPaged(@Param("q") String q, Pageable pageable);

    /**
     * Find disponibles ordenados por carga.
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    @Query("SELECT d FROM Teacher d WHERE d.available = true ORDER BY d.cargaHorariaSemanal ASC")
    List<Teacher> findAvailableOrdenadosByCarga();

    /**
     * Find todos ordenados por carga.
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    @Query("SELECT d FROM Teacher d ORDER BY d.cargaHorariaSemanal ASC")
    List<Teacher> findAllOrdenadosByCarga();

    /**
     * Reportes: nombre de un conjunto acotado de teachers (los que participan en el process).
     * @param ids identificadores de los docentes cuyo nombre se consulta
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    @Query("SELECT d.id, u.nombre, u.apellido FROM Teacher d JOIN d.appUser u WHERE d.id IN :ids")
    List<Object[]> findNombresByIds(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);
}
