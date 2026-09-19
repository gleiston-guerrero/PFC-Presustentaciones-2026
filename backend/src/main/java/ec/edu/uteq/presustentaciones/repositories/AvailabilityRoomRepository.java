package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.AvailabilityRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Contrato. Repositorio de acceso a datos de availability room.
 */
@Repository
public interface AvailabilityRoomRepository extends JpaRepository<AvailabilityRoom, Long> {
    /**
     * Busca el/los registro(s) con room id y fecha.
     * @param roomId roomId
     * @param date date
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<AvailabilityRoom> findByRoomIdAndDate(Long roomId, LocalDate date);
    /**
     * Busca el/los registro(s) con fecha.
     * @param date date
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<AvailabilityRoom> findByDate(LocalDate date);
}
