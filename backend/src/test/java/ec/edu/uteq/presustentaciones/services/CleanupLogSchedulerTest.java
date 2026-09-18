package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.CleanupLogLog;
import ec.edu.uteq.presustentaciones.repositories.AuditRepository;
import ec.edu.uteq.presustentaciones.repositories.CleanupLogLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * RNF-19: depuración automática de la bitácora. No expone ningún endpoint -- ver
 * {@code CleanupLogScheduler}, {@code @Scheduled} puro -- y deja traza verificable de
 * cuántas entradas borró y hasta qué fecha.
 */
class CleanupLogSchedulerTest {

    private AuditRepository auditRepository;
    private CleanupLogLogRepository logRepository;
    private CleanupLogScheduler scheduler;

    @BeforeEach
    void setUp() {
        auditRepository = mock(AuditRepository.class);
        logRepository = mock(CleanupLogLogRepository.class);
        scheduler = new CleanupLogScheduler(auditRepository, logRepository);
        ReflectionTestUtils.setField(scheduler, "retentionDias", 730);
    }

    @Test
    void purgeBorraLoAnteriorAlCorteYDejaTrazaWithElCountExacto() {
        when(auditRepository.eraseAnterioresA(any())).thenReturn(42);

        scheduler.purge();

        verify(auditRepository).eraseAnterioresA(any(LocalDateTime.class));

        org.mockito.ArgumentCaptor<CleanupLogLog> captor =
                org.mockito.ArgumentCaptor.forClass(CleanupLogLog.class);
        verify(logRepository).save(captor.capture());
        CleanupLogLog traza = captor.getValue();
        assertEquals(42, traza.getEntradasEliminadas());
        assertNotNull(traza.getDateCorte());
        assertNotNull(traza.getDateEjecucion());
        // El corte es ~730 dias atras (margen amplio por el tiempo de ejecucion de la prueba).
        long diasDeMargen = ChronoUnit.DAYS.between(traza.getDateCorte(), LocalDateTime.now());
        assertTrue(diasDeMargen >= 729 && diasDeMargen <= 731,
                "el corte debe ser ~730 dias atras, fue hace " + diasDeMargen + " dias");
    }

    @Test
    void purgeWithoutNadaQueEraseTambienDejaTraza() {
        when(auditRepository.eraseAnterioresA(any())).thenReturn(0);

        scheduler.purge();

        verify(logRepository).save(argThat(traza -> traza.getEntradasEliminadas() == 0));
    }

    @Test
    void elPeriodDeRetentionEsConfigurable() {
        ReflectionTestUtils.setField(scheduler, "retentionDias", 30);
        when(auditRepository.eraseAnterioresA(any())).thenReturn(5);

        scheduler.purge();

        org.mockito.ArgumentCaptor<LocalDateTime> corteCaptor = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        verify(auditRepository).eraseAnterioresA(corteCaptor.capture());
        long dias = ChronoUnit.DAYS.between(corteCaptor.getValue(), LocalDateTime.now());
        assertTrue(dias >= 29 && dias <= 31);
    }
}
