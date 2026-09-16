package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.EscalaCriterioDTO;
import ec.edu.uteq.presustentaciones.dto.EvaluacionRubricaRequest;
import ec.edu.uteq.presustentaciones.dto.EvaluacionRubricaResponse;
import ec.edu.uteq.presustentaciones.dto.ObservacionesSolicitudDTO;
import ec.edu.uteq.presustentaciones.entities.*;
import ec.edu.uteq.presustentaciones.repositories.*;
import ec.edu.uteq.presustentaciones.security.service.SolicitudAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RubricaEvaluacionServiceImpl implements RubricaEvaluacionService {

    private final EvaluacionCriterioRepository evalCriterioRepo;
    private final CriterioRubricaRepository criterioRepo;
    private final JuradoRepository juradoRepo;
    private final SolicitudRepository solicitudRepo;
    private final RubricaRepository rubricaRepo;
    private final TutorRepository tutorRepo;
    private final EvaluacionFinalRepository evaluacionFinalRepo;
    private final EvaluacionJuradoRepository javaEvaluacionJuradoRepo;
    private final EvaluadorRepository evaluadorRepo;
    private final TipoEvaluadorRepository tipoEvaluadorRepo;
    private final SolicitudAccessService solicitudAccessService;
    private final PermisoService permisoService;

    /** Mismo criterio que EvaluacionJuradoService.validarPuedeRegistrar: solo el propio
     * jurado, o ADMIN/COORDINADOR, puede registrar una evaluación de rúbrica -- evita que
     * un jurado registre escalas a nombre de otro (IDOR de escritura). */
    private void validarPuedeRegistrar(Jurado jurado) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Usuario no autenticado");
        }
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin || permisoService.tienePermiso(auth, "EVALUACION_CALIFICAR")) {
            return;
        }
        if (!permisoService.esPropioDocente(auth, jurado.getDocente().getId())) {
            throw new AccessDeniedException("Solo puedes registrar tu propia evaluación como jurado");
        }
    }

    /**
     * El jurado registra sus escalas por criterio.
     *
     * @param req calificación por criterio de rúbrica emitida por un jurado
     * @return la evaluación registrada, con la nota calculada para ese jurado
     * @throws RuntimeException si la solicitud, la rúbrica o el jurado no existen
     */
    @Override
    @Transactional
    public EvaluacionRubricaResponse registrarEvaluacion(EvaluacionRubricaRequest req) {
        Solicitud solicitud = solicitudRepo.findById(req.getSolicitudId())
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada: " + req.getSolicitudId()));

        Jurado jurado = juradoRepo.findById(req.getJuradoId())
                .orElseThrow(() -> new RuntimeException("Jurado no encontrado: " + req.getJuradoId()));

        if (!jurado.getSolicitud().getId().equals(req.getSolicitudId())) {
            throw new RuntimeException("El jurado no pertenece a esta solicitud.");
        }

        validarPuedeRegistrar(jurado);

        rubricaRepo.findById(req.getRubricaId())
                .orElseThrow(() -> new RuntimeException("Rúbrica no encontrada: " + req.getRubricaId()));

        List<CriterioRubrica> criterios = criterioRepo.findByRubricaIdOrderByOrdenAsc(req.getRubricaId());
        if (criterios.isEmpty()) {
            throw new RuntimeException("La rúbrica no tiene criterios definidos.");
        }
        if (req.getCriterios() == null || req.getCriterios().size() != criterios.size()) {
            throw new RuntimeException("Debe evaluar todos los " + criterios.size() + " criterios de la rúbrica.");
        }
        for (EscalaCriterioDTO c : req.getCriterios()) {
            if (c.getEscala() < 1 || c.getEscala() > 100) {
                throw new RuntimeException("Escala inválida: " + c.getEscala() + ". Use valores entre 1 y 100.");
            }
        }

        // Buscar o crear el Evaluador correspondiente para este jurado
        Evaluador evaluador = evaluadorRepo.findBySolicitudIdAndDocenteIdAndTipoEvaluadorCodigo(
                req.getSolicitudId(), jurado.getDocente().getId(), "JURADO")
            .orElseGet(() -> {
                TipoEvaluador tipo = tipoEvaluadorRepo.findByCodigo("JURADO")
                        .orElseThrow(() -> new RuntimeException("Tipo evaluador JURADO no configurado."));
                Evaluador ev = Evaluador.builder()
                        .solicitud(solicitud)
                        .docente(jurado.getDocente())
                        .miembroTribunal(jurado)
                        .tipoEvaluador(tipo)
                        .peso(1.0)
                        .build();
                return evaluadorRepo.save(ev);
            });

        // Permite re-evaluación: eliminar la anterior
        evalCriterioRepo.deleteBySolicitudIdAndEvaluadorId(req.getSolicitudId(), evaluador.getId());

        List<EvaluacionCriterio> guardadas = new ArrayList<>();
        for (EscalaCriterioDTO cDto : req.getCriterios()) {
            CriterioRubrica criterio = criterios.stream()
                    .filter(c -> c.getId().equals(cDto.getCriterioId()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Criterio no encontrado: " + cDto.getCriterioId()));

            double notaObtenida = Math.round(criterio.getPonderacion() * cDto.getEscala() / 100.0 * 100.0) / 100.0;
            String observacionAuto = EvaluacionCriterio.getObservacionPorRango(cDto.getEscala());

            EvaluacionCriterio ec = EvaluacionCriterio.builder()
                    .solicitud(solicitud)
                    .evaluador(evaluador)
                    .jurado(jurado)
                    .criterio(criterio)
                    .escala(cDto.getEscala())
                    .notaObtenida(notaObtenida)
                    .observacionAuto(observacionAuto)
                    .observacionManual(cDto.getObservacionManual())
                    .observaciones(cDto.getObservaciones())
                    .build();
            guardadas.add(evalCriterioRepo.save(ec));
        }

        return buildResponse(jurado, guardadas, req.getSolicitudId(), evaluador.getId());
    }
 
    /**
     * Estado de la evaluación de un jurado para una solicitud.
     *
     * @param solicitudId id de la solicitud
     * @param juradoId    id del jurado
     * @return la evaluación de ese jurado para esa solicitud, si ya la registró
     */
    @Override
    public EvaluacionRubricaResponse obtenerEvaluacionJurado(Long solicitudId, Long juradoId) {
        Jurado jurado = juradoRepo.findById(juradoId)
                .orElseThrow(() -> new RuntimeException("Jurado no encontrado: " + juradoId));
        solicitudAccessService.validarAcceso(jurado.getSolicitud(), "EVALUACION_CALIFICAR");

        Evaluador evaluador = evaluadorRepo.findBySolicitudIdAndDocenteIdAndTipoEvaluadorCodigo(
                solicitudId, jurado.getDocente().getId(), "JURADO")
                .orElseThrow(() -> new RuntimeException("Evaluador no registrado para el jurado."));

        List<EvaluacionCriterio> evals = evalCriterioRepo.findBySolicitudIdAndEvaluadorId(solicitudId, evaluador.getId());
        return buildResponse(jurado, evals, solicitudId, evaluador.getId());
    }
 
    /**
     * Resumen de todos los jurados para una solicitud.
     *
     * @param solicitudId id de la solicitud
     * @return las evaluaciones registradas por cada jurado de esa solicitud
     */
    @Override
    public List<EvaluacionRubricaResponse> obtenerEvaluacionesSolicitud(Long solicitudId) {
        Solicitud solicitudParaAcceso = solicitudRepo.findById(solicitudId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada: " + solicitudId));
        solicitudAccessService.validarAcceso(solicitudParaAcceso, "EVALUACION_CALIFICAR");

        List<Jurado> jurados = juradoRepo.findBySolicitudId(solicitudId);
        return jurados.stream()
                .map(j -> {
                    var evOpt = evaluadorRepo.findBySolicitudIdAndDocenteIdAndTipoEvaluadorCodigo(
                            solicitudId, j.getDocente().getId(), "JURADO");
                    List<EvaluacionCriterio> evals = evOpt.isPresent()
                            ? evalCriterioRepo.findBySolicitudIdAndEvaluadorId(solicitudId, evOpt.get().getId())
                            : new ArrayList<>();
                    return buildResponse(j, evals, solicitudId, evOpt.map(Evaluador::getId).orElse(null));
                })
                .collect(Collectors.toList());
    }
 
    /**
     * Nota promedio del tribunal (40%) lista para usar en la evaluación final.
     *
     * @param solicitudId id de la solicitud
     * @return el promedio, redondeado a 2 decimales, de las notas de los jurados que ya
     *         evaluaron; {@code 0.0} si ninguno ha evaluado todavía
     */
    @Override
    public Double calcularNotaTribunal(Long solicitudId) {
        Solicitud solicitudParaAcceso = solicitudRepo.findById(solicitudId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada: " + solicitudId));
        solicitudAccessService.validarAcceso(solicitudParaAcceso, "EVALUACION_CALIFICAR");
        return promedioTribunal(solicitudId);
    }
 
    // ── Helper ──────────────────────────────────────────────────────────────
 
    /** Calcula el promedio de (suma de notas por jurado) en Java para evitar subqueries en JPQL */
    private Double promedioTribunal(Long solicitudId) {
        List<Object[]> filas = evalCriterioRepo.sumaPorEvaluador(solicitudId);
        if (filas == null || filas.isEmpty()) return null;
        double suma = filas.stream()
                .mapToDouble(f -> ((Number) f[1]).doubleValue())
                .sum();
        double promedio = suma / filas.size();
        return Math.round(promedio * 100.0) / 100.0;
    }
 
    private EvaluacionRubricaResponse buildResponse(Jurado jurado,
                                                     List<EvaluacionCriterio> evals,
                                                     Long solicitudId,
                                                     Long evaluadorId) {
        String nombre = jurado.getDocente() != null && jurado.getDocente().getUsuario() != null
                ? jurado.getDocente().getUsuario().getNombre() + " " + jurado.getDocente().getUsuario().getApellido()
                : "Docente #" + jurado.getId();
 
        List<EvaluacionRubricaResponse.CriterioResultado> detalles = evals.stream()
                .map(ec -> EvaluacionRubricaResponse.CriterioResultado.builder()
                        .criterioId(ec.getCriterio().getId())
                        .nombreCriterio(ec.getCriterio().getNombre())
                        .ponderacion(ec.getCriterio().getPonderacion())
                        .escala(ec.getEscala())
                        .rangoDescripcion(EvaluacionCriterio.getRangoDescripcion(ec.getEscala()))
                        .notaObtenida(ec.getNotaObtenida())
                        .observacionAuto(ec.getObservacionAuto())
                        .observacionManual(ec.getObservacionManual())
                        .observaciones(ec.getObservaciones())
                        .build())
                .collect(Collectors.toList());
 
        double notaTotal = evals.stream()
                .mapToDouble(EvaluacionCriterio::getNotaObtenida)
                .sum();
        notaTotal = Math.round(notaTotal * 100.0) / 100.0;
 
        Double notaPromedio = promedioTribunal(solicitudId);
 
        List<Jurado> todosJurados = juradoRepo.findBySolicitudId(solicitudId);
        boolean completo = false;
        if (!todosJurados.isEmpty()) {
            completo = true;
            for (Jurado j : todosJurados) {
                var evOpt = evaluadorRepo.findBySolicitudIdAndDocenteIdAndTipoEvaluadorCodigo(
                        solicitudId, j.getDocente().getId(), "JURADO");
                if (evOpt.isPresent()) {
                    if (!evalCriterioRepo.existsBySolicitudIdAndEvaluadorId(solicitudId, evOpt.get().getId())) {
                        completo = false;
                        break;
                    }
                } else {
                    completo = false;
                    break;
                }
            }
        }
 
        return EvaluacionRubricaResponse.builder()
                .solicitudId(solicitudId)
                .juradoId(jurado.getId())
                .nombreJurado(nombre)
                .rolJurado(jurado.getRolJurado() != null ? jurado.getRolJurado().getNombre() : "")
                .detalles(detalles)
                .notaTotalJurado(evals.isEmpty() ? null : notaTotal)
                .notaPromedioTribunal(notaPromedio)
                .tribunalCompleto(completo)
                .build();
    }

    /**
     * Obtener todas las observaciones de una solicitud (tutor, jurados, coordinador).
     *
     * @param solicitudId id de la solicitud
     * @return observaciones consolidadas de todos los actores que han evaluado la solicitud
     */
    @Override
    @Transactional(readOnly = true)
    public ObservacionesSolicitudDTO obtenerObservacionesSolicitud(Long solicitudId) {
        Solicitud solicitud = solicitudRepo.findById(solicitudId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada: " + solicitudId));
        solicitudAccessService.validarAcceso(solicitud, "EVALUACION_CALIFICAR");

        String nombreEstudiante = "";
        if (solicitud.getEstudiante() != null && solicitud.getEstudiante().getUsuario() != null) {
            nombreEstudiante = solicitud.getEstudiante().getUsuario().getNombre() + " " 
                    + solicitud.getEstudiante().getUsuario().getApellido();
        }

        ObservacionesSolicitudDTO.ObservacionesTutorDTO tutorDTO = null;
        var tutorOpt = tutorRepo.findBySolicitudId(solicitudId);
        if (tutorOpt.isPresent()) {
            Tutor tutor = tutorOpt.get();
            String nombreTutor = tutor.getDocente() != null && tutor.getDocente().getUsuario() != null
                    ? tutor.getDocente().getUsuario().getNombre() + " " + tutor.getDocente().getUsuario().getApellido()
                    : "Tutor";
            String fechaRegistro = tutor.getFechaAsignacion() != null
                    ? tutor.getFechaAsignacion().toString() : null;
            tutorDTO = ObservacionesSolicitudDTO.ObservacionesTutorDTO.builder()
                    .tutorId(tutor.getId())
                    .nombreTutor(nombreTutor)
                    .observaciones(tutor.getObservaciones())
                    .fechaRegistro(fechaRegistro)
                    .build();
        }

        List<ObservacionesSolicitudDTO.ObservacionesJuradoDTO> juradosDTO = new ArrayList<>();
        List<Jurado> jurados = juradoRepo.findBySolicitudId(solicitudId);
        
        List<EvaluacionJurado> evaluacionesJurado = javaEvaluacionJuradoRepo.findBySolicitudId(solicitudId);
        
        for (Jurado jurado : jurados) {
            String nombreJurado = jurado.getDocente() != null && jurado.getDocente().getUsuario() != null
                    ? jurado.getDocente().getUsuario().getNombre() + " " + jurado.getDocente().getUsuario().getApellido()
                    : "Docente";
            
            EvaluacionJurado evalJurado = evaluacionesJurado.stream()
                    .filter(e -> e.getJurado().getId().equals(jurado.getId()))
                    .findFirst()
                    .orElse(null);
            
            var evOpt = evaluadorRepo.findBySolicitudIdAndDocenteIdAndTipoEvaluadorCodigo(solicitudId, jurado.getDocente().getId(), "JURADO");
            List<EvaluacionCriterio> criterios = evOpt.isPresent()
                    ? evalCriterioRepo.findBySolicitudIdAndEvaluadorId(solicitudId, evOpt.get().getId())
                    : new ArrayList<>();
            List<ObservacionesSolicitudDTO.CriterioObservacionDTO> criteriosDTO = criterios.stream()
                    .map(ec -> ObservacionesSolicitudDTO.CriterioObservacionDTO.builder()
                            .nombreCriterio(ec.getCriterio().getNombre())
                            .ponderacion(ec.getCriterio().getPonderacion())
                            .escala(ec.getEscala())
                            .rangoDescripcion(EvaluacionCriterio.getRangoDescripcion(ec.getEscala()))
                            .notaObtenida(ec.getNotaObtenida())
                            .observacionAuto(ec.getObservacionAuto())
                            .observacionManual(ec.getObservacionManual())
                            .build())
                    .collect(Collectors.toList());
            
            juradosDTO.add(ObservacionesSolicitudDTO.ObservacionesJuradoDTO.builder()
                    .juradoId(jurado.getId())
                    .nombreJurado(nombreJurado)
                    .rol(jurado.getRolJurado() != null ? jurado.getRolJurado().getNombre() : "")
                    .criterios(criteriosDTO)
                    .notaJurado(evalJurado != null ? evalJurado.getNotaJurado() : null)
                    .observaciones(evalJurado != null ? evalJurado.getObservaciones() : null)
                    .resultado(evalJurado != null ? evalJurado.getResultado() : null)
                    .comentarioPreestablecido(evalJurado != null ? evalJurado.getComentarioPreestablecido() : null)
                    .build());
        }

        ObservacionesSolicitudDTO.ObservacionesCoordinadorDTO coordinadorDTO = null;
        var evaluacionOpt = evaluacionFinalRepo.findBySolicitudId(solicitudId);
        if (evaluacionOpt.isPresent()) {
            EvaluacionFinal ev = evaluacionOpt.get();
            coordinadorDTO = ObservacionesSolicitudDTO.ObservacionesCoordinadorDTO.builder()
                    .observaciones(ev.getObservaciones())
                    .notaInstructor(ev.getNotaInstructor())
                    .notaFinal(ev.getNotaFinal())
                    .resultado(ev.getResultado() != null ? ev.getResultado().getNombre() : "")
                    .build();
        }

        return ObservacionesSolicitudDTO.builder()
                .solicitudId(solicitudId)
                .tituloTema(solicitud.getTituloTema())
                .nombreEstudiante(nombreEstudiante)
                .tutor(tutorDTO)
                .jurados(juradosDTO)
                .coordinador(coordinadorDTO)
                .build();
    }
}
