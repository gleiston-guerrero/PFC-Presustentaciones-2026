package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.ReporteActividadTeacherDTO;
import ec.edu.uteq.presustentaciones.dto.ReporteCountDTO;
import ec.edu.uteq.presustentaciones.dto.ReporteResumenDTO;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Reportes agregados para COORDINADOR y ADMINISTRADOR (permission REPORTES_VER, id 18).
 * Todas las cifras se calculan con COUNT/GROUP BY en PostgreSQL — nunca se carga una
 * tabla completa en memoria para count.
 */
public interface ReporteService {

    /** Resumen general del process de pre-sustentaciones (dashboard). */
    ReporteResumenDTO resumen(LocalDate desde, LocalDate hasta, String program);

    /** Cantidad de submissions/pre-sustentaciones por estado. */
    List<ReporteCountDTO> submissionsPorEstado(LocalDate desde, LocalDate hasta, String program);

    /** Cantidad de pre-sustentaciones por período académico. */
    List<ReporteCountDTO> sustentacionesPorPeriod(LocalDate desde, LocalDate hasta);

    /** Estado de las minutes: generadas, revisadas, observadas, finalizadas, anuladas, pendientes de firma. */
    Map<String, Long> resumenMinutes(LocalDate desde, LocalDate hasta);

    /** Actividad por teacher: como panelist, como tutor y minutes firmadas. */
    List<ReporteActividadTeacherDTO> actividadPorTeacher();

    /** Estadísticas por program/programa: total, completadas y rechazadas. */
    List<Map<String, Object>> estadisticasPorProgram();
}
