package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.*;
import lombok.*;

/**
 * Catálogo de estados del minutes de pre-sustentación (V19__history_minutes_y_reportes.sql).
 * Mismo patrón que {@link EstadoSubmission}: id SMALLINT explícito (lo siembra la migración,
 * no es autogenerado), codigo UNIQUE y @JsonValue para que la API exponga solo el código
 * ("GENERADA", "FINALIZADA", ...) en vez del objeto completo.
 */
@Entity
@Table(name = "estados_acta", schema = "presus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EstadoMinutes {

    @Id
    @Column(name = "id")
    @JsonProperty("id")
    private Short id;

    @JsonValue
    @Column(name = "codigo", nullable = false, unique = true, length = 30)
    @JsonProperty("codigo")
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 80)
    @JsonProperty("nombre")
    private String nombre;

    @Column(name = "orden", nullable = false)
    @JsonProperty("orden")
    private Short orden;

    /**
     * Contraparte de @JsonValue: ver EstadoSubmission.fromCodigo (round-trip vía caché Redis).
     *
     * @param codigo código del estado de minutes
     * @return una instancia con solo el código repoblado (id/nombre quedan null)
     */
    @JsonCreator
    public static EstadoMinutes fromCodigo(String codigo) {
        return EstadoMinutes.builder().codigo(codigo).build();
    }
}
