package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.BaseFisicaDTO;
import ec.edu.uteq.presustentaciones.dto.EstadoWalDTO;
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
 * generarBaseFisica() solo se prueba en su validacion previa a invocar el binario externo.
 */
@ExtendWith(MockitoExtension.class)
class WalPitrServiceTest {

    @TempDir
    Path tempDir;

    private Path walDir;
    private Path basesParentDir;

    @Mock
    private JdbcTemplate jdbc;

    private WalPitrService service;

    @BeforeEach
    void setUp() throws IOException {
        walDir = tempDir.resolve("wal");
        Files.createDirectories(walDir);
        basesParentDir = tempDir.resolve("backups");
        Files.createDirectories(basesParentDir);

        service = new WalPitrService(jdbc);
        ReflectionTestUtils.setField(service, "walDir", walDir.toString());
        ReflectionTestUtils.setField(service, "backupsDir", basesParentDir.resolve("respaldos").toString());
        ReflectionTestUtils.setField(service, "datasourceUrl", "jdbc:postgresql://localhost:5432/BdPresustentaciones");
        ReflectionTestUtils.setField(service, "dbUsername", "postgres");
        ReflectionTestUtils.setField(service, "dbPassword", "x");
    }

    private Map<String, Object> filaArchiver(String archiveMode) {
        Map<String, Object> fila = new java.util.HashMap<>();
        fila.put("archive_mode", archiveMode);
        fila.put("wal_level", "replica");
        fila.put("archive_command", "test ! -f /wal/%f && cp %p /wal/%f");
        fila.put("archive_timeout", "60");
        fila.put("archived_count", "42");
        fila.put("last_archived_wal", "000000010000000000000005");
        fila.put("last_archived_time", null);
        fila.put("failed_count", "0");
        fila.put("last_failed_time", null);
        return fila;
    }

    private void crearBaseFisica(String nombre) throws IOException {
        Path base = tempDir.resolve("backups").resolve("bases").resolve(nombre);
        Files.createDirectories(base);
        Files.writeString(base.resolve("base.tar.gz"), "contenido");
    }

    // ── estado ───────────────────────────────────────────────────────────────

    @Test
    void estadoAdviertePitrNoDisponibleSiElArchivadoEstaDesactivado() {
        when(jdbc.queryForMap(anyString())).thenReturn(filaArchiver("off"));

        EstadoWalDTO dto = service.estado();

        assertFalse(dto.isArchivadoActivo());
        assertTrue(dto.getPitrDisponibleDesde().contains("desactivado"));
        assertNotNull(dto.getAdvertencia());
    }

    @Test
    void estadoAdviertePitrNoDisponibleSiFaltaBaseFisicaAunConArchivadoActivo() {
        when(jdbc.queryForMap(anyString())).thenReturn(filaArchiver("on"));

        EstadoWalDTO dto = service.estado();

        assertTrue(dto.isArchivadoActivo());
        assertFalse(dto.isHayBaseFisica());
        assertTrue(dto.getPitrDisponibleDesde().contains("falta una base"));
        assertNotNull(dto.getAdvertencia());
    }

    @Test
    void estadoReportaPitrDisponibleConArchivadoActivoYBaseFisica() throws IOException {
        when(jdbc.queryForMap(anyString())).thenReturn(filaArchiver("on"));
        crearBaseFisica("base_20260101_000000");

        EstadoWalDTO dto = service.estado();

        assertTrue(dto.isHayBaseFisica());
        assertNull(dto.getAdvertencia());
        assertFalse(dto.getPitrDisponibleDesde().contains("no disponible"));
    }

    @Test
    void estadoNoRompeSiLaConsultaAPostgresFalla() {
        when(jdbc.queryForMap(anyString())).thenThrow(new RuntimeException("conexión rechazada"));

        EstadoWalDTO dto = service.estado();

        assertFalse(dto.isArchivadoActivo());
        assertNotNull(dto.getPitrDisponibleDesde());
    }

    @Test
    void estadoCuentaLosSegmentosWalRealesEnElDirectorio() throws IOException {
        when(jdbc.queryForMap(anyString())).thenReturn(filaArchiver("on"));
        Files.writeString(walDir.resolve("0000000100000000000000A1"), "segmento-real");
        Files.writeString(walDir.resolve("no-es-un-segmento.txt"), "ignorar");

        EstadoWalDTO dto = service.estado();

        assertEquals(1, dto.getSegmentosEnDisco());
    }

    // ── forzarSwitchWal ──────────────────────────────────────────────────────

    @Test
    void forzarSwitchWalDevuelveElNombreDelSegmentoCerrado() {
        when(jdbc.queryForObject(anyString(), (Class<String>) any())).thenReturn("000000010000000000000009");

        assertEquals("000000010000000000000009", service.forzarSwitchWal());
    }

    @Test
    void forzarSwitchWalPropagaUnErrorClaroSiPostgresFalla() {
        when(jdbc.queryForObject(anyString(), (Class<String>) any())).thenThrow(new RuntimeException("sin permisos"));

        assertThrows(RuntimeException.class, () -> service.forzarSwitchWal());
    }

    // ── limpiarWal ───────────────────────────────────────────────────────────

    @Test
    void limpiarWalDevuelveCeroSiElDirectorioNoExiste() {
        ReflectionTestUtils.setField(service, "walDir", tempDir.resolve("no-existe").toString());
        assertEquals(0, service.limpiarWal(7));
    }

    @Test
    void limpiarWalBorraSegmentosMasViejosQueElCorteYConservaLosRecientes() throws IOException {
        Path viejo = walDir.resolve("0000000100000000000000A1");
        Path reciente = walDir.resolve("0000000100000000000000A2");
        Files.writeString(viejo, "x");
        Files.writeString(reciente, "y");
        Files.setLastModifiedTime(viejo, java.nio.file.attribute.FileTime.from(
                java.time.Instant.now().minusSeconds(30L * 86400)));
        // 'reciente' conserva su mtime real (ahora), asi que sobrevive al corte de 7 dias.

        int borrados = service.limpiarWal(7);

        assertEquals(1, borrados);
        assertFalse(Files.exists(viejo));
        assertTrue(Files.exists(reciente));
    }

    @Test
    void limpiarWalIgnoraArchivosQueNoSonSegmentosNiBackupNiHistory() throws IOException {
        Path ajeno = walDir.resolve("readme.txt");
        Files.writeString(ajeno, "no tocar");
        Files.setLastModifiedTime(ajeno, java.nio.file.attribute.FileTime.from(
                java.time.Instant.now().minusSeconds(365L * 86400)));

        assertEquals(0, service.limpiarWal(7));
        assertTrue(Files.exists(ajeno));
    }

    // ── listarBases / eliminarBase ───────────────────────────────────────────

    @Test
    void listarBasesDevuelveListaVaciaSiNoHayNinguna() {
        assertEquals(List.of(), service.listarBases());
    }

    @Test
    void listarBasesEncuentraLasCarpetasBaseOrdenadasPorFechaDescendente() throws IOException {
        crearBaseFisica("base_20260101_000000");
        Path segunda = tempDir.resolve("backups").resolve("bases").resolve("base_20260201_000000");
        Files.createDirectories(segunda);
        Files.writeString(segunda.resolve("x.tar.gz"), "y");
        Files.setLastModifiedTime(segunda, java.nio.file.attribute.FileTime.from(java.time.Instant.now()));

        List<BaseFisicaDTO> bases = service.listarBases();

        assertEquals(2, bases.size());
        assertEquals("base_20260201_000000", bases.get(0).getNombre());
    }

    @Test
    void eliminarBaseRechazaUnNombreInvalido() {
        assertThrows(IllegalArgumentException.class, () -> service.eliminarBase("../etc/passwd"));
    }

    @Test
    void eliminarBaseRechazaUnaBaseQueNoExiste() {
        assertThrows(IllegalArgumentException.class, () -> service.eliminarBase("base_20260101_000000"));
    }

    @Test
    void eliminarBaseBorraLaCarpetaCompleta() throws IOException {
        crearBaseFisica("base_20260101_000000");

        service.eliminarBase("base_20260101_000000");

        assertFalse(Files.exists(tempDir.resolve("backups").resolve("bases").resolve("base_20260101_000000")));
    }

    // ── generarBaseFisica: solo la validacion previa al binario externo ──────

    @Test
    void generarBaseFisicaFallaTempranoSiLaUrlDeConexionNoEsInterpretable() {
        ReflectionTestUtils.setField(service, "datasourceUrl", "no-es-una-url-jdbc");

        assertThrows(IllegalStateException.class, () -> service.generarBaseFisica());
    }
}
