package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.CleanupBitacoraLog;
import ec.edu.uteq.presustentaciones.repositories.AuditRepository;
import ec.edu.uteq.presustentaciones.repositories.CleanupBitacoraLogRepository;
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
public class CleanupBitacoraScheduler {

    private final AuditRepository auditRepository;
    private final CleanupBitacoraLogRepository logRepository;

    @Value("${app.auditoria.retencion-dias:730}")
    private int retencionDias;

    /** Primer día de cada mes, 03:30 -- fuera de horario académico, después del backup diario. */
    @Scheduled(cron = "0 30 3 1 * *")
    @Transactional
    public void purge() {
        LocalDateTime fechaCorte = LocalDateTime.now().minusDays(retencionDias);
        int eliminadas = auditRepository.eraseAnterioresA(fechaCorte);
        logRepository.save(CleanupBitacoraLog.builder()
                .fechaEjecucion(LocalDateTime.now())
                .entradasEliminadas(eliminadas)
                .fechaCorte(fechaCorte)
                .build());
        if (eliminadas > 0) {
            log.info("Depuración de bitácora (RNF-19): {} entradas anteriores a {} eliminadas.",
                    eliminadas, fechaCorte);
        }
    }
}
