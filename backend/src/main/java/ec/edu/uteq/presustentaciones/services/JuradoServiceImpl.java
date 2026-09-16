package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.Docente;
import ec.edu.uteq.presustentaciones.entities.Jurado;
import ec.edu.uteq.presustentaciones.entities.Solicitud;
import ec.edu.uteq.presustentaciones.entities.Tutor;
import ec.edu.uteq.presustentaciones.repositories.DocenteRepository;
import ec.edu.uteq.presustentaciones.repositories.JuradoRepository;
import ec.edu.uteq.presustentaciones.repositories.SolicitudRepository;
import ec.edu.uteq.presustentaciones.repositories.TutorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class JuradoServiceImpl implements JuradoService {

    private final JuradoRepository juradoRepository;
    private final TutorRepository tutorRepository;
    private final DocenteRepository docenteRepository;
    private final SolicitudRepository solicitudRepository;
    private final NotificacionService notificacionService;
    private final EmailService emailService;
    private final ec.edu.uteq.presustentaciones.repositories.RolJuradoRepository rolJuradoRepository;
    private final ec.edu.uteq.presustentaciones.repositories.EstadoSolicitudRepository estadoSolicitudRepository;

    // ── Jurados ───────────────────────────────────────────────────────────────

    /**
     * Asigna (upsert) un docente como jurado de una solicitud con el rol indicado.
     *
     * @param solicitudId id de la solicitud
     * @param docenteId   id del docente a asignar
     * @param rol         código de rol de jurado ({@code PRESIDENTE}, {@code VOCAL_1} o
     *                    {@code VOCAL_2})
     * @return el registro de jurado creado o actualizado
     * @throws RuntimeException si el rol no es uno de los tres válidos
     */
    @Override
    @Transactional
    public Jurado asignarJurado(Long solicitudId, Long docenteId, String rol) {
        Solicitud solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada: " + solicitudId));
        Docente docente = docenteRepository.findById(docenteId)
                .orElseThrow(() -> new RuntimeException("Docente no encontrado: " + docenteId));

        // Validar que la tutoría esté COMPLETADA antes de asignar tribunal
        Tutor tutor = tutorRepository.findBySolicitudId(solicitudId)
                .orElseThrow(() -> new RuntimeException(
                        "No puedes asignar tribunal: esta solicitud no tiene tutor asignado"));
        if (!"COMPLETADA".equals(tutor.getEstado())) {
            throw new RuntimeException(
                    "No puedes asignar tribunal: la tutoría aún no ha completado las 3 revisiones obligatorias");
        }

        boolean yaAsignado = juradoRepository.findBySolicitudId(solicitudId).stream()
                .anyMatch(j -> j.getDocente().getId().equals(docenteId));
        if (yaAsignado) {
            throw new RuntimeException("El docente ya está asignado como jurado en esta solicitud.");
        }

        // ERR-03: el docente ya asignado como tutor de esta misma solicitud no puede
        // además ser miembro del tribunal (conflicto de interés).
        if (tutor.getDocente() != null && tutor.getDocente().getId().equals(docenteId)) {
            throw new RuntimeException("El docente ya es el tutor de esta solicitud y no puede además ser jurado (conflicto de interés).");
        }

        if (docente.getDisponible() != null && !docente.getDisponible()) {
            throw new RuntimeException("El docente no está disponible para ser asignado como jurado.");
        }

        // ERR-03: normalizado una sola vez -- se usa tanto para validar como para guardar,
        // así el chequeo de "rol duplicado" compara contra el mismo código que quedará en BD
        // (antes comparaba el string crudo de entrada contra un código ya colapsado distinto,
        // por lo que nunca coincidían).
        String rolNormalizado = rol == null ? null : rol.trim().toUpperCase();
        List<String> rolesValidos = List.of("PRESIDENTE", "VOCAL_1", "VOCAL_2");
        if (rolNormalizado == null || !rolesValidos.contains(rolNormalizado)) {
            throw new RuntimeException("Rol inválido. Use: PRESIDENTE, VOCAL_1 o VOCAL_2");
        }

        boolean rolOcupado = juradoRepository.findBySolicitudId(solicitudId).stream()
                .anyMatch(j -> rolNormalizado.equalsIgnoreCase(j.getRol()));
        if (rolOcupado) {
            throw new RuntimeException("El rol '" + rolNormalizado + "' ya está asignado en esta solicitud.");
        }

        Jurado guardado = crearJuradoSinNotificar(solicitud, docente, rolNormalizado);

        // Cambiar estado a EVALUACION
        ec.edu.uteq.presustentaciones.entities.EstadoSolicitud estadoEvaluacion = estadoSolicitudRepository.findByCodigo("EVALUACION")
                .orElseGet(() -> estadoSolicitudRepository.save(ec.edu.uteq.presustentaciones.entities.EstadoSolicitud.builder()
                        .codigo("EVALUACION").nombre("Evaluacion").build()));

        solicitud.setEstado(estadoEvaluacion);
        solicitudRepository.save(solicitud);

        // Notificar al docente asignado como jurado
        notificarDocenteJurado(docente, solicitud, rolNormalizado);

        // Notificar al estudiante que se le asignó un jurado
        notificarEstudianteJurado(solicitud, docente, rolNormalizado);

        return guardado;
    }

    /**
     * @param solicitudId id de la solicitud
     * @return los jurados asignados a esa solicitud (0 a 3 registros)
     */
    @Override
    public List<Jurado> listarPorSolicitud(Long solicitudId) {
        return juradoRepository.findBySolicitudId(solicitudId);
    }

    /**
     * @param pageable configuración de paginación
     * @return página de todos los registros de jurado del sistema
     */
    @Override
    public Page<Jurado> listarTodos(Pageable pageable) {
        return juradoRepository.findAll(pageable);
    }

    /** @param juradoId id del registro de jurado a eliminar */
    @Override
    @Transactional
    public void eliminarJurado(Long juradoId) {
        Jurado jurado = juradoRepository.findById(juradoId)
                .orElseThrow(() -> new RuntimeException("Jurado no encontrado: " + juradoId));
        Docente docente = jurado.getDocente();
        int nuevaCarga = Math.max(0, docente.getCargaHorariaSemanal() - 1);
        docente.setCargaHorariaSemanal(nuevaCarga);
        docenteRepository.save(docente);
        juradoRepository.deleteById(juradoId);
    }

    // ── Tutor ─────────────────────────────────────────────────────────────────

    /**
     * @param solicitudId id de la solicitud
     * @param docenteId   id del docente que actuará como tutor
     * @return el registro de tutoría creado
     */
    @Override
    @Transactional
    public Tutor asignarTutor(Long solicitudId, Long docenteId) {
        Solicitud solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada: " + solicitudId));
        Docente docente = docenteRepository.findById(docenteId)
                .orElseThrow(() -> new RuntimeException("Docente no encontrado: " + docenteId));

        Tutor tutor = tutorRepository.findBySolicitudId(solicitudId)
                .orElse(Tutor.builder().solicitud(solicitud).build());

        tutor.setDocente(docente);
        tutor.setEstado("ACTIVO");

        // Re-fetch tras save para garantizar que todas las asociaciones estén cargadas
        Tutor guardado = tutorRepository.findById(tutorRepository.save(tutor).getId())
                .orElseThrow(() -> new RuntimeException("Error al recuperar el tutor guardado"));

        // Cambiar estado a TUTORIA
        ec.edu.uteq.presustentaciones.entities.EstadoSolicitud estadoTutoria = estadoSolicitudRepository.findByCodigo("TUTORIA")
                .orElseGet(() -> estadoSolicitudRepository.save(ec.edu.uteq.presustentaciones.entities.EstadoSolicitud.builder()
                        .codigo("TUTORIA").nombre("Tutoria").build()));
 
        solicitud.setEstado(estadoTutoria);
        solicitudRepository.save(solicitud);

        // Notificar al docente asignado como tutor
        notificarDocenteTutor(docente, solicitud);

        // Notificar al estudiante que tiene tutor asignado
        notificarEstudianteTutor(solicitud, docente);

        return guardado;
    }

    /**
     * @param solicitudId id de la solicitud
     * @return el tutor asignado, si existe
     */
    @Override
    public Optional<Tutor> obtenerTutorDeSolicitud(Long solicitudId) {
        // Antes filtraba estado == "ACTIVO", pero cuando la tutoría termina el registro pasa a
        // "COMPLETADA" y ESE es justo el estado normal cuando ya hay acta que firmar. El filtro
        // hacía que "Firmar Acta" respondiera 404 ("No tienes un rol asignado") a todo tutor de
        // un proyecto ya calificado. El tutor se quita con un DELETE, así que si la fila existe,
        // ese docente es el tutor — sin importar si la tutoría sigue en curso o ya cerró.
        return tutorRepository.findBySolicitudId(solicitudId);
    }

    /** @param tutorId id del registro de tutoría a eliminar */
    @Override
    @Transactional
    public void eliminarTutor(Long tutorId) {
        tutorRepository.deleteById(tutorId);
    }

    // ── Sugerencia automática ─────────────────────────────────────────────────

    /**
     * Sugiere docentes candidatos a jurado para una solicitud (excluyendo al tutor asignado y a
     * quienes ya tengan conflicto de horario), sin asignarlos todavía.
     *
     * @param solicitudId id de la solicitud
     * @param cantidad    número máximo de docentes a sugerir
     * @return lista de docentes candidatos, tamaño ≤ {@code cantidad}
     */
    @Override
    public List<Docente> sugerirDocentes(Long solicitudId, int cantidad) {
        List<Long> idsOcupados = new ArrayList<>();
        juradoRepository.findBySolicitudId(solicitudId)
                .forEach(j -> idsOcupados.add(j.getDocente().getId()));
        tutorRepository.findBySolicitudId(solicitudId)
                .ifPresent(t -> idsOcupados.add(t.getDocente().getId()));

        List<Docente> candidatos = docenteRepository.findDisponiblesOrdenadosPorCarga().stream()
                .filter(d -> !idsOcupados.contains(d.getId()))
                .collect(Collectors.toList());

        if (candidatos.size() < cantidad) {
            candidatos = docenteRepository.findTodosOrdenadosPorCarga().stream()
                    .filter(d -> !idsOcupados.contains(d.getId()))
                    .collect(Collectors.toList());
        }

        return candidatos.stream().limit(cantidad).collect(Collectors.toList());
    }

    /**
     * Asigna automáticamente los 3 roles de tribunal (PRESIDENTE, VOCAL_1, VOCAL_2) para una
     * solicitud, usando la misma lógica de sugerencia que {@link #sugerirDocentes}.
     *
     * @param solicitudId id de la solicitud
     * @throws RuntimeException si no hay suficientes docentes disponibles para completar el
     *                          tribunal
     */
    @Override
    @Transactional
    public void asignarJuradosAutomaticamente(Long solicitudId) {
        // Validar tutoría completada (asignarJurado ya no se llama, validamos aquí)
        Tutor tutor = tutorRepository.findBySolicitudId(solicitudId)
                .orElseThrow(() -> new RuntimeException(
                        "No puedes asignar tribunal: esta solicitud no tiene tutor asignado"));
        if (!"COMPLETADA".equals(tutor.getEstado())) {
            throw new RuntimeException(
                    "No puedes asignar tribunal: la tutoría aún no ha completado las 3 revisiones obligatorias");
        }

        Solicitud solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada: " + solicitudId));

        List<String> rolesOcupados = juradoRepository.findBySolicitudId(solicitudId)
                .stream().map(Jurado::getRol).collect(Collectors.toList());
        List<String> rolesFaltantes = new ArrayList<>(List.of("PRESIDENTE", "VOCAL_1", "VOCAL_2"))
                .stream().filter(r -> !rolesOcupados.contains(r)).collect(Collectors.toList());

        if (rolesFaltantes.isEmpty()) return;

        List<Docente> sugeridos = sugerirDocentes(solicitudId, rolesFaltantes.size());
        if (sugeridos.size() < rolesFaltantes.size()) {
            throw new RuntimeException(
                    "No hay suficientes docentes para asignar automáticamente. " +
                            "Disponibles: " + sugeridos.size() + ", requeridos: " + rolesFaltantes.size());
        }

        // Asignar sin notificar al estudiante en cada iteración
        for (int i = 0; i < rolesFaltantes.size(); i++) {
            Docente docente = sugeridos.get(i);
            String rol = rolesFaltantes.get(i);
            // ERR-03: sugerirDocentes() ya excluye al tutor de esta solicitud de idsOcupados,
            // así que el conflicto de interés tutor==jurado no puede ocurrir aquí. El pool de
            // respaldo (findTodosOrdenadosPorCarga) ignora "disponible" a propósito -- es el
            // fallback para cuando no hay suficientes docentes disponibles y completar el
            // tribunal es preferible a fallar -- pero se deja constancia en el log.
            if (docente.getDisponible() != null && !docente.getDisponible()) {
                log.warn("Asignación automática de jurado usó un docente no disponible (id={}) " +
                        "por falta de suficientes docentes disponibles para la solicitud {}.", docente.getId(), solicitudId);
            }
            crearJuradoSinNotificar(solicitud, docente, rol);
            notificarDocenteJurado(docente, solicitud, rol);  // cada docente es destinatario distinto
        }
 
        // Cambiar estado a EVALUACION
        ec.edu.uteq.presustentaciones.entities.EstadoSolicitud estadoEvaluacion = estadoSolicitudRepository.findByCodigo("EVALUACION")
                .orElseGet(() -> estadoSolicitudRepository.save(ec.edu.uteq.presustentaciones.entities.EstadoSolicitud.builder()
                        .codigo("EVALUACION").nombre("Evaluacion").build()));
 
        solicitud.setEstado(estadoEvaluacion);
        solicitudRepository.save(solicitud);

        // Una sola notificación + correo agrupado al estudiante
        List<Jurado> todosJurados = juradoRepository.findBySolicitudId(solicitudId);
        notificarEstudianteTribunalCompleto(solicitud, todosJurados);
    }

    // ── Asignación masiva vía procedimiento almacenado ──────────────────────────

    /**
     * Asigna en lote pares (solicitudId, docenteId) al rol indicado, invocando
     * sp_asignar_jurado_masivo una vez por par. Toda la operación corre dentro
     * de una única transacción: si un par falla (rol inválido, FK inexistente),
     * se revierten también los pares ya procesados en esa misma llamada.
     *
     * @param solicitudIds ids de las solicitudes, en el mismo orden que {@code docenteIds}
     * @param docenteIds   ids de los docentes a asignar, uno por cada solicitud del arreglo
     * @param rolCodigo    código de rol aplicado a todos los pares del lote
     * @throws RuntimeException si los dos arreglos no tienen la misma longitud, o si el
     *                          procedimiento almacenado rechaza algún par (rol inválido, FK
     *                          inexistente, o conflicto de horario)
     */
    @Override
    @Transactional
    public void asignarJuradoMasivo(List<Long> solicitudIds, List<Long> docenteIds, String rolCodigo) {
        if (solicitudIds == null || docenteIds == null || solicitudIds.size() != docenteIds.size()) {
            throw new RuntimeException("Los arreglos de solicitudes y docentes deben tener la misma longitud");
        }
        for (int i = 0; i < solicitudIds.size(); i++) {
            juradoRepository.spAsignarJuradoMasivo(solicitudIds.get(i), docenteIds.get(i), rolCodigo);
        }
    }

    // ── Vista del docente ─────────────────────────────────────────────────────

    /**
     * @param docenteId id del docente
     * @return las asignaciones de jurado de ese docente, en cualquier solicitud
     */
    @Override
    public List<Jurado> listarPorDocente(Long docenteId) {
        return juradoRepository.findByDocenteId(docenteId);
    }

    /**
     * @param docenteId id del docente
     * @return las tutorías activas de ese docente
     */
    @Override
    public List<Tutor> listarTutoriasPorDocente(Long docenteId) {
        return tutorRepository.findByDocenteId(docenteId);
    }

    /**
     * @param solicitudId id de la solicitud
     * @param usuarioId   id del usuario autenticado (se resuelve contra el docente vinculado)
     * @return la asignación de jurado de ese usuario en esa solicitud, si existe
     */
    @Override
    public Optional<Jurado> obtenerInfoJurado(Long solicitudId, Long usuarioId) {
        return juradoRepository.findBySolicitudIdAndUsuarioId(solicitudId, usuarioId);
    }

    // ── Helpers internos ─────────────────────────────────────────────────────

    /**
     * Guarda el jurado y actualiza la carga del docente. No envía ninguna notificación.
     * ERR-03: antes colapsaba cualquier rol que empezara con "VOCAL" al código genérico
     * "VOCAL" antes de guardar, así que VOCAL_1/VOCAL_2 nunca quedaban en BD tal cual --
     * rompía la pantalla de Evaluar (busca literalmente esos códigos) y el chequeo de rol
     * duplicado. Se guarda el código ya normalizado por el llamador, sin transformarlo.
     */
    private Jurado crearJuradoSinNotificar(Solicitud solicitud, Docente docente, String rol) {
        docente.setCargaHorariaSemanal(docente.getCargaHorariaSemanal() + 1);
        docenteRepository.save(docente);

        String codigoRol = rol.toUpperCase();
        final String finalCodigoRol = codigoRol;
        ec.edu.uteq.presustentaciones.entities.RolJurado rolJurado = rolJuradoRepository.findByCodigo(codigoRol)
                .orElseGet(() -> {
                    return rolJuradoRepository.save(ec.edu.uteq.presustentaciones.entities.RolJurado.builder()
                            .codigo(finalCodigoRol)
                            .nombre(finalCodigoRol.substring(0, 1).toUpperCase() + finalCodigoRol.substring(1).toLowerCase())
                            .build());
                });

        return juradoRepository.save(Jurado.builder()
                .solicitud(solicitud)
                .docente(docente)
                .rolJurado(rolJurado)
                .confirmado(false)
                .build());
    }

    /** Una sola notificación en BD + un solo correo al estudiante con el tribunal completo. */
    private void notificarEstudianteTribunalCompleto(Solicitud solicitud, List<Jurado> jurados) {
        try {
            String presidente = jurados.stream().filter(j -> "PRESIDENTE".equals(j.getRol()))
                    .map(j -> j.getDocente().getUsuario().getNombre() + " " + j.getDocente().getUsuario().getApellido())
                    .findFirst().orElse("-");
            String vocal1 = jurados.stream().filter(j -> "VOCAL_1".equals(j.getRol()))
                    .map(j -> j.getDocente().getUsuario().getNombre() + " " + j.getDocente().getUsuario().getApellido())
                    .findFirst().orElse("-");
            String vocal2 = jurados.stream().filter(j -> "VOCAL_2".equals(j.getRol()))
                    .map(j -> j.getDocente().getUsuario().getNombre() + " " + j.getDocente().getUsuario().getApellido())
                    .findFirst().orElse("-");

            String mensaje = String.format(
                    "⚖️ Se ha asignado tu tribunal completo para tu pre-sustentación \"%s\". " +
                    "Presidente: %s, Vocal 1: %s, Vocal 2: %s. Tu solicitud ahora está en fase de evaluación.",
                    solicitud.getTituloTema(), presidente, vocal1, vocal2);

            Long estudianteUsuarioId = solicitud.getEstudiante().getUsuario().getId();
            notificacionService.crearNotificacion(estudianteUsuarioId, mensaje);

            String email = solicitud.getEstudiante().getUsuario().getEmailNotificaciones();
            if (email == null || email.isBlank()) {
                email = solicitud.getEstudiante().getUsuario().getEmail();
            }
            emailService.enviarNotificacion(email, mensaje);
        } catch (Exception e) {
            log.warn("No se pudo notificar al estudiante sobre tribunal completo: {}", e.getMessage());
        }
    }

    // ── Helpers de notificación ───────────────────────────────────────────────

    private void notificarDocenteJurado(Docente docente, Solicitud solicitud, String rol) {
        try {
            String rolLabel = switch (rol.toUpperCase()) {
                case "PRESIDENTE" -> "Presidente del tribunal";
                case "VOCAL_1"    -> "Vocal 1 del tribunal";
                case "VOCAL_2"    -> "Vocal 2 del tribunal";
                default           -> rol;
            };
            notificacionService.crearNotificacion(docente.getUsuario().getId(),
                    String.format("⚖️ Has sido asignado como %s para evaluar la pre-sustentación \"%s\" " +
                                    "del estudiante %s %s. Por favor ingresa al sistema para confirmar tu participación.",
                            rolLabel,
                            solicitud.getTituloTema(),
                            solicitud.getEstudiante().getUsuario().getNombre(),
                            solicitud.getEstudiante().getUsuario().getApellido()));
        } catch (Exception e) {
            log.warn("No se pudo notificar al docente jurado: {}", e.getMessage());
        }
    }

    private void notificarEstudianteJurado(Solicitud solicitud, Docente docente, String rol) {
        try {
            String rolLabel = switch (rol.toUpperCase()) {
                case "PRESIDENTE" -> "Presidente";
                case "VOCAL_1"    -> "Vocal 1";
                case "VOCAL_2"    -> "Vocal 2";
                default           -> rol;
            };
            notificacionService.crearNotificacion(solicitud.getEstudiante().getUsuario().getId(),
                    String.format("👨‍🏫 Se ha asignado al docente %s %s como %s del tribunal para tu pre-sustentación \"%s\".",
                            docente.getUsuario().getNombre(),
                            docente.getUsuario().getApellido(),
                            rolLabel,
                            solicitud.getTituloTema()));
        } catch (Exception e) {
            log.warn("No se pudo notificar al estudiante sobre jurado: {}", e.getMessage());
        }
    }

    private void notificarDocenteTutor(Docente docente, Solicitud solicitud) {
        try {
            notificacionService.crearNotificacion(docente.getUsuario().getId(),
                    String.format("📚 Has sido asignado como tutor del anteproyecto \"%s\" " +
                                    "del estudiante %s %s. Ingresa al sistema para revisar los detalles.",
                            solicitud.getTituloTema(),
                            solicitud.getEstudiante().getUsuario().getNombre(),
                            solicitud.getEstudiante().getUsuario().getApellido()));
        } catch (Exception e) {
            log.warn("No se pudo notificar al docente tutor: {}", e.getMessage());
        }
    }

    private void notificarEstudianteTutor(Solicitud solicitud, Docente docente) {
        try {
            notificacionService.crearNotificacion(solicitud.getEstudiante().getUsuario().getId(),
                    String.format("🎓 El docente %s %s ha sido asignado como tu tutor para el anteproyecto \"%s\". " +
                                    "Tu solicitud ahora está en fase de tutoría. Puedes ponerte en contacto con él a través del sistema.",
                            docente.getUsuario().getNombre(),
                            docente.getUsuario().getApellido(),
                            solicitud.getTituloTema()));
        } catch (Exception e) {
            log.warn("No se pudo notificar al estudiante sobre tutor: {}", e.getMessage());
        }
    }

    /**
     * Variante de {@link #asignarJuradoMasivo} que invoca directamente la sobrecarga de
     * {@code sp_asignar_jurado_masivo} que recibe arreglos SQL ({@code BIGINT[]}) en una sola
     * llamada, en vez de iterar en Java. Ver la nota de fusión de ramas en
     * {@code docs/basedatos/CATALOGO-SP.md} sobre por qué la variante escalar (iterando en
     * Java) es la que queda verificada end-to-end, no esta.
     *
     * @param solicitudIds arreglo de ids de solicitud
     * @param docenteIds   arreglo de ids de docente, en el mismo orden
     * @param rol          código de rol aplicado a todo el lote
     */
    @Override
    @Transactional
    public void asignarJuradoMasivoSP(Long[] solicitudIds, Long[] docenteIds, String rol) {
        juradoRepository.spAsignarJuradoMasivo(solicitudIds, docenteIds, rol);
    }
}
