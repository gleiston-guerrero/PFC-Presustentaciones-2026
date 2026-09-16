package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.BackupConfig;
import ec.edu.uteq.presustentaciones.repositories.BackupConfigRepository;
import ec.edu.uteq.presustentaciones.services.backup.OrigenBackup;
import ec.edu.uteq.presustentaciones.services.backup.TipoBackup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Ejecuta el backup FULL automático según el schedule editable en
 * {@code presus.backup_config}, y aplica la retención GFS.
 *
 * <p>No usa {@code @Scheduled(cron=...)} directo porque la expresión vive en la base y
 * debe poder changese sin reiniciar. En su lugar hace un "tick" cada minuto y decide si
 * toca: compara la fecha del último backup automático contra el próximo disparo que
 * marca el cron. Efecto secundario deseable: si el servidor estuvo caído a la hora
 * programada, al volver genera <b>una</b> copia de recuperación (no una por cada slot
 * perdido) y sigue.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BackupScheduler {

    private final BackupConfigRepository configRepo;
    private final BackupService backupService;

    private volatile boolean corriendo = false;

    /** Tick cada minuto (tras 45 s de gracia al arrancar). */
    @Scheduled(fixedDelay = 60_000, initialDelay = 45_000)
    public void tick() {
        if (corriendo) return;

        BackupConfig cfg = configRepo.findById(BackupConfig.ID_UNICO).orElse(null);
        if (cfg == null || !cfg.isActivo() || cfg.getCron() == null || cfg.getCron().isBlank()) {
            return;
        }

        CronExpression cron;
        try {
            cron = CronExpression.parse(cfg.getCron().trim());
        } catch (IllegalArgumentException e) {
            log.warn("Cron de respaldos inválido ('{}'), no se ejecutará hasta corregirlo.", cfg.getCron());
            return;
        }

        LocalDateTime ultimoAuto = backupService.fechaUltimoAutomatico();
        LocalDateTime proximo = cron.next(ultimoAuto);
        if (proximo == null || proximo.isAfter(LocalDateTime.now())) {
            return; // todavía no toca
        }

        corriendo = true;
        try {
            log.info("Cronograma: generando respaldo FULL automático (programado para ~{})", proximo);
            backupService.generate(TipoBackup.FULL, OrigenBackup.AUTOMATICO);
            backupService.aplicarRetencion();
        } catch (Exception e) {
            log.error("El respaldo automático programado falló: {}", e.getMessage(), e);
        } finally {
            corriendo = false;
        }
    }

    /** Tick del diferencial (Fase 2), misma mecánica que el FULL pero con su propio cron. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 75_000)
    public void tickDiferencial() {
        if (corriendo) return;

        BackupConfig cfg = configRepo.findById(BackupConfig.ID_UNICO).orElse(null);
        if (cfg == null || !cfg.isDiferencialActivo()
                || cfg.getCronDiferencial() == null || cfg.getCronDiferencial().isBlank()) {
            return;
        }
        CronExpression cron;
        try {
            cron = CronExpression.parse(cfg.getCronDiferencial().trim());
        } catch (IllegalArgumentException e) {
            log.warn("Cron del diferencial inválido ('{}').", cfg.getCronDiferencial());
            return;
        }
        LocalDateTime ultimo = backupService.fechaUltimoDiferencialAutomatico();
        LocalDateTime proximo = cron.next(ultimo);
        if (proximo == null || proximo.isAfter(LocalDateTime.now())) {
            return;
        }
        corriendo = true;
        try {
            log.info("Cronograma: generando respaldo DIFERENCIAL automático (programado para ~{})", proximo);
            backupService.generateDiferencial(OrigenBackup.AUTOMATICO);
        } catch (Exception e) {
            log.error("El respaldo diferencial programado falló: {}", e.getMessage(), e);
        } finally {
            corriendo = false;
        }
    }

    /** Barrido de retención diario a las 03:15, independiente del backup programado. */
    @Scheduled(cron = "0 15 3 * * *")
    public void barridoRetencion() {
        boolean activo = configRepo.findById(BackupConfig.ID_UNICO)
                .map(BackupConfig::isActivo).orElse(false);
        if (!activo) return;
        try {
            backupService.aplicarRetencion();
        } catch (Exception e) {
            log.error("El barrido de retención diario falló: {}", e.getMessage(), e);
        }
    }
}
