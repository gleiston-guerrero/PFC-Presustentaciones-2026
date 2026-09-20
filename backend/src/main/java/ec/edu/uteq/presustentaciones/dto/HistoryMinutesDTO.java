package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import ec.edu.uteq.presustentaciones.entities.HistoryStatusMinutes;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Una entrada del timeline de trazabilidad de un minutes. Aplana
 * {@link HistoryStatusMinutes} a lo que el timeline del frontend necesita:
 * quién (email + nombre + role), qué acción, transición de estado y motivo.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryMinutesDTO {

    @JsonProperty("id")
    private Long id;
    @JsonProperty("actaId")
    private Long minutesId;
    @JsonProperty("accion")
    private String accion;
    @JsonProperty("estadoAnterior")
    private String statusAnterior;
    @JsonProperty("estadoNuevo")
    private String statusNew;
    @JsonProperty("usuarioEmail")
    private String appUserEmail;
    @JsonProperty("usuarioNombre")
    private String appUserNombre;
    @JsonProperty("rolUsuario")
    private String roleAppUser;
    @JsonProperty("comentario")
    private String comment;
    @JsonProperty("fecha")
    private LocalDateTime date;

    /**
     * De.
     * @param h registro del historial de cambios de estado del acta
     * @return el HistoryMinutesDTO correspondiente
     */
    public static HistoryMinutesDTO from(HistoryStatusMinutes h) {
        var u = h.getAppUser();
        return HistoryMinutesDTO.builder()
                .id(h.getId())
                .minutesId(h.getMinutes() != null ? h.getMinutes().getId() : null)
                .accion(h.getAccion())
                .statusAnterior(h.getStatusAnterior() != null ? h.getStatusAnterior().getCode() : null)
                .statusNew(h.getStatusNew() != null ? h.getStatusNew().getCode() : null)
                .appUserEmail(u != null ? u.getEmail() : null)
                .appUserNombre(u != null ? (u.getNombre() + " " + u.getApellido()) : "Sistema")
                .roleAppUser(h.getRoleAppUser())
                .comment(h.getComment())
                .date(h.getDateCambio())
                .build();
    }
}
