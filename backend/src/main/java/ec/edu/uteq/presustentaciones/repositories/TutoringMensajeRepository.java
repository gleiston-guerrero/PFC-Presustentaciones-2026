package ec.edu.uteq.presustentaciones.repositories;

import ec.edu.uteq.presustentaciones.entities.TutoringMensaje;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TutoringMensajeRepository extends JpaRepository<TutoringMensaje, Long> {

    @org.springframework.data.jpa.repository.Query("SELECT m FROM TutoringMensaje m JOIN FETCH m.remitente WHERE m.fase.id = :faseId ORDER BY m.fechaEnvio ASC")
    List<TutoringMensaje> findByFaseIdOrderByFechaEnvioAsc(@org.springframework.data.repository.query.Param("faseId") Long faseId);

    long countByFaseIdAndLeidoFalseAndRemitenteIdNot(Long faseId, Long remitenteId);

    @org.springframework.data.jpa.repository.Query("SELECT m FROM TutoringMensaje m JOIN FETCH m.remitente WHERE m.fase.id = :faseId AND m.leido = false AND m.remitente.id != :remitenteId")
    List<TutoringMensaje> findByFaseIdAndLeidoFalseAndRemitenteIdNot(@org.springframework.data.repository.query.Param("faseId") Long faseId, @org.springframework.data.repository.query.Param("remitenteId") Long remitenteId);
}
