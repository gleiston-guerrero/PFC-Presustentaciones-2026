package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

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
    @JsonProperty("archivadoActivo")
    private boolean archivadoActivo;          // archive_mode = on
    @JsonProperty("walLevel")
    private String walLevel;                 // replica / logical / minimal
    @JsonProperty("archiveCommand")
    private String archiveCommand;
    @JsonProperty("archiveTimeoutSegundos")
    private int archiveTimeoutSegundos;

    // ── pg_stat_archiver ────────────────────────────────────────────────
    @JsonProperty("segmentosArchivados")
    private long segmentosArchivados;     // archived_count
    @JsonProperty("ultimoSegmento")
    private String ultimoSegmento;          // last_archived_wal
    @JsonProperty("ultimoArchivado")
    private LocalDateTime ultimoArchivado;         // last_archived_time
    @JsonProperty("fallos")
    private long fallos;                  // failed_count
    @JsonProperty("ultimoFallo")
    private LocalDateTime ultimoFallo;             // last_failed_time

    // ── Directorio de archivado (volumen compartido) ────────────────────
    @JsonProperty("segmentosEnDisco")
    private long segmentosEnDisco;
    @JsonProperty("tamanoArchivadoBytes")
    private long tamanoArchivadoBytes;
    @JsonProperty("tamanoArchivadoLegible")
    private String tamanoArchivadoLegible;
    @JsonProperty("segmentoMasAntiguo")
    private LocalDateTime segmentoMasAntiguo;

    /** Punto más antiguo al que se puede recuperar (base física más antigua, o el WAL más viejo). */
    @JsonProperty("pitrDisponibleDesde")
    private String pitrDisponibleDesde;

    // ── Backups físicos base (pg_basebackup) ──────────────────────────
    @JsonProperty("basesFisicas")
    private List<BaseFisicaDTO> basesFisicas;
    @JsonProperty("hayBaseFisica")
    private boolean hayBaseFisica;
    @JsonProperty("advertencia")
    private String advertencia;   // p. ej. "hay WAL pero ninguna base física: no se puede hacer PITR"
}
