package ec.edu.uteq.presustentaciones.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Configuración del schedule de backups: se usa tanto para leer como para update. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupConfigDTO {

    @NotNull
    private Boolean activo;

    /** Expresión cron de Spring (6 campos). Se valida con CronExpression.parse antes de save. */
    @NotBlank
    private String cron;

    @NotNull @Min(0) @Max(365)
    private Integer retenerDiarios;

    @NotNull @Min(0) @Max(104)
    private Integer retenerSemanales;

    @NotNull @Min(0) @Max(120)
    private Integer retenerMensuales;

    // ── Fase 2 ──────────────────────────────────────────────────────────
    @NotNull @Min(0) @Max(365)
    private Integer retenerDiasWal;

    @NotNull
    private Boolean diferencialActivo;

    @NotBlank
    private String cronDiferencial;

    /** Solo lectura: texto legible del cron ("Cada domingo a las 23:00"). */
    private String cronDescripcion;
    private String cronDiferencialDescripcion;
}
