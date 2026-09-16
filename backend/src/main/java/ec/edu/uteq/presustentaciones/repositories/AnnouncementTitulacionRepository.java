package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.AnnouncementTitulacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnnouncementTitulacionRepository extends JpaRepository<AnnouncementTitulacion, Integer> {
    Optional<AnnouncementTitulacion> findByCodigo(String codigo);
    List<AnnouncementTitulacion> findByPeriodAcademicoId(Integer periodId);
    /** Devuelve la announcement activa (activa = true) */
    Optional<AnnouncementTitulacion> findFirstByActivaTrue();
    List<AnnouncementTitulacion> findByActivaTrue();
}
