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
public class EstadoBackupsDTO {

    @JsonProperty("ultimoRespaldo")
    private BackupInfoDTO ultimoBackup;          // null si no hay ninguno
    @JsonProperty("ultimoRespaldoHace")
    private String        ultimoBackupHace;      // "hace 3 horas" / "—"

    private boolean       programacionActiva;
    private LocalDateTime proximoAutomatico;       // null si está desactivada o el cron es inválido
    private String        proximoAutomaticoTexto;  // "domingo 23:00" / "programación pausada"

    @JsonProperty("totalRespaldos")
    private int              totalBackups;
    @JsonProperty("conteoPorTipo")
    private Map<String,Long>  countPorTipo;        // {FULL: 4, DIFERENCIAL: 0}
    @JsonProperty("conteoPorOrigen")
    private Map<String,Long>  countPorOrigen;      // {AUTOMATICO: 3, MANUAL: 1}

    private long   espacioUsadoBytes;
    private String espacioUsadoLegible;
    private long   espacioLibreBytes;
    private String espacioLibreLegible;

    /** Ventana máxima de pérdida de datos estimada según la programación. */
    private String rpoEstimado;

    private LocalDateTime ultimaPruebaRestauracion;      // null si nunca se registró
    private String        ultimaPruebaResultado;         // EXITOSA | FALLIDA | —
    private String        ultimaPruebaHace;              // "hace 12 días" / "nunca"
}
