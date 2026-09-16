package ec.edu.uteq.presustentaciones.dto;

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
    private String nombre;            // base_20260907_154346
    private long   tamanoBytes;
    private String tamanoLegible;
    private LocalDateTime fechaCreacion;
}
