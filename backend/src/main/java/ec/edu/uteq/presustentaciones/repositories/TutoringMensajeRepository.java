package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.TutoringMensaje;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TutoringMensajeRepository extends JpaRepository<TutoringMensaje, Long> {

    @org.springframework.data.jpa.repository.Query("SELECT m FROM TutoringMensaje m JOIN FETCH m.remitente WHERE m.fase.id = :faseId ORDER BY m.fechaEnvio ASC")
    /**
     * Busca el/los registro(s) con fase id o der by fecha envio asc.
     * @param faseId faseId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<TutoringMensaje> findByFaseIdOrderByFechaEnvioAsc(@org.springframework.data.repository.query.Param("faseId") Long faseId);

    /**
     * Cuenta los registros con fase id y leido false y remitente id not.
     * @param faseId faseId
     * @param remitenteId remitenteId
     * @return la cantidad de registros
     */
    long countByFaseIdAndLeidoFalseAndRemitenteIdNot(Long faseId, Long remitenteId);

    @org.springframework.data.jpa.repository.Query("SELECT m FROM TutoringMensaje m JOIN FETCH m.remitente WHERE m.fase.id = :faseId AND m.leido = false AND m.remitente.id != :remitenteId")
    /**
     * Busca el/los registro(s) con fase id y leido false y remitente id not.
     * @param faseId faseId
     * @param remitenteId remitenteId
     * @return los resultados encontrados (vacío si no hay coincidencias)
     */
    List<TutoringMensaje> findByFaseIdAndLeidoFalseAndRemitenteIdNot(@org.springframework.data.repository.query.Param("faseId") Long faseId, @org.springframework.data.repository.query.Param("remitenteId") Long remitenteId);
}
