package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.*;
import lombok.*;

/**
 * Result evaluation.
 */
@Entity
@Table(name = "resultados_evaluacion", schema = "presus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResultEvaluation {

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

    /**
     * Contraparte de @JsonValue: ver RoleAppUser.fromCodigo para el motivo (round-trip vía caché Redis).
     *
     * @param code código del resultado de evaluación
     * @return una instancia con solo el código repoblado (id/nombre quedan null)
     */
    @JsonCreator
    public static ResultEvaluation fromCode(String code) {
        return ResultEvaluation.builder().code(code).build();
    }
}
