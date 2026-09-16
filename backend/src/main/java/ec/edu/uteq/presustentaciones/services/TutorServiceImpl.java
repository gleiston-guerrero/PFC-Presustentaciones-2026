package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.MiEstudianteTutoradoDTO;
import ec.edu.uteq.presustentaciones.entities.Docente;
import ec.edu.uteq.presustentaciones.entities.Estudiante;
import ec.edu.uteq.presustentaciones.entities.Solicitud;
import ec.edu.uteq.presustentaciones.entities.Tutor;
import ec.edu.uteq.presustentaciones.repositories.DocenteRepository;
import ec.edu.uteq.presustentaciones.repositories.SolicitudRepository;
import ec.edu.uteq.presustentaciones.repositories.TutorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
@Slf4j
public class TutorServiceImpl implements TutorService {

    private final TutorRepository tutorRepository;
    private final SolicitudRepository solicitudRepository;
    private final DocenteRepository docenteRepository;
    private final NotificacionService notificacionService;
    private final ec.edu.uteq.presustentaciones.repositories.EstadoSolicitudRepository estadoSolicitudRepository;

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

        tutorRepository.findBySolicitudId(solicitudId).ifPresent(t -> tutorRepository.delete(t));

        Tutor tutor = Tutor.builder()
                .solicitud(solicitud)
                .docente(docente)
                .estado("ACTIVO")
                .build();
        Tutor guardado = tutorRepository.save(tutor);

        ec.edu.uteq.presustentaciones.entities.EstadoSolicitud estadoTutoria = estadoSolicitudRepository.findByCodigo("TUTORIA")
                .orElseGet(() -> estadoSolicitudRepository.save(ec.edu.uteq.presustentaciones.entities.EstadoSolicitud.builder()
                        .codigo("TUTORIA").nombre("Tutoria").build()));

        solicitud.setEstado(estadoTutoria);
        solicitudRepository.save(solicitud);

        // Notificar al docente asignado
        try {
            notificacionService.crearNotificacion(docente.getUsuario().getId(),
                    String.format("📚 Has sido asignado como tutor del anteproyecto \"%s\" " +
                                    "del estudiante %s %s.",
                            solicitud.getTituloTema(),
                            solicitud.getEstudiante().getUsuario().getNombre(),
                            solicitud.getEstudiante().getUsuario().getApellido()));
        } catch (Exception e) {
            log.warn("No se pudo notificar al docente tutor: {}", e.getMessage());
        }

        // Notificar al estudiante
        try {
            notificacionService.crearNotificacion(solicitud.getEstudiante().getUsuario().getId(),
                    String.format("🎓 El docente %s %s ha sido asignado como tu tutor para \"%s\". Tu solicitud ahora está en fase de tutoría.",
                            docente.getUsuario().getNombre(),
                            docente.getUsuario().getApellido(),
                            solicitud.getTituloTema()));
        } catch (Exception e) {
            log.warn("No se pudo notificar al estudiante sobre tutor: {}", e.getMessage());
        }

        return guardado;
    }

    /**
     * @param solicitudId id de la solicitud
     * @return el tutor asignado, si existe
     */
    @Override
    public Optional<Tutor> buscarPorSolicitud(Long solicitudId) {
        return tutorRepository.findBySolicitudId(solicitudId);
    }

    /**
     * @param pageable configuración de paginación
     * @return página de todos los registros de tutoría del sistema
     */
    @Override
    public Page<Tutor> listarTodos(Pageable pageable) {
        return tutorRepository.findAll(pageable);
    }

    /** @param tutorId id del registro de tutoría a eliminar */
    @Override
    public void eliminarTutor(Long tutorId) {
        tutorRepository.deleteById(tutorId);
    }

    /**
     * @param usuarioIdDocente id del usuario docente
     * @return los estudiantes tutorados actualmente por ese docente
     */
    @Override
    public List<MiEstudianteTutoradoDTO> misEstudiantes(Long usuarioIdDocente) {
        return tutorRepository.findByDocenteUsuarioId(usuarioIdDocente).stream()
                .map(tutor -> {
                    Solicitud solicitud = tutor.getSolicitud();
                    Estudiante estudiante = solicitud.getEstudiante();
                    return MiEstudianteTutoradoDTO.builder()
                            .tutorId(tutor.getId())
                            .solicitudId(solicitud.getId())
                            .estudianteUsuarioId(estudiante.getUsuario().getId())
                            .nombre(estudiante.getUsuario().getNombre())
                            .apellido(estudiante.getUsuario().getApellido())
                            .email(estudiante.getUsuario().getEmail())
                            .telefono(estudiante.getTelefono())
                            .expedienteCodigo(estudiante.getExpedienteCodigo())
                            .carreraNombre(estudiante.getCarreraEntidad() != null ? estudiante.getCarreraEntidad().getNombre() : estudiante.getCarrera())
                            .semestreActual(estudiante.getSemestreActual())
                            .estadoAcademicoCodigo(estudiante.getEstadoAcademico() != null ? estudiante.getEstadoAcademico().getCodigo() : null)
                            .estadoAcademicoNombre(estudiante.getEstadoAcademico() != null ? estudiante.getEstadoAcademico().getNombre() : null)
                            .tituloTema(solicitud.getTituloTema())
                            .estadoSolicitudCodigo(solicitud.getEstado() != null ? solicitud.getEstado().getCodigo() : solicitud.getEstadoCodigo())
                            .estadoSolicitudNombre(solicitud.getEstado() != null ? solicitud.getEstado().getNombre() : null)
                            .estadoTutoria(tutor.getEstado())
                            .fechaAsignacion(tutor.getFechaAsignacion())
                            .build();
                })
                .toList();
    }

    /**
     * Invoca el procedimiento almacenado de estadísticas de carga de tutores.
     *
     * @return una fila por docente con su carga actual de tutorías
     */
    @Override
    public List<Map<String, Object>> obtenerEstadisticasTutoresSP() {
        List<Object[]> res = tutorRepository.obtenerEstadisticasTutoresSp();
        List<Map<String, Object>> list = new java.util.ArrayList<>();
        for (Object[] row : res) {
            Map<String, Object> map = new HashMap<>();
            map.put("tutorDocenteId", row[0]);
            map.put("tutorNombre", row[1]);
            map.put("tutoriasActivas", row[2]);
            map.put("tutoriasCompletadas", row[3]);
            map.put("totalFasesAprobadas", row[4]);
            list.add(map);
        }
        return list;
    }
}