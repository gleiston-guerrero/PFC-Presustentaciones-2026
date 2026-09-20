package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.TutoringPhase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Contrato. Repositorio de acceso a datos de tutoring phase.
 */
@Repository
public interface TutoringPhaseRepository extends JpaRepository<TutoringPhase, Long> {

    /**
     * Busca el/los registro(s) con tutor id o der by numero fase asc.
     * @param tutorId identificador del tutor
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    @org.springframework.data.jpa.repository.Query("SELECT f FROM TutoringPhase f JOIN FETCH f.tutor t JOIN FETCH t.submission s JOIN FETCH s.student e JOIN FETCH e.appUser u WHERE f.tutor.id = :tutorId ORDER BY f.numeroPhase ASC")
    List<TutoringPhase> findByTutorIdOrderByNumeroPhaseAsc(@Param("tutorId") Long tutorId);

    /**
     * Busca el/los registro(s) con tutor id y numero fase.
     * @param tutorId identificador del tutor
     * @param numeroPhase número de la fase de tutoría, empezando en 1
     * @return el registro si existe, vacío si no
     */
    Optional<TutoringPhase> findByTutorIdAndNumeroPhase(Long tutorId, Integer numeroPhase);

    /**
     * Cuenta los registros con tutor id y estado.
     * @param tutorId identificador del tutor
     * @param status estado de la fase de tutoría por el que se cuenta
     * @return la cantidad de registros
     */
    long countByTutorIdAndStatus(Long tutorId, String status);

    /**
     * Cuenta los registros con tutor id.
     * @param tutorId identificador del tutor
     * @return la cantidad de registros
     */
    long countByTutorId(Long tutorId);

    /**
     * Sp register tutoring avance.
     * @param tutorId identificador del tutor
     * @param numeroPhase número de la fase de tutoría, empezando en 1
     * @param filePdf nombre del PDF que el estudiante entregó en la fase
     * @param sizeBytes tamaño del PDF entregado, en bytes
     * @param sha256 huella SHA-256 del PDF, para comprobar su integridad
     */
    @org.springframework.data.jpa.repository.query.Procedure(procedureName = "presus.sp_registrar_tutoria_avance")
    void spRegisterTutoringProgress(
            @Param("p_tutor_id") Long tutorId,
            @Param("p_numero_fase") Integer numeroPhase,
            @Param("p_archivo_pdf") String filePdf,
            @Param("p_tamano_bytes") Long sizeBytes,
            @Param("p_sha256") String sha256
    );
}
