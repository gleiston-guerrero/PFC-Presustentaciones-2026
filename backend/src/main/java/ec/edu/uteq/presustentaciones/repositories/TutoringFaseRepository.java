package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.TutoringFase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TutoringFaseRepository extends JpaRepository<TutoringFase, Long> {

    @org.springframework.data.jpa.repository.Query("SELECT f FROM TutoringFase f JOIN FETCH f.tutor t JOIN FETCH t.submission s JOIN FETCH s.student e JOIN FETCH e.appUser u WHERE f.tutor.id = :tutorId ORDER BY f.numeroFase ASC")
    /**
     * Busca el/los registro(s) con tutor id o der by numero fase asc.
     * @param tutorId tutorId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<TutoringFase> findByTutorIdOrderByNumeroFaseAsc(@Param("tutorId") Long tutorId);

    /**
     * Busca el/los registro(s) con tutor id y numero fase.
     * @param tutorId tutorId
     * @param numeroFase numeroFase
     * @return el registro si existe, vacío si no
     */
    Optional<TutoringFase> findByTutorIdAndNumeroFase(Long tutorId, Integer numeroFase);

    /**
     * Cuenta los registros con tutor id y estado.
     * @param tutorId tutorId
     * @param estado estado
     * @return la cantidad de registros
     */
    long countByTutorIdAndEstado(Long tutorId, String estado);

    /**
     * Cuenta los registros con tutor id.
     * @param tutorId tutorId
     * @return la cantidad de registros
     */
    long countByTutorId(Long tutorId);

    @org.springframework.data.jpa.repository.query.Procedure(procedureName = "presus.sp_registrar_tutoria_avance")
    /**
     * Sp register tutoring avance.
     * @param tutorId tutorId
     * @param numeroFase numeroFase
     * @param archivoPdf archivoPdf
     * @param tamanoBytes tamanoBytes
     * @param sha256 sha256
     */
    void spRegisterTutoringAvance(
            @Param("p_tutor_id") Long tutorId,
            @Param("p_numero_fase") Integer numeroFase,
            @Param("p_archivo_pdf") String archivoPdf,
            @Param("p_tamano_bytes") Long tamanoBytes,
            @Param("p_sha256") String sha256
    );
}
