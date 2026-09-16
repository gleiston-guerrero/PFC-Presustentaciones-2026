package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.*;
import lombok.*;

/**
 * Catálogo de estados del acta de pre-sustentación (V19__historial_actas_y_reportes.sql).
 * Mismo patrón que {@link EstadoSolicitud}: id SMALLINT explícito (lo siembra la migración,
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
public class EstadoActa {

    @Id
    @Column(name = "id")
    private Short id;

    @JsonValue
    @Column(name = "codigo", nullable = false, unique = true, length = 30)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 80)
    private String nombre;

    @Column(name = "orden", nullable = false)
    private Short orden;

    /**
     * Contraparte de @JsonValue: ver EstadoSolicitud.fromCodigo (round-trip vía caché Redis).
     *
     * @param codigo código del estado de acta
     * @return una instancia con solo el código repoblado (id/nombre quedan null)
     */
    @JsonCreator
    public static EstadoActa fromCodigo(String codigo) {
        return EstadoActa.builder().codigo(codigo).build();
    }
}
