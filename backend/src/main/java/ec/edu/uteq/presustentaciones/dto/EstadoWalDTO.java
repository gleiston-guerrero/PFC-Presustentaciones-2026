package ec.edu.uteq.presustentaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** Estado del archivado continuo de WAL y de los backups físicos base (panel PITR). */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstadoWalDTO {

    // ── Configuración del motor ─────────────────────────────────────────
    private boolean archivadoActivo;          // archive_mode = on
    private String  walLevel;                 // replica / logical / minimal
    private String  archiveCommand;
    private int     archiveTimeoutSegundos;

    // ── pg_stat_archiver ────────────────────────────────────────────────
    private long          segmentosArchivados;     // archived_count
    private String        ultimoSegmento;          // last_archived_wal
    private LocalDateTime  ultimoArchivado;         // last_archived_time
    private long           fallos;                  // failed_count
    private LocalDateTime  ultimoFallo;             // last_failed_time

    // ── Directorio de archivado (volumen compartido) ────────────────────
    private long   segmentosEnDisco;
    private long   tamanoArchivadoBytes;
    private String tamanoArchivadoLegible;
    private LocalDateTime segmentoMasAntiguo;

    /** Punto más antiguo al que se puede recuperar (base física más antigua, o el WAL más viejo). */
    private String pitrDisponibleDesde;

    // ── Backups físicos base (pg_basebackup) ──────────────────────────
    private List<BaseFisicaDTO> basesFisicas;
    private boolean hayBaseFisica;
    private String  advertencia;   // p. ej. "hay WAL pero ninguna base física: no se puede hacer PITR"
}
