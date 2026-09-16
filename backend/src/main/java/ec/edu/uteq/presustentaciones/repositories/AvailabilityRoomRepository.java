package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.AvailabilityRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AvailabilityRoomRepository extends JpaRepository<AvailabilityRoom, Long> {
    List<AvailabilityRoom> findByRoomIdAndFecha(Long roomId, LocalDate fecha);
    List<AvailabilityRoom> findByFecha(LocalDate fecha);
}
