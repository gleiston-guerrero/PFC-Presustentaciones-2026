package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.*;
import lombok.*;

/**
 * Catalogo persistido de estados de solicitud (tabla {@code presus.estados_solicitud}).
 *
 * <p>No confundir con el enum del mismo nombre en {@code ..presustentaciones.enums}:
 * aquel fija los estados que el codigo conoce, y esta entidad es la fila del catalogo
 * en la base, con su identificador y su etiqueta visible.</p>
 */
@Entity
@Table(name = "estados_solicitud", schema = "presus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatusSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    @JsonProperty("id")
    private Short id;

    @JsonValue
    @Column(name = "codigo", nullable = false, unique = true, length = 30)
    @JsonProperty("codigo")
    private String code;

    @Column(name = "nombre", nullable = false, length = 80)
    @JsonProperty("nombre")
    private String nombre;

    @Column(name = "orden", nullable = false)
    @JsonProperty("orden")
    private Short orden;

    /**
     * Contraparte de @JsonValue: ver RoleAppUser.fromCodigo para el motivo (round-trip vía caché Redis).
     *
     * @param code código del estado de submission
     * @return una instancia con solo el código repoblado (id/nombre quedan null)
     */
    @JsonCreator
    public static StatusSubmission fromCode(String code) {
        return StatusSubmission.builder().code(code).build();
    }
}
