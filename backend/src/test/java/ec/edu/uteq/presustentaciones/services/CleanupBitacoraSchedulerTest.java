package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.CleanupBitacoraLog;
import ec.edu.uteq.presustentaciones.repositories.AuditRepository;
import ec.edu.uteq.presustentaciones.repositories.CleanupBitacoraLogRepository;
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
 * {@code CleanupBitacoraScheduler}, {@code @Scheduled} puro -- y deja traza verificable de
 * cuántas entradas borró y hasta qué fecha.
 */
class CleanupBitacoraSchedulerTest {

    private AuditRepository auditRepository;
    private CleanupBitacoraLogRepository logRepository;
    private CleanupBitacoraScheduler scheduler;

    @BeforeEach
    void setUp() {
        auditRepository = mock(AuditRepository.class);
        logRepository = mock(CleanupBitacoraLogRepository.class);
        scheduler = new CleanupBitacoraScheduler(auditRepository, logRepository);
        ReflectionTestUtils.setField(scheduler, "retencionDias", 730);
    }

    @Test
    void purgeBorraLoAnteriorAlCorteYDejaTrazaConElCountExacto() {
        when(auditRepository.eraseAnterioresA(any())).thenReturn(42);

        scheduler.purge();

        verify(auditRepository).eraseAnterioresA(any(LocalDateTime.class));

        org.mockito.ArgumentCaptor<CleanupBitacoraLog> captor =
                org.mockito.ArgumentCaptor.forClass(CleanupBitacoraLog.class);
        verify(logRepository).save(captor.capture());
        CleanupBitacoraLog traza = captor.getValue();
        assertEquals(42, traza.getEntradasEliminadas());
        assertNotNull(traza.getFechaCorte());
        assertNotNull(traza.getFechaEjecucion());
        // El corte es ~730 dias atras (margen amplio por el tiempo de ejecucion de la prueba).
        long diasDeMargen = ChronoUnit.DAYS.between(traza.getFechaCorte(), LocalDateTime.now());
        assertTrue(diasDeMargen >= 729 && diasDeMargen <= 731,
                "el corte debe ser ~730 dias atras, fue hace " + diasDeMargen + " dias");
    }

    @Test
    void purgeSinNadaQueEraseTambienDejaTraza() {
        when(auditRepository.eraseAnterioresA(any())).thenReturn(0);

        scheduler.purge();

        verify(logRepository).save(argThat(traza -> traza.getEntradasEliminadas() == 0));
    }

    @Test
    void elPeriodDeRetencionEsConfigurable() {
        ReflectionTestUtils.setField(scheduler, "retencionDias", 30);
        when(auditRepository.eraseAnterioresA(any())).thenReturn(5);

        scheduler.purge();

        org.mockito.ArgumentCaptor<LocalDateTime> corteCaptor = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        verify(auditRepository).eraseAnterioresA(corteCaptor.capture());
        long dias = ChronoUnit.DAYS.between(corteCaptor.getValue(), LocalDateTime.now());
        assertTrue(dias >= 29 && dias <= 31);
    }
}
