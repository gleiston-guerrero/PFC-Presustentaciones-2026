package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.AnnouncementDegree;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Contrato. Repositorio de acceso a datos de announcement degree.
 */
@Repository
public interface AnnouncementDegreeRepository extends JpaRepository<AnnouncementDegree, Integer> {
    /**
     * Busca el/los registro(s) con codigo.
     * @param code code
     * @return el registro si existe, vacío si no
     */
    Optional<AnnouncementDegree> findByCode(String code);
    /**
     * Busca el/los registro(s) con period academico id.
     * @param periodId periodId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<AnnouncementDegree> findByPeriodAcademicId(Integer periodId);
    /**
     * Devuelve la announcement activa (activa = true)
     * @return el registro si existe, vacío si no
     */
    Optional<AnnouncementDegree> findFirstByActiveTrue();
    /**
     * Busca el/los registro(s) con activa true.
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<AnnouncementDegree> findByActiveTrue();
}
