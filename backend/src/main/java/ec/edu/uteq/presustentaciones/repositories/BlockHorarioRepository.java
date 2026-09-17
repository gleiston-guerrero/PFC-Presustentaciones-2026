package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.BlockHorario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BlockHorarioRepository extends JpaRepository<BlockHorario, Short> {
    /**
     * Busca el/los registro(s) con shift id.
     * @param shiftId shiftId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<BlockHorario> findByShiftId(Short shiftId);
    /**
     * Busca el/los registro(s) con shift codigo.
     * @param shiftCodigo shiftCodigo
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<BlockHorario> findByShiftCodigo(String shiftCodigo);
}
