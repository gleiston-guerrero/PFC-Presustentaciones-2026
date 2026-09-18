package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.Audit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AuditRepository extends JpaRepository<Audit, Long> {

    /**
     * RNF-19: depuración automática de la bitácora (nunca vía API -- ver RNF-18). Bulk delete
     * de JPA: no dispara ningún trigger ni evento de aplicación, es un {@code DELETE} directo,
     * y devuelve cuántas filas borró para que {@code CleanupLogScheduler} pueda dejar
     * traza exacta.
     */
    @Modifying
    @Query("DELETE FROM Audit a WHERE a.date < :fechaCorte")
    int eraseAnterioresA(@Param("fechaCorte") LocalDateTime dateCorte);

    @Query("SELECT a FROM Audit a WHERE " +
           "(:tabla IS NULL OR :tabla = '' OR a.tabla = :tabla) " +
           "AND (:accion IS NULL OR :accion = '' OR a.accion = :accion) " +
           "AND (:appUserId IS NULL OR a.appUserId = :appUserId) " +
           "AND (:texto IS NULL OR :texto = '' " +
           "     OR LOWER(a.appUserNombre) LIKE LOWER(CONCAT('%', :texto, '%')) " +
           "     OR LOWER(a.tabla) LIKE LOWER(CONCAT('%', :texto, '%')))")
    /**
     * Search con filtros.
     * @param tabla tabla
     * @param accion accion
     * @param appUserId appUserId
     * @param texto texto
     * @param pageable pageable
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    Page<Audit> searchWithFiltros(@Param("tabla") String tabla,
                                      @Param("accion") String accion,
                                      @Param("appUserId") Long appUserId,
                                      @Param("texto") String texto,
                                      Pageable pageable);
}
