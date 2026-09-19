package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Objeto de transferencia de role.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleDTO {
    @JsonProperty("id")
    private Short id;
    @JsonProperty("codigo")
    private String code;
    @JsonProperty("nombre")
    private String nombre;
    @JsonProperty("usuariosAsignados")
    private long appUsersAsignados;
    @JsonProperty("permisos")
    private List<String> permissions;
}
