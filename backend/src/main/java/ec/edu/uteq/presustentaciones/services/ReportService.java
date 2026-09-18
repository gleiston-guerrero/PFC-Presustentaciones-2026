package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.ReportActivityTeacherDTO;
import ec.edu.uteq.presustentaciones.dto.ReportCountDTO;
import ec.edu.uteq.presustentaciones.dto.ReportSummaryDTO;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Reportes agregados para COORDINADOR y ADMINISTRADOR (permission REPORTES_VER, id 18).
 * Todas las cifras se calculan con COUNT/GROUP BY en PostgreSQL — nunca se carga una
 * tabla completa en memoria para count.
 */
public interface ReportService {

    /** Resumen general del process de pre-sustentaciones (dashboard). */
    ReportSummaryDTO summary(LocalDate from, LocalDate to, String program);

    /** Cantidad de submissions/pre-sustentaciones por estado. */
    List<ReportCountDTO> submissionsByStatus(LocalDate from, LocalDate to, String program);

    /** Cantidad de pre-sustentaciones por período académico. */
    List<ReportCountDTO> defensesByPeriod(LocalDate from, LocalDate to);

    /** Estado de las minutes: generadas, revisadas, observadas, finalizadas, anuladas, pendientes de firma. */
    Map<String, Long> summaryMinutes(LocalDate from, LocalDate to);

    /** Actividad por teacher: como panelist, como tutor y minutes firmadas. */
    List<ReportActivityTeacherDTO> activityByTeacher();

    /** Estadísticas por program/programa: total, completadas y rechazadas. */
    List<Map<String, Object>> statsByProgram();
}
