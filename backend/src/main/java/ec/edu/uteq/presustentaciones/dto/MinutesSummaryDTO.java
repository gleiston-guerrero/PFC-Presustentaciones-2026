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
public class MinutesSummaryDTO {

    @JsonProperty("id")
    private Long id;
    @JsonProperty("solicitudId")
    private Long submissionId;
    @JsonProperty("estudianteNombre")
    private String studentNombre;
    @JsonProperty("carrera")
    private String program;
    @JsonProperty("tituloTopic")
    private String tituloTopic;
    @JsonProperty("estado")
    private String status;
    @JsonProperty("estadoNombre")
    private String statusNombre;
    @JsonProperty("fechaGeneracion")
    private LocalDate dateGeneracion;
    @JsonProperty("firmada")
    private boolean firmada;
    @JsonProperty("firmantesPendientes")
    private String signersPending;

    /**
     * De.
     * @param a acta de la que se toman los datos
     * @return el MinutesResumenDTO correspondiente
     */
    public static MinutesSummaryDTO from(Minutes a) {
        var sol = a.getSubmission();
        var est = sol != null ? sol.getStudent() : null;
        var usr = est != null ? est.getAppUser() : null;
        return MinutesSummaryDTO.builder()
                .id(a.getId())
                .submissionId(sol != null ? sol.getId() : null)
                .studentNombre(usr != null ? (usr.getNombre() + " " + usr.getApellido()) : "—")
                .program(est != null ? est.getProgram() : "—")
                .tituloTopic(sol != null ? sol.getTituloTopic() : "—")
                .status(a.getStatus() != null ? a.getStatus().getCode() : null)
                .statusNombre(a.getStatus() != null ? a.getStatus().getNombre() : null)
                .dateGeneracion(a.getDateGeneracion())
                .firmada(a.isFirmada())
                .signersPending(a.getSignersPending())
                .build();
    }
}
