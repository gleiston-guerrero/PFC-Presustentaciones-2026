package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.CleanupLogLog;
import ec.edu.uteq.presustentaciones.repositories.AuditRepository;
import ec.edu.uteq.presustentaciones.repositories.CleanupLogLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * RNF-19: depura automáticamente las entradas de {@code presus.audit} anteriores al
 * período de retención declarado en {@code docs/etica/RETENCION-DATOS.md} (2 años por
 * omisión). Sigue el mismo patrón que {@link BackupScheduler} (tarea programada, cron
 * configurable por propiedad, nunca un endpoint) -- deliberado, porque RNF-18 exige que
 * <b>ninguna operación de la API</b> pueda erase la bitácora; una tarea de sistema sin
 * entrada de appUser no es eso: es la política de retención ya declarada, aplicándose sola.
 *
 * <p>Cada corrida deja traza verificable en {@code presus.cleanup_bitacora_log}: cuántas
 * entradas borró y hasta qué fecha, para que "automático" no signifique "invisible".
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CleanupLogScheduler {

    private final AuditRepository auditRepository;
    private final CleanupLogLogRepository logRepository;

    @Value("${app.auditoria.retencion-dias:730}")
    private int retentionDias;

    /** Primer día de cada mes, 03:30 -- fuera de horario académico, después del backup diario. */
    @Scheduled(cron = "0 30 3 1 * *")
    @Transactional
    public void purge() {
        LocalDateTime dateCorte = LocalDateTime.now().minusDays(retentionDias);
        int eliminadas = auditRepository.eraseAnterioresA(dateCorte);
        logRepository.save(CleanupLogLog.builder()
                .dateEjecucion(LocalDateTime.now())
                .entradasEliminadas(eliminadas)
                .dateCorte(dateCorte)
                .build());
        if (eliminadas > 0) {
            log.info("Depuración de bitácora (RNF-19): {} entradas anteriores a {} eliminadas.",
                    eliminadas, dateCorte);
        }
    }
}
