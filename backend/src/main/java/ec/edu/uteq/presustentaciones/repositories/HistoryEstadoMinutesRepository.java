package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.HistoryEstadoMinutes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HistoryEstadoMinutesRepository extends JpaRepository<HistoryEstadoMinutes, Long> {

    @Query("SELECT h FROM HistoryEstadoMinutes h " +
           "LEFT JOIN FETCH h.appUser u " +
           "LEFT JOIN FETCH h.estadoAnterior ea " +
           "JOIN FETCH h.estadoNuevo en " +
           "WHERE h.minutes.id = :minutesId ORDER BY h.fechaCambio DESC, h.id DESC")
    /**
     * Busca el/los registro(s) con minutes id o der by fecha cambio desc.
     * @param minutesId minutesId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<HistoryEstadoMinutes> findByMinutesIdOrderByFechaCambioDesc(@Param("minutesId") Long minutesId);

    /**
     * Cuenta los registros con minutes id.
     * @param minutesId minutesId
     * @return la cantidad de registros
     */
    long countByMinutesId(Long minutesId);
}
