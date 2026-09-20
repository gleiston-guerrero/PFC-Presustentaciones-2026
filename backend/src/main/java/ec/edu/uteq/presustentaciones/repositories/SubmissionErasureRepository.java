package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.SubmissionErasure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Contrato. Repositorio de acceso a datos de submission erasure.
 */
@Repository
public interface SubmissionErasureRepository extends JpaRepository<SubmissionErasure, Long> {
    /**
     * Busca el/los registro(s) con app user id.
     * @param appUserId identificador del usuario del sistema
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<SubmissionErasure> findByAppUserId(Long appUserId);
    /**
     * Devuelve todos los registros con o der by fecha submission desc.
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<SubmissionErasure> findAllByOrderByDateSubmissionDesc();
    /**
     * Indica si existe algún registro con app user id y estado.
     * @param appUserId identificador del usuario del sistema
     * @param status estado del registro de la solicitud eliminada
     * @return true si se cumple la condición, false si no
     */
    boolean existsByAppUserIdAndStatus(Long appUserId, String status);
}
