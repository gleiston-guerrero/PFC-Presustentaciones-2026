package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Contrato. Repositorio de acceso a datos de room.
 */
@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {
}
