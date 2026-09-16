package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * RF-03: Endpoint de polling para estado en tiempo real.
 * El frontend consulta cada N segundos para reflejar cambios sin WebSocket.
 */
@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/estado")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class EstadoTiempoRealController {

    private final SubmissionRepository submissionRepo;
    private final ProposalRepository proposalRepo;
    private final ScheduleRepository scheduleRepo;
    private final MinutesRepository minutesRepo;
    private final EvaluationRepository evaluationRepo;

    /**
     * Devuelve el estado completo de una submission en un solo request: estado de la
     * submission, proposal, schedule, evaluación y minutes. Pensado para que el frontend
     * haga polling cada 15 s sin encadenar cinco llamadas distintas.
     *
     * @param id submission consultada
     * @return 200 con el mapa de estado. Las claves de un módulo que todavía no existe para
     *         esa submission (sin proposal, sin schedule, sin evaluación) se
     *         <b>omiten</b> del mapa en vez de venir en null, así que el frontend debe
     *         comprobar presencia. La única excepción es {@code minutesGenerada}, que siempre
     *         viene (false si aún no hay minutes), más {@code timestamp} con la marca de tiempo
     *         del servidor
     */
    @GetMapping("/solicitud/{id}")
    public ResponseEntity<Map<String, Object>> estadoSubmission(@PathVariable Long id) {
        Map<String, Object> estado = new HashMap<>();

        submissionRepo.findById(id).ifPresent(s -> {
            estado.put("solicitudEstado", s.getEstado());
            estado.put("solicitudId", s.getId());
        });

        proposalRepo.findBySubmissionId(id).ifPresent(a -> {
            estado.put("anteproyectoEstado", a.getEstado());
            estado.put("anteproyectoSha256", a.getSha256Hash() != null ? a.getSha256Hash() : null);
            estado.put("anteproyectoIntegridadVerificada", a.getSha256Hash() != null);
        });

        scheduleRepo.findBySubmissionId(id).ifPresent(c -> {
            estado.put("cronogramaFecha", c.getFechaInicio());
            estado.put("cronogramaSala", c.getRoom() != null ? c.getRoom().getNombre() : null);
            estado.put("cronogramaEstado", c.getEstado());
        });

        evaluationRepo.findBySubmissionId(id).ifPresent(e -> {
            estado.put("evaluacionNota", e.getNotaFinal());
            estado.put("evaluacionResultado", e.getResultado());
        });

        minutesRepo.findBySubmissionId(id).ifPresent(a -> {
            estado.put("actaGenerada", true);
            estado.put("actaFirmadaPresidente", a.isFirmadaPresidente());
            estado.put("actaFirmadaVocal1", a.isFirmadaVocal1());
            estado.put("actaFirmadaVocal2", a.isFirmadaVocal2());
            estado.put("actaFirmadaTutor", a.isFirmadaTutor());
            estado.put("actaCompleta", a.isFirmada());
        });

        if (!estado.containsKey("actaGenerada")) estado.put("actaGenerada", false);
        estado.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(estado);
    }

    /**
     * Estado resumido de varias submissions en un solo request, para la lista de
     * "mis asignaciones" del teacher: evita una llamada por fila de la tabla.
     *
     * @param submissionIds identificadores de las submissions a consultar
     * @return 200 con un mapa de id de submission a su estado resumido
     */
    @PostMapping("/solicitudes/batch")
    public ResponseEntity<Map<Long, Map<String, Object>>> estadoBatch(
            @RequestBody java.util.List<Long> submissionIds) {
        Map<Long, Map<String, Object>> resultado = new HashMap<>();
        for (Long id : submissionIds) {
            Map<String, Object> est = new HashMap<>();
            submissionRepo.findById(id).ifPresent(s -> est.put("estado", s.getEstado()));
            evaluationRepo.findBySubmissionId(id).ifPresent(e -> est.put("evaluada", true));
            if (!est.containsKey("evaluada")) est.put("evaluada", false);
            resultado.put(id, est);
        }
        return ResponseEntity.ok(resultado);
    }
}
