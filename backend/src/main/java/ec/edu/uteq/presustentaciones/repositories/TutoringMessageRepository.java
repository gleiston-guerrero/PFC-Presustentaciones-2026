package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.TutoringMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Contrato. Repositorio de acceso a datos de tutoring message.
 */
@Repository
public interface TutoringMessageRepository extends JpaRepository<TutoringMessage, Long> {

    /**
     * Busca el/los registro(s) con fase id o der by fecha envio asc.
     * @param phaseId identificador de la fase de tutoría
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    @org.springframework.data.jpa.repository.Query("SELECT m FROM TutoringMessage m JOIN FETCH m.sender WHERE m.phase.id = :faseId ORDER BY m.dateEnvio ASC")
    List<TutoringMessage> findByPhaseIdOrderByDateEnvioAsc(@org.springframework.data.repository.query.Param("faseId") Long phaseId);

    /**
     * Cuenta los registros con fase id y leido false y remitente id not.
     * @param phaseId identificador de la fase de tutoría
     * @param senderId identificador del usuario emisor cuyos mensajes se excluyen
     * @return la cantidad de registros
     */
    long countByPhaseIdAndLeidoFalseAndSenderIdNot(Long phaseId, Long senderId);

    /**
     * Busca el/los registro(s) con fase id y leido false y remitente id not.
     * @param phaseId identificador de la fase de tutoría
     * @param senderId identificador del usuario emisor cuyos mensajes se excluyen
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    @org.springframework.data.jpa.repository.Query("SELECT m FROM TutoringMessage m JOIN FETCH m.sender WHERE m.phase.id = :faseId AND m.leido = false AND m.sender.id != :remitenteId")
    List<TutoringMessage> findByPhaseIdAndLeidoFalseAndSenderIdNot(@org.springframework.data.repository.query.Param("faseId") Long phaseId, @org.springframework.data.repository.query.Param("remitenteId") Long senderId);
}
