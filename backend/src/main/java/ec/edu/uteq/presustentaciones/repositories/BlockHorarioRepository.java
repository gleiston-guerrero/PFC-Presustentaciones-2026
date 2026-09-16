package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.BlockHorario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BlockHorarioRepository extends JpaRepository<BlockHorario, Short> {
    List<BlockHorario> findByShiftId(Short shiftId);
    List<BlockHorario> findByShiftCodigo(String shiftCodigo);
}
