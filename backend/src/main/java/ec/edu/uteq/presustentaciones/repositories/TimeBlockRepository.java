package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.TimeBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TimeBlockRepository extends JpaRepository<TimeBlock, Short> {
    /**
     * Busca el/los registro(s) con shift id.
     * @param shiftId shiftId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<TimeBlock> findByShiftId(Short shiftId);
    /**
     * Busca el/los registro(s) con shift codigo.
     * @param shiftCode shiftCode
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<TimeBlock> findByShiftCode(String shiftCode);
}
