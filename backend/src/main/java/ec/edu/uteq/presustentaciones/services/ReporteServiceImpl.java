package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.ReporteActividadTeacherDTO;
import ec.edu.uteq.presustentaciones.dto.ReporteCountDTO;
import ec.edu.uteq.presustentaciones.dto.ReporteResumenDTO;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReporteServiceImpl implements ReporteService {

    private final SubmissionRepository submissionRepository;
    private final MinutesRepository minutesRepository;
    private final PanelistRepository panelistRepository;
    private final TutorRepository tutorRepository;
    private final TeacherRepository teacherRepository;

    // Sentinelas para rangos "sin filtro": una query con ":fecha IS NULL" deja a Postgres
    // sin tipo para el bind. Mismo criterio que SubmissionRepository.searchConFiltros.
    private static final LocalDate MIN_FECHA = LocalDate.of(1900, 1, 1);
    private static final LocalDate MAX_FECHA = LocalDate.of(2999, 12, 31);

    private static LocalDateTime inicioDe(LocalDate d) { return (d == null ? MIN_FECHA : d).atStartOfDay(); }
    private static LocalDateTime finDe(LocalDate d)    { return (d == null ? MAX_FECHA : d).atTime(LocalTime.MAX); }
    private static LocalDate desdeDe(LocalDate d)      { return d == null ? MIN_FECHA : d; }
    private static LocalDate hastaDe(LocalDate d)      { return d == null ? MAX_FECHA : d; }
    private static String limpiar(String s)           { return (s == null || s.isBlank()) ? null : s.trim(); }
    private static long asLong(Object o)              { return o == null ? 0L : ((Number) o).longValue(); }

    /**
     * Resumen general del process de pre-sustentaciones (dashboard).
     *
     * @param desde   fecha mínima a incluir, o {@code null} para no acotar
     * @param hasta   fecha máxima a incluir, o {@code null} para no acotar
     * @param program program a filtrar, o {@code null}/vacío para todas
     * @return el resumen agregado del process
     */
    @Override
    public ReporteResumenDTO resumen(LocalDate desde, LocalDate hasta, String program) {
        List<ReporteCountDTO> porEstado = submissionsPorEstado(desde, hasta, program);
        Map<String, Long> minutes = resumenMinutes(desde, hasta);

        long total = porEstado.stream().mapToLong(ReporteCountDTO::getCantidad).sum();
        long completadas = porEstado.stream().filter(c -> "COMPLETADA".equals(c.getEtiqueta()))
                .mapToLong(ReporteCountDTO::getCantidad).sum();
        long rechazadas = porEstado.stream().filter(c -> "RECHAZADA".equals(c.getEtiqueta()) || "SUSPENDIDA".equals(c.getEtiqueta()))
                .mapToLong(ReporteCountDTO::getCantidad).sum();

        return ReporteResumenDTO.builder()
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
                .minutesPendientesFirma(minutes.getOrDefault("pendientesFirma", 0L))
                .submissionsPorEstado(porEstado)
                .sustentacionesPorPeriod(sustentacionesPorPeriod(desde, hasta))
                .build();
    }

    /**
     * Cantidad de submissions/pre-sustentaciones por estado.
     *
     * @param desde   fecha mínima a incluir, o {@code null} para no acotar
     * @param hasta   fecha máxima a incluir, o {@code null} para no acotar
     * @param program program a filtrar, o {@code null}/vacío para todas
     * @return el count de submissions agrupado por estado
     */
    @Override
    public List<ReporteCountDTO> submissionsPorEstado(LocalDate desde, LocalDate hasta, String program) {
        return submissionRepository.countPorEstado(inicioDe(desde), finDe(hasta), limpiar(program)).stream()
                .map(r -> new ReporteCountDTO((String) r[0], asLong(r[1])))
                .toList();
    }

    /**
     * Cantidad de pre-sustentaciones por período académico.
     *
     * @param desde fecha mínima a incluir, o {@code null} para no acotar
     * @param hasta fecha máxima a incluir, o {@code null} para no acotar
     * @return el count de sustentaciones agrupado por período
     */
    @Override
    public List<ReporteCountDTO> sustentacionesPorPeriod(LocalDate desde, LocalDate hasta) {
        return submissionRepository.countPorPeriod(inicioDe(desde), finDe(hasta)).stream()
                .map(r -> new ReporteCountDTO((String) r[0], asLong(r[1])))
                .toList();
    }

    /**
     * Estado de las minutes: generadas, revisadas, observadas, finalizadas, anuladas, pendientes
     * de firma.
     *
     * @param desde fecha mínima a incluir, o {@code null} para no acotar
     * @param hasta fecha máxima a incluir, o {@code null} para no acotar
     * @return mapa de estado de minutes a cantidad
     */
    @Override
    public Map<String, Long> resumenMinutes(LocalDate desde, LocalDate hasta) {
        Map<String, Long> out = new LinkedHashMap<>();
        long total = 0;
        for (Object[] r : minutesRepository.countPorEstado(desdeDe(desde), hastaDe(hasta))) {
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
    public List<ReporteActividadTeacherDTO> actividadPorTeacher() {
        Map<Long, long[]> acc = new LinkedHashMap<>(); // id -> [panelist, tutor, minutesFirmadas]
        Map<Long, String> nombres = new LinkedHashMap<>();

        for (Object[] r : panelistRepository.countAsignacionesPorTeacher()) {
            Long id = ((Number) r[0]).longValue();
            nombres.put(id, (r[1] + " " + r[2]).trim());
            acc.computeIfAbsent(id, k -> new long[3])[0] = asLong(r[3]);
        }
        for (Object[] r : tutorRepository.countTutoringsPorTeacher()) {
            Long id = ((Number) r[0]).longValue();
            acc.computeIfAbsent(id, k -> new long[3])[1] = asLong(r[1]);
        }
        for (Object[] r : panelistRepository.countMinutesFirmadasPorTeacher()) {
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

        List<ReporteActividadTeacherDTO> out = new ArrayList<>();
        for (Map.Entry<Long, long[]> e : acc.entrySet()) {
            long[] v = e.getValue();
            out.add(new ReporteActividadTeacherDTO(
                    e.getKey(), nombres.getOrDefault(e.getKey(), "Docente #" + e.getKey()),
                    v[0], v[1], v[2]));
        }
        out.sort((a, b) -> Long.compare(
                b.getComoPanelist() + b.getComoTutor(), a.getComoPanelist() + a.getComoTutor()));
        return out;
    }

    /**
     * Estadísticas por program/programa: total, completadas y rechazadas.
     *
     * @return las estadísticas agregadas por program
     */
    @Override
    public List<Map<String, Object>> estadisticasPorProgram() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] r : submissionRepository.estadisticasPorProgram()) {
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
