package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.BackupInfoDTO;
import ec.edu.uteq.presustentaciones.dto.EstadoRespaldosDTO;
import ec.edu.uteq.presustentaciones.dto.RespaldoConfigDTO;
import ec.edu.uteq.presustentaciones.entities.RespaldoConfig;
import ec.edu.uteq.presustentaciones.entities.RespaldoPruebaRestauracion;
import ec.edu.uteq.presustentaciones.repositories.RespaldoConfigRepository;
import ec.edu.uteq.presustentaciones.repositories.RespaldoPruebaRestauracionRepository;
import ec.edu.uteq.presustentaciones.services.backup.OrigenRespaldo;
import ec.edu.uteq.presustentaciones.services.backup.TipoRespaldo;
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
 * configuracion del cronograma, la bitacora de pruebas de restauracion, y las validaciones
 * de nombre/ruta. Los metodos que si invocan un binario externo (generar, generarDiferencial,
 * restaurar) solo se prueban en su rama de validacion previa a invocarlo.
 */
@ExtendWith(MockitoExtension.class)
class BackupServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private RespaldoConfigRepository configRepo;

    @Mock
    private RespaldoPruebaRestauracionRepository pruebaRepo;

    private BackupService backupService;

    @BeforeEach
    void setUp() {
        backupService = new BackupService(configRepo, pruebaRepo);
        ReflectionTestUtils.setField(backupService, "backupsDir", tempDir.toString());
        ReflectionTestUtils.setField(backupService, "datasourceUrl", "jdbc:postgresql://localhost:5432/BdPresustentaciones");
        ReflectionTestUtils.setField(backupService, "dbUsername", "postgres");
        ReflectionTestUtils.setField(backupService, "dbPassword", "x");
    }

    private void crearArchivo(String nombre, Instant fecha) throws IOException {
        Path p = tempDir.resolve(nombre);
        Files.writeString(p, "contenido");
        Files.setLastModifiedTime(p, FileTime.from(fecha));
    }

    private RespaldoConfig configPorDefecto() {
        return RespaldoConfig.builder()
                .id(RespaldoConfig.ID_UNICO)
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

    // ── listar / aInfo ───────────────────────────────────────────────────────

    @Test
    void listarDevuelveListaVaciaSiElDirectorioNoExiste() {
        ReflectionTestUtils.setField(backupService, "backupsDir", tempDir.resolve("no-existe").toString());
        assertEquals(List.of(), backupService.listar());
    }

    @Test
    void listarIgnoraArchivosQueNoSonRespaldo() throws IOException {
        crearArchivo("notas.txt", Instant.now());
        assertEquals(List.of(), backupService.listar());
    }

    @Test
    void listarParseaElFormatoNuevoConTipoYOrigen() throws IOException {
        crearArchivo("respaldo_FULL_AUTOMATICO_20260907_230000.dump", Instant.now());

        List<BackupInfoDTO> resultado = backupService.listar();

        assertEquals(1, resultado.size());
        assertEquals("FULL", resultado.get(0).getTipo());
        assertEquals("AUTOMATICO", resultado.get(0).getOrigen());
    }

    @Test
    void listarInterpretaElFormatoAntiguoComoFullManual() throws IOException {
        crearArchivo("respaldo_20260101_000000.dump", Instant.now());

        List<BackupInfoDTO> resultado = backupService.listar();

        assertEquals(1, resultado.size());
        assertEquals("FULL", resultado.get(0).getTipo());
        assertEquals("MANUAL", resultado.get(0).getOrigen());
    }

    @Test
    void listarOrdenaDelMasRecienteAlMasAntiguo() throws IOException {
        Instant ahora = Instant.now();
        crearArchivo("respaldo_FULL_MANUAL_20260101_000000.dump", ahora.minusSeconds(3600));
        crearArchivo("respaldo_FULL_MANUAL_20260102_000000.dump", ahora);

        List<BackupInfoDTO> resultado = backupService.listar();

        assertEquals(2, resultado.size());
        assertTrue(resultado.get(0).getFechaCreacion().isAfter(resultado.get(1).getFechaCreacion()));
    }

    // ── config / configDTO ───────────────────────────────────────────────────

    @Test
    void configDevuelveLaExistenteSiYaHayUnaGuardada() {
        RespaldoConfig existente = configPorDefecto();
        when(configRepo.findById(RespaldoConfig.ID_UNICO)).thenReturn(Optional.of(existente));

        assertSame(existente, backupService.config());
    }

    @Test
    void configCreaUnaPorDefectoSiNoExisteNinguna() {
        when(configRepo.findById(RespaldoConfig.ID_UNICO)).thenReturn(Optional.empty());
        when(configRepo.save(any(RespaldoConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        RespaldoConfig c = backupService.config();

        assertTrue(c.isActivo());
        assertEquals("0 0 23 * * SUN", c.getCron());
    }

    @Test
    void configDtoDescribeElCronReconocidoYElNoReconocido() {
        RespaldoConfig c = configPorDefecto();
        c.setCronDiferencial("0 15 4 * * *"); // no esta en el catalogo de presets
        when(configRepo.findById(RespaldoConfig.ID_UNICO)).thenReturn(Optional.of(c));

        RespaldoConfigDTO dto = backupService.configDTO();

        assertEquals("Cada domingo a las 23:00", dto.getCronDescripcion());
        assertTrue(dto.getCronDiferencialDescripcion().startsWith("Expresión personalizada"));
    }

    @Test
    void configDtoUsaCatorceComoRetenerDiasWalPorDefectoSiEsNull() {
        RespaldoConfig c = configPorDefecto();
        c.setRetenerDiasWal(null);
        when(configRepo.findById(RespaldoConfig.ID_UNICO)).thenReturn(Optional.of(c));

        assertEquals(14, backupService.configDTO().getRetenerDiasWal());
    }

    @Test
    void actualizarConfigRechazaUnCronPrincipalInvalido() {
        RespaldoConfigDTO dto = new RespaldoConfigDTO();
        dto.setCron("no-es-un-cron");
        dto.setCronDiferencial("0 30 2 * * WED,FRI");

        assertThrows(IllegalArgumentException.class, () -> backupService.actualizarConfig(dto));
    }

    @Test
    void actualizarConfigRechazaUnCronDiferencialInvalido() {
        RespaldoConfigDTO dto = new RespaldoConfigDTO();
        dto.setCron("0 0 23 * * SUN");
        dto.setCronDiferencial("no-es-un-cron");

        assertThrows(IllegalArgumentException.class, () -> backupService.actualizarConfig(dto));
    }

    @Test
    void actualizarConfigGuardaLosNuevosValores() {
        when(configRepo.findById(RespaldoConfig.ID_UNICO)).thenReturn(Optional.of(configPorDefecto()));
        ArgumentCaptor<RespaldoConfig> captor = ArgumentCaptor.forClass(RespaldoConfig.class);
        when(configRepo.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        RespaldoConfigDTO dto = new RespaldoConfigDTO();
        dto.setActivo(false);
        dto.setCron("0 0 2 * * *");
        dto.setRetenerDiarios(5);
        dto.setRetenerSemanales(3);
        dto.setRetenerMensuales(6);
        dto.setRetenerDiasWal(10);
        dto.setDiferencialActivo(true);
        dto.setCronDiferencial("0 0 3 * * *");

        RespaldoConfigDTO resultado = backupService.actualizarConfig(dto);

        assertFalse(resultado.getActivo());
        assertEquals("0 0 2 * * *", captor.getValue().getCron());
        assertEquals((short) 5, captor.getValue().getRetenerDiarios());
    }

    // ── pruebas / registrarPrueba ────────────────────────────────────────────

    @Test
    void pruebasDelegaAlRepositorio() {
        when(pruebaRepo.findTop50ByOrderByFechaDesc()).thenReturn(List.of(RespaldoPruebaRestauracion.builder().build()));
        assertEquals(1, backupService.pruebas().size());
    }

    @Test
    void registrarPruebaRechazaUnNombreInvalido() {
        assertThrows(IllegalArgumentException.class,
                () -> backupService.registrarPrueba("../etc/passwd", "EXITOSA", "a@uteq.edu.ec", "ok"));
    }

    @Test
    void registrarPruebaNormalizaResultadoDesconocidoAExitosa() {
        when(pruebaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RespaldoPruebaRestauracion p = backupService.registrarPrueba(
                "respaldo_FULL_MANUAL_20260101_000000.dump", "cualquier-cosa", "a@uteq.edu.ec", "  notas  ");

        assertEquals("EXITOSA", p.getResultado());
        assertEquals("notas", p.getNotas());
    }

    @Test
    void registrarPruebaConservaResultadoFallida() {
        when(pruebaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RespaldoPruebaRestauracion p = backupService.registrarPrueba(
                "respaldo_FULL_MANUAL_20260101_000000.dump", "FALLIDA", "", null);

        assertEquals("FALLIDA", p.getResultado());
        assertNull(p.getNotas());
    }

    // ── aplicarRetencion (GFS) ───────────────────────────────────────────────

    @Test
    void aplicarRetencionNoHaceNadaSiNoHayAutomaticas() {
        when(configRepo.findById(RespaldoConfig.ID_UNICO)).thenReturn(Optional.of(configPorDefecto()));
        assertEquals(List.of(), backupService.aplicarRetencion());
    }

    @Test
    void aplicarRetencionConservaLaMasRecienteAunqueLaRetencionSeaCero() throws IOException {
        RespaldoConfig cfg = configPorDefecto();
        cfg.setRetenerDiarios((short) 0);
        cfg.setRetenerSemanales((short) 0);
        cfg.setRetenerMensuales((short) 0);
        when(configRepo.findById(RespaldoConfig.ID_UNICO)).thenReturn(Optional.of(cfg));

        crearArchivo("respaldo_FULL_AUTOMATICO_20260101_000000.dump", Instant.now());

        List<String> eliminados = backupService.aplicarRetencion();

        assertEquals(List.of(), eliminados);
        assertTrue(Files.exists(tempDir.resolve("respaldo_FULL_AUTOMATICO_20260101_000000.dump")));
    }

    @Test
    void aplicarRetencionNuncaTocaManualNiEvento() throws IOException {
        RespaldoConfig cfg = configPorDefecto();
        cfg.setRetenerDiarios((short) 0);
        when(configRepo.findById(RespaldoConfig.ID_UNICO)).thenReturn(Optional.of(cfg));

        crearArchivo("respaldo_FULL_MANUAL_20200101_000000.dump", Instant.now().minusSeconds(999_999_999));
        crearArchivo("respaldo_FULL_EVENTO_20200101_010000.dump", Instant.now().minusSeconds(999_999_998));
        // Sin automaticas: aplicarRetencion sale por la lista vacia sin tocar nada.

        List<String> eliminados = backupService.aplicarRetencion();

        assertEquals(List.of(), eliminados);
        assertTrue(Files.exists(tempDir.resolve("respaldo_FULL_MANUAL_20200101_000000.dump")));
        assertTrue(Files.exists(tempDir.resolve("respaldo_FULL_EVENTO_20200101_010000.dump")));
    }

    @Test
    void aplicarRetencionEliminaLasAutomaticasFueraDeLaVentanaDiariaSemanalYMensual() throws IOException {
        RespaldoConfig cfg = configPorDefecto();
        cfg.setRetenerDiarios((short) 1);
        cfg.setRetenerSemanales((short) 0);
        cfg.setRetenerMensuales((short) 0);
        when(configRepo.findById(RespaldoConfig.ID_UNICO)).thenReturn(Optional.of(cfg));

        Instant hoy = Instant.now();
        // El nombre debe matchear NOMBRE_NUEVO (timestamp de 8+6 digitos) para que aInfo()
        // reconozca el origen AUTOMATICO -- si no, aInfo() lo clasifica como MANUAL por defecto.
        crearArchivo("respaldo_FULL_AUTOMATICO_20260901_000000.dump", hoy);                              // conservado: diario #1
        crearArchivo("respaldo_FULL_AUTOMATICO_20250901_000000.dump", hoy.minusSeconds(365L * 86400));   // hace 1 año: candidato a borrar

        List<String> eliminados = backupService.aplicarRetencion();

        assertEquals(List.of("respaldo_FULL_AUTOMATICO_20250901_000000.dump"), eliminados);
        assertFalse(Files.exists(tempDir.resolve("respaldo_FULL_AUTOMATICO_20250901_000000.dump")));
        assertTrue(Files.exists(tempDir.resolve("respaldo_FULL_AUTOMATICO_20260901_000000.dump")));
    }

    @Test
    void aplicarRetencionRespetaElLimiteDeCoposSemanalesEntreVariasSemanas() throws IOException {
        RespaldoConfig cfg = configPorDefecto();
        cfg.setRetenerDiarios((short) 0);
        cfg.setRetenerSemanales((short) 1); // solo 1 cupo semanal ademas de la salvavidas
        cfg.setRetenerMensuales((short) 0);
        when(configRepo.findById(RespaldoConfig.ID_UNICO)).thenReturn(Optional.of(cfg));

        // Tres semanas ISO distintas: semana3 (mas reciente, salvavidas), semana2 (usa el
        // unico cupo semanal disponible) y semana1 (se queda sin cupo y se elimina).
        Instant semana1 = LocalDateTime.of(2026, 2, 9, 10, 0).toInstant(ZoneOffset.UTC);
        Instant semana2 = LocalDateTime.of(2026, 2, 16, 10, 0).toInstant(ZoneOffset.UTC);
        Instant semana3 = LocalDateTime.of(2026, 2, 23, 10, 0).toInstant(ZoneOffset.UTC);
        crearArchivo("respaldo_FULL_AUTOMATICO_20260209_100000.dump", semana1);
        crearArchivo("respaldo_FULL_AUTOMATICO_20260216_100000.dump", semana2);
        crearArchivo("respaldo_FULL_AUTOMATICO_20260223_100000.dump", semana3);

        List<String> eliminados = backupService.aplicarRetencion();

        assertEquals(List.of("respaldo_FULL_AUTOMATICO_20260209_100000.dump"), eliminados);
        assertTrue(Files.exists(tempDir.resolve("respaldo_FULL_AUTOMATICO_20260216_100000.dump")));
        assertTrue(Files.exists(tempDir.resolve("respaldo_FULL_AUTOMATICO_20260223_100000.dump")));
    }

    // ── leer / eliminar / resolverExistente ──────────────────────────────────

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
    void eliminarBorraElArchivoReal() throws IOException {
        Files.writeString(tempDir.resolve("respaldo_FULL_MANUAL_20260101_000000.dump"), "x");

        backupService.eliminar("respaldo_FULL_MANUAL_20260101_000000.dump");

        assertFalse(Files.exists(tempDir.resolve("respaldo_FULL_MANUAL_20260101_000000.dump")));
    }

    // ── restaurar / generarDiferencial: solo la rama de validacion previa ────

    @Test
    void restaurarRechazaUnDiferencialPorqueNoSeRestauraSolo() throws IOException {
        Files.writeString(tempDir.resolve("respaldo_DIFERENCIAL_MANUAL_20260101_000000.tar.gz"), "x");

        assertThrows(IllegalArgumentException.class,
                () -> backupService.restaurar("respaldo_DIFERENCIAL_MANUAL_20260101_000000.tar.gz"));
    }

    @Test
    void generarDiferencialFallaSiNoHayNingunFullDelQuePartir() {
        assertThrows(IllegalStateException.class,
                () -> backupService.generarDiferencial(OrigenRespaldo.MANUAL));
    }
}
