package ec.edu.uteq.presustentaciones.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ec.edu.uteq.presustentaciones.dto.BackupInfoDTO;
import ec.edu.uteq.presustentaciones.dto.EstadoRespaldosDTO;
import ec.edu.uteq.presustentaciones.dto.RespaldoConfigDTO;
import ec.edu.uteq.presustentaciones.entities.RespaldoConfig;
import ec.edu.uteq.presustentaciones.entities.RespaldoPruebaRestauracion;
import ec.edu.uteq.presustentaciones.repositories.RespaldoConfigRepository;
import ec.edu.uteq.presustentaciones.repositories.RespaldoPruebaRestauracionRepository;
import ec.edu.uteq.presustentaciones.services.backup.OrigenRespaldo;
import ec.edu.uteq.presustentaciones.services.backup.TipoRespaldo;

/**
 * Genera y administra los respaldos de la base de datos para el apartado "Gestión de
 * Respaldos" del administrador (permiso {@code BACKUPS_GESTIONAR}).
 *
 * <p>Fase 1 del plan (ver {@code docs/basedatos/PLAN-RESPALDOS-RECUPERACION.md}):
 * <ul>
 *   <li>Respaldo FULL bajo demanda ({@code pg_dump -Fc}) — igual que antes.</li>
 *   <li>Respaldo FULL automático según un cronograma cron editable ({@code BackupScheduler}).</li>
 *   <li>Retención automática GFS (grandfather-father-son) de las copias automáticas.</li>
 *   <li>Panel de estado + bitácora de pruebas de restauración.</li>
 * </ul>
 *
 * <p>El tipo ({@link TipoRespaldo}) y el origen ({@link OrigenRespaldo}) van codificados en
 * el nombre del archivo: {@code respaldo_<TIPO>_<ORIGEN>_<yyyyMMdd_HHmmss>.dump}. Los
 * nombres del formato antiguo ({@code respaldo_<yyyyMMdd_HHmmss>.dump}) se interpretan
 * como FULL / MANUAL.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BackupService {

    /** Aceptado por descargar/restaurar/eliminar (FULL = .dump, DIFERENCIAL = .tar.gz). */
    private static final Pattern NOMBRE_VALIDO = Pattern.compile("^[A-Za-z0-9._-]+\\.(dump|tar\\.gz)$");
    /** respaldo_FULL_AUTOMATICO_20260907_230000.dump  /  respaldo_DIFERENCIAL_MANUAL_..._....tar.gz */
    private static final Pattern NOMBRE_NUEVO =
            Pattern.compile("^respaldo_(FULL|DIFERENCIAL)_(MANUAL|AUTOMATICO|EVENTO)_(\\d{8}_\\d{6})\\.(?:dump|tar\\.gz)$");
    /** respaldo_20260907_230000.dump  (formato antiguo -> FULL / MANUAL) */
    private static final Pattern NOMBRE_ANTIGUO = Pattern.compile("^respaldo_(\\d{8}_\\d{6})\\.dump$");

    private static final Pattern JDBC_URL =
            Pattern.compile("^jdbc:postgresql://([^:/]+)(?::(\\d+))?/([^?;]+).*$");
    private static final DateTimeFormatter SELLO = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter TS_SQL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Tablas incluidas en el respaldo diferencial (prioridad de recuperación del plan,
     * §1.4). El "qué cambió desde el último FULL" se resuelve por la tabla de auditoría
     * (V15): registro_id con evento posterior al FULL. Todas tienen PK "id".
     */
    private static final List<String> TABLAS_DIFERENCIAL = List.of(
            "usuarios", "estudiante", "solicitud", "actas", "evaluaciones_finales",
            "temas_propuestos", "recursos_titulacion", "progreso_estudiante");

    private static final long TIMEOUT_MINUTOS = 30;

    @Value("${app.backups.dir:uploads/backups}")
    private String backupsDir;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    private final RespaldoConfigRepository configRepo;
    private final RespaldoPruebaRestauracionRepository pruebaRepo;

    // ── Consultas ────────────────────────────────────────────────────────────

    /**
     * Respaldos existentes, del más reciente al más antiguo.
     *
     * @return lista de metadatos de cada archivo de respaldo válido en el directorio
     *         configurado, o una lista vacía si el directorio aún no existe
     */
    public List<BackupInfoDTO> listar() {
        Path dir = Paths.get(backupsDir);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> archivos = Files.list(dir)) {
            return archivos
                    .filter(Files::isRegularFile)
                    .filter(p -> { String n = p.getFileName().toString();
                                   return n.endsWith(".dump") || n.endsWith(".tar.gz"); })
                    .map(this::aInfo)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(BackupInfoDTO::getFechaCreacion).reversed())
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("No se pudo listar el directorio de respaldos: " + e.getMessage(), e);
        }
    }

    /**
     * Panel de estado del apartado.
     *
     * @return resumen con el último respaldo, la próxima ejecución programada, el uso de
     *         espacio en disco y el resultado de la última prueba de restauración
     */
    public EstadoRespaldosDTO estado() {
        List<BackupInfoDTO> todos = listar();
        RespaldoConfig cfg = config();

        BackupInfoDTO ultimo = todos.isEmpty() ? null : todos.get(0);
        LocalDateTime ahora = LocalDateTime.now();

        LocalDateTime proximo = null;
        String proximoTexto = "programación pausada";
        String rpo = "sin protección automática — solo copias manuales";
        if (cfg.isActivo()) {
            CronExpression cron = cronValido(cfg.getCron());
            if (cron != null) {
                proximo = cron.next(ahora);
                proximoTexto = proximo != null ? ("en " + humanizar(Duration.between(ahora, proximo))) : "—";
                rpo = "≈ " + humanizarIntervaloCron(cron, ahora);
            } else {
                proximoTexto = "expresión cron inválida";
            }
        }

        long usado = todos.stream().mapToLong(BackupInfoDTO::getTamanoBytes).sum();
        long libre = espacioLibre();

        RespaldoPruebaRestauracion ultimaPrueba = pruebaRepo.findFirstByOrderByFechaDesc();

        return EstadoRespaldosDTO.builder()
                .ultimoRespaldo(ultimo)
                .ultimoRespaldoHace(ultimo != null ? "hace " + humanizar(Duration.between(ultimo.getFechaCreacion(), ahora)) : "—")
                .programacionActiva(cfg.isActivo())
                .proximoAutomatico(proximo)
                .proximoAutomaticoTexto(proximoTexto)
                .totalRespaldos(todos.size())
                .conteoPorTipo(todos.stream().collect(Collectors.groupingBy(
                        BackupInfoDTO::getTipo, LinkedHashMap::new, Collectors.counting())))
                .conteoPorOrigen(todos.stream().collect(Collectors.groupingBy(
                        BackupInfoDTO::getOrigen, LinkedHashMap::new, Collectors.counting())))
                .espacioUsadoBytes(usado)
                .espacioUsadoLegible(formatoTamano(usado))
                .espacioLibreBytes(libre)
                .espacioLibreLegible(formatoTamano(libre))
                .rpoEstimado(rpo)
                .ultimaPruebaRestauracion(ultimaPrueba != null ? ultimaPrueba.getFecha() : null)
                .ultimaPruebaResultado(ultimaPrueba != null ? ultimaPrueba.getResultado() : "—")
                .ultimaPruebaHace(ultimaPrueba != null
                        ? "hace " + humanizar(Duration.between(ultimaPrueba.getFecha(), ahora)) : "nunca")
                .build();
    }

    // ── Configuración del cronograma ─────────────────────────────────────────

    /**
     * Configuración vigente del cronograma de respaldos, creando una por defecto si todavía
     * no existe ninguna fila en {@code presus.respaldo_config}.
     *
     * @return la configuración guardada (nunca {@code null})
     */
    public RespaldoConfig config() {
        return configRepo.findById(RespaldoConfig.ID_UNICO)
                .orElseGet(() -> configRepo.save(RespaldoConfig.builder()
                        .id(RespaldoConfig.ID_UNICO)
                        .activo(true)
                        .cron("0 0 23 * * SUN")
                        .retenerDiarios((short) 7)
                        .retenerSemanales((short) 5)
                        .retenerMensuales((short) 12)
                        .retenerDiasWal((short) 14)
                        .diferencialActivo(false)
                        .cronDiferencial("0 30 2 * * WED,FRI")
                        .actualizadoEn(LocalDateTime.now())
                        .build()));
    }

    /**
     * Configuración vigente del cronograma, en el DTO expuesto por la API (incluye la
     * descripción legible del cron, ver {@link #describirCron(String)}).
     *
     * @return la configuración vigente, convertida a DTO
     */
    public RespaldoConfigDTO configDTO() {
        return aConfigDTO(config());
    }

    /**
     * Actualiza la configuración del cronograma de respaldos (activación, expresión cron
     * y política de retención GFS), validando ambas expresiones cron antes de guardar nada.
     *
     * @param dto nueva configuración enviada por el administrador
     * @return la configuración ya guardada, en el mismo DTO
     * @throws IllegalArgumentException si el cron principal o el del diferencial no son
     *                                   expresiones cron válidas de 6 campos
     */
    public RespaldoConfigDTO actualizarConfig(RespaldoConfigDTO dto) {
        if (cronValido(dto.getCron()) == null) {
            throw new IllegalArgumentException(
                    "Expresión cron inválida: '" + dto.getCron() + "'. Usa el formato de 6 campos de Spring "
                    + "(ej. \"0 0 23 * * SUN\" = domingos 23:00).");
        }
        if (cronValido(dto.getCronDiferencial()) == null) {
            throw new IllegalArgumentException(
                    "Expresión cron del diferencial inválida: '" + dto.getCronDiferencial() + "'.");
        }
        RespaldoConfig c = config();
        c.setActivo(Boolean.TRUE.equals(dto.getActivo()));
        c.setCron(dto.getCron().trim());
        c.setRetenerDiarios(dto.getRetenerDiarios().shortValue());
        c.setRetenerSemanales(dto.getRetenerSemanales().shortValue());
        c.setRetenerMensuales(dto.getRetenerMensuales().shortValue());
        c.setRetenerDiasWal(dto.getRetenerDiasWal().shortValue());
        c.setDiferencialActivo(Boolean.TRUE.equals(dto.getDiferencialActivo()));
        c.setCronDiferencial(dto.getCronDiferencial().trim());
        c.setActualizadoEn(LocalDateTime.now());
        c.setActualizadoPor(usuarioActual());
        RespaldoConfig guardado = configRepo.save(c);
        log.info("Cronograma de respaldos actualizado por {}: full activo={} cron='{}' retencion={}/{}/{} "
                + "walDias={} diferencial activo={} cron='{}'",
                usuarioActual(), guardado.isActivo(), guardado.getCron(),
                guardado.getRetenerDiarios(), guardado.getRetenerSemanales(), guardado.getRetenerMensuales(),
                guardado.getRetenerDiasWal(), guardado.isDiferencialActivo(), guardado.getCronDiferencial());
        return aConfigDTO(guardado);
    }

    // ── Pruebas de restauración ─────────────────────────────────────────────

    /** @return las últimas 50 pruebas de restauración registradas, de la más reciente a la más antigua */
    public List<RespaldoPruebaRestauracion> pruebas() {
        return pruebaRepo.findTop50ByOrderByFechaDesc();
    }

    /**
     * Registra en la bitácora el resultado de una prueba de restauración manual. El respaldo
     * probado puede ya no existir en disco (se probó y se borró después): no se exige que
     * exista, solo que el nombre tenga forma válida, para no guardar basura.
     *
     * @param respaldoNombre nombre del archivo de respaldo que se probó
     * @param resultado      {@code "FALLIDA"} para marcarla como fallida; cualquier otro
     *                       valor (incluido {@code null}) se guarda como {@code "EXITOSA"}
     * @param responsable    quién ejecutó la prueba; si viene vacío se usa el usuario
     *                       autenticado actual
     * @param notas          observaciones libres de la prueba, o {@code null} si no hay
     * @return la prueba ya guardada
     * @throws IllegalArgumentException si {@code respaldoNombre} es nulo o no tiene la forma
     *                                   de un nombre de respaldo válido
     */
    public RespaldoPruebaRestauracion registrarPrueba(String respaldoNombre, String resultado,
                                                      String responsable, String notas) {
        // el respaldo puede ya no existir (se probó y se borró) -> no se exige que exista,
        // pero el nombre sí se valida contra el patrón para no guardar basura.
        if (respaldoNombre == null || !NOMBRE_VALIDO.matcher(respaldoNombre).matches()) {
            throw new IllegalArgumentException("Nombre de respaldo inválido.");
        }
        RespaldoPruebaRestauracion p = RespaldoPruebaRestauracion.builder()
                .respaldoNombre(respaldoNombre)
                .fecha(LocalDateTime.now())
                .resultado("FALLIDA".equals(resultado) ? "FALLIDA" : "EXITOSA")
                .responsable(responsable != null && !responsable.isBlank() ? responsable.trim() : usuarioActual())
                .notas(notas != null && !notas.isBlank() ? notas.trim() : null)
                .build();
        return pruebaRepo.save(p);
    }

    // ── Generación ──────────────────────────────────────────────────────────

    /** Compat: FULL manual. @return el respaldo FULL manual generado */
    public BackupInfoDTO generar() {
        return generar(TipoRespaldo.FULL, OrigenRespaldo.MANUAL);
    }

    /**
     * Genera un respaldo con {@code pg_dump -Fc} etiquetado con su tipo y origen.
     *
     * @param tipo   FULL o DIFERENCIAL (para el diferencial real, ver
     *               {@link #generarDiferencial(OrigenRespaldo)})
     * @param origen quién lo disparó: MANUAL, AUTOMATICO o EVENTO
     * @return metadatos del archivo de respaldo generado
     * @throws RuntimeException si {@code pg_dump} falla o termina sin error pero deja el
     *                          archivo vacío
     */
    public BackupInfoDTO generar(TipoRespaldo tipo, OrigenRespaldo origen) {
        Conexion c = parsearConexion();
        Path dir = crearDirectorio();
        String nombre = "respaldo_" + tipo.name() + "_" + origen.name() + "_"
                + LocalDateTime.now().format(SELLO) + ".dump";
        Path destino = dir.resolve(nombre);

        List<String> comando = List.of(
                "pg_dump",
                "-h", c.host, "-p", c.port, "-U", c.usuario, "-d", c.baseDatos,
                "--format=custom", "--compress=6", "--no-owner", "--no-privileges",
                "-f", destino.toAbsolutePath().toString());

        ejecutar(comando, "pg_dump", "generar el respaldo");

        if (!Files.isRegularFile(destino) || tamano(destino) == 0L) {
            throw new RuntimeException("pg_dump terminó sin error pero el archivo de respaldo quedó vacío.");
        }
        log.info("Respaldo {} / {} generado: {} ({} bytes) por {}",
                tipo, origen, nombre, tamano(destino), usuarioActual());
        return aInfo(destino);
    }

    /**
     * Fecha del respaldo AUTOMÁTICO más reciente, o una fecha muy antigua si no hay ninguno.
     *
     * @return la fecha de creación del último respaldo automático, o {@code now() - 10 años}
     *         si nunca se ha generado uno (para que el scheduler lo trate como "ya toca")
     */
    public LocalDateTime fechaUltimoAutomatico() {
        return listar().stream()
                .filter(b -> OrigenRespaldo.AUTOMATICO.name().equals(b.getOrigen()))
                .map(BackupInfoDTO::getFechaCreacion)
                .max(Comparator.naturalOrder())
                .orElse(LocalDateTime.now().minusYears(10));
    }

    /**
     * Fecha del último AUTOMÁTICO DIFERENCIAL, o muy antigua si no hay ninguno (para el scheduler).
     *
     * @return la fecha de creación del último diferencial automático, o {@code now() - 10 años}
     *         si nunca se ha generado uno
     */
    public LocalDateTime fechaUltimoDiferencialAutomatico() {
        return listar().stream()
                .filter(b -> TipoRespaldo.DIFERENCIAL.name().equals(b.getTipo()))
                .filter(b -> OrigenRespaldo.AUTOMATICO.name().equals(b.getOrigen()))
                .map(BackupInfoDTO::getFechaCreacion)
                .max(Comparator.naturalOrder())
                .orElse(LocalDateTime.now().minusYears(10));
    }

    /**
     * Genera un respaldo DIFERENCIAL: las filas cambiadas (INSERT/UPDATE) desde el último
     * FULL, más la lista de ids eliminados, empaquetadas en un {@code .tar.gz} de CSVs.
     * El "qué cambió" se resuelve por la tabla de auditoría (V15). Se restaura aplicando
     * los CSV sobre una restauración del FULL base (procedimiento en el plan §4).
     *
     * @param origen quién lo disparó: MANUAL o AUTOMATICO
     * @return metadatos del archivo {@code .tar.gz} generado
     * @throws IllegalStateException si no existe ningún respaldo FULL del que partir
     * @throws RuntimeException      si falla la exportación con {@code psql}, la creación del
     *                                {@code .tar.gz}, o cualquier operación de archivo
     */
    public BackupInfoDTO generarDiferencial(OrigenRespaldo origen) {
        BackupInfoDTO ultimoFull = listar().stream()
                .filter(b -> TipoRespaldo.FULL.name().equals(b.getTipo()))
                .max(Comparator.comparing(BackupInfoDTO::getFechaCreacion))
                .orElseThrow(() -> new IllegalStateException(
                        "No hay ningún respaldo FULL del que partir. Genera un FULL primero."));

        Conexion c = parsearConexion();
        Path dir = crearDirectorio();
        String sello = LocalDateTime.now().format(SELLO);
        String fullTs = ultimoFull.getFechaCreacion().format(TS_SQL);
        Path work = dir.resolve("dif_tmp_" + sello);
        try {
            Files.createDirectories(work);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo crear el directorio temporal del diferencial: " + e.getMessage(), e);
        }

        int totalFilas = 0;
        StringBuilder manifiesto = new StringBuilder()
                .append("full_base=").append(ultimoFull.getNombre()).append('\n')
                .append("full_base_fecha=").append(fullTs).append('\n')
                .append("generado=").append(LocalDateTime.now()).append('\n');
        try {
            for (String tabla : TABLAS_DIFERENCIAL) {
                String archivo = work.resolve(tabla + ".csv").toAbsolutePath().toString();
                String sql = "\\copy (SELECT t.* FROM presus." + tabla + " t WHERE t.id IN "
                        + "(SELECT DISTINCT registro_id FROM presus.auditoria WHERE tabla = '" + tabla
                        + "' AND fecha > '" + fullTs + "' AND accion <> 'ELIMINAR' AND registro_id IS NOT NULL)) "
                        + "TO '" + archivo + "' WITH (FORMAT csv, HEADER true)";
                Proceso r = correr(List.of("psql", "-h", c.host, "-p", c.port, "-U", c.usuario,
                        "-d", c.baseDatos, "-v", "ON_ERROR_STOP=1", "-q", "-c", sql), "psql");
                if (r.codigo != 0) {
                    throw new RuntimeException("psql falló exportando '" + tabla + "': " + resumirError(r.salida));
                }
                long filas = contarLineasCsv(work.resolve(tabla + ".csv"));
                totalFilas += filas;
                manifiesto.append("tabla.").append(tabla).append('=').append(filas).append('\n');
            }
            // ids eliminados desde el FULL (para que el restore también los quite)
            String archivoDel = work.resolve("_eliminados.csv").toAbsolutePath().toString();
            String sqlDel = "\\copy (SELECT tabla, registro_id, fecha FROM presus.auditoria "
                    + "WHERE accion = 'ELIMINAR' AND fecha > '" + fullTs + "') "
                    + "TO '" + archivoDel + "' WITH (FORMAT csv, HEADER true)";
            correr(List.of("psql", "-h", c.host, "-p", c.port, "-U", c.usuario, "-d", c.baseDatos,
                    "-v", "ON_ERROR_STOP=1", "-q", "-c", sqlDel), "psql");

            manifiesto.append("filas_total=").append(totalFilas).append('\n');
            Files.writeString(work.resolve("manifest.txt"), manifiesto.toString());

            String nombre = "respaldo_DIFERENCIAL_" + origen.name() + "_" + sello + ".tar.gz";
            Path destino = dir.resolve(nombre);
            Proceso tar = correr(List.of("tar", "-czf", destino.toAbsolutePath().toString(),
                    "-C", work.toAbsolutePath().toString(), "."), "tar");
            if (tar.codigo != 0 || !Files.isRegularFile(destino)) {
                throw new RuntimeException("No se pudo empaquetar el diferencial: " + resumirError(tar.salida));
            }
            log.info("Respaldo DIFERENCIAL / {} generado: {} ({} filas desde {}) por {}",
                    origen, nombre, totalFilas, fullTs, usuarioActual());
            return aInfo(destino);
        } catch (IOException e) {
            throw new RuntimeException("Error generando el diferencial: " + e.getMessage(), e);
        } finally {
            borrarDirRecursivo(work);
        }
    }

    private static long contarLineasCsv(Path csv) {
        try (Stream<String> lineas = Files.lines(csv)) {
            long n = lineas.count();
            return n > 0 ? n - 1 : 0; // menos la cabecera
        } catch (IOException e) {
            return 0;
        }
    }

    private static void borrarDirRecursivo(Path d) {
        if (d == null || !Files.exists(d)) return;
        try (Stream<Path> w = Files.walk(d)) {
            w.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignore) {}
            });
        } catch (IOException ignore) {}
    }

    // ── Retención GFS ───────────────────────────────────────────────────────

    /**
     * Aplica la política grandfather-father-son sobre las copias <b>AUTOMÁTICAS</b> de tipo
     * FULL. Las copias MANUAL y EVENTO nunca se tocan. Siempre se conserva, como mínimo, la
     * automática más reciente aunque la configuración diga 0.
     *
     * @return nombres de los archivos eliminados
     */
    public List<String> aplicarRetencion() {
        RespaldoConfig cfg = config();
        List<BackupInfoDTO> autos = listar().stream()
                .filter(b -> TipoRespaldo.FULL.name().equals(b.getTipo()))
                .filter(b -> OrigenRespaldo.AUTOMATICO.name().equals(b.getOrigen()))
                .sorted(Comparator.comparing(BackupInfoDTO::getFechaCreacion).reversed())
                .toList();
        if (autos.isEmpty()) {
            return List.of();
        }

        Set<String> conservar = new LinkedHashSet<>();
        conservar.add(autos.get(0).getNombre()); // salvavidas: nunca quedarse sin ninguna

        int diarios = Math.max(0, cfg.getRetenerDiarios());
        for (int i = 0; i < Math.min(diarios, autos.size()); i++) {
            conservar.add(autos.get(i).getNombre());
        }

        // Semanal: 1 por semana ISO entre las que no cayeron en el nivel diario.
        Set<String> semanas = new LinkedHashSet<>();
        for (BackupInfoDTO b : autos) {
            if (conservar.contains(b.getNombre())) continue;
            String clave = b.getFechaCreacion().get(IsoFields.WEEK_BASED_YEAR)
                    + "-W" + b.getFechaCreacion().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            if (semanas.contains(clave)) continue;
            if (semanas.size() >= cfg.getRetenerSemanales()) continue;
            semanas.add(clave);
            conservar.add(b.getNombre());
        }

        // Mensual: 1 por mes entre las que quedan.
        Set<String> meses = new LinkedHashSet<>();
        for (BackupInfoDTO b : autos) {
            if (conservar.contains(b.getNombre())) continue;
            String clave = b.getFechaCreacion().getYear() + "-" + b.getFechaCreacion().getMonthValue();
            if (meses.contains(clave)) continue;
            if (meses.size() >= cfg.getRetenerMensuales()) continue;
            meses.add(clave);
            conservar.add(b.getNombre());
        }

        List<String> eliminados = new ArrayList<>();
        Path dir = Paths.get(backupsDir);
        for (BackupInfoDTO b : autos) {
            if (conservar.contains(b.getNombre())) continue;
            try {
                Files.deleteIfExists(dir.resolve(b.getNombre()));
                eliminados.add(b.getNombre());
            } catch (IOException e) {
                log.warn("Retención: no se pudo borrar {}: {}", b.getNombre(), e.getMessage());
            }
        }
        if (!eliminados.isEmpty()) {
            log.info("Retención GFS: {} copias automáticas eliminadas, {} conservadas.",
                    eliminados.size(), conservar.size());
        }
        return eliminados;
    }

    // ── Descargar / restaurar / eliminar ────────────────────────────────────

    /**
     * Lee el contenido crudo de un respaldo, para descargarlo.
     *
     * @param nombre nombre del archivo de respaldo
     * @return el contenido completo del archivo
     * @throws IllegalArgumentException si el nombre es inválido, intenta salir del
     *                                   directorio de respaldos, o el archivo no existe
     * @throws RuntimeException         si falla la lectura del archivo en disco
     */
    public byte[] leer(String nombre) {
        Path archivo = resolverExistente(nombre);
        try {
            return Files.readAllBytes(archivo);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo leer el respaldo: " + e.getMessage(), e);
        }
    }

    /**
     * Restaura la base de datos completa desde un respaldo FULL con {@code pg_restore}
     * (operación destructiva: reemplaza el contenido actual).
     *
     * @param nombre nombre del respaldo FULL a restaurar
     * @throws IllegalArgumentException si el nombre es inválido, el archivo no existe, o es
     *                                   un respaldo DIFERENCIAL (esos no se restauran solos)
     */
    public void restaurar(String nombre) {
        Path archivo = resolverExistente(nombre);
        if (nombre.endsWith(".tar.gz")) {
            throw new IllegalArgumentException(
                    "Un respaldo DIFERENCIAL no se restaura solo: se aplica sobre una restauración del "
                    + "FULL base. Descárgalo y sigue el procedimiento del plan (§4): restaurar el FULL "
                    + "y luego \\copy de cada CSV del .tar.gz.");
        }
        Conexion c = parsearConexion();
        List<String> comando = List.of(
                "pg_restore",
                "-h", c.host, "-p", c.port, "-U", c.usuario, "-d", c.baseDatos,
                "--clean", "--if-exists", "--no-owner", "--no-privileges",
                archivo.toAbsolutePath().toString());
        int codigo = ejecutarTolerante(comando, "pg_restore");
        log.warn("Restauración de base ejecutada desde {} por {} (código pg_restore={})",
                nombre, usuarioActual(), codigo);
    }

    /**
     * Elimina permanentemente un archivo de respaldo.
     *
     * @param nombre nombre del respaldo a eliminar
     * @throws IllegalArgumentException si el nombre es inválido o el archivo no existe
     * @throws RuntimeException         si falla el borrado en disco
     */
    public void eliminar(String nombre) {
        Path archivo = resolverExistente(nombre);
        try {
            Files.delete(archivo);
            log.info("Respaldo eliminado: {} por {}", nombre, usuarioActual());
        } catch (IOException e) {
            throw new RuntimeException("No se pudo eliminar el respaldo: " + e.getMessage(), e);
        }
    }

    // ── Internos ────────────────────────────────────────────────────────────

    private record Conexion(String host, String port, String baseDatos, String usuario) {}

    private Conexion parsearConexion() {
        Matcher m = JDBC_URL.matcher(datasourceUrl == null ? "" : datasourceUrl.trim());
        if (!m.matches()) {
            throw new IllegalStateException(
                    "No se pudo interpretar la URL de la base de datos para generar el respaldo.");
        }
        String host = m.group(1);
        String port = m.group(2) != null ? m.group(2) : "5432";
        return new Conexion(host, port, m.group(3), dbUsername);
    }

    private Path crearDirectorio() {
        try {
            Path dir = Paths.get(backupsDir);
            Files.createDirectories(dir);
            return dir;
        } catch (IOException e) {
            throw new RuntimeException("No se pudo crear el directorio de respaldos: " + e.getMessage(), e);
        }
    }

    private Path resolverExistente(String nombre) {
        if (nombre == null || !NOMBRE_VALIDO.matcher(nombre).matches() || nombre.contains("..")) {
            throw new IllegalArgumentException("Nombre de respaldo inválido.");
        }
        Path dir = Paths.get(backupsDir).toAbsolutePath().normalize();
        Path archivo = dir.resolve(nombre).normalize();
        if (!archivo.getParent().equals(dir)) {
            throw new IllegalArgumentException("Nombre de respaldo inválido.");
        }
        if (!Files.isRegularFile(archivo)) {
            throw new IllegalArgumentException("El respaldo '" + nombre + "' no existe.");
        }
        return archivo;
    }

    private void ejecutar(List<String> comando, String binario, String descripcion) {
        Proceso r = correr(comando, binario);
        if (r.codigo != 0) {
            log.error("{} falló (código {}): {}", binario, r.codigo, r.salida);
            throw new RuntimeException("No se pudo " + descripcion + ": " + resumirError(r.salida));
        }
    }

    private int ejecutarTolerante(List<String> comando, String binario) {
        Proceso r = correr(comando, binario);
        if (!r.salida.isBlank()) {
            log.warn("{} avisos: {}", binario, r.salida);
        }
        return r.codigo;
    }

    private record Proceso(int codigo, String salida) {}

    private Proceso correr(List<String> comando, String binario) {
        ProcessBuilder pb = new ProcessBuilder(comando);
        pb.environment().put("PGPASSWORD", dbPassword == null ? "" : dbPassword);
        pb.redirectErrorStream(true);
        Process proceso;
        try {
            proceso = pb.start();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "El comando '" + binario + "' no está disponible en el servidor. "
                    + "Revisa que la imagen del backend incluya postgresql-client.");
        }
        String salida;
        try {
            salida = new String(proceso.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!proceso.waitFor(TIMEOUT_MINUTOS, TimeUnit.MINUTES)) {
                proceso.destroyForcibly();
                throw new RuntimeException(
                        "El respaldo excedió el tiempo máximo de " + TIMEOUT_MINUTOS + " minutos.");
            }
        } catch (IOException e) {
            throw new RuntimeException("Error leyendo la salida de " + binario + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("La operación de respaldo fue interrumpida.");
        }
        return new Proceso(proceso.exitValue(), salida.trim());
    }

    private static String resumirError(String salida) {
        if (salida == null || salida.isBlank()) return "sin detalle (revisa los logs del backend).";
        String[] lineas = salida.strip().split("\\r?\\n");
        String ultima = lineas[lineas.length - 1].trim();
        return ultima.length() > 300 ? ultima.substring(0, 300) + "…" : ultima;
    }

    private BackupInfoDTO aInfo(Path p) {
        try {
            String nombre = p.getFileName().toString();
            long bytes = Files.size(p);
            LocalDateTime creado = LocalDateTime.ofInstant(
                    Files.getLastModifiedTime(p).toInstant(), ZoneId.systemDefault());

            String tipo = TipoRespaldo.FULL.name();
            String origen = OrigenRespaldo.MANUAL.name();
            Matcher nuevo = NOMBRE_NUEVO.matcher(nombre);
            if (nuevo.matches()) {
                tipo = nuevo.group(1);
                origen = nuevo.group(2);
            }

            return BackupInfoDTO.builder()
                    .nombre(nombre)
                    .tipo(tipo)
                    .origen(origen)
                    .tamanoBytes(bytes)
                    .tamanoLegible(formatoTamano(bytes))
                    .fechaCreacion(creado)
                    .build();
        } catch (IOException e) {
            log.warn("No se pudo leer metadatos de {}: {}", p, e.getMessage());
            return null;
        }
    }

    private long tamano(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return 0L;
        }
    }

    private long espacioLibre() {
        try {
            return Files.getFileStore(crearDirectorio()).getUsableSpace();
        } catch (Exception e) {
            return -1L;
        }
    }

    private static CronExpression cronValido(String cron) {
        if (cron == null || cron.isBlank()) return null;
        try {
            return CronExpression.parse(cron.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private RespaldoConfigDTO aConfigDTO(RespaldoConfig c) {
        return RespaldoConfigDTO.builder()
                .activo(c.isActivo())
                .cron(c.getCron())
                .retenerDiarios((int) c.getRetenerDiarios())
                .retenerSemanales((int) c.getRetenerSemanales())
                .retenerMensuales((int) c.getRetenerMensuales())
                .retenerDiasWal(c.getRetenerDiasWal() == null ? 14 : (int) c.getRetenerDiasWal())
                .diferencialActivo(c.isDiferencialActivo())
                .cronDiferencial(c.getCronDiferencial() != null ? c.getCronDiferencial() : "0 30 2 * * WED,FRI")
                .cronDescripcion(describirCron(c.getCron()))
                .cronDiferencialDescripcion(describirCron(c.getCronDiferencial()))
                .build();
    }

    /** Descripción best-effort para los presets comunes; si no reconoce, muestra el cron crudo. */
    private static String describirCron(String cron) {
        if (cron == null) return "—";
        return switch (cron.trim()) {
            case "0 0 23 * * SUN" -> "Cada domingo a las 23:00";
            case "0 0 2 * * *"    -> "Todos los días a las 02:00";
            case "0 0 3 * * *"    -> "Todos los días a las 03:00";
            case "0 0 1 * * MON-FRI" -> "De lunes a viernes a la 01:00";
            case "0 0 0 1 * *"    -> "El día 1 de cada mes a medianoche";
            case "0 30 2 * * WED,FRI" -> "Miércoles y viernes a las 02:30";
            case "0 0 * * * *"    -> "Cada hora (¡solo para pruebas!)";
            default -> "Expresión personalizada: " + cron.trim();
        };
    }

    /** "2 días", "5 horas", "12 minutos". */
    private static String humanizar(Duration d) {
        long s = Math.abs(d.getSeconds());
        if (s < 60) return "menos de 1 minuto";
        if (s < 3600) return (s / 60) + " min";
        if (s < 86400) return (s / 3600) + " h";
        long dias = s / 86400;
        return dias + (dias == 1 ? " día" : " días");
    }

    /** Intervalo aproximado entre dos disparos consecutivos del cron. */
    private static String humanizarIntervaloCron(CronExpression cron, LocalDateTime desde) {
        LocalDateTime n1 = cron.next(desde);
        if (n1 == null) return "desconocido";
        LocalDateTime n2 = cron.next(n1);
        if (n2 == null) return "desconocido";
        return humanizar(Duration.between(n1, n2));
    }

    private static String formatoTamano(long bytes) {
        if (bytes < 0) return "—";
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1f MB", mb);
        double gb = mb / 1024.0;
        if (gb < 1024) return String.format("%.2f GB", gb);
        return String.format("%.2f TB", gb / 1024.0);
    }

    private static String usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getName() != null ? auth.getName() : "sistema";
    }
}
