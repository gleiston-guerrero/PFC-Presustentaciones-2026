package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "resultados_evaluacion", schema = "presus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResultadoEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private Short id;

    @JsonValue
    @Column(name = "codigo", nullable = false, unique = true, length = 30)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 80)
    private String nombre;

    /**
     * Contraparte de @JsonValue: ver RoleAppUser.fromCodigo para el motivo (round-trip vía caché Redis).
     *
     * @param codigo código del resultado de evaluación
     * @return una instancia con solo el código repoblado (id/nombre quedan null)
     */
    @JsonCreator
    public static ResultadoEvaluation fromCodigo(String codigo) {
        return ResultadoEvaluation.builder().codigo(codigo).build();
    }
}
