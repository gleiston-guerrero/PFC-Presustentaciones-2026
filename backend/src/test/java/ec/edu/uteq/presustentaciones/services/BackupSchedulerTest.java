package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.BackupConfig;
import ec.edu.uteq.presustentaciones.repositories.BackupConfigRepository;
import ec.edu.uteq.presustentaciones.services.backup.SourceBackup;
import ec.edu.uteq.presustentaciones.services.backup.KindBackup;
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
 * ser el disparador de los backups automaticos. Cubre las tres tareas programadas
 * (tick, tickDiferencial, barridoRetencion) y sus ramas de guarda: sin config, config
 * inactiva, cron en blanco/invalido, y si ya toca o no segun el proximo disparo del cron.
 */
@ExtendWith(MockitoExtension.class)
class BackupSchedulerTest {

    @Mock
    private BackupConfigRepository configRepo;

    @Mock
    private BackupService backupService;

    @InjectMocks
    private BackupScheduler scheduler;

    private BackupConfig configBase() {
        return BackupConfig.builder()
                .id(BackupConfig.ID_UNICO)
                .activo(true)
                .cron("* * * * * *") // cada segundo: siempre toca
                .differentialActivo(true)
                .cronDifferential("* * * * * *")
                .build();
    }

    // ── tick ─────────────────────────────────────────────────────────────────

    @Test
    void tickNotMakesNothingIfAlreadyHasRunInCourse() {
        ReflectionTestUtils.setField(scheduler, "corriendo", true);

        scheduler.tick();

        verifyNoInteractions(configRepo);
    }

    @Test
    void tickNotMakesNothingIfNotHasConfiguration() {
        when(configRepo.findById(BackupConfig.ID_UNICO)).thenReturn(Optional.empty());

        scheduler.tick();

        verify(backupService, never()).generate(any(), any());
    }

    @Test
    void tickNotMakesNothingIfSchedulingIsPaused() {
        BackupConfig cfg = configBase();
        cfg.setActivo(false);
        when(configRepo.findById(BackupConfig.ID_UNICO)).thenReturn(Optional.of(cfg));

        scheduler.tick();

        verify(backupService, never()).generate(any(), any());
    }

    @Test
    void tickNotMakesNothingIfCronIsInBlank() {
        BackupConfig cfg = configBase();
        cfg.setCron("   ");
        when(configRepo.findById(BackupConfig.ID_UNICO)).thenReturn(Optional.of(cfg));

        scheduler.tick();

        verify(backupService, never()).generate(any(), any());
    }

    @Test
    void tickNotMakesNothingIfCronIsInvalid() {
        BackupConfig cfg = configBase();
        cfg.setCron("no-es-un-cron");
        when(configRepo.findById(BackupConfig.ID_UNICO)).thenReturn(Optional.of(cfg));

        scheduler.tick();

        verify(backupService, never()).generate(any(), any());
    }

    @Test
    void tickNotGeneratesNothingIfYetNotTouchesAccordingCron() {
        BackupConfig cfg = configBase();
        cfg.setCron("0 0 0 1 1 *"); // una vez al año, 1 de enero medianoche
        when(configRepo.findById(BackupConfig.ID_UNICO)).thenReturn(Optional.of(cfg));
        when(backupService.dateLastAutomatic()).thenReturn(LocalDateTime.now());

        scheduler.tick();

        verify(backupService, never()).generate(any(), any());
    }

    @Test
    void tickGeneratesBackupAndAppliesRetentionWhenAlreadyTouches() {
        BackupConfig cfg = configBase();
        when(configRepo.findById(BackupConfig.ID_UNICO)).thenReturn(Optional.of(cfg));
        when(backupService.dateLastAutomatic()).thenReturn(LocalDateTime.now().minusMinutes(5));

        scheduler.tick();

        verify(backupService).generate(KindBackup.FULL, SourceBackup.AUTOMATICO);
        verify(backupService).applyRetention();
    }

    @Test
    void tickNotPropagatesExceptionIfBackupFails() {
        BackupConfig cfg = configBase();
        when(configRepo.findById(BackupConfig.ID_UNICO)).thenReturn(Optional.of(cfg));
        when(backupService.dateLastAutomatic()).thenReturn(LocalDateTime.now().minusMinutes(5));
        when(backupService.generate(any(), any())).thenThrow(new RuntimeException("pg_dump no disponible"));

        scheduler.tick(); // no debe lanzar

        assertFalse((boolean) ReflectionTestUtils.getField(scheduler, "corriendo"));
    }

    // ── tickDiferencial ──────────────────────────────────────────────────────

    @Test
    void tickDifferentialNotMakesNothingIfIsDisabled() {
        BackupConfig cfg = configBase();
        cfg.setDifferentialActivo(false);
        when(configRepo.findById(BackupConfig.ID_UNICO)).thenReturn(Optional.of(cfg));

        scheduler.tickDifferential();

        verify(backupService, never()).generateDifferential(any());
    }

    @Test
    void tickDifferentialGeneratesBackupWhenAlreadyTouches() {
        BackupConfig cfg = configBase();
        when(configRepo.findById(BackupConfig.ID_UNICO)).thenReturn(Optional.of(cfg));
        when(backupService.dateLastDifferentialAutomatic()).thenReturn(LocalDateTime.now().minusMinutes(5));

        scheduler.tickDifferential();

        verify(backupService).generateDifferential(SourceBackup.AUTOMATICO);
    }

    // ── barridoRetencion ─────────────────────────────────────────────────────

    @Test
    void sweepRetentionNotMakesNothingIfSchedulingIsInactive() {
        BackupConfig cfg = configBase();
        cfg.setActivo(false);
        when(configRepo.findById(BackupConfig.ID_UNICO)).thenReturn(Optional.of(cfg));

        scheduler.sweepRetention();

        verify(backupService, never()).applyRetention();
    }

    @Test
    void sweepRetentionNotMakesNothingIfNotHasConfiguration() {
        when(configRepo.findById(BackupConfig.ID_UNICO)).thenReturn(Optional.empty());

        scheduler.sweepRetention();

        verify(backupService, never()).applyRetention();
    }

    @Test
    void sweepRetentionAppliesRetentionIfIsActive() {
        when(configRepo.findById(BackupConfig.ID_UNICO)).thenReturn(Optional.of(configBase()));

        scheduler.sweepRetention();

        verify(backupService).applyRetention();
    }

    @Test
    void sweepRetentionNotPropagatesExceptionIfRetentionFails() {
        when(configRepo.findById(BackupConfig.ID_UNICO)).thenReturn(Optional.of(configBase()));
        doThrow(new RuntimeException("disco lleno")).when(backupService).applyRetention();

        scheduler.sweepRetention(); // no debe lanzar
    }
}
