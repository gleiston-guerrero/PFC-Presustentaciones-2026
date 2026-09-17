package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.AvailabilityRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AvailabilityRoomRepository extends JpaRepository<AvailabilityRoom, Long> {
    /**
     * Busca el/los registro(s) con room id y fecha.
     * @param roomId roomId
     * @param fecha fecha
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<AvailabilityRoom> findByRoomIdAndFecha(Long roomId, LocalDate fecha);
    /**
     * Busca el/los registro(s) con fecha.
     * @param fecha fecha
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<AvailabilityRoom> findByFecha(LocalDate fecha);
}
