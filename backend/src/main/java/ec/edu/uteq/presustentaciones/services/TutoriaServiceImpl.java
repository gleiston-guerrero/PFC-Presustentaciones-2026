package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.TutoriaFaseDTO;
import ec.edu.uteq.presustentaciones.dto.TutoriaMensajeDTO;
import ec.edu.uteq.presustentaciones.dto.TutoriaResumenDTO;
import ec.edu.uteq.presustentaciones.entities.*;
import ec.edu.uteq.presustentaciones.enums.EstadoSolicitud;
import ec.edu.uteq.presustentaciones.repositories.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(readOnly = true)
public class TutoriaServiceImpl implements TutoriaService {

    private final TutorRepository tutorRepository;
    private final TutoriaFaseRepository tutoriaFaseRepository;
    private final TutoriaMensajeRepository tutoriaMensajeRepository;
    private final UsuarioRepository usuarioRepository;
    private final AnteproyectoRepository anteproyectoRepository;
    private final NotificacionService notificacionService;

    @Value("${app.upload.dir.tutorias:uploads/tutorias}")
    private String uploadDir;

    @Value("${app.upload.dir:uploads/anteproyectos}")
    private String uploadDirAnteproyectos;

    public TutoriaServiceImpl(TutorRepository tutorRepository,
                              TutoriaFaseRepository tutoriaFaseRepository,
                              TutoriaMensajeRepository tutoriaMensajeRepository,
                              UsuarioRepository usuarioRepository,
                              AnteproyectoRepository anteproyectoRepository,
                              NotificacionService notificacionService) {
        this.tutorRepository = tutorRepository;
        this.tutoriaFaseRepository = tutoriaFaseRepository;
        this.tutoriaMensajeRepository = tutoriaMensajeRepository;
        this.usuarioRepository = usuarioRepository;
        this.anteproyectoRepository = anteproyectoRepository;
        this.notificacionService = notificacionService;
    }

    // ── Resumen ───────────────────────────────────────────────────────────────

    /**
     * @param tutorId   id del registro de tutoría
     * @param usuarioId id del usuario que consulta (para resolver permisos de vista)
     * @return resumen de la tutoría: fase actual, progreso y estado
     * @throws RuntimeException si la tutoría no existe
     */
    @Override
    public TutoriaResumenDTO obtenerResumen(Long tutorId, Long usuarioId) {
        Tutor tutor = tutorRepository.findById(tutorId)
                .orElseThrow(() -> new RuntimeException("Tutor no encontrado"));

        validarAccesoATutoria(tutor, usuarioId);
        return construirResumenParaTutor(tutor, usuarioId);
    }

    // ── Fases ─────────────────────────────────────────────────────────────────

    /**
     * @param tutorId   id del registro de tutoría
     * @param usuarioId id del usuario que consulta (para resolver permisos)
     * @return las fases registradas de esa tutoría, en orden
     */
    @Override
    public List<TutoriaFaseDTO> obtenerFases(Long tutorId, Long usuarioId) {
        Tutor tutor = tutorRepository.findById(tutorId)
                .orElseThrow(() -> new RuntimeException("Tutor no encontrado"));
                
        validarAccesoATutoria(tutor, usuarioId);
        
        return tutoriaFaseRepository.findByTutorIdOrderByNumeroFaseAsc(tutorId).stream()
                .map(this::mapFaseConMensajes)
                .collect(Collectors.toList());
    }

    /**
     * @param tutorId        id del registro de tutoría
     * @param tutorUsuarioId id del usuario docente que crea la fase
     * @param observacion    observación inicial del docente para esta fase
     * @return la fase creada
     * @throws RuntimeException si la tutoría no existe
     */
    @Override
    @Transactional
    public TutoriaFaseDTO crearFaseConObservacion(Long tutorId, Long tutorUsuarioId, String observacion) {
        Tutor tutor = tutorRepository.findById(tutorId)
                .orElseThrow(() -> new RuntimeException("Tutor no encontrado"));

        if (!tutor.getDocente().getUsuario().getId().equals(tutorUsuarioId)) {
            throw new org.springframework.security.access.AccessDeniedException("No autorizado");
        }

        long totalFases = tutoriaFaseRepository.countByTutorId(tutorId);
        if (totalFases >= 3) {
            throw new RuntimeException("No se pueden crear más de 3 fases de revisión");
        }

        if (totalFases > 0) {
            List<TutoriaFase> fases = tutoriaFaseRepository.findByTutorIdOrderByNumeroFaseAsc(tutorId);
            TutoriaFase ultima = fases.get(fases.size() - 1);
            if (!"APROBADA".equals(ultima.getEstado())) {
                throw new RuntimeException("Debes aprobar la fase actual antes de crear una nueva");
            }
        }

        TutoriaFase fase = TutoriaFase.builder()
                .tutor(tutor)
                .numeroFase((int) totalFases + 1)
                .estado("PENDIENTE_ESTUDIANTE")
                .build();
        fase = tutoriaFaseRepository.save(fase);

        Usuario tutorUsuario = usuarioRepository.findById(tutorUsuarioId)
                .orElseThrow(() -> new RuntimeException("Usuario tutor no encontrado"));

        TutoriaMensaje mensaje = TutoriaMensaje.builder()
                .fase(fase)
                .remitente(tutorUsuario)
                .contenido(observacion)
                .tipo("OBSERVACION")
                .leido(false)
                .build();
        tutoriaMensajeRepository.save(mensaje);

        return mapFaseConMensajes(fase);
    }

    // ── Subida de PDF ─────────────────────────────────────────────────────────

    /**
     * @param faseId              id de la fase de tutoría
     * @param archivo             PDF corregido subido por el estudiante
     * @param estudianteUsuarioId id del usuario estudiante que sube el archivo
     * @return la fase actualizada con el nuevo PDF
     * @throws RuntimeException si la fase no existe o el archivo no es un PDF válido
     */
    @Override
    @Transactional
    public TutoriaFaseDTO subirPdfCorregido(Long faseId, MultipartFile archivo, Long estudianteUsuarioId) {
        TutoriaFase fase = tutoriaFaseRepository.findById(faseId)
                .orElseThrow(() -> new RuntimeException("Fase no encontrada"));

        Long estudianteUsuarioReal = fase.getTutor().getSolicitud().getEstudiante().getUsuario().getId();
        if (!estudianteUsuarioReal.equals(estudianteUsuarioId)) {
            throw new org.springframework.security.access.AccessDeniedException("No autorizado");
        }

        Solicitud solicitud = fase.getTutor().getSolicitud();
        if (solicitud.getEstado() != null && "SUSPENDIDA".equalsIgnoreCase(solicitud.getEstado().getCodigo())) {
            throw new RuntimeException("No puedes subir más archivos. Este tema ha sido suspendido por: " + solicitud.getMotivoSuspension());
        }

        if (!"PENDIENTE_ESTUDIANTE".equals(fase.getEstado())) {
            throw new RuntimeException("Solo puedes subir el PDF cuando el tutor ha enviado observaciones");
        }

        String contentType = archivo.getContentType();
        if (contentType == null || !contentType.equals("application/pdf")) {
            throw new RuntimeException("Solo se permiten archivos PDF");
        }
        if (archivo.getSize() > 10L * 1024 * 1024) {
            throw new RuntimeException("El archivo no puede superar los 10 MB");
        }

        Long tutorId = fase.getTutor().getId();
        int numeroFase = fase.getNumeroFase();

        Path dirPath = Paths.get(uploadDir, tutorId.toString(), "fase_" + numeroFase);
        try {
            Files.createDirectories(dirPath);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo crear el directorio de uploads", e);
        }

        // Eliminar archivo anterior si existe
        if (fase.getArchivoPdfEstudiante() != null) {
            try {
                Files.deleteIfExists(dirPath.resolve(fase.getArchivoPdfEstudiante()));
            } catch (IOException e) {
                log.warn("No se pudo eliminar el archivo anterior: {}", e.getMessage());
            }
        }

        String nombreArchivo = "tutor_" + tutorId + "_fase" + numeroFase + "_" + UUID.randomUUID() + ".pdf";
        Path rutaArchivo = dirPath.resolve(nombreArchivo);
        String sha256 = calcularSha256YGuardar(archivo, rutaArchivo);

        fase.setArchivoPdfEstudiante(nombreArchivo);
        fase.setSha256Pdf(sha256);
        fase.setTamanoPdfBytes(archivo.getSize());
        fase.setEstado("PENDIENTE_TUTOR");
        fase = tutoriaFaseRepository.save(fase);

        Usuario estudiante = usuarioRepository.findById(estudianteUsuarioId)
                .orElseThrow(() -> new RuntimeException("Usuario estudiante no encontrado"));

        TutoriaMensaje mensajeAuto = TutoriaMensaje.builder()
                .fase(fase)
                .remitente(estudiante)
                .contenido("He subido las correcciones solicitadas.")
                .tipo("RESPUESTA")
                .leido(false)
                .build();
        tutoriaMensajeRepository.save(mensajeAuto);

        return mapFaseConMensajes(fase);
    }

    // ── Aprobación ────────────────────────────────────────────────────────────

    /**
     * @param faseId         id de la fase a aprobar
     * @param tutorUsuarioId id del usuario docente que aprueba
     * @param comentario     comentario opcional de aprobación
     * @return la fase actualizada en estado aprobado
     * @throws RuntimeException si la fase no existe
     */
    @Override
    @Transactional
    public TutoriaFaseDTO aprobarFase(Long faseId, Long tutorUsuarioId, String comentario) {
        TutoriaFase fase = tutoriaFaseRepository.findById(faseId)
                .orElseThrow(() -> new RuntimeException("Fase no encontrada"));

        if (!fase.getTutor().getDocente().getUsuario().getId().equals(tutorUsuarioId)) {
            throw new org.springframework.security.access.AccessDeniedException("No autorizado");
        }

        if (!"PENDIENTE_TUTOR".equals(fase.getEstado())) {
            throw new RuntimeException("No puedes aprobar una fase sin correcciones del estudiante");
        }

        if (fase.getArchivoPdfEstudiante() == null) {
            throw new RuntimeException("No existe un PDF del estudiante para aprobar");
        }

        fase.setEstado("APROBADA");
        fase.setFechaAprobacion(LocalDateTime.now());
        fase = tutoriaFaseRepository.save(fase);

        Usuario tutorUsuario = usuarioRepository.findById(tutorUsuarioId)
                .orElseThrow(() -> new RuntimeException("Usuario tutor no encontrado"));

        TutoriaMensaje mensajeAprobacion = TutoriaMensaje.builder()
                .fase(fase)
                .remitente(tutorUsuario)
                .contenido(comentario != null && !comentario.isBlank() ? comentario : "Fase aprobada.")
                .tipo("APROBACION")
                .leido(false)
                .build();
        tutoriaMensajeRepository.save(mensajeAprobacion);

        try {
            Long estudianteUsuarioId = fase.getTutor().getSolicitud().getEstudiante().getUsuario().getId();
            notificacionService.crearNotificacion(estudianteUsuarioId,
                    String.format("Tu tutor aprobó la fase %d de tutoría.", fase.getNumeroFase()));
        } catch (Exception e) {
            log.warn("No se pudo notificar la aprobación de fase {}: {}", fase.getId(), e.getMessage());
        }

        // Si las 3 fases están APROBADAS, marcar tutor como COMPLETADA y actualizar el Anteproyecto
        Long tutorId = fase.getTutor().getId();
        long totalFases = tutoriaFaseRepository.countByTutorId(tutorId);
        long fasesAprobadas = tutoriaFaseRepository.countByTutorIdAndEstado(tutorId, "APROBADA");
        if (totalFases == 3 && fasesAprobadas == 3) {
            Tutor tutor = fase.getTutor();
            tutor.setEstado("COMPLETADA");
            tutorRepository.save(tutor);

            // Reemplazar el PDF del Anteproyecto con el PDF final aprobado de la Fase 3
            TutoriaFase fase3 = tutoriaFaseRepository.findByTutorIdOrderByNumeroFaseAsc(tutorId)
                    .stream()
                    .filter(f -> f.getNumeroFase() == 3)
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Fase 3 no encontrada"));

            // Copiar físicamente el PDF de la Fase 3 a la carpeta de anteproyectos
            Path origen = Paths.get(uploadDir, tutorId.toString(), "fase_3", fase3.getArchivoPdfEstudiante());
            Path destDir = Paths.get(uploadDirAnteproyectos);
            Path destino = destDir.resolve(fase3.getArchivoPdfEstudiante());
            try {
                Files.createDirectories(destDir);
                Files.copy(origen, destino, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException("No se pudo copiar el PDF de la Fase 3 al directorio de anteproyectos", e);
            }

            anteproyectoRepository.findBySolicitudId(tutor.getSolicitud().getId())
                    .ifPresent(anteproyecto -> {
                        anteproyecto.setArchivoPdf(fase3.getArchivoPdfEstudiante());
                        anteproyecto.setSha256Hash(fase3.getSha256Pdf());
                        anteproyecto.setTamanoBytes(fase3.getTamanoPdfBytes());
                        anteproyecto.setEstado("APROBADO");
                        anteproyecto.setObservaciones("PDF final aprobado tras completar las 3 fases de tutoría");
                        anteproyectoRepository.save(anteproyecto);
                    });
        }

        return mapFaseConMensajes(fase);
    }

    // ── Mensajes ──────────────────────────────────────────────────────────────

    /**
     * @param faseId      id de la fase de tutoría
     * @param remitenteId id del usuario que envía el mensaje
     * @param contenido   texto del mensaje
     * @param tipo        tipo de mensaje (p. ej. comentario, corrección)
     * @return el mensaje creado
     * @throws RuntimeException si la fase no existe
     */
    @Override
    @Transactional
    public TutoriaMensajeDTO enviarMensaje(Long faseId, Long remitenteId, String contenido, String tipo) {
        TutoriaFase fase = tutoriaFaseRepository.findById(faseId)
                .orElseThrow(() -> new RuntimeException("Fase no encontrada"));

        Usuario remitente = usuarioRepository.findById(remitenteId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Long tutorUserId = (fase.getTutor() != null && fase.getTutor().getDocente() != null && fase.getTutor().getDocente().getUsuario() != null)
                ? fase.getTutor().getDocente().getUsuario().getId() : null;
        Long estudianteUserId = (fase.getTutor() != null && fase.getTutor().getSolicitud() != null && fase.getTutor().getSolicitud().getEstudiante() != null && fase.getTutor().getSolicitud().getEstudiante().getUsuario() != null)
                ? fase.getTutor().getSolicitud().getEstudiante().getUsuario().getId() : null;
        boolean esPrivilegiado = remitente.getRol() != null &&
                ("ADMIN".equalsIgnoreCase(remitente.getRol()) || "COORDINADOR".equalsIgnoreCase(remitente.getRol()));

        if (!remitenteId.equals(tutorUserId) && !remitenteId.equals(estudianteUserId) && !esPrivilegiado) {
            throw new org.springframework.security.access.AccessDeniedException("No autorizado para enviar mensajes en esta tutoría");
        }

        TutoriaMensaje mensaje = TutoriaMensaje.builder()
                .fase(fase)
                .remitente(remitente)
                .contenido(contenido)
                .tipo(tipo)
                .leido(false)
                .build();

        return mapMensaje(tutoriaMensajeRepository.save(mensaje));
    }

    /**
     * @param faseId    id de la fase de tutoría
     * @param usuarioId id del usuario que marca los mensajes como leídos
     */
    @Override
    @Transactional
    public void marcarMensajesLeidos(Long faseId, Long usuarioId) {
        List<TutoriaMensaje> noLeidos = tutoriaMensajeRepository
                .findByFaseIdAndLeidoFalseAndRemitenteIdNot(faseId, usuarioId);
        noLeidos.forEach(m -> m.setLeido(true));
        tutoriaMensajeRepository.saveAll(noLeidos);
    }

    // ── PDF ───────────────────────────────────────────────────────────────────

    /**
     * @param faseId    id de la fase de tutoría
     * @param usuarioId id del usuario que solicita el PDF
     * @return el recurso PDF de esa fase, para descarga
     * @throws RuntimeException si la fase no existe o no tiene PDF
     */
    @Override
    public Resource obtenerPdfFase(Long faseId, Long usuarioId) {
        TutoriaFase fase = tutoriaFaseRepository.findById(faseId)
                .orElseThrow(() -> new RuntimeException("Fase no encontrada"));

        validarAccesoATutoria(fase.getTutor(), usuarioId);

        if (fase.getArchivoPdfEstudiante() == null) {
            throw new RuntimeException("Esta fase no tiene PDF cargado");
        }

        Path ruta = Paths.get(uploadDir,
                fase.getTutor().getId().toString(),
                "fase_" + fase.getNumeroFase(),
                fase.getArchivoPdfEstudiante()).normalize();

        try {
            Resource resource = new UrlResource(ruta.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new RuntimeException("No se puede leer el archivo PDF");
            }
            return resource;
        } catch (Exception e) {
            throw new RuntimeException("Error al acceder al archivo PDF: " + e.getMessage(), e);
        }
    }

    // ── Listados por usuario ──────────────────────────────────────────────────

    /**
     * @param estudianteUsuarioId id del usuario estudiante
     * @return resúmenes de todas las tutorías de ese estudiante
     */
    @Override
    public List<TutoriaResumenDTO> obtenerTutoriasEstudiante(Long estudianteUsuarioId) {
        return tutorRepository.findBySolicitudEstudianteUsuarioId(estudianteUsuarioId).stream()
                .map(tutor -> construirResumenParaTutor(tutor, estudianteUsuarioId))
                .collect(Collectors.toList());
    }

    /**
     * @param docenteUsuarioId id del usuario docente
     * @return resúmenes de todas las tutorías a cargo de ese docente
     */
    @Override
    public List<TutoriaResumenDTO> obtenerTutoriasDocente(Long docenteUsuarioId) {
        return tutorRepository.findByDocenteUsuarioId(docenteUsuarioId).stream()
                .map(tutor -> construirResumenParaTutor(tutor, docenteUsuarioId))
                .collect(Collectors.toList());
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    private void validarAccesoATutoria(Tutor tutor, Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
                
        boolean esAdminOCoord = "ADMIN".equalsIgnoreCase(usuario.getRol())
                || "COORDINADOR".equalsIgnoreCase(usuario.getRol());
                
        if (esAdminOCoord) {
            return;
        }
        
        Long tutorUserId = (tutor.getDocente() != null && tutor.getDocente().getUsuario() != null)
                ? tutor.getDocente().getUsuario().getId() : null;
        Long estudianteUserId = (tutor.getSolicitud() != null && tutor.getSolicitud().getEstudiante() != null && tutor.getSolicitud().getEstudiante().getUsuario() != null)
                ? tutor.getSolicitud().getEstudiante().getUsuario().getId() : null;
                
        if (!usuarioId.equals(tutorUserId) && !usuarioId.equals(estudianteUserId)) {
            throw new org.springframework.security.access.AccessDeniedException("No autorizado para acceder a esta tutoría");
        }
    }

    private TutoriaResumenDTO construirResumenParaTutor(Tutor tutor, Long usuarioId) {
        Long tutorId = tutor.getId();
        List<TutoriaFase> fases = tutoriaFaseRepository.findByTutorIdOrderByNumeroFaseAsc(tutorId);

        long mensajesNoLeidos = fases.stream()
                .mapToLong(fase -> tutoriaMensajeRepository
                        .countByFaseIdAndLeidoFalseAndRemitenteIdNot(fase.getId(), usuarioId))
                .sum();

        long fasesAprobadas = tutoriaFaseRepository.countByTutorIdAndEstado(tutorId, "APROBADA");

        Solicitud solicitud = tutor.getSolicitud();
        String nombreEstudiante = solicitud.getEstudiante().getUsuario().getNombre()
                + " " + solicitud.getEstudiante().getUsuario().getApellido();
        String nombreTutor = tutor.getDocente().getUsuario().getNombre()
                + " " + tutor.getDocente().getUsuario().getApellido();

        boolean solicitudSuspendida = solicitud.getEstado() != null && "SUSPENDIDA".equals(solicitud.getEstado().getCodigo());

        return TutoriaResumenDTO.builder()
                .tutorId(tutorId)
                .solicitudId(solicitud.getId())
                .tituloTema(solicitud.getTituloTema())
                .nombreEstudiante(nombreEstudiante)
                .nombreTutor(nombreTutor)
                .totalFases(fases.size())
                .fasesAprobadas(fasesAprobadas)
                .estadoTutoria(tutor.getEstado())
                .mensajesNoLeidos(mensajesNoLeidos)
                .solicitudSuspendida(solicitudSuspendida)
                .build();
    }

    private TutoriaFaseDTO mapFaseConMensajes(TutoriaFase fase) {
        List<TutoriaMensajeDTO> mensajes = tutoriaMensajeRepository
                .findByFaseIdOrderByFechaEnvioAsc(fase.getId()).stream()
                .map(this::mapMensaje)
                .collect(Collectors.toList());

        return TutoriaFaseDTO.builder()
                .id(fase.getId())
                .tutorId(fase.getTutor().getId())
                .numeroFase(fase.getNumeroFase())
                .estado(fase.getEstado())
                .fechaInicio(fase.getFechaInicio())
                .fechaAprobacion(fase.getFechaAprobacion())
                .archivoPdfEstudiante(fase.getArchivoPdfEstudiante())
                .tamanoPdfBytes(fase.getTamanoPdfBytes())
                .mensajes(mensajes)
                .build();
    }

    private TutoriaMensajeDTO mapMensaje(TutoriaMensaje m) {
        String nombreRemitente = m.getRemitente().getNombre() + " " + m.getRemitente().getApellido();
        return TutoriaMensajeDTO.builder()
                .id(m.getId())
                .faseId(m.getFase().getId())
                .remitenteId(m.getRemitente().getId())
                .nombreRemitente(nombreRemitente)
                .contenido(m.getContenido())
                .fechaEnvio(m.getFechaEnvio())
                .tipo(m.getTipo())
                .leido(m.getLeido())
                .build();
    }

    private String calcularSha256YGuardar(MultipartFile archivo, Path destino) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = archivo.getInputStream();
                 DigestInputStream dis = new DigestInputStream(is, digest)) {
                Files.copy(dis, destino, StandardCopyOption.REPLACE_EXISTING);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException("Error al calcular SHA-256 del archivo", e);
        }
    }

    /**
     * Registra el avance de una fase de tutoría vía procedimiento almacenado.
     *
     * @param tutorId     id del registro de tutoría
     * @param numeroFase  número de fase que avanza
     * @param archivoPdf  nombre del archivo PDF asociado al avance
     * @param tamanoBytes tamaño en bytes del archivo
     * @param sha256      hash SHA-256 del archivo, para verificación de integridad posterior
     * @param usuarioId   id del usuario estudiante que registra el avance (para validacion)
     */
    @Override
    @Transactional
    public void registrarAvanceSP(Long tutorId, Integer numeroFase, String archivoPdf, Long tamanoBytes, String sha256, Long usuarioId) {
        Tutor tutor = tutorRepository.findById(tutorId)
                .orElseThrow(() -> new RuntimeException("Tutor no encontrado"));
                
        validarAccesoATutoria(tutor, usuarioId);

        tutoriaFaseRepository.spRegistrarTutoriaAvance(tutorId, numeroFase, archivoPdf, tamanoBytes, sha256);
    }
}
