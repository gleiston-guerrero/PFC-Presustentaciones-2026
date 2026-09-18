package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Metadatos de un backup físico base ({@code pg_basebackup}), la base para PITR. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaseFisicaDTO {
    @JsonProperty("nombre")
    private String nombre;            // base_20260907_154346
    @JsonProperty("tamanoBytes")
    private long tamanoBytes;
    @JsonProperty("tamanoLegible")
    private String tamanoLegible;
    @JsonProperty("fechaCreacion")
    private LocalDateTime fechaCreacion;
}
