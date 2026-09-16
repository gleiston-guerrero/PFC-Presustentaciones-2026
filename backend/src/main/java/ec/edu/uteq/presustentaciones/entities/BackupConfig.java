package ec.edu.uteq.presustentaciones.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Configuración (fila única, id = 1) del schedule de backups: cada cuánto se genera
 * el FULL automático y cuántas copias conservar en cada nivel de la retención GFS.
 * Ver {@code V28__gestion_backups_schedule.sql}.
 */
@Entity
@Table(name = "respaldo_config", schema = "presus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BackupConfig {

    public static final short ID_UNICO = 1;

    @Id
    private Short id;

    @Column(nullable = false)
    private boolean activo;

    /** Expresión cron de Spring (6 campos). */
    @Column(nullable = false, length = 120)
    private String cron;

    @Column(name = "retener_diarios", nullable = false)
    private Short retenerDiarios;

    @Column(name = "retener_semanales", nullable = false)
    private Short retenerSemanales;

    @Column(name = "retener_mensuales", nullable = false)
    private Short retenerMensuales;

    // ── Fase 2 ──────────────────────────────────────────────────────────
    /** Días que se conserva el WAL archivado (PITR). */
    @Column(name = "retener_dias_wal", nullable = false)
    private Short retenerDiasWal;

    /** Programación del backup diferencial. */
    @Column(name = "diferencial_activo", nullable = false)
    private boolean diferencialActivo;

    @Column(name = "cron_diferencial", nullable = false, length = 120)
    private String cronDiferencial;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    @Column(name = "actualizado_por", length = 200)
    private String actualizadoPor;
}
