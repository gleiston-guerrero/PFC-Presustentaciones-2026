package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import ec.edu.uteq.presustentaciones.entities.HistoryEstadoMinutes;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Una entrada del timeline de trazabilidad de un minutes. Aplana
 * {@link HistoryEstadoMinutes} a lo que el timeline del frontend necesita:
 * quién (email + nombre + role), qué acción, transición de estado y motivo.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryMinutesDTO {

    private Long id;
    @JsonProperty("actaId")
    private Long minutesId;
    private String accion;
    private String estadoAnterior;
    private String estadoNuevo;
    @JsonProperty("usuarioEmail")
    private String appUserEmail;
    @JsonProperty("usuarioNombre")
    private String appUserNombre;
    @JsonProperty("rolUsuario")
    private String roleAppUser;
    private String comentario;
    private LocalDateTime fecha;

    /**
     * De.
     * @param h h
     * @return el HistoryMinutesDTO correspondiente
     */
    public static HistoryMinutesDTO de(HistoryEstadoMinutes h) {
        var u = h.getAppUser();
        return HistoryMinutesDTO.builder()
                .id(h.getId())
                .minutesId(h.getMinutes() != null ? h.getMinutes().getId() : null)
                .accion(h.getAccion())
                .estadoAnterior(h.getEstadoAnterior() != null ? h.getEstadoAnterior().getCodigo() : null)
                .estadoNuevo(h.getEstadoNuevo() != null ? h.getEstadoNuevo().getCodigo() : null)
                .appUserEmail(u != null ? u.getEmail() : null)
                .appUserNombre(u != null ? (u.getNombre() + " " + u.getApellido()) : "Sistema")
                .roleAppUser(h.getRoleAppUser())
                .comentario(h.getComentario())
                .fecha(h.getFechaCambio())
                .build();
    }
}
