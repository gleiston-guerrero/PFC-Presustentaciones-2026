package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.ReportActivityTeacherDTO;
import ec.edu.uteq.presustentaciones.dto.ReportCountDTO;
import ec.edu.uteq.presustentaciones.dto.ReportSummaryDTO;
import ec.edu.uteq.presustentaciones.repositories.MinutesRepository;
import ec.edu.uteq.presustentaciones.repositories.TeacherRepository;
import ec.edu.uteq.presustentaciones.repositories.PanelistRepository;
import ec.edu.uteq.presustentaciones.repositories.SubmissionRepository;
import ec.edu.uteq.presustentaciones.repositories.TutorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementacion del servicio de report.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {

    private final SubmissionRepository submissionRepository;
    private final MinutesRepository minutesRepository;
    private final PanelistRepository panelistRepository;
    private final TutorRepository tutorRepository;
    private final TeacherRepository teacherRepository;

    // Sentinelas para rangos "sin filtro": una query con ":fecha IS NULL" deja a Postgres
    // sin tipo para el bind. Mismo criterio que SubmissionRepository.searchConFiltros.
    private static final LocalDate MIN_FECHA = LocalDate.of(1900, 1, 1);
    private static final LocalDate MAX_FECHA = LocalDate.of(2999, 12, 31);

    private static LocalDateTime startOfDay(LocalDate d) { return (d == null ? MIN_FECHA : d).atStartOfDay(); }
    private static LocalDateTime endOfDay(LocalDate d)    { return (d == null ? MAX_FECHA : d).atTime(LocalTime.MAX); }
    private static LocalDate fromOrMin(LocalDate d)      { return d == null ? MIN_FECHA : d; }
    private static LocalDate toOrMax(LocalDate d)      { return d == null ? MAX_FECHA : d; }
    private static String clean(String s)           { return (s == null || s.isBlank()) ? null : s.trim(); }
    private static long asLong(Object o)              { return o == null ? 0L : ((Number) o).longValue(); }

    /**
     * Resumen general del process de pre-sustentaciones (dashboard).
     *
     * @param from   fecha mínima a incluir, o {@code null} para no acotar
     * @param to   fecha máxima a incluir, o {@code null} para no acotar
     * @param program program a filtrar, o {@code null}/vacío para todas
     * @return el resumen agregado del process
     */
    @Override
    public ReportSummaryDTO summary(LocalDate from, LocalDate to, String program) {
        List<ReportCountDTO> byStatus = submissionsByStatus(from, to, program);
        Map<String, Long> minutes = summaryMinutes(from, to);

        long total = byStatus.stream().mapToLong(ReportCountDTO::getCantidad).sum();
        long completadas = byStatus.stream().filter(c -> "COMPLETADA".equals(c.getEtiqueta()))
                .mapToLong(ReportCountDTO::getCantidad).sum();
        long rechazadas = byStatus.stream().filter(c -> "RECHAZADA".equals(c.getEtiqueta()) || "SUSPENDIDA".equals(c.getEtiqueta()))
                .mapToLong(ReportCountDTO::getCantidad).sum();

        return ReportSummaryDTO.builder()
                .totalSubmissions(total)
                .submissionsCompletadas(completadas)
                .submissionsRechazadas(rechazadas)
                .submissionsEnProcess(Math.max(0, total - completadas - rechazadas))
                .totalMinutes(minutes.getOrDefault("total", 0L))
                .minutesGeneradas(minutes.getOrDefault("GENERADA", 0L))
                .minutesRevisadas(minutes.getOrDefault("REVISADA", 0L))
                .minutesObservadas(minutes.getOrDefault("OBSERVADA", 0L))
                .minutesFinalizadas(minutes.getOrDefault("FINALIZADA", 0L))
                .minutesAnuladas(minutes.getOrDefault("ANULADA", 0L))
                .minutesPendingSignature(minutes.getOrDefault("pendientesFirma", 0L))
                .submissionsByStatus(byStatus)
                .defensesByPeriod(defensesByPeriod(from, to))
                .build();
    }

    /**
     * Cantidad de submissions/pre-sustentaciones por estado.
     *
     * @param from   fecha mínima a incluir, o {@code null} para no acotar
     * @param to   fecha máxima a incluir, o {@code null} para no acotar
     * @param program program a filtrar, o {@code null}/vacío para todas
     * @return el count de submissions agrupado por estado
     */
    @Override
    public List<ReportCountDTO> submissionsByStatus(LocalDate from, LocalDate to, String program) {
        return submissionRepository.countByStatus(startOfDay(from), endOfDay(to), clean(program)).stream()
                .map(r -> new ReportCountDTO((String) r[0], asLong(r[1])))
                .toList();
    }

    /**
     * Cantidad de pre-sustentaciones por período académico.
     *
     * @param from fecha mínima a incluir, o {@code null} para no acotar
     * @param to fecha máxima a incluir, o {@code null} para no acotar
     * @return el count de sustentaciones agrupado por período
     */
    @Override
    public List<ReportCountDTO> defensesByPeriod(LocalDate from, LocalDate to) {
        return submissionRepository.countByPeriod(startOfDay(from), endOfDay(to)).stream()
                .map(r -> new ReportCountDTO((String) r[0], asLong(r[1])))
                .toList();
    }

    /**
     * Estado de las minutes: generadas, revisadas, observadas, finalizadas, anuladas, pendientes
     * de firma.
     *
     * @param from fecha mínima a incluir, o {@code null} para no acotar
     * @param to fecha máxima a incluir, o {@code null} para no acotar
     * @return mapa de estado de minutes a cantidad
     */
    @Override
    public Map<String, Long> summaryMinutes(LocalDate from, LocalDate to) {
        Map<String, Long> out = new LinkedHashMap<>();
        long total = 0;
        for (Object[] r : minutesRepository.countByStatus(fromOrMin(from), toOrMax(to))) {
            long c = asLong(r[1]);
            out.put((String) r[0], c);
            total += c;
        }
        for (String e : List.of("GENERADA", "REVISADA", "OBSERVADA", "FINALIZADA", "ANULADA")) {
            out.putIfAbsent(e, 0L);
        }
        out.put("total", total);
        out.put("pendientesFirma", minutesRepository.countByFirmadaFalse());
        return out;
    }

    /**
     * Actividad por teacher: como panelist, como tutor y minutes firmadas.
     *
     * @return la actividad agregada de cada teacher
     */
    @Override
    public List<ReportActivityTeacherDTO> activityByTeacher() {
        Map<Long, long[]> acc = new LinkedHashMap<>(); // id -> [panelist, tutor, minutesFirmadas]
        Map<Long, String> nombres = new LinkedHashMap<>();

        for (Object[] r : panelistRepository.countAsignacionesByTeacher()) {
            Long id = ((Number) r[0]).longValue();
            nombres.put(id, (r[1] + " " + r[2]).trim());
            acc.computeIfAbsent(id, k -> new long[3])[0] = asLong(r[3]);
        }
        for (Object[] r : tutorRepository.countTutoringsByTeacher()) {
            Long id = ((Number) r[0]).longValue();
            acc.computeIfAbsent(id, k -> new long[3])[1] = asLong(r[1]);
        }
        for (Object[] r : panelistRepository.countMinutesFirmadasByTeacher()) {
            Long id = ((Number) r[0]).longValue();
            acc.computeIfAbsent(id, k -> new long[3])[2] = asLong(r[1]);
        }

        // Nombres de los teachers que solo aparecen por tutoría (una sola consulta acotada).
        List<Long> faltantes = acc.keySet().stream().filter(id -> !nombres.containsKey(id)).toList();
        if (!faltantes.isEmpty()) {
            for (Object[] r : teacherRepository.findNombresByIds(faltantes)) {
                nombres.put(((Number) r[0]).longValue(), (r[1] + " " + r[2]).trim());
            }
        }

        List<ReportActivityTeacherDTO> out = new ArrayList<>();
        for (Map.Entry<Long, long[]> e : acc.entrySet()) {
            long[] v = e.getValue();
            out.add(new ReportActivityTeacherDTO(
                    e.getKey(), nombres.getOrDefault(e.getKey(), "Docente #" + e.getKey()),
                    v[0], v[1], v[2]));
        }
        out.sort((a, b) -> Long.compare(
                b.getAsPanelist() + b.getAsTutor(), a.getAsPanelist() + a.getAsTutor()));
        return out;
    }

    /**
     * Estadísticas por program/programa: total, completadas y rechazadas.
     *
     * @return las estadísticas agregadas por program
     */
    @Override
    public List<Map<String, Object>> statsByProgram() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] r : submissionRepository.statsByProgram()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("carrera", r[0]);
            row.put("total", asLong(r[1]));
            row.put("completadas", asLong(r[2]));
            row.put("rechazadas", asLong(r[3]));
            out.add(row);
        }
        return out;
    }
}
