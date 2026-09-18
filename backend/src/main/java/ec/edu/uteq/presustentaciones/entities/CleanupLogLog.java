package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** RNF-19: traza de cada corrida del expurgo automático de {@code presus.audit}. */
@Entity
@Table(name = "depuracion_bitacora_log", schema = "presus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CleanupLogLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonProperty("id")
    private Long id;

    @Column(name = "fecha_ejecucion", nullable = false)
    @JsonProperty("fechaEjecucion")
    private LocalDateTime dateEjecucion;

    @Column(name = "entradas_eliminadas", nullable = false)
    @JsonProperty("entradasEliminadas")
    private Integer entradasEliminadas;

    @Column(name = "fecha_corte", nullable = false)
    @JsonProperty("fechaCorte")
    private LocalDateTime dateCorte;
}
