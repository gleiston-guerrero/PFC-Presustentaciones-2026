package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.RespaldoConfig;
import ec.edu.uteq.presustentaciones.repositories.RespaldoConfigRepository;
import ec.edu.uteq.presustentaciones.services.backup.OrigenRespaldo;
import ec.edu.uteq.presustentaciones.services.backup.TipoRespaldo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * BackupScheduler no tenia ningun test (0% de ramas, 30 sin ejercitar segun JaCoCo) pese a
 * ser el disparador de los respaldos automaticos. Cubre las tres tareas programadas
 * (tick, tickDiferencial, barridoRetencion) y sus ramas de guarda: sin config, config
 * inactiva, cron en blanco/invalido, y si ya toca o no segun el proximo disparo del cron.
 */
@ExtendWith(MockitoExtension.class)
class BackupSchedulerTest {

    @Mock
    private RespaldoConfigRepository configRepo;

    @Mock
    private BackupService backupService;

    @InjectMocks
    private BackupScheduler scheduler;

    private RespaldoConfig configBase() {
        return RespaldoConfig.builder()
                .id(RespaldoConfig.ID_UNICO)
                .activo(true)
                .cron("* * * * * *") // cada segundo: siempre toca
                .diferencialActivo(true)
                .cronDiferencial("* * * * * *")
                .build();
    }

    // ── tick ─────────────────────────────────────────────────────────────────

    @Test
    void tickNoHaceNadaSiYaHayUnaCorridaEnCurso() {
        ReflectionTestUtils.setField(scheduler, "corriendo", true);

        scheduler.tick();

        verifyNoInteractions(configRepo);
    }

    @Test
    void tickNoHaceNadaSiNoHayConfiguracion() {
        when(configRepo.findById(RespaldoConfig.ID_UNICO)).thenReturn(Optional.empty());

        scheduler.tick();

        verify(backupService, never()).generar(any(), any());
    }

    @Test
    void tickNoHaceNadaSiLaProgramacionEstaPausada() {
        RespaldoConfig cfg = configBase();
        cfg.setActivo(false);
        when(configRepo.findById(RespaldoConfig.ID_UNICO)).thenReturn(Optional.of(cfg));

        scheduler.tick();

        verify(backupService, never()).generar(any(), any());
    }

    @Test
    void tickNoHaceNadaSiElCronEstaEnBlanco() {
        RespaldoConfig cfg = configBase();
        cfg.setCron("   ");
        when(configRepo.findById(RespaldoConfig.ID_UNICO)).thenReturn(Optional.of(cfg));

        scheduler.tick();

        verify(backupService, never()).generar(any(), any());
    }

    @Test
    void tickNoHaceNadaSiElCronEsInvalido() {
        RespaldoConfig cfg = configBase();
        cfg.setCron("no-es-un-cron");
        when(configRepo.findById(RespaldoConfig.ID_UNICO)).thenReturn(Optional.of(cfg));

        scheduler.tick();

        verify(backupService, never()).generar(any(), any());
    }

    @Test
    void tickNoGeneraNadaSiTodaviaNoTocaSegunElCron() {
        RespaldoConfig cfg = configBase();
        cfg.setCron("0 0 0 1 1 *"); // una vez al año, 1 de enero medianoche
        when(configRepo.findById(RespaldoConfig.ID_UNICO)).thenReturn(Optional.of(cfg));
        when(backupService.fechaUltimoAutomatico()).thenReturn(LocalDateTime.now());

        scheduler.tick();

        verify(backupService, never()).generar(any(), any());
    }

    @Test
    void tickGeneraElRespaldoYAplicaRetencionCuandoYaToca() {
        RespaldoConfig cfg = configBase();
        when(configRepo.findById(RespaldoConfig.ID_UNICO)).thenReturn(Optional.of(cfg));
        when(backupService.fechaUltimoAutomatico()).thenReturn(LocalDateTime.now().minusMinutes(5));

        scheduler.tick();

        verify(backupService).generar(TipoRespaldo.FULL, OrigenRespaldo.AUTOMATICO);
        verify(backupService).aplicarRetencion();
    }

    @Test
    void tickNoPropagaLaExcepcionSiElRespaldoFalla() {
        RespaldoConfig cfg = configBase();
        when(configRepo.findById(RespaldoConfig.ID_UNICO)).thenReturn(Optional.of(cfg));
        when(backupService.fechaUltimoAutomatico()).thenReturn(LocalDateTime.now().minusMinutes(5));
        when(backupService.generar(any(), any())).thenThrow(new RuntimeException("pg_dump no disponible"));

        scheduler.tick(); // no debe lanzar

        assertFalse((boolean) ReflectionTestUtils.getField(scheduler, "corriendo"));
    }

    // ── tickDiferencial ──────────────────────────────────────────────────────

    @Test
    void tickDiferencialNoHaceNadaSiEstaDesactivado() {
        RespaldoConfig cfg = configBase();
        cfg.setDiferencialActivo(false);
        when(configRepo.findById(RespaldoConfig.ID_UNICO)).thenReturn(Optional.of(cfg));

        scheduler.tickDiferencial();

        verify(backupService, never()).generarDiferencial(any());
    }

    @Test
    void tickDiferencialGeneraElRespaldoCuandoYaToca() {
        RespaldoConfig cfg = configBase();
        when(configRepo.findById(RespaldoConfig.ID_UNICO)).thenReturn(Optional.of(cfg));
        when(backupService.fechaUltimoDiferencialAutomatico()).thenReturn(LocalDateTime.now().minusMinutes(5));

        scheduler.tickDiferencial();

        verify(backupService).generarDiferencial(OrigenRespaldo.AUTOMATICO);
    }

    // ── barridoRetencion ─────────────────────────────────────────────────────

    @Test
    void barridoRetencionNoHaceNadaSiLaProgramacionEstaInactiva() {
        RespaldoConfig cfg = configBase();
        cfg.setActivo(false);
        when(configRepo.findById(RespaldoConfig.ID_UNICO)).thenReturn(Optional.of(cfg));

        scheduler.barridoRetencion();

        verify(backupService, never()).aplicarRetencion();
    }

    @Test
    void barridoRetencionNoHaceNadaSiNoHayConfiguracion() {
        when(configRepo.findById(RespaldoConfig.ID_UNICO)).thenReturn(Optional.empty());

        scheduler.barridoRetencion();

        verify(backupService, never()).aplicarRetencion();
    }

    @Test
    void barridoRetencionAplicaLaRetencionSiEstaActiva() {
        when(configRepo.findById(RespaldoConfig.ID_UNICO)).thenReturn(Optional.of(configBase()));

        scheduler.barridoRetencion();

        verify(backupService).aplicarRetencion();
    }

    @Test
    void barridoRetencionNoPropagaLaExcepcionSiLaRetencionFalla() {
        when(configRepo.findById(RespaldoConfig.ID_UNICO)).thenReturn(Optional.of(configBase()));
        doThrow(new RuntimeException("disco lleno")).when(backupService).aplicarRetencion();

        scheduler.barridoRetencion(); // no debe lanzar
    }
}
