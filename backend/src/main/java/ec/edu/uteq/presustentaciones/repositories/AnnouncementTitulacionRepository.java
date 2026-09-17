package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.AnnouncementTitulacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnnouncementTitulacionRepository extends JpaRepository<AnnouncementTitulacion, Integer> {
    /**
     * Busca el/los registro(s) con codigo.
     * @param codigo codigo
     * @return el registro si existe, vacío si no
     */
    Optional<AnnouncementTitulacion> findByCodigo(String codigo);
    /**
     * Busca el/los registro(s) con period academico id.
     * @param periodId periodId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<AnnouncementTitulacion> findByPeriodAcademicoId(Integer periodId);
    /** Devuelve la announcement activa (activa = true) */
    Optional<AnnouncementTitulacion> findFirstByActivaTrue();
    /**
     * Busca el/los registro(s) con activa true.
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<AnnouncementTitulacion> findByActivaTrue();
}
