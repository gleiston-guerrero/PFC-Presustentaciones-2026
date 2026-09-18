package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/** Panel de estado del apartado "Gestión de Respaldos". */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusBackupsDTO {

    @JsonProperty("ultimoRespaldo")
    private BackupInfoDTO lastBackup;          // null si no hay ninguno
    @JsonProperty("ultimoRespaldoHace")
    private String        lastBackupHace;      // "hace 3 horas" / "—"

    @JsonProperty("programacionActiva")
    private boolean schedulingActive;
    @JsonProperty("proximoAutomatico")
    private LocalDateTime proximoAutomatic;       // null si está desactivada o el cron es inválido
    @JsonProperty("proximoAutomaticoTexto")
    private String proximoAutomaticTexto;  // "domingo 23:00" / "programación pausada"

    @JsonProperty("totalRespaldos")
    private int              totalBackups;
    @JsonProperty("conteoPorTipo")
    private Map<String,Long>  countByKind;        // {FULL: 4, DIFERENCIAL: 0}
    @JsonProperty("conteoPorOrigen")
    private Map<String,Long>  countBySource;      // {AUTOMATICO: 3, MANUAL: 1}

    @JsonProperty("espacioUsadoBytes")
    private long espacioUsadoBytes;
    @JsonProperty("espacioUsadoLegible")
    private String espacioUsadoLegible;
    @JsonProperty("espacioLibreBytes")
    private long espacioFreeBytes;
    @JsonProperty("espacioLibreLegible")
    private String espacioFreeLegible;

    /** Ventana máxima de pérdida de datos estimada según la programación. */
    @JsonProperty("rpoEstimado")
    private String rpoEstimado;

    @JsonProperty("ultimaPruebaRestauracion")
    private LocalDateTime lastDrillRestore;      // null si nunca se registró
    @JsonProperty("ultimaPruebaResultado")
    private String lastDrillResult;         // EXITOSA | FALLIDA | —
    @JsonProperty("ultimaPruebaHace")
    private String lastDrillHace;              // "hace 12 días" / "nunca"
}
