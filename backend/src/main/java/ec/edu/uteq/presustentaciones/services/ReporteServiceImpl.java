package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.ReporteActividadDocenteDTO;
import ec.edu.uteq.presustentaciones.dto.ReporteConteoDTO;
import ec.edu.uteq.presustentaciones.dto.ReporteResumenDTO;
import ec.edu.uteq.presustentaciones.repositories.ActaRepository;
import ec.edu.uteq.presustentaciones.repositories.DocenteRepository;
import ec.edu.uteq.presustentaciones.repositories.JuradoRepository;
import ec.edu.uteq.presustentaciones.repositories.SolicitudRepository;
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

    private final SolicitudRepository solicitudRepository;
    private final ActaRepository actaRepository;
    private final JuradoRepository juradoRepository;
    private final TutorRepository tutorRepository;
    private final DocenteRepository docenteRepository;

    // Sentinelas para rangos "sin filtro": una query con ":fecha IS NULL" deja a Postgres
    // sin tipo para el bind. Mismo criterio que SolicitudRepository.buscarConFiltros.
    private static final LocalDate MIN_FECHA = LocalDate.of(1900, 1, 1);
    private static final LocalDate MAX_FECHA = LocalDate.of(2999, 12, 31);

    private static LocalDateTime inicioDe(LocalDate d) { return (d == null ? MIN_FECHA : d).atStartOfDay(); }
    private static LocalDateTime finDe(LocalDate d)    { return (d == null ? MAX_FECHA : d).atTime(LocalTime.MAX); }
    private static LocalDate desdeDe(LocalDate d)      { return d == null ? MIN_FECHA : d; }
    private static LocalDate hastaDe(LocalDate d)      { return d == null ? MAX_FECHA : d; }
    private static String limpiar(String s)           { return (s == null || s.isBlank()) ? null : s.trim(); }
    private static long asLong(Object o)              { return o == null ? 0L : ((Number) o).longValue(); }

    /**
     * Resumen general del proceso de pre-sustentaciones (dashboard).
     *
     * @param desde   fecha mínima a incluir, o {@code null} para no acotar
     * @param hasta   fecha máxima a incluir, o {@code null} para no acotar
     * @param carrera carrera a filtrar, o {@code null}/vacío para todas
     * @return el resumen agregado del proceso
     */
    @Override
    public ReporteResumenDTO resumen(LocalDate desde, LocalDate hasta, String carrera) {
        List<ReporteConteoDTO> porEstado = solicitudesPorEstado(desde, hasta, carrera);
        Map<String, Long> actas = resumenActas(desde, hasta);

        long total = porEstado.stream().mapToLong(ReporteConteoDTO::getCantidad).sum();
        long completadas = porEstado.stream().filter(c -> "COMPLETADA".equals(c.getEtiqueta()))
                .mapToLong(ReporteConteoDTO::getCantidad).sum();
        long rechazadas = porEstado.stream().filter(c -> "RECHAZADA".equals(c.getEtiqueta()) || "SUSPENDIDA".equals(c.getEtiqueta()))
                .mapToLong(ReporteConteoDTO::getCantidad).sum();

        return ReporteResumenDTO.builder()
                .totalSolicitudes(total)
                .solicitudesCompletadas(completadas)
                .solicitudesRechazadas(rechazadas)
                .solicitudesEnProceso(Math.max(0, total - completadas - rechazadas))
                .totalActas(actas.getOrDefault("total", 0L))
                .actasGeneradas(actas.getOrDefault("GENERADA", 0L))
                .actasRevisadas(actas.getOrDefault("REVISADA", 0L))
                .actasObservadas(actas.getOrDefault("OBSERVADA", 0L))
                .actasFinalizadas(actas.getOrDefault("FINALIZADA", 0L))
                .actasAnuladas(actas.getOrDefault("ANULADA", 0L))
                .actasPendientesFirma(actas.getOrDefault("pendientesFirma", 0L))
                .solicitudesPorEstado(porEstado)
                .sustentacionesPorPeriodo(sustentacionesPorPeriodo(desde, hasta))
                .build();
    }

    /**
     * Cantidad de solicitudes/pre-sustentaciones por estado.
     *
     * @param desde   fecha mínima a incluir, o {@code null} para no acotar
     * @param hasta   fecha máxima a incluir, o {@code null} para no acotar
     * @param carrera carrera a filtrar, o {@code null}/vacío para todas
     * @return el conteo de solicitudes agrupado por estado
     */
    @Override
    public List<ReporteConteoDTO> solicitudesPorEstado(LocalDate desde, LocalDate hasta, String carrera) {
        return solicitudRepository.contarPorEstado(inicioDe(desde), finDe(hasta), limpiar(carrera)).stream()
                .map(r -> new ReporteConteoDTO((String) r[0], asLong(r[1])))
                .toList();
    }

    /**
     * Cantidad de pre-sustentaciones por período académico.
     *
     * @param desde fecha mínima a incluir, o {@code null} para no acotar
     * @param hasta fecha máxima a incluir, o {@code null} para no acotar
     * @return el conteo de sustentaciones agrupado por período
     */
    @Override
    public List<ReporteConteoDTO> sustentacionesPorPeriodo(LocalDate desde, LocalDate hasta) {
        return solicitudRepository.contarPorPeriodo(inicioDe(desde), finDe(hasta)).stream()
                .map(r -> new ReporteConteoDTO((String) r[0], asLong(r[1])))
                .toList();
    }

    /**
     * Estado de las actas: generadas, revisadas, observadas, finalizadas, anuladas, pendientes
     * de firma.
     *
     * @param desde fecha mínima a incluir, o {@code null} para no acotar
     * @param hasta fecha máxima a incluir, o {@code null} para no acotar
     * @return mapa de estado de acta a cantidad
     */
    @Override
    public Map<String, Long> resumenActas(LocalDate desde, LocalDate hasta) {
        Map<String, Long> out = new LinkedHashMap<>();
        long total = 0;
        for (Object[] r : actaRepository.contarPorEstado(desdeDe(desde), hastaDe(hasta))) {
            long c = asLong(r[1]);
            out.put((String) r[0], c);
            total += c;
        }
        for (String e : List.of("GENERADA", "REVISADA", "OBSERVADA", "FINALIZADA", "ANULADA")) {
            out.putIfAbsent(e, 0L);
        }
        out.put("total", total);
        out.put("pendientesFirma", actaRepository.countByFirmadaFalse());
        return out;
    }

    /**
     * Actividad por docente: como jurado, como tutor y actas firmadas.
     *
     * @return la actividad agregada de cada docente
     */
    @Override
    public List<ReporteActividadDocenteDTO> actividadPorDocente() {
        Map<Long, long[]> acc = new LinkedHashMap<>(); // id -> [jurado, tutor, actasFirmadas]
        Map<Long, String> nombres = new LinkedHashMap<>();

        for (Object[] r : juradoRepository.contarAsignacionesPorDocente()) {
            Long id = ((Number) r[0]).longValue();
            nombres.put(id, (r[1] + " " + r[2]).trim());
            acc.computeIfAbsent(id, k -> new long[3])[0] = asLong(r[3]);
        }
        for (Object[] r : tutorRepository.contarTutoriasPorDocente()) {
            Long id = ((Number) r[0]).longValue();
            acc.computeIfAbsent(id, k -> new long[3])[1] = asLong(r[1]);
        }
        for (Object[] r : juradoRepository.contarActasFirmadasPorDocente()) {
            Long id = ((Number) r[0]).longValue();
            acc.computeIfAbsent(id, k -> new long[3])[2] = asLong(r[1]);
        }

        // Nombres de los docentes que solo aparecen por tutoría (una sola consulta acotada).
        List<Long> faltantes = acc.keySet().stream().filter(id -> !nombres.containsKey(id)).toList();
        if (!faltantes.isEmpty()) {
            for (Object[] r : docenteRepository.findNombresByIds(faltantes)) {
                nombres.put(((Number) r[0]).longValue(), (r[1] + " " + r[2]).trim());
            }
        }

        List<ReporteActividadDocenteDTO> out = new ArrayList<>();
        for (Map.Entry<Long, long[]> e : acc.entrySet()) {
            long[] v = e.getValue();
            out.add(new ReporteActividadDocenteDTO(
                    e.getKey(), nombres.getOrDefault(e.getKey(), "Docente #" + e.getKey()),
                    v[0], v[1], v[2]));
        }
        out.sort((a, b) -> Long.compare(
                b.getComoJurado() + b.getComoTutor(), a.getComoJurado() + a.getComoTutor()));
        return out;
    }

    /**
     * Estadísticas por carrera/programa: total, completadas y rechazadas.
     *
     * @return las estadísticas agregadas por carrera
     */
    @Override
    public List<Map<String, Object>> estadisticasPorCarrera() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] r : solicitudRepository.estadisticasPorCarrera()) {
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
