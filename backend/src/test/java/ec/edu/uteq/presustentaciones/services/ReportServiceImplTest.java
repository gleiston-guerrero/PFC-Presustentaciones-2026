package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.ReportActivityTeacherDTO;
import ec.edu.uteq.presustentaciones.dto.ReportCountDTO;
import ec.edu.uteq.presustentaciones.dto.ReportSummaryDTO;
import ec.edu.uteq.presustentaciones.repositories.MinutesRepository;
import ec.edu.uteq.presustentaciones.repositories.TeacherRepository;
import ec.edu.uteq.presustentaciones.repositories.PanelistRepository;
import ec.edu.uteq.presustentaciones.repositories.SubmissionRepository;
import ec.edu.uteq.presustentaciones.repositories.TutorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ReporteServiceImpl agrega las cifras del process de pre-sustentaciones a partir de
 * consultas GROUP BY (COUNT en la base, nunca cargando la tabla). Estos tests verifican
 * el mapeo Object[] -> DTO y la combinación de actividad por teacher.
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock private SubmissionRepository submissionRepository;
    @Mock private MinutesRepository minutesRepository;
    @Mock private PanelistRepository panelistRepository;
    @Mock private TutorRepository tutorRepository;
    @Mock private TeacherRepository teacherRepository;

    @InjectMocks private ReportServiceImpl reportService;

    @Test
    void submissionsByStatusMapsRowsGrouped() {
        when(submissionRepository.countByStatus(any(), any(), any())).thenReturn(java.util.List.<Object[]>of(
                new Object[]{"COMPLETADA", 9L},
                new Object[]{"RECHAZADA", 2L}));

        List<ReportCountDTO> r = reportService.submissionsByStatus(null, null, null);

        assertEquals(2, r.size());
        assertEquals("COMPLETADA", r.get(0).getEtiqueta());
        assertEquals(9L, r.get(0).getCantidad());
    }

    @Test
    void summaryMinutesFillsStatusesMissingWithZero() {
        when(minutesRepository.countByStatus(any(), any())).thenReturn(java.util.List.<Object[]>of(
                new Object[]{"FINALIZADA", 5L}));
        when(minutesRepository.countByFirmadaFalse()).thenReturn(3L);

        Map<String, Long> r = reportService.summaryMinutes(null, null);

        assertEquals(5L, r.get("FINALIZADA"));
        assertEquals(0L, r.get("GENERADA"));
        assertEquals(0L, r.get("ANULADA"));
        assertEquals(5L, r.get("total"));
        assertEquals(3L, r.get("pendientesFirma"));
    }

    @Test
    void activityByTeacherCombinesPanelistTutorAndMinutesSigned() {
        when(panelistRepository.countAsignacionesByTeacher()).thenReturn(java.util.List.<Object[]>of(
                new Object[]{1L, "Luis", "Pérez", 4L}));
        when(tutorRepository.countTutoringsByTeacher()).thenReturn(java.util.List.<Object[]>of(
                new Object[]{1L, 2L}));
        when(panelistRepository.countMinutesFirmadasByTeacher()).thenReturn(java.util.List.<Object[]>of(
                new Object[]{1L, 3L}));

        List<ReportActivityTeacherDTO> r = reportService.activityByTeacher();

        assertEquals(1, r.size());
        ReportActivityTeacherDTO d = r.get(0);
        assertEquals("Luis Pérez", d.getTeacher());
        assertEquals(4L, d.getAsPanelist());
        assertEquals(2L, d.getAsTutor());
        assertEquals(3L, d.getMinutesFirmadas());
        verify(teacherRepository, never()).findNombresByIds(any());
    }

    @Test
    void summaryCalculatesTotalsAndInProcess() {
        when(submissionRepository.countByStatus(any(), any(), any())).thenReturn(java.util.List.<Object[]>of(
                new Object[]{"COMPLETADA", 10L},
                new Object[]{"EVALUACION", 5L},
                new Object[]{"RECHAZADA", 3L}));
        when(minutesRepository.countByStatus(any(), any())).thenReturn(java.util.List.<Object[]>of());
        when(minutesRepository.countByFirmadaFalse()).thenReturn(0L);
        when(submissionRepository.countByPeriod(any(), any())).thenReturn(java.util.List.<Object[]>of());

        ReportSummaryDTO r = reportService.summary(null, null, null);

        assertEquals(18L, r.getTotalSubmissions());
        assertEquals(10L, r.getSubmissionsCompletadas());
        assertEquals(3L, r.getSubmissionsRechazadas());
        assertEquals(5L, r.getSubmissionsEnProcess());
    }
}
