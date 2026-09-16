package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.PromedioEvaluacionResult;
import ec.edu.uteq.presustentaciones.entities.Evaluacion;
import ec.edu.uteq.presustentaciones.entities.EvaluacionFinal;
import ec.edu.uteq.presustentaciones.entities.Rubrica;
import ec.edu.uteq.presustentaciones.entities.Solicitud;
import ec.edu.uteq.presustentaciones.repositories.EvaluacionFinalRepository;
import ec.edu.uteq.presustentaciones.repositories.EvaluacionRepository;
import ec.edu.uteq.presustentaciones.repositories.RubricaRepository;
import ec.edu.uteq.presustentaciones.repositories.SolicitudRepository;
import ec.edu.uteq.presustentaciones.security.service.SolicitudAccessService;
import ec.edu.uteq.presustentaciones.security.service.UsuarioActualService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
@Slf4j
public class EvaluacionServiceImpl implements EvaluacionService {

    private final EvaluacionFinalRepository evaluacionRepository;
    private final SolicitudRepository solicitudRepository;
    private final RubricaRepository rubricaRepository;
    private final NotificacionService notificacionService;
    private final ec.edu.uteq.presustentaciones.repositories.EstadoSolicitudRepository estadoSolicitudRepository;
    private final ec.edu.uteq.presustentaciones.repositories.ResultadoEvaluacionRepository resultadoEvaluacionRepository;
    private final EvaluacionRepository evaluacionSpRepository;
    private final UsuarioActualService usuarioActualService;
    private final SolicitudAccessService solicitudAccessService;
    private final PermisoService permisoService;

    /** ADMIN o titular de EVALUACION_CALIFICAR (ADMIN/COORDINADOR); el resto solo puede
     * consultar su propia información -- evita que un estudiante o docente lea las
     * evaluaciones de otro usuario cambiando el id en la URL (IDOR). */
    private boolean esAdminOCoordinador() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return isAdmin || permisoService.tienePermiso(auth, "EVALUACION_CALIFICAR");
    }

    private void validarAccesoPropioOAdmin(Long usuarioIdObjetivo) {
        if (esAdminOCoordinador()) return;
        Long usuarioActualId = usuarioActualService.usuario().getId();
        if (!usuarioActualId.equals(usuarioIdObjetivo)) {
            throw new AccessDeniedException("No tienes permiso para consultar las evaluaciones de otro usuario");
        }
    }

    private void validarAccesoEstudiantePropioOAdmin(Long estudianteIdObjetivo) {
        if (esAdminOCoordinador()) return;
        Long estudianteActualId = usuarioActualService.estudianteIdOrNull();
        if (estudianteActualId == null || !estudianteActualId.equals(estudianteIdObjetivo)) {
            throw new AccessDeniedException("No tienes permiso para consultar las evaluaciones de otro estudiante");
        }
    }

    /**
     * Agrega las notas por criterio del tribunal (evaluaciones_criterio) con la nota del
     * instructor y persiste nota_final/estado_resultado vía sp_calcular_promedio_evaluacion
     * (Fase 3 / Criterio P1, categoría "cálculos agregados").
     *
     * @param solicitudId id de la solicitud a calcular
     * @return solicitudId, nota final ponderada y estado del resultado ("APROBADO"/"REPROBADO")
     * @throws RuntimeException si la solicitud no existe o el procedimiento no devuelve fila
     */
    @Override
    @Transactional
    public PromedioEvaluacionResult calcularPromedioSp(Long solicitudId) {
        Solicitud solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada: " + solicitudId));

        // sp_calcular_promedio_evaluacion necesita una fila previa en "evaluaciones" (lee su
        // nota_instructor); la crea si no existe, tomando la nota del instructor ya
        // registrada en el flujo ponderado (evaluaciones_finales) cuando esté disponible.
        Evaluacion base = evaluacionSpRepository.findBySolicitudId(solicitudId)
                .orElseGet(() -> {
                    Double notaInstructor = evaluacionRepository.findBySolicitudId(solicitudId)
                            .map(EvaluacionFinal::getNotaInstructor)
                            .orElse(null);
                    return evaluacionSpRepository.save(Evaluacion.builder()
                            .solicitud(solicitud)
                            .notaInstructor(notaInstructor)
                            .build());
                });

        List<PromedioEvaluacionResult> resultado = evaluacionSpRepository.calcularPromedioEvaluacion(solicitudId);
        if (resultado.isEmpty()) {
            throw new RuntimeException("El procedimiento no devolvió resultado para la solicitud " + solicitudId);
        }
        return resultado.get(0);
    }

    /**
     * Registra evaluación con notas separadas de instructor y jurado (RF-09).
     *
     * @param solicitudId    id de la solicitud a evaluar
     * @param rubricaId      id de la rúbrica aplicada
     * @param notaInstructor nota del instructor del curso, entre 0 y 10
     * @param notaJurado     nota promedio del tribunal, entre 0 y 10
     * @param observaciones  observaciones opcionales de la evaluación
     * @param pesoInstructor peso del instructor en la ponderación (0-100); {@code null} usa 60
     * @param pesoJurado     peso del jurado en la ponderación (0-100); {@code null} usa 40
     * @return la evaluación final persistida, con nota final calculada y resultado asignado
     * @throws RuntimeException si la solicitud o la rúbrica no existen, los pesos no suman
     *                          100, o alguna nota está fuera de 0-10
     */
    @Override
    @Transactional
    public EvaluacionFinal evaluarSolicitud(Long solicitudId, Long rubricaId,
                                       Double notaInstructor, Double notaJurado,
                                       String observaciones,
                                       Double pesoInstructor, Double pesoJurado) {
        Solicitud solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada: " + solicitudId));
        Rubrica rubrica = rubricaRepository.findById(rubricaId)
                .orElseThrow(() -> new RuntimeException("Rúbrica no encontrada: " + rubricaId));

        double sumaPesos = (pesoInstructor != null ? pesoInstructor : 60.0)
                + (pesoJurado != null ? pesoJurado : 40.0);
        if (Math.abs(sumaPesos - 100.0) > 0.01) {
            throw new RuntimeException("Los pesos deben sumar 100. Suma actual: " + sumaPesos);
        }

        if (notaInstructor < 0 || notaInstructor > 10 || notaJurado < 0 || notaJurado > 10) {
            throw new RuntimeException("Las notas deben estar entre 0 y 10.");
        }

        EvaluacionFinal e = EvaluacionFinal.builder()
                .solicitud(solicitud)
                .rubrica(rubrica)
                .notaInstructor(notaInstructor)
                .notaJuradoPromedio(notaJurado)
                .pesoInstructor((pesoInstructor != null ? pesoInstructor : 60.0) / 100.0)
                .pesoJurado((pesoJurado != null ? pesoJurado : 40.0) / 100.0)
                .observaciones(observaciones)
                .build();

        e.calcularNotaFinal();
        
        String resCod = e.getNotaFinal() >= 7.0 ? "APROBADO" : "REPROBADO";
        ec.edu.uteq.presustentaciones.entities.ResultadoEvaluacion res = resultadoEvaluacionRepository.findByCodigo(resCod)
                .orElseGet(() -> resultadoEvaluacionRepository.save(ec.edu.uteq.presustentaciones.entities.ResultadoEvaluacion.builder()
                        .codigo(resCod).nombre(resCod.substring(0,1) + resCod.substring(1).toLowerCase()).build()));
        
        e.setResultado(res);
        e.setComentarioPreestablecido(generarComentarioPorRango(e.getNotaFinal()));
        EvaluacionFinal guardada = evaluacionRepository.save(e);

        // Cambiar estado a CALIFICADA
        ec.edu.uteq.presustentaciones.entities.EstadoSolicitud estadoCalificada = estadoSolicitudRepository.findByCodigo("CALIFICADA")
                .orElseGet(() -> estadoSolicitudRepository.save(ec.edu.uteq.presustentaciones.entities.EstadoSolicitud.builder()
                        .codigo("CALIFICADA").nombre("Calificada").build()));
        solicitud.setEstado(estadoCalificada);
        solicitudRepository.save(solicitud);

        notificarNotaFinal(solicitud, guardada);

        return guardada;
    }

    /**
     * Compatibilidad: evalúa pasando nota final directa (para uso legacy).
     *
     * @param solicitudId   id de la solicitud a evaluar
     * @param rubricaId     id de la rúbrica aplicada
     * @param notaFinal     nota final ya calculada externamente
     * @param observaciones observaciones opcionales
     * @return la evaluación final persistida
     * @throws RuntimeException si la solicitud o la rúbrica no existen
     */
    @Override
    @Transactional
    public EvaluacionFinal evaluarSolicitud(Long solicitudId, Long rubricaId,
                                       Double notaFinal, String observaciones) {
        Solicitud solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));
        Rubrica rubrica = rubricaRepository.findById(rubricaId)
                .orElseThrow(() -> new RuntimeException("Rúbrica no encontrada"));

        String resCod = notaFinal >= 7 ? "APROBADO" : "REPROBADO";
        ec.edu.uteq.presustentaciones.entities.ResultadoEvaluacion res = resultadoEvaluacionRepository.findByCodigo(resCod)
                .orElseGet(() -> resultadoEvaluacionRepository.save(ec.edu.uteq.presustentaciones.entities.ResultadoEvaluacion.builder()
                        .codigo(resCod).nombre(resCod.substring(0,1) + resCod.substring(1).toLowerCase()).build()));

        EvaluacionFinal e = EvaluacionFinal.builder()
                .solicitud(solicitud).rubrica(rubrica)
                .notaFinal(notaFinal).observaciones(observaciones)
                .pesoInstructor(0.6).pesoJurado(0.4)
                .resultado(res)
                .build();
        e.setComentarioPreestablecido(generarComentarioPorRango(notaFinal));
        EvaluacionFinal guardada = evaluacionRepository.save(e);

        // Cambiar estado a CALIFICADA
        ec.edu.uteq.presustentaciones.entities.EstadoSolicitud estadoCalificada = estadoSolicitudRepository.findByCodigo("CALIFICADA")
                .orElseGet(() -> estadoSolicitudRepository.save(ec.edu.uteq.presustentaciones.entities.EstadoSolicitud.builder()
                        .codigo("CALIFICADA").nombre("Calificada").build()));
        solicitud.setEstado(estadoCalificada);
        solicitudRepository.save(solicitud);

        notificarNotaFinal(solicitud, guardada);

        return guardada;
    }

    /**
     * @param pageable configuración de paginación
     * @return página de todas las evaluaciones finales del sistema
     */
    @Override
    public Page<EvaluacionFinal> listarEvaluaciones(Pageable pageable) {
        return evaluacionRepository.findAll(pageable);
    }

    /**
     * @param estudianteId id del estudiante
     * @return las evaluaciones finales de las solicitudes de ese estudiante
     */
    @Override
    public List<EvaluacionFinal> listarPorEstudiante(Long estudianteId) {
        validarAccesoEstudiantePropioOAdmin(estudianteId);
        return evaluacionRepository.findByEstudianteId(estudianteId);
    }

    /**
     * @param usuarioId id del usuario autenticado
     * @return las evaluaciones finales visibles para ese usuario
     */
    @Override
    public List<EvaluacionFinal> listarPorUsuario(Long usuarioId) {
        validarAccesoPropioOAdmin(usuarioId);
        return evaluacionRepository.findByUsuarioId(usuarioId);
    }

    /**
     * @param solicitudId id de la solicitud
     * @return la evaluación final de esa solicitud, si ya fue calificada
     */
    @Override
    public Optional<EvaluacionFinal> buscarPorSolicitud(Long solicitudId) {
        Optional<EvaluacionFinal> evaluacion = evaluacionRepository.findBySolicitudId(solicitudId);
        evaluacion.ifPresent(e -> solicitudAccessService.validarAcceso(e.getSolicitud(), "EVALUACION_CALIFICAR"));
        return evaluacion;
    }

    /**
     * Variante de {@link #calcularPromedioSp} que devuelve el resultado como un mapa
     * genérico en vez de un DTO tipado, para consumo directo desde el controlador.
     *
     * @param solicitudId id de la solicitud a calcular
     * @return mapa con las claves {@code solicitudId}, {@code notaFinal} y
     *         {@code estadoResultado}; vacío si el procedimiento no devolvió filas
     */
    @Override
    @Transactional
    public Map<String, Object> calcularPromedioSP(Long solicitudId) {
        List<Object[]> res = evaluacionRepository.calcularPromedioEvaluacionSp(solicitudId);
        Map<String, Object> map = new HashMap<>();
        if (!res.isEmpty()) {
            Object[] row = res.get(0);
            map.put("solicitudId", row[0]);
            map.put("notaFinal", row[1]);
            map.put("estadoResultado", row[2]);
        }
        return map;
    }

    /**
     * Deriva un comentario preestablecido a partir del rango de la nota final, para dejar
     * una observación por defecto cuando el evaluador no escribe una propia.
     *
     * @param notaFinal nota final calculada, o {@code null}
     * @return un comentario según el rango (≤3, ≤6, &gt;6), o cadena vacía si {@code notaFinal}
     *         es {@code null}
     */
    public String generarComentarioPorRango(Double notaFinal) {
        if (notaFinal == null) return "";
        if (notaFinal <= 3) {
            return "El trabajo no cumple con los requisitos mínimos esperados. Se evidencian falencias significativas que requieren correcciones sustanciales.";
        } else if (notaFinal <= 6) {
            return "El trabajo presenta un nivel aceptable pero con aspectos que requieren mejoras o correcciones para alcanzar los estándares esperados.";
        } else {
            return "El trabajo cumple satisfactoriamente con los objetivos y requisitos establecidos, demostrando un desempeño adecuado.";
        }
    }

    // ── Notificación nota final ───────────────────────────────────────────────

    private void notificarNotaFinal(Solicitud solicitud, EvaluacionFinal evaluacion) {
        try {
            Long usuarioId = solicitud.getEstudiante().getUsuario().getId();
            String titulo  = solicitud.getTituloTema();
            Double nota    = evaluacion.getNotaFinal();
            
            String resNombre = evaluacion.getResultado() != null ? evaluacion.getResultado().getNombre() : "";
            String resCodigo = evaluacion.getResultado() != null ? evaluacion.getResultado().getCodigo() : "";
            if (resCodigo.isEmpty()) {
                resCodigo = nota != null && nota >= 7 ? "APROBADO" : "REPROBADO";
                resNombre = "APROBADO".equals(resCodigo) ? "Aprobado" : "Reprobado";
            }

            String emoji = "APROBADO".equals(resCodigo) ? "🎉" : "😔";
            String msg;

            if (nota != null) {
                msg = String.format(
                        "%s Tu pre-sustentación \"%s\" ha sido evaluada. " +
                                "Nota final: %.2f / 10 — Resultado: %s. Tu solicitud ahora está en fase de calificación.",
                        emoji, titulo, nota, resNombre);
            } else {
                msg = String.format(
                        "%s Tu pre-sustentación \"%s\" ha sido evaluada. Resultado: %s. Tu solicitud ahora está en fase de calificación.",
                        emoji, titulo, resNombre);
            }

            if (evaluacion.getObservaciones() != null && !evaluacion.getObservaciones().isBlank()) {
                msg += " Observaciones: " + evaluacion.getObservaciones();
            }

            notificacionService.crearNotificacion(usuarioId, msg);
        } catch (Exception e) {
            log.warn("No se pudo notificar nota final al estudiante: {}", e.getMessage());
        }
    }
}
