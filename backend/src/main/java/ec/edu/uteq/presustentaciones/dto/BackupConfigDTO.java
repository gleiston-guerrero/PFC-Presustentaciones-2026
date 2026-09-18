package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

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
    @JsonProperty("activo")
    private Boolean activo;

    /** Expresión cron de Spring (6 campos). Se valida con CronExpression.parse antes de save. */
    @NotBlank
    @JsonProperty("cron")
    private String cron;

    @NotNull @Min(0) @Max(365)
    @JsonProperty("retenerDiarios")
    private Integer retenerDiarios;

    @NotNull @Min(0) @Max(104)
    @JsonProperty("retenerSemanales")
    private Integer retenerSemanales;

    @NotNull @Min(0) @Max(120)
    @JsonProperty("retenerMensuales")
    private Integer retenerMensuales;

    // ── Fase 2 ──────────────────────────────────────────────────────────
    @NotNull @Min(0) @Max(365)
    @JsonProperty("retenerDiasWal")
    private Integer retenerDiasWal;

    @NotNull
    @JsonProperty("diferencialActivo")
    private Boolean differentialActivo;

    @NotBlank
    @JsonProperty("cronDiferencial")
    private String cronDifferential;

    /** Solo lectura: texto legible del cron ("Cada domingo a las 23:00"). */
    @JsonProperty("cronDescripcion")
    private String cronDescription;
    @JsonProperty("cronDiferencialDescripcion")
    private String cronDifferentialDescription;
}
