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

@Repository
public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    List<Teacher> findByDisponibleTrue();

    Optional<Teacher> findByAppUserId(Long appUserId);

    /**
     * ERR-02: la tabla teacher tiene 9,807 filas -- /api/teachers (findAll sin Pageable) traía
     * todo de una vez y el <select> con un <option> por fila congelaba el navegador al openlo.
     * Búsqueda de texto libre (nombre/apellido/área de especialidad) + paginado, mismo patrón
     * que AppUserRepository.searchPaginado, para alimentar un combobox con typeahead en vez del
     * <select> nativo.
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
    Page<Teacher> searchPaginado(@Param("q") String q, Pageable pageable);

    @Query("SELECT d FROM Teacher d WHERE d.disponible = true ORDER BY d.cargaHorariaSemanal ASC")
    List<Teacher> findDisponiblesOrdenadosPorCarga();

    @Query("SELECT d FROM Teacher d ORDER BY d.cargaHorariaSemanal ASC")
    List<Teacher> findTodosOrdenadosPorCarga();

    /** Reportes: nombre de un conjunto acotado de teachers (los que participan en el process). */
    @Query("SELECT d.id, u.nombre, u.apellido FROM Teacher d JOIN d.appUser u WHERE d.id IN :ids")
    List<Object[]> findNombresByIds(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);
}
