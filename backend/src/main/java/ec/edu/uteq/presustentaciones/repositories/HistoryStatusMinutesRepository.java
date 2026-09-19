package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.HistoryStatusMinutes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Contrato. Repositorio de acceso a datos de history status minutes.
 */
@Repository
public interface HistoryStatusMinutesRepository extends JpaRepository<HistoryStatusMinutes, Long> {

    /**
     * Busca el/los registro(s) con minutes id o der by fecha cambio desc.
     * @param minutesId minutesId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    @Query("SELECT h FROM HistoryStatusMinutes h " +
           "LEFT JOIN FETCH h.appUser u " +
           "LEFT JOIN FETCH h.statusAnterior ea " +
           "JOIN FETCH h.statusNew en " +
           "WHERE h.minutes.id = :minutesId ORDER BY h.dateCambio DESC, h.id DESC")
    List<HistoryStatusMinutes> findByMinutesIdOrderByDateCambioDesc(@Param("minutesId") Long minutesId);

    /**
     * Cuenta los registros con minutes id.
     * @param minutesId minutesId
     * @return la cantidad de registros
     */
    long countByMinutesId(Long minutesId);
}
