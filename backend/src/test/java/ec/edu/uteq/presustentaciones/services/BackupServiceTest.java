package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.BackupInfoDTO;
import ec.edu.uteq.presustentaciones.dto.EstadoBackupsDTO;
import ec.edu.uteq.presustentaciones.dto.BackupConfigDTO;
import ec.edu.uteq.presustentaciones.entities.BackupConfig;
import ec.edu.uteq.presustentaciones.entities.BackupPruebaRestauracion;
import ec.edu.uteq.presustentaciones.repositories.BackupConfigRepository;
import ec.edu.uteq.presustentaciones.repositories.BackupPruebaRestauracionRepository;
import ec.edu.uteq.presustentaciones.services.backup.OrigenBackup;
import ec.edu.uteq.presustentaciones.services.backup.TipoBackup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * BackupService no tenia ningun test (0% de ramas, 162 sin ejercitar segun JaCoCo) pese a
 * ser la logica de negocio mas grande del backend sin cubrir. Prueba todo lo que es seguro
 * probar sin invocar binarios externos reales (pg_dump/pg_restore/psql/tar): listado real
 * de archivos en un directorio temporal, la politica de retencion GFS completa, la
 * configuracion del schedule, la bitacora de pruebas de restauracion, y las validaciones
 * de nombre/ruta. Los metodos que si invocan un binario externo (generate, generateDiferencial,
 * restore) solo se prueban en su rama de validacion previa a invocarlo.
 */
@ExtendWith(MockitoExtension.class)
class BackupServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private BackupConfigRepository configRepo;

    @Mock
    private BackupPruebaRestauracionRepository pruebaRepo;

    private BackupService backupService;

    @BeforeEach
    void setUp() {
        backupService = new BackupService(configRepo, pruebaRepo);
        ReflectionTestUtils.setField(backupService, "backupsDir", tempDir.toString());
        ReflectionTestUtils.setField(backupService, "datasourceUrl", "jdbc:postgresql://localhost:5432/BdPresustentaciones");
        ReflectionTestUtils.setField(backupService, "dbUsername", "postgres");
        ReflectionTestUtils.setField(backupService, "dbPassword", "x");
    }

    private void createArchivo(String nombre, Instant fecha) throws IOException {
        Path p = tempDir.resolve(nombre);
        Files.writeString(p, "contenido");
        Files.setLastModifiedTime(p, FileTime.from(fecha));
    }

    private BackupConfig configPorDefecto() {
        return BackupConfig.builder()
                .id(BackupConfig.ID_UNICO)
                .activo(true)
                .cron("0 0 23 * * SUN")
                .retenerDiarios((short) 3)
                .retenerSemanales((short) 2)
                .retenerMensuales((short) 2)
                .retenerDiasWal((short) 14)
                .diferencialActivo(false)
                .cronDiferencial("0 30 2 * * WED,FRI")
                .actualizadoEn(LocalDateTime.now())
                .build();
    }

    // ── list / aInfo ───────────────────────────────────────────────────────

    @Test
    void listDevuelveListaVaciaSiElDirectorioNoExiste() {
        ReflectionTestUtils.setField(backupService, "backupsDir", tempDir.resolve("no-existe").toString());
        assertEquals(List.of(), backupService.list());
    }

    @Test
    void listIgnoraArchivosQueNoSonBackup() throws IOException {
        createArchivo("notas.txt", Instant.now());
        assertEquals(List.of(), backupService.list());
    }

    @Test
    void listParseaElFormatoNuevoConTipoYOrigen() throws IOException {
        createArchivo("respaldo_FULL_AUTOMATICO_20260907_230000.dump", Instant.now());

        List<BackupInfoDTO> resultado = backupService.list();

        assertEquals(1, resultado.size());
        assertEquals("FULL", resultado.get(0).getTipo());
        assertEquals("AUTOMATICO", resultado.get(0).getOrigen());
    }

    @Test
    void listInterpretaElFormatoAntiguoComoFullManual() throws IOException {
        createArchivo("respaldo_20260101_000000.dump", Instant.now());

        List<BackupInfoDTO> resultado = backupService.list();

        assertEquals(1, resultado.size());
        assertEquals("FULL", resultado.get(0).getTipo());
        assertEquals("MANUAL", resultado.get(0).getOrigen());
    }

    @Test
    void listOrdenaDelMasRecienteAlMasAntiguo() throws IOException {
        Instant ahora = Instant.now();
        createArchivo("respaldo_FULL_MANUAL_20260101_000000.dump", ahora.minusSeconds(3600));
        createArchivo("respaldo_FULL_MANUAL_20260102_000000.dump", ahora);

        List<BackupInfoDTO> resultado = backupService.list();

        assertEquals(2, resultado.size());
        assertTrue(resultado.get(0).getFechaCreacion().isAfter(resultado.get(1).getFechaCreacion()));
    }

    // ── config / configDTO ───────────────────────────────────────────────────

    @Test
    void configDevuelveLaExistenteSiYaHayUnaGuardada() {
        BackupConfig existente = configPorDefecto();
        when(configRepo.findById(BackupConfig.ID_UNICO)).thenReturn(Optional.of(existente));

        assertSame(existente, backupService.config());
    }

    @Test
    void configCreaUnaPorDefectoSiNoExisteNinguna() {
        when(configRepo.findById(BackupConfig.ID_UNICO)).thenReturn(Optional.empty());
        when(configRepo.save(any(BackupConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        BackupConfig c = backupService.config();

        assertTrue(c.isActivo());
        assertEquals("0 0 23 * * SUN", c.getCron());
    }

    @Test
    void configDtoDescribeElCronReconocidoYElNoReconocido() {
        BackupConfig c = configPorDefecto();
        c.setCronDiferencial("0 15 4 * * *"); // no esta en el catalogo de presets
        when(configRepo.findById(BackupConfig.ID_UNICO)).thenReturn(Optional.of(c));

        BackupConfigDTO dto = backupService.configDTO();

        assertEquals("Cada domingo a las 23:00", dto.getCronDescripcion());
        assertTrue(dto.getCronDiferencialDescripcion().startsWith("Expresión personalizada"));
    }

    @Test
    void configDtoUsaCatorceComoRetenerDiasWalPorDefectoSiEsNull() {
        BackupConfig c = configPorDefecto();
        c.setRetenerDiasWal(null);
        when(configRepo.findById(BackupConfig.ID_UNICO)).thenReturn(Optional.of(c));

        assertEquals(14, backupService.configDTO().getRetenerDiasWal());
    }

    @Test
    void updateConfigRechazaUnCronPrincipalInvalido() {
        BackupConfigDTO dto = new BackupConfigDTO();
        dto.setCron("no-es-un-cron");
        dto.setCronDiferencial("0 30 2 * * WED,FRI");

        assertThrows(IllegalArgumentException.class, () -> backupService.updateConfig(dto));
    }

    @Test
    void updateConfigRechazaUnCronDiferencialInvalido() {
        BackupConfigDTO dto = new BackupConfigDTO();
        dto.setCron("0 0 23 * * SUN");
        dto.setCronDiferencial("no-es-un-cron");

        assertThrows(IllegalArgumentException.class, () -> backupService.updateConfig(dto));
    }

    @Test
    void updateConfigGuardaLosNuevosValores() {
        when(configRepo.findById(BackupConfig.ID_UNICO)).thenReturn(Optional.of(configPorDefecto()));
        ArgumentCaptor<BackupConfig> captor = ArgumentCaptor.forClass(BackupConfig.class);
        when(configRepo.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        BackupConfigDTO dto = new BackupConfigDTO();
        dto.setActivo(false);
        dto.setCron("0 0 2 * * *");
        dto.setRetenerDiarios(5);
        dto.setRetenerSemanales(3);
        dto.setRetenerMensuales(6);
        dto.setRetenerDiasWal(10);
        dto.setDiferencialActivo(true);
        dto.setCronDiferencial("0 0 3 * * *");

        BackupConfigDTO resultado = backupService.updateConfig(dto);

        assertFalse(resultado.getActivo());
        assertEquals("0 0 2 * * *", captor.getValue().getCron());
        assertEquals((short) 5, captor.getValue().getRetenerDiarios());
    }

    // ── pruebas / registerPrueba ────────────────────────────────────────────

    @Test
    void pruebasDelegaAlRepositorio() {
        when(pruebaRepo.findTop50ByOrderByFechaDesc()).thenReturn(List.of(BackupPruebaRestauracion.builder().build()));
        assertEquals(1, backupService.pruebas().size());
    }

    @Test
    void registerPruebaRechazaUnNombreInvalido() {
        assertThrows(IllegalArgumentException.class,
                () -> backupService.registerPrueba("../etc/passwd", "EXITOSA", "a@uteq.edu.ec", "ok"));
    }

    @Test
    void registerPruebaNormalizaResultadoDesconocidoAExitosa() {
        when(pruebaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BackupPruebaRestauracion p = backupService.registerPrueba(
                "respaldo_FULL_MANUAL_20260101_000000.dump", "cualquier-cosa", "a@uteq.edu.ec", "  notas  ");

        assertEquals("EXITOSA", p.getResultado());
        assertEquals("notas", p.getNotas());
    }

    @Test
    void registerPruebaConservaResultadoFallida() {
        when(pruebaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BackupPruebaRestauracion p = backupService.registerPrueba(
                "respaldo_FULL_MANUAL_20260101_000000.dump", "FALLIDA", "", null);

        assertEquals("FALLIDA", p.getResultado());
        assertNull(p.getNotas());
    }

    // ── aplicarRetencion (GFS) ───────────────────────────────────────────────

    @Test
    void aplicarRetencionNoHaceNadaSiNoHayAutomaticas() {
        when(configRepo.findById(BackupConfig.ID_UNICO)).thenReturn(Optional.of(configPorDefecto()));
        assertEquals(List.of(), backupService.aplicarRetencion());
    }

    @Test
    void aplicarRetencionConservaLaMasRecienteAunqueLaRetencionSeaCero() throws IOException {
        BackupConfig cfg = configPorDefecto();
        cfg.setRetenerDiarios((short) 0);
        cfg.setRetenerSemanales((short) 0);
        cfg.setRetenerMensuales((short) 0);
        when(configRepo.findById(BackupConfig.ID_UNICO)).thenReturn(Optional.of(cfg));

        createArchivo("respaldo_FULL_AUTOMATICO_20260101_000000.dump", Instant.now());

        List<String> eliminados = backupService.aplicarRetencion();

        assertEquals(List.of(), eliminados);
        assertTrue(Files.exists(tempDir.resolve("respaldo_FULL_AUTOMATICO_20260101_000000.dump")));
    }

    @Test
    void aplicarRetencionNuncaTocaManualNiEvento() throws IOException {
        BackupConfig cfg = configPorDefecto();
        cfg.setRetenerDiarios((short) 0);
        when(configRepo.findById(BackupConfig.ID_UNICO)).thenReturn(Optional.of(cfg));

        createArchivo("respaldo_FULL_MANUAL_20200101_000000.dump", Instant.now().minusSeconds(999_999_999));
        createArchivo("respaldo_FULL_EVENTO_20200101_010000.dump", Instant.now().minusSeconds(999_999_998));
        // Sin automaticas: aplicarRetencion sale por la lista vacia sin tocar nada.

        List<String> eliminados = backupService.aplicarRetencion();

        assertEquals(List.of(), eliminados);
        assertTrue(Files.exists(tempDir.resolve("respaldo_FULL_MANUAL_20200101_000000.dump")));
        assertTrue(Files.exists(tempDir.resolve("respaldo_FULL_EVENTO_20200101_010000.dump")));
    }

    @Test
    void aplicarRetencionEliminaLasAutomaticasFueraDeLaVentanaDiariaSemanalYMensual() throws IOException {
        BackupConfig cfg = configPorDefecto();
        cfg.setRetenerDiarios((short) 1);
        cfg.setRetenerSemanales((short) 0);
        cfg.setRetenerMensuales((short) 0);
        when(configRepo.findById(BackupConfig.ID_UNICO)).thenReturn(Optional.of(cfg));

        Instant hoy = Instant.now();
        // El nombre debe matchear NOMBRE_NUEVO (timestamp de 8+6 digitos) para que aInfo()
        // reconozca el origen AUTOMATICO -- si no, aInfo() lo clasifica como MANUAL por defecto.
        createArchivo("respaldo_FULL_AUTOMATICO_20260901_000000.dump", hoy);                              // conservado: diario #1
        createArchivo("respaldo_FULL_AUTOMATICO_20250901_000000.dump", hoy.minusSeconds(365L * 86400));   // hace 1 año: candidato a erase

        List<String> eliminados = backupService.aplicarRetencion();

        assertEquals(List.of("respaldo_FULL_AUTOMATICO_20250901_000000.dump"), eliminados);
        assertFalse(Files.exists(tempDir.resolve("respaldo_FULL_AUTOMATICO_20250901_000000.dump")));
        assertTrue(Files.exists(tempDir.resolve("respaldo_FULL_AUTOMATICO_20260901_000000.dump")));
    }

    @Test
    void aplicarRetencionRespetaElLimiteDeCoposSemanalesEntreVariasSemanas() throws IOException {
        BackupConfig cfg = configPorDefecto();
        cfg.setRetenerDiarios((short) 0);
        cfg.setRetenerSemanales((short) 1); // solo 1 cupo semanal ademas de la salvavidas
        cfg.setRetenerMensuales((short) 0);
        when(configRepo.findById(BackupConfig.ID_UNICO)).thenReturn(Optional.of(cfg));

        // Tres semanas ISO distintas: semana3 (mas reciente, salvavidas), semana2 (usa el
        // unico cupo semanal disponible) y semana1 (se queda sin cupo y se elimina).
        Instant semana1 = LocalDateTime.of(2026, 2, 9, 10, 0).toInstant(ZoneOffset.UTC);
        Instant semana2 = LocalDateTime.of(2026, 2, 16, 10, 0).toInstant(ZoneOffset.UTC);
        Instant semana3 = LocalDateTime.of(2026, 2, 23, 10, 0).toInstant(ZoneOffset.UTC);
        createArchivo("respaldo_FULL_AUTOMATICO_20260209_100000.dump", semana1);
        createArchivo("respaldo_FULL_AUTOMATICO_20260216_100000.dump", semana2);
        createArchivo("respaldo_FULL_AUTOMATICO_20260223_100000.dump", semana3);

        List<String> eliminados = backupService.aplicarRetencion();

        assertEquals(List.of("respaldo_FULL_AUTOMATICO_20260209_100000.dump"), eliminados);
        assertTrue(Files.exists(tempDir.resolve("respaldo_FULL_AUTOMATICO_20260216_100000.dump")));
        assertTrue(Files.exists(tempDir.resolve("respaldo_FULL_AUTOMATICO_20260223_100000.dump")));
    }

    // ── leer / delete / resolveExistente ──────────────────────────────────

    @Test
    void leerRechazaUnNombreConTraversal() {
        assertThrows(IllegalArgumentException.class, () -> backupService.leer("../../etc/passwd"));
    }

    @Test
    void leerRechazaUnArchivoQueNoExiste() {
        assertThrows(IllegalArgumentException.class,
                () -> backupService.leer("respaldo_FULL_MANUAL_20260101_000000.dump"));
    }

    @Test
    void leerDevuelveElContenidoReal() throws IOException {
        Files.writeString(tempDir.resolve("respaldo_FULL_MANUAL_20260101_000000.dump"), "datos-reales");

        byte[] contenido = backupService.leer("respaldo_FULL_MANUAL_20260101_000000.dump");

        assertEquals("datos-reales", new String(contenido));
    }

    @Test
    void deleteBorraElArchivoReal() throws IOException {
        Files.writeString(tempDir.resolve("respaldo_FULL_MANUAL_20260101_000000.dump"), "x");

        backupService.delete("respaldo_FULL_MANUAL_20260101_000000.dump");

        assertFalse(Files.exists(tempDir.resolve("respaldo_FULL_MANUAL_20260101_000000.dump")));
    }

    // ── restore / generateDiferencial: solo la rama de validacion previa ────

    @Test
    void restoreRechazaUnDiferencialPorqueNoSeRestauraSolo() throws IOException {
        Files.writeString(tempDir.resolve("respaldo_DIFERENCIAL_MANUAL_20260101_000000.tar.gz"), "x");

        assertThrows(IllegalArgumentException.class,
                () -> backupService.restore("respaldo_DIFERENCIAL_MANUAL_20260101_000000.tar.gz"));
    }

    @Test
    void generateDiferencialFallaSiNoHayNingunFullDelQuePartir() {
        assertThrows(IllegalStateException.class,
                () -> backupService.generateDiferencial(OrigenBackup.MANUAL));
    }
}
