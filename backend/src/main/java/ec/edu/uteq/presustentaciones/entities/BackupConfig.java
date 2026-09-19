package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

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

    /**
     * I d  u n i c o.
     */
    public static final short ID_UNICO = 1;

    @Id
    @JsonProperty("id")
    private Short id;

    @Column(nullable = false)
    @JsonProperty("activo")
    private boolean activo;

    /** Expresión cron de Spring (6 campos). */
    @Column(nullable = false, length = 120)
    @JsonProperty("cron")
    private String cron;

    @Column(name = "retener_diarios", nullable = false)
    @JsonProperty("retenerDiarios")
    private Short retenerDiarios;

    @Column(name = "retener_semanales", nullable = false)
    @JsonProperty("retenerSemanales")
    private Short retenerSemanales;

    @Column(name = "retener_mensuales", nullable = false)
    @JsonProperty("retenerMensuales")
    private Short retenerMensuales;

    // ── Fase 2 ──────────────────────────────────────────────────────────
    /** Días que se conserva el WAL archivado (PITR). */
    @Column(name = "retener_dias_wal", nullable = false)
    @JsonProperty("retenerDiasWal")
    private Short retenerDiasWal;

    /** Programación del backup diferencial. */
    @Column(name = "diferencial_activo", nullable = false)
    @JsonProperty("diferencialActivo")
    private boolean differentialActivo;

    @Column(name = "cron_diferencial", nullable = false, length = 120)
    @JsonProperty("cronDiferencial")
    private String cronDifferential;

    @Column(name = "actualizado_en", nullable = false)
    @JsonProperty("actualizadoEn")
    private LocalDateTime actualizadoEn;

    @Column(name = "actualizado_por", length = 200)
    @JsonProperty("actualizadoPor")
    private String actualizadoBy;
}
