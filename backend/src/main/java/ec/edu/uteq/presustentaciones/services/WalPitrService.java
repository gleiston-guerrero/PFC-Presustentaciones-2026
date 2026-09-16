package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.BaseFisicaDTO;
import ec.edu.uteq.presustentaciones.dto.EstadoWalDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Fase 2 del plan de backups: archivado continuo de WAL / PITR (el "incremental") y
 * backups físicos base ({@code pg_basebackup}).
 *
 * <p>El archivado de WAL se activa en {@code docker-compose.yml} (no se puede activate en
 * caliente: es configuración del motor). Este servicio lo <b>consulta</b>
 * ({@code pg_stat_archiver}, {@code pg_settings}, el directorio compartido {@code /wal}) y
 * ofrece acciones ligeras: forzar el cierre del segmento actual, limpiar WAL viejo y
 * generate la base física. La restauración PITR en sí requiere parar el motor y es un
 * procedimiento documentado (ver {@code docs/basedatos/PLAN-RESPALDOS-RECUPERACION.md} §4).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WalPitrService {

    private static final Pattern SEGMENTO_WAL = Pattern.compile("^[0-9A-F]{24}$");
    private static final Pattern JDBC_URL =
            Pattern.compile("^jdbc:postgresql://([^:/]+)(?::(\\d+))?/([^?;]+).*$");
    private static final DateTimeFormatter SELLO = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final long TIMEOUT_MIN = 30;

    @Value("${app.wal.dir:/wal}")
    private String walDir;

    @Value("${app.backups.dir:uploads/backups}")
    private String backupsDir;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    private final JdbcTemplate jdbc;

    // ── Estado ──────────────────────────────────────────────────────────

    /**
     * @return el estado del archivado de WAL, del directorio compartido y de las bases físicas
     */
    public EstadoWalDTO estado() {
        Map<String, Object> s;
        try {
            s = jdbc.queryForMap(
                "SELECT " +
                " (SELECT setting FROM pg_settings WHERE name='archive_mode')     AS archive_mode, " +
                " (SELECT setting FROM pg_settings WHERE name='wal_level')         AS wal_level, " +
                " (SELECT setting FROM pg_settings WHERE name='archive_command')   AS archive_command, " +
                " (SELECT setting FROM pg_settings WHERE name='archive_timeout')   AS archive_timeout, " +
                " a.archived_count, a.last_archived_wal, a.last_archived_time, " +
                " a.failed_count, a.last_failed_time " +
                "FROM pg_stat_archiver a");
        } catch (Exception e) {
            log.warn("No se pudo consultar el estado de archivado de WAL: {}", e.getMessage());
            s = Map.of();
        }

        boolean archivadoActivo = "on".equalsIgnoreCase(str(s.get("archive_mode")));

        // Directorio compartido de WAL archivado
        long segmentos = 0, bytes = 0;
        LocalDateTime masAntiguo = null;
        Path dir = Paths.get(walDir);
        if (Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.list(dir)) {
                List<Path> segs = files.filter(Files::isRegularFile)
                        .filter(p -> SEGMENTO_WAL.matcher(p.getFileName().toString()).matches())
                        .toList();
                segmentos = segs.size();
                for (Path p : segs) {
                    bytes += sizeOf(p);
                    LocalDateTime m = mtime(p);
                    if (m != null && (masAntiguo == null || m.isBefore(masAntiguo))) masAntiguo = m;
                }
            } catch (IOException e) {
                log.warn("No se pudo leer {}: {}", walDir, e.getMessage());
            }
        }

        List<BaseFisicaDTO> bases = listBases();
        boolean hayBase = !bases.isEmpty();
        LocalDateTime baseMasAntigua = bases.stream()
                .map(BaseFisicaDTO::getFechaCreacion).min(Comparator.naturalOrder()).orElse(null);

        String pitrDesde;
        String advertencia = null;
        if (!archivadoActivo) {
            pitrDesde = "no disponible — el archivado de WAL está desactivado";
            advertencia = "Activa el archivado en docker-compose.yml para habilitar PITR.";
        } else if (!hayBase) {
            pitrDesde = "no disponible — falta una base física";
            advertencia = "Hay WAL archivado pero ninguna base física. El WAL solo sirve para "
                    + "recuperar HACIA ADELANTE desde una base: genera una con «Crear base física».";
        } else {
            LocalDateTime ref = baseMasAntigua != null ? baseMasAntigua : masAntiguo;
            pitrDesde = ref != null ? ref.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "—";
        }

        return EstadoWalDTO.builder()
                .archivadoActivo(archivadoActivo)
                .walLevel(str(s.get("wal_level")))
                .archiveCommand(str(s.get("archive_command")))
                .archiveTimeoutSegundos(parseInt(str(s.get("archive_timeout")), 0))
                .segmentosArchivados(parseLong(s.get("archived_count")))
                .ultimoSegmento(str(s.get("last_archived_wal")))
                .ultimoArchivado(toLdt(s.get("last_archived_time")))
                .fallos(parseLong(s.get("failed_count")))
                .ultimoFallo(toLdt(s.get("last_failed_time")))
                .segmentosEnDisco(segmentos)
                .tamanoArchivadoBytes(bytes)
                .tamanoArchivadoLegible(formato(bytes))
                .segmentoMasAntiguo(masAntiguo)
                .pitrDisponibleDesde(pitrDesde)
                .basesFisicas(bases)
                .hayBaseFisica(hayBase)
                .advertencia(advertencia)
                .build();
    }

    // ── Acciones ────────────────────────────────────────────────────────

    /**
     * Cierra el segmento de WAL actual para que se archive de inmediato.
     *
     * @return el nombre del segmento de WAL cerrado
     */
    public String forzarSwitchWal() {
        try {
            String wal = jdbc.queryForObject("SELECT pg_walfile_name(pg_switch_wal())", String.class);
            log.info("pg_switch_wal(): segmento {} cerrado para archivado", wal);
            return wal;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo forzar el cierre del segmento de WAL: " + e.getMessage(), e);
        }
    }

    /**
     * Borra segmentos de WAL archivados más antiguos que {@code dias} días, pero nunca los
     * necesarios para la base física más antigua que se conserva.
     *
     * @param dias antigüedad mínima en días para que un segmento sea candidato a borrado
     * @return cantidad de segmentos eliminados
     */
    public int limpiarWal(int dias) {
        Path dir = Paths.get(walDir);
        if (!Files.isDirectory(dir)) return 0;

        LocalDateTime porDias = LocalDateTime.now().minusDays(Math.max(0, dias));
        LocalDateTime baseMasAntigua = listBases().stream()
                .map(BaseFisicaDTO::getFechaCreacion).min(Comparator.naturalOrder()).orElse(null);
        // corte = el más conservador de los dos (no erase WAL que una base podría necesitar)
        LocalDateTime corte = baseMasAntigua != null && baseMasAntigua.isBefore(porDias)
                ? baseMasAntigua : porDias;

        int borrados = 0;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : files.filter(Files::isRegularFile).toList()) {
                String n = p.getFileName().toString();
                // segmentos, .backup y .history viejos; nunca el .history más reciente
                boolean candidato = SEGMENTO_WAL.matcher(n).matches()
                        || n.endsWith(".backup") || n.endsWith(".history");
                LocalDateTime m = mtime(p);
                if (candidato && m != null && m.isBefore(corte)) {
                    try {
                        Files.deleteIfExists(p);
                        borrados++;
                    } catch (IOException e) {
                        log.warn("No se pudo borrar WAL {}: {}", n, e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("No se pudo limpiar el WAL archivado: " + e.getMessage(), e);
        }
        if (borrados > 0) log.info("Limpieza de WAL: {} segmentos eliminados (corte {})", borrados, corte);
        return borrados;
    }

    // ── Base física (pg_basebackup) ─────────────────────────────────────

    /** @return las bases físicas generadas, más recientes primero */
    public List<BaseFisicaDTO> listBases() {
        Path dir = basesDir();
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> hijos = Files.list(dir)) {
            return hijos.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("base_"))
                    .map(this::aBaseDTO)
                    .filter(java.util.Objects::nonNull)
                    .sorted(Comparator.comparing(BaseFisicaDTO::getFechaCreacion).reversed())
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("No se pudo listar las bases físicas: " + e.getMessage(), e);
        }
    }

    /**
     * Genera un backup físico base ({@code pg_basebackup}), la base para PITR.
     *
     * @return los metadatos de la base física generada
     * @throws RuntimeException si no se pudo create el directorio de bases, o {@code pg_basebackup} falla
     */
    public BaseFisicaDTO generateBaseFisica() {
        Conexion c = conexion();
        Path dir = basesDir();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo crear el directorio de bases: " + e.getMessage(), e);
        }
        String nombre = "base_" + LocalDateTime.now().format(SELLO);
        Path destino = dir.resolve(nombre);

        List<String> comando = List.of(
                "pg_basebackup",
                "-h", c.host, "-p", c.port, "-U", c.appUser,
                "-D", destino.toAbsolutePath().toString(),
                "--format=tar", "--gzip", "--wal-method=stream", "--checkpoint=fast", "--progress");

        ProcessResult r = correr(comando, "pg_basebackup");
        if (r.codigo != 0) {
            // limpiar restos parciales
            try { eraseRec(destino); } catch (Exception ignore) {}
            throw new RuntimeException("No se pudo generar la base física: " + ultimaLine(r.salida));
        }
        log.info("Base física generada: {} ({})", nombre, formato(tamanoDir(destino)));
        return aBaseDTO(destino);
    }

    /**
     * @param nombre nombre de la base física a delete
     * @throws IllegalArgumentException si el nombre no tiene el formato esperado
     */
    public void deleteBase(String nombre) {
        if (nombre == null || !nombre.matches("^base_[0-9]{8}_[0-9]{6}$")) {
            throw new IllegalArgumentException("Nombre de base física inválido.");
        }
        Path dir = basesDir().resolve(nombre).normalize();
        if (!dir.getParent().equals(basesDir().toAbsolutePath().normalize()) && !dir.getParent().equals(basesDir())) {
            throw new IllegalArgumentException("Nombre de base física inválido.");
        }
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("La base física '" + nombre + "' no existe.");
        }
        try {
            eraseRec(dir);
            log.info("Base física eliminada: {}", nombre);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo eliminar la base física: " + e.getMessage(), e);
        }
    }

    // ── Internos ────────────────────────────────────────────────────────

    private record Conexion(String host, String port, String baseDatos, String appUser) {}

    private Conexion conexion() {
        Matcher m = JDBC_URL.matcher(datasourceUrl == null ? "" : datasourceUrl.trim());
        if (!m.matches()) throw new IllegalStateException("URL de la base de datos no interpretable.");
        return new Conexion(m.group(1), m.group(2) != null ? m.group(2) : "5432", m.group(3), dbUsername);
    }

    private Path basesDir() {
        return Paths.get(backupsDir).getParent() != null
                ? Paths.get(backupsDir).getParent().resolve("bases")
                : Paths.get("uploads/bases");
    }

    private record ProcessResult(int codigo, String salida) {}

    private ProcessResult correr(List<String> comando, String bin) {
        ProcessBuilder pb = new ProcessBuilder(comando);
        pb.environment().put("PGPASSWORD", dbPassword == null ? "" : dbPassword);
        pb.redirectErrorStream(true);
        Process p;
        try {
            p = pb.start();
        } catch (IOException e) {
            throw new IllegalStateException("El comando '" + bin + "' no está disponible en el servidor.");
        }
        String salida;
        try {
            salida = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(TIMEOUT_MIN, TimeUnit.MINUTES)) {
                p.destroyForcibly();
                throw new RuntimeException(bin + " excedió el tiempo máximo.");
            }
        } catch (IOException e) {
            throw new RuntimeException("Error leyendo la salida de " + bin + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Operación interrumpida.");
        }
        return new ProcessResult(p.exitValue(), salida.trim());
    }

    private BaseFisicaDTO aBaseDTO(Path d) {
        try {
            long bytes = tamanoDir(d);
            LocalDateTime creado = mtime(d);
            return BaseFisicaDTO.builder()
                    .nombre(d.getFileName().toString())
                    .tamanoBytes(bytes)
                    .tamanoLegible(formato(bytes))
                    .fechaCreacion(creado)
                    .build();
        } catch (Exception e) {
            return null;
        }
    }

    private static long tamanoDir(Path d) {
        try (Stream<Path> w = Files.walk(d)) {
            return w.filter(Files::isRegularFile).mapToLong(WalPitrService::sizeOf).sum();
        } catch (IOException e) {
            return 0L;
        }
    }

    private static void eraseRec(Path d) throws IOException {
        if (!Files.exists(d)) return;
        try (Stream<Path> w = Files.walk(d)) {
            w.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignore) {}
            });
        }
    }

    private static long sizeOf(Path p) { try { return Files.size(p); } catch (IOException e) { return 0L; } }

    private static LocalDateTime mtime(Path p) {
        try {
            return LocalDateTime.ofInstant(Files.getLastModifiedTime(p).toInstant(), ZoneId.systemDefault());
        } catch (IOException e) { return null; }
    }

    private static String ultimaLine(String s) {
        if (s == null || s.isBlank()) return "sin detalle (ver logs del backend).";
        String[] l = s.strip().split("\\r?\\n");
        String u = l[l.length - 1].trim();
        return u.length() > 300 ? u.substring(0, 300) + "…" : u;
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }
    private static int parseInt(String s, int def) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; } }
    private static long parseLong(Object o) { try { return Long.parseLong(o.toString().trim()); } catch (Exception e) { return 0L; } }

    private static LocalDateTime toLdt(Object o) {
        if (o == null) return null;
        if (o instanceof java.sql.Timestamp t) return t.toLocalDateTime();
        if (o instanceof Instant i) return LocalDateTime.ofInstant(i, ZoneId.systemDefault());
        return null;
    }

    private static String formato(long bytes) {
        if (bytes <= 0) return "0 B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1f MB", mb);
        return String.format("%.2f GB", mb / 1024.0);
    }
}
