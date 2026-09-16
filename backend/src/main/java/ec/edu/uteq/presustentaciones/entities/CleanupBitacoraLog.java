package ec.edu.uteq.presustentaciones.entities;

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
public class CleanupBitacoraLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fecha_ejecucion", nullable = false)
    private LocalDateTime fechaEjecucion;

    @Column(name = "entradas_eliminadas", nullable = false)
    private Integer entradasEliminadas;

    @Column(name = "fecha_corte", nullable = false)
    private LocalDateTime fechaCorte;
}
