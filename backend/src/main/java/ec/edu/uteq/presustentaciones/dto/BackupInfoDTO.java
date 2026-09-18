package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Metadatos de un archivo de backup (dump) de la base de datos, para la tabla del
 * apartado "Gestión de Respaldos" del administrador. No expone la ruta absoluta en el
 * servidor -- solo el nombre del archivo, que es lo único que los endpoints aceptan de
 * vuelta para download / restore / delete.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupInfoDTO {

    /** Nombre del archivo, p. ej. {@code backup_FULL_AUTOMATICO_20260907_230000.dump}. */
    @JsonProperty("nombre")
    private String nombre;

    /** FULL | DIFERENCIAL (leído del nombre del archivo; los formatos antiguos son FULL). */
    @JsonProperty("tipo")
    private String tipo;

    /** MANUAL | AUTOMATICO | EVENTO (leído del nombre del archivo; los antiguos son MANUAL). */
    @JsonProperty("origen")
    private String origen;

    /** Tamaño en bytes. */
    @JsonProperty("tamanoBytes")
    private long tamanoBytes;

    /** Tamaño ya formateado para mostrar ("12.8 MB"). */
    @JsonProperty("tamanoLegible")
    private String tamanoLegible;

    /** Fecha de creación del archivo (hora del servidor). */
    @JsonProperty("fechaCreacion")
    private LocalDateTime fechaCreacion;
}
