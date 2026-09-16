package ec.edu.uteq.presustentaciones.entities;

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
public class EstadoSolicitud {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
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
     * Contraparte de @JsonValue: ver RolUsuario.fromCodigo para el motivo (round-trip vía caché Redis).
     *
     * @param codigo código del estado de solicitud
     * @return una instancia con solo el código repoblado (id/nombre quedan null)
     */
    @JsonCreator
    public static EstadoSolicitud fromCodigo(String codigo) {
        return EstadoSolicitud.builder().codigo(codigo).build();
    }
}
