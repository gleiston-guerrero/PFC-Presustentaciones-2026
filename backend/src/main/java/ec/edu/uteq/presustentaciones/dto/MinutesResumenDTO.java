package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import ec.edu.uteq.presustentaciones.entities.Minutes;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Vista de lista de un minutes (para "Mis actas" del teacher y la gestión del
 * administrador). No expone la submission completa ni el árbol de firmantes:
 * solo lo necesario para la tabla + el enlace al detalle.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MinutesResumenDTO {

    private Long id;
    @JsonProperty("solicitudId")
    private Long submissionId;
    @JsonProperty("estudianteNombre")
    private String studentNombre;
    @JsonProperty("carrera")
    private String program;
    private String tituloTopic;
    private String estado;
    private String estadoNombre;
    private LocalDate fechaGeneracion;
    private boolean firmada;
    private String firmantesPendientes;

    /**
     * De.
     * @param a a
     * @return el MinutesResumenDTO correspondiente
     */
    public static MinutesResumenDTO de(Minutes a) {
        var sol = a.getSubmission();
        var est = sol != null ? sol.getStudent() : null;
        var usr = est != null ? est.getAppUser() : null;
        return MinutesResumenDTO.builder()
                .id(a.getId())
                .submissionId(sol != null ? sol.getId() : null)
                .studentNombre(usr != null ? (usr.getNombre() + " " + usr.getApellido()) : "—")
                .program(est != null ? est.getProgram() : "—")
                .tituloTopic(sol != null ? sol.getTituloTopic() : "—")
                .estado(a.getEstado() != null ? a.getEstado().getCodigo() : null)
                .estadoNombre(a.getEstado() != null ? a.getEstado().getNombre() : null)
                .fechaGeneracion(a.getFechaGeneracion())
                .firmada(a.isFirmada())
                .firmantesPendientes(a.getFirmantesPendientes())
                .build();
    }
}
