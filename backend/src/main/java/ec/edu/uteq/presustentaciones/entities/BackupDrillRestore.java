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
public class BackupDrillRestore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonProperty("id")
    private Long id;

    @Column(name = "respaldo_nombre", nullable = false, length = 255)
    @JsonProperty("respaldoNombre")
    private String backupNombre;

    @Column(name = "fecha", nullable = false)
    @JsonProperty("fecha")
    private LocalDateTime date;

    /** EXITOSA | FALLIDA */
    @Column(name = "resultado", nullable = false, length = 20)
    @JsonProperty("resultado")
    private String result;

    @Column(length = 200)
    @JsonProperty("responsable")
    private String responsable;

    @Column(columnDefinition = "text")
    @JsonProperty("notas")
    private String notas;
}
