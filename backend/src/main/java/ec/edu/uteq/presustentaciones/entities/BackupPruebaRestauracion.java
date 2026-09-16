package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Bitácora de las pruebas de restauración periódicas exigidas por el plan de backups
 * (mensuales: restore la última copia en una instancia desechable y verify).
 * Ver {@code V28__gestion_backups_schedule.sql}.
 */
@Entity
@Table(name = "respaldo_prueba_restauracion", schema = "presus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BackupPruebaRestauracion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "respaldo_nombre", nullable = false, length = 255)
    @JsonProperty("respaldoNombre")
    private String backupNombre;

    @Column(nullable = false)
    private LocalDateTime fecha;

    /** EXITOSA | FALLIDA */
    @Column(nullable = false, length = 20)
    private String resultado;

    @Column(length = 200)
    private String responsable;

    @Column(columnDefinition = "text")
    private String notas;
}
