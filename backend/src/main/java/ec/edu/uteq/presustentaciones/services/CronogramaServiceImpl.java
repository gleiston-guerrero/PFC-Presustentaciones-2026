package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.Cronograma;
import ec.edu.uteq.presustentaciones.entities.Jurado;
import ec.edu.uteq.presustentaciones.entities.Sala;
import ec.edu.uteq.presustentaciones.entities.Solicitud;
import ec.edu.uteq.presustentaciones.entities.Tutor;
import ec.edu.uteq.presustentaciones.repositories.CronogramaRepository;
import ec.edu.uteq.presustentaciones.repositories.JuradoRepository;
import ec.edu.uteq.presustentaciones.repositories.SalaRepository;
import ec.edu.uteq.presustentaciones.repositories.SolicitudRepository;
import ec.edu.uteq.presustentaciones.repositories.TutorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CronogramaServiceImpl implements CronogramaService {

    private final CronogramaRepository cronogramaRepository;
    private final SolicitudRepository solicitudRepository;
    private final SalaRepository salaRepository;
    private final JuradoRepository juradoRepository;
    private final TutorRepository tutorRepository;
    private final NotificacionService notificacionService;
    private final ec.edu.uteq.presustentaciones.repositories.EstadoCronogramaRepository estadoCronogramaRepository;

    private static final LocalTime HORA_INICIO = LocalTime.of(8, 0);
    private static final LocalTime HORA_FIN    = LocalTime.of(17, 0);
    private static final int DURACION = 45;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm");

    /**
     * Programa la defensa de una solicitud en una sala y horario específicos, validando
     * primero que el tribunal esté completo y la tutoría completada, y luego que ningún
     * jurado tenga conflicto de horario (vía {@code sp_validar_conflicto_jurado}).
     *
     * @param solicitudId id de la solicitud a programar
     * @param salaId      id de la sala donde se realizará la defensa
     * @param fecha       fecha de la defensa
     * @param hora        hora de inicio de la defensa
     * @return el cronograma creado, en estado "PROGRAMADO"
     * @throws RuntimeException si el tribunal no está completo, la tutoría no está
     *                          completada, o algún jurado tiene conflicto de horario
     */
    @Override
    @Transactional
    public Cronograma crearCronograma(Long solicitudId, Long salaId, LocalDate fecha, LocalTime hora) {
        validarPrerequisitosParaCronograma(solicitudId);
 
        Solicitud solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));
        Sala sala = salaRepository.findById(salaId)
                .orElseThrow(() -> new RuntimeException("Sala no encontrada"));
 
        LocalDateTime inicio = LocalDateTime.of(fecha, hora);
        LocalDateTime fin = inicio.plusMinutes(DURACION);
 
        List<Cronograma> conflictos = cronogramaRepository.findConflictos(salaId, inicio, fin);
        if (!conflictos.isEmpty()) {
            throw new RuntimeException(
                    "Conflicto de horario: la sala '" + sala.getNombre() +
                            "' ya tiene una pre-sustentación programada en esa franja.");
        }

        // sp_validar_conflicto_jurado (Fase 3 / Criterio P1, categoría "validaciones
        // cruzadas"): ningún docente ya asignado como jurado de esta solicitud puede quedar
        // programado en dos defensas cuyos horarios se solapen.
        for (Jurado jurado : juradoRepository.findBySolicitudId(solicitudId)) {
            Boolean disponible = juradoRepository.validarConflictoJurado(
                    solicitudId, jurado.getDocente().getId(), inicio, DURACION, null);
            if (Boolean.FALSE.equals(disponible)) {
                throw new RuntimeException(
                        "Conflicto de horario: el docente " + jurado.getDocente().getUsuario().getNombre() +
                                " " + jurado.getDocente().getUsuario().getApellido() +
                                " ya es jurado de otra defensa programada en esa franja.");
            }
        }

        ec.edu.uteq.presustentaciones.entities.EstadoCronograma estadoProgramado = estadoCronogramaRepository.findByCodigo("PROGRAMADO")
                .orElseGet(() -> estadoCronogramaRepository.save(ec.edu.uteq.presustentaciones.entities.EstadoCronograma.builder()
                        .codigo("PROGRAMADO").nombre("Programado").build()));

        Cronograma cronograma = cronogramaRepository.save(Cronograma.builder()
                .solicitud(solicitud).sala(sala)
                .convocatoria(solicitud.getConvocatoria())
                .fechaInicio(inicio).duracionMin(DURACION).estado(estadoProgramado).build());
 
        notificarProgramacion(cronograma);
        return cronograma;
    }
 
    /**
     * RF-04: Asignación automática sin conflictos.
     *
     * @param solicitudId id de la solicitud a programar
     * @return el cronograma creado con la primera sala/franja libre encontrada
     * @throws RuntimeException si no hay ninguna combinación de sala/franja disponible
     */
    @Override
    @Transactional
    public Cronograma asignarAutomatico(Long solicitudId) {
        validarPrerequisitosParaCronograma(solicitudId);
 
        solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));
 
        Optional<Cronograma> existente = cronogramaRepository.findBySolicitudId(solicitudId);
        if (existente.isPresent() && existente.get().getEstado() != null && "PROGRAMADO".equals(existente.get().getEstado().getCodigo())) {
            return existente.get();
        }
 
        List<Sala> salas = salaRepository.findAll().stream()
                .filter(s -> Boolean.TRUE.equals(s.getDisponible())).toList();
        if (salas.isEmpty()) throw new RuntimeException("No hay salas disponibles.");
 
        for (int diasAdelantar = 1; diasAdelantar <= 30; diasAdelantar++) {
            LocalDate fecha = LocalDate.now().plusDays(diasAdelantar);
            if (fecha.getDayOfWeek().getValue() >= 6) continue;
 
            List<LocalDateTime> franjas = franjasDisponibles(fecha, DURACION);
            for (LocalDateTime franja : franjas) {
                for (Sala sala : salas) {
                    List<Cronograma> conflictos = cronogramaRepository
                            .findConflictos(sala.getId(), franja, franja.plusMinutes(DURACION));
                    if (conflictos.isEmpty()) {
                        Solicitud solicitud = solicitudRepository.findById(solicitudId).get();
                        
                        ec.edu.uteq.presustentaciones.entities.EstadoCronograma estadoProgramado = estadoCronogramaRepository.findByCodigo("PROGRAMADO")
                                .orElseGet(() -> estadoCronogramaRepository.save(ec.edu.uteq.presustentaciones.entities.EstadoCronograma.builder()
                                        .codigo("PROGRAMADO").nombre("Programado").build()));

                        Cronograma cronograma = cronogramaRepository.save(Cronograma.builder()
                                .solicitud(solicitud).sala(sala)
                                .convocatoria(solicitud.getConvocatoria())
                                .fechaInicio(franja).duracionMin(DURACION).estado(estadoProgramado)
                                .build());
                        notificarProgramacion(cronograma);
                        return cronograma;
                    }
                }
            }
        }
        throw new RuntimeException(
                "No se encontró disponibilidad en los próximos 30 días. Verifique las salas o el calendario.");
    }
 
    private void validarPrerequisitosParaCronograma(Long solicitudId) {
        // 1. Tribunal completo: los 3 roles deben estar asignados
        List<String> rolesAsignados = juradoRepository.findBySolicitudId(solicitudId)
                .stream().map(Jurado::getRol).toList();
        boolean tribunalCompleto = rolesAsignados.contains("PRESIDENTE")
                && rolesAsignados.contains("VOCAL_1")
                && rolesAsignados.contains("VOCAL_2");
        if (!tribunalCompleto) {
            throw new RuntimeException(
                    "No se puede programar la presentación: el tribunal no está completo. " +
                    "Se requieren Presidente, Vocal 1 y Vocal 2.");
        }

        // 2. Tutoría COMPLETADA
        Tutor tutor = tutorRepository.findBySolicitudId(solicitudId)
                .orElseThrow(() -> new RuntimeException(
                        "No se puede programar la presentación: la tutoría no ha sido completada."));
        if (!"COMPLETADA".equals(tutor.getEstado())) {
            throw new RuntimeException(
                    "No se puede programar la presentación: la tutoría no ha sido completada.");
        }
    }

    /** Notifica al estudiante y a los jurados asignados cuando se programa la exposición */
    private void notificarProgramacion(Cronograma c) {
        try {
            Solicitud s = c.getSolicitud();
            String fechaStr = c.getFechaInicio().format(FMT);
            String sala     = c.getSala().getNombre();
            String titulo   = s.getTituloTema();

            // Notificar al estudiante
            Long estudianteUsuarioId = s.getEstudiante().getUsuario().getId();
            notificacionService.crearNotificacion(estudianteUsuarioId,
                    String.format("📅 Tu pre-sustentación \"%s\" ha sido programada para el %s en la sala %s. " +
                            "Duración estimada: %d minutos.", titulo, fechaStr, sala, c.getDuracionMin()));
        } catch (Exception e) {
            log.warn("No se pudo enviar notificación de programación: {}", e.getMessage());
        }
    }

    /**
     * RF-04: Verificar disponibilidad de sala en franja horaria.
     *
     * @param salaId      id de la sala a verificar
     * @param inicio      instante de inicio de la franja propuesta
     * @param duracionMin duración de la defensa en minutos
     * @return {@code true} si la sala está libre en toda esa franja, {@code false} si se
     *         solapa con otro cronograma ya programado
     */
    @Override
    public boolean estaDisponible(Long salaId, LocalDateTime inicio, int duracionMin) {
        return cronogramaRepository.findConflictos(salaId, inicio, inicio.plusMinutes(duracionMin)).isEmpty();
    }

    /**
     * RF-04: Franjas libres para una fecha.
     *
     * @param fecha       fecha sobre la que buscar franjas libres
     * @param duracionMin duración de la defensa en minutos
     * @return lista de instantes de inicio disponibles en cualquier sala esa fecha
     */
    @Override
    public List<LocalDateTime> franjasDisponibles(LocalDate fecha, int duracionMin) {
        List<LocalDateTime> franjas = new ArrayList<>();
        LocalDateTime cursor = LocalDateTime.of(fecha, HORA_INICIO);
        LocalDateTime limite = LocalDateTime.of(fecha, HORA_FIN);
        while (!cursor.plusMinutes(duracionMin).isAfter(limite)) {
            franjas.add(cursor);
            cursor = cursor.plusMinutes(duracionMin);
        }
        return franjas;
    }

    /**
     * @param pageable configuración de paginación
     * @return página de todos los cronogramas del sistema
     */
    @Override public Page<Cronograma> listarCronogramas(Pageable pageable) { return cronogramaRepository.findAll(pageable); }

    /**
     * @param id id del estudiante
     * @return los cronogramas de las solicitudes de ese estudiante
     */
    @Override public List<Cronograma> listarPorEstudiante(Long id) { return cronogramaRepository.findByEstudianteId(id); }

    /**
     * @param id id del usuario autenticado
     * @return los cronogramas visibles para ese usuario (como estudiante o como jurado/tutor)
     */
    @Override public List<Cronograma> listarPorUsuario(Long id) { return cronogramaRepository.findByUsuarioId(id); }

    /**
     * @param id id de la solicitud
     * @return el cronograma de esa solicitud, si ya fue programada
     */
    @Override public Optional<Cronograma> buscarPorSolicitud(Long id) { return cronogramaRepository.findBySolicitudId(id); }

    /** @param id id del cronograma a eliminar */
    @Override public void eliminar(Long id) { cronogramaRepository.deleteById(id); }
}