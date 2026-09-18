package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.SubmissionErasure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubmissionErasureRepository extends JpaRepository<SubmissionErasure, Long> {
    /**
     * Busca el/los registro(s) con app user id.
     * @param appUserId appUserId
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
     * @param appUserId appUserId
     * @param status status
     * @return true si se cumple la condición, false si no
     */
    boolean existsByAppUserIdAndStatus(Long appUserId, String status);
}
