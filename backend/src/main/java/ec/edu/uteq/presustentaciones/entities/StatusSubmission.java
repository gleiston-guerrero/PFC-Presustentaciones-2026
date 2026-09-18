package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.*;
import lombok.*;

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
