package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.BasePhysicalDTO;
import ec.edu.uteq.presustentaciones.dto.StatusWalDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * WalPitrService no tenia ningun test (0% de ramas, 90 sin ejercitar segun JaCoCo). Prueba
 * todo lo que es seguro probar sin invocar pg_basebackup real: el panel de estado (con sus
 * combinaciones de archivado activo/inactivo y base fisica presente/ausente), la limpieza
 * de WAL viejo, y el listado/eliminacion de bases fisicas sobre un directorio temporal.
 * generateBaseFisica() solo se prueba en su validacion previa a invocar el binario externo.
 */
@ExtendWith(MockitoExtension.class)
class WalPitrServiceTest {

    @TempDir
    Path tempDir;

    private Path walDir;
    private Path baseBackupsParentDir;

    @Mock
    private JdbcTemplate jdbc;

    private WalPitrService service;

    @BeforeEach
    void setUp() throws IOException {
        walDir = tempDir.resolve("wal");
        Files.createDirectories(walDir);
        baseBackupsParentDir = tempDir.resolve("backups");
        Files.createDirectories(baseBackupsParentDir);

        service = new WalPitrService(jdbc);
        ReflectionTestUtils.setField(service, "walDir", walDir.toString());
        ReflectionTestUtils.setField(service, "backupsDir", baseBackupsParentDir.resolve("respaldos").toString());
        ReflectionTestUtils.setField(service, "datasourceUrl", "jdbc:postgresql://localhost:5432/BdPresustentaciones");
        ReflectionTestUtils.setField(service, "dbUsername", "postgres");
        ReflectionTestUtils.setField(service, "dbPassword", "x");
    }

    private Map<String, Object> rowArchiver(String archiveMode) {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("archive_mode", archiveMode);
        row.put("wal_level", "replica");
        row.put("archive_command", "test ! -f /wal/%f && cp %p /wal/%f");
        row.put("archive_timeout", "60");
        row.put("archived_count", "42");
        row.put("last_archived_wal", "000000010000000000000005");
        row.put("last_archived_time", null);
        row.put("failed_count", "0");
        row.put("last_failed_time", null);
        return row;
    }

    private void createBasePhysical(String nombre) throws IOException {
        Path base = tempDir.resolve("backups").resolve("bases").resolve(nombre);
        Files.createDirectories(base);
        Files.writeString(base.resolve("base.tar.gz"), "contenido");
    }

    // ── estado ───────────────────────────────────────────────────────────────

    @Test
    void statusWarnsPitrNotAvailableIfArchivedIsDisabled() {
        when(jdbc.queryForMap(anyString())).thenReturn(rowArchiver("off"));

        StatusWalDTO dto = service.status();

        assertFalse(dto.isArchivadoActivo());
        assertTrue(dto.getPitrAvailableFrom().contains("desactivado"));
        assertNotNull(dto.getAdvertencia());
    }

    @Test
    void statusWarnsPitrNotAvailableIfMissingBasePhysicalStillWithArchivedActive() {
        when(jdbc.queryForMap(anyString())).thenReturn(rowArchiver("on"));

        StatusWalDTO dto = service.status();

        assertTrue(dto.isArchivadoActivo());
        assertFalse(dto.isHayBasePhysical());
        assertTrue(dto.getPitrAvailableFrom().contains("falta una base"));
        assertNotNull(dto.getAdvertencia());
    }

    @Test
    void statusReportsPitrAvailableWithArchivedActiveAndBasePhysical() throws IOException {
        when(jdbc.queryForMap(anyString())).thenReturn(rowArchiver("on"));
        createBasePhysical("base_20260101_000000");

        StatusWalDTO dto = service.status();

        assertTrue(dto.isHayBasePhysical());
        assertNull(dto.getAdvertencia());
        assertFalse(dto.getPitrAvailableFrom().contains("no disponible"));
    }

    @Test
    void statusNotBreaksIfQueryToPostgresFails() {
        when(jdbc.queryForMap(anyString())).thenThrow(new RuntimeException("conexión rechazada"));

        StatusWalDTO dto = service.status();

        assertFalse(dto.isArchivadoActivo());
        assertNotNull(dto.getPitrAvailableFrom());
    }

    @Test
    void statusAccountSegmentsWalRealInDirectory() throws IOException {
        when(jdbc.queryForMap(anyString())).thenReturn(rowArchiver("on"));
        Files.writeString(walDir.resolve("0000000100000000000000A1"), "segmento-real");
        Files.writeString(walDir.resolve("no-es-un-segmento.txt"), "ignorar");

        StatusWalDTO dto = service.status();

        assertEquals(1, dto.getSegmentosEnDisco());
    }

    // ── forzarSwitchWal ──────────────────────────────────────────────────────

    @Test
    void forceSwitchWalReturnsNameOfSegmentClosed() {
        when(jdbc.queryForObject(anyString(), (Class<String>) any())).thenReturn("000000010000000000000009");

        assertEquals("000000010000000000000009", service.forceSwitchWal());
    }

    @Test
    void forceSwitchWalPropagatesFailurePlainIfPostgresFails() {
        when(jdbc.queryForObject(anyString(), (Class<String>) any())).thenThrow(new RuntimeException("sin permisos"));

        assertThrows(RuntimeException.class, () -> service.forceSwitchWal());
    }

    // ── limpiarWal ───────────────────────────────────────────────────────────

    @Test
    void cleanWalReturnsZeroIfDirectoryNotExists() {
        ReflectionTestUtils.setField(service, "walDir", tempDir.resolve("no-existe").toString());
        assertEquals(0, service.cleanWal(7));
    }

    @Test
    void cleanWalDeletesSegmentsMoreOldThatCutoffAndKeepsRecent() throws IOException {
        Path viejo = walDir.resolve("0000000100000000000000A1");
        Path reciente = walDir.resolve("0000000100000000000000A2");
        Files.writeString(viejo, "x");
        Files.writeString(reciente, "y");
        Files.setLastModifiedTime(viejo, java.nio.file.attribute.FileTime.from(
                java.time.Instant.now().minusSeconds(30L * 86400)));
        // 'reciente' conserva su mtime real (ahora), asi que sobrevive al corte de 7 dias.

        int borrados = service.cleanWal(7);

        assertEquals(1, borrados);
        assertFalse(Files.exists(viejo));
        assertTrue(Files.exists(reciente));
    }

    @Test
    void cleanWalIgnoresFilesThatNotAreSegmentsOrBackupOrHistory() throws IOException {
        Path ajeno = walDir.resolve("readme.txt");
        Files.writeString(ajeno, "no tocar");
        Files.setLastModifiedTime(ajeno, java.nio.file.attribute.FileTime.from(
                java.time.Instant.now().minusSeconds(365L * 86400)));

        assertEquals(0, service.cleanWal(7));
        assertTrue(Files.exists(ajeno));
    }

    // ── listBases / deleteBase ───────────────────────────────────────────

    @Test
    void listBaseBackupsReturnsEmptyListIfNoneExists() {
        assertEquals(List.of(), service.listBaseBackups());
    }

    @Test
    void listBaseBackupsFindsFoldersBaseSortedByDateDescending() throws IOException {
        // Las dos fechas se fijan a mano, con un mes de diferencia. Antes solo se
        // fijaba la de la segunda carpeta y la de la primera quedaba en su hora
        // real de creacion: las separaba ~1 ms, y bastaba con que la maquina
        // fuera mas lenta (p. ej. bajo la instrumentacion de JaCoCo) para que
        // empataran y el orden quedara indefinido. El test no mide el reloj,
        // comprueba que se ordena por fecha descendente.
        createBasePhysical("base_20260101_000000");
        Path primera = tempDir.resolve("backups").resolve("bases").resolve("base_20260101_000000");
        Files.setLastModifiedTime(primera, java.nio.file.attribute.FileTime.from(
                java.time.Instant.parse("2026-01-01T00:00:00Z")));

        Path segunda = tempDir.resolve("backups").resolve("bases").resolve("base_20260201_000000");
        Files.createDirectories(segunda);
        Files.writeString(segunda.resolve("x.tar.gz"), "y");
        Files.setLastModifiedTime(segunda, java.nio.file.attribute.FileTime.from(
                java.time.Instant.parse("2026-02-01T00:00:00Z")));

        List<BasePhysicalDTO> baseBackups = service.listBaseBackups();

        assertEquals(2, baseBackups.size());
        assertEquals("base_20260201_000000", baseBackups.get(0).getNombre());
    }

    @Test
    void deleteBaseRejectsNameInvalid() {
        assertThrows(IllegalArgumentException.class, () -> service.deleteBase("../etc/passwd"));
    }

    @Test
    void deleteBaseRejectsBaseThatNotExists() {
        assertThrows(IllegalArgumentException.class, () -> service.deleteBase("base_20260101_000000"));
    }

    @Test
    void deleteBaseDeletesFolderComplete() throws IOException {
        createBasePhysical("base_20260101_000000");

        service.deleteBase("base_20260101_000000");

        assertFalse(Files.exists(tempDir.resolve("backups").resolve("bases").resolve("base_20260101_000000")));
    }

    // ── generateBaseFisica: solo la validacion previa al binario externo ──────

    @Test
    void generateBasePhysicalFailsEarlyIfUrlOfConnectionNotIsInterpretable() {
        ReflectionTestUtils.setField(service, "datasourceUrl", "no-es-una-url-jdbc");

        assertThrows(IllegalStateException.class, () -> service.generateBasePhysical());
    }
}
