package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.TutoringFaseDTO;
import ec.edu.uteq.presustentaciones.dto.TutoringMensajeDTO;
import ec.edu.uteq.presustentaciones.dto.TutoringResumenDTO;
import ec.edu.uteq.presustentaciones.entities.*;
import ec.edu.uteq.presustentaciones.enums.EstadoSubmission;
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
public class TutoringServiceImpl implements TutoringService {

    private final TutorRepository tutorRepository;
    private final TutoringFaseRepository tutoringFaseRepository;
    private final TutoringMensajeRepository tutoringMensajeRepository;
    private final AppUserRepository appUserRepository;
    private final ProposalRepository proposalRepository;
    private final NotificationService notificationService;

    @Value("${app.upload.dir.tutorias:uploads/tutorias}")
    private String uploadDir;

    @Value("${app.upload.dir:uploads/anteproyectos}")
    private String uploadDirProposals;

    /**
     * Construye TutoringServiceImpl, inyectando tutorRepository, tutoringFaseRepository, tutoringMensajeRepository, appUserRepository, proposalRepository, notificationService.
     * @param tutorRepository tutorRepository
     * @param tutoringFaseRepository tutoringFaseRepository
     * @param tutoringMensajeRepository tutoringMensajeRepository
     * @param appUserRepository appUserRepository
     * @param proposalRepository proposalRepository
     * @param notificationService notificationService
     */
    public TutoringServiceImpl(TutorRepository tutorRepository,
                              TutoringFaseRepository tutoringFaseRepository,
                              TutoringMensajeRepository tutoringMensajeRepository,
                              AppUserRepository appUserRepository,
                              ProposalRepository proposalRepository,
                              NotificationService notificationService) {
        this.tutorRepository = tutorRepository;
        this.tutoringFaseRepository = tutoringFaseRepository;
        this.tutoringMensajeRepository = tutoringMensajeRepository;
        this.appUserRepository = appUserRepository;
        this.proposalRepository = proposalRepository;
        this.notificationService = notificationService;
    }

    // ── Resumen ───────────────────────────────────────────────────────────────

    /**
     * @param tutorId   id del registro de tutoría
     * @param appUserId id del appUser que consulta (para resolve permissions de vista)
     * @return resumen de la tutoría: fase actual, progress y estado
     * @throws RuntimeException si la tutoría no existe
     */
    @Override
    public TutoringResumenDTO obtainResumen(Long tutorId, Long appUserId) {
        Tutor tutor = tutorRepository.findById(tutorId)
                .orElseThrow(() -> new RuntimeException("Tutor no encontrado"));

        validateAccesoATutoring(tutor, appUserId);
        return buildResumenParaTutor(tutor, appUserId);
    }

    // ── Fases ─────────────────────────────────────────────────────────────────

    /**
     * @param tutorId   id del registro de tutoría
     * @param appUserId id del appUser que consulta (para resolve permissions)
     * @return las fases registradas de esa tutoría, en orden
     */
    @Override
    public List<TutoringFaseDTO> obtainFases(Long tutorId, Long appUserId) {
        Tutor tutor = tutorRepository.findById(tutorId)
                .orElseThrow(() -> new RuntimeException("Tutor no encontrado"));
                
        validateAccesoATutoring(tutor, appUserId);
        
        return tutoringFaseRepository.findByTutorIdOrderByNumeroFaseAsc(tutorId).stream()
                .map(this::mapFaseConMensajes)
                .collect(Collectors.toList());
    }

    /**
     * @param tutorId        id del registro de tutoría
     * @param tutorAppUserId id del appUser teacher que crea la fase
     * @param observacion    observación inicial del teacher para esta fase
     * @return la fase creada
     * @throws RuntimeException si la tutoría no existe
     */
    @Override
    @Transactional
    public TutoringFaseDTO createFaseConObservacion(Long tutorId, Long tutorAppUserId, String observacion) {
        Tutor tutor = tutorRepository.findById(tutorId)
                .orElseThrow(() -> new RuntimeException("Tutor no encontrado"));

        if (!tutor.getTeacher().getAppUser().getId().equals(tutorAppUserId)) {
            throw new org.springframework.security.access.AccessDeniedException("No autorizado");
        }

        long totalFases = tutoringFaseRepository.countByTutorId(tutorId);
        if (totalFases >= 3) {
            throw new RuntimeException("No se pueden crear más de 3 fases de revisión");
        }

        if (totalFases > 0) {
            List<TutoringFase> fases = tutoringFaseRepository.findByTutorIdOrderByNumeroFaseAsc(tutorId);
            TutoringFase ultima = fases.get(fases.size() - 1);
            if (!"APROBADA".equals(ultima.getEstado())) {
                throw new RuntimeException("Debes aprobar la fase actual antes de crear una nueva");
            }
        }

        TutoringFase fase = TutoringFase.builder()
                .tutor(tutor)
                .numeroFase((int) totalFases + 1)
                .estado("PENDIENTE_ESTUDIANTE")
                .build();
        fase = tutoringFaseRepository.save(fase);

        AppUser tutorAppUser = appUserRepository.findById(tutorAppUserId)
                .orElseThrow(() -> new RuntimeException("Usuario tutor no encontrado"));

        TutoringMensaje mensaje = TutoringMensaje.builder()
                .fase(fase)
                .remitente(tutorAppUser)
                .contenido(observacion)
                .tipo("OBSERVACION")
                .leido(false)
                .build();
        tutoringMensajeRepository.save(mensaje);

        return mapFaseConMensajes(fase);
    }

    // ── Subida de PDF ─────────────────────────────────────────────────────────

    /**
     * @param faseId              id de la fase de tutoría
     * @param archivo             PDF corregido subido por el student
     * @param studentAppUserId id del appUser student que sube el archivo
     * @return la fase actualizada con el nuevo PDF
     * @throws RuntimeException si la fase no existe o el archivo no es un PDF válido
     */
    @Override
    @Transactional
    public TutoringFaseDTO uploadPdfCorregido(Long faseId, MultipartFile archivo, Long studentAppUserId) {
        TutoringFase fase = tutoringFaseRepository.findById(faseId)
                .orElseThrow(() -> new RuntimeException("Fase no encontrada"));

        Long studentAppUserReal = fase.getTutor().getSubmission().getStudent().getAppUser().getId();
        if (!studentAppUserReal.equals(studentAppUserId)) {
            throw new org.springframework.security.access.AccessDeniedException("No autorizado");
        }

        Submission submission = fase.getTutor().getSubmission();
        if (submission.getEstado() != null && "SUSPENDIDA".equalsIgnoreCase(submission.getEstado().getCodigo())) {
            throw new RuntimeException("No puedes subir más archivos. Este tema ha sido suspendido por: " + submission.getMotivoSuspension());
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

        // Delete archivo anterior si existe
        if (fase.getArchivoPdfStudent() != null) {
            try {
                Files.deleteIfExists(dirPath.resolve(fase.getArchivoPdfStudent()));
            } catch (IOException e) {
                log.warn("No se pudo eliminar el archivo anterior: {}", e.getMessage());
            }
        }

        String nombreArchivo = "tutor_" + tutorId + "_fase" + numeroFase + "_" + UUID.randomUUID() + ".pdf";
        Path rutaArchivo = dirPath.resolve(nombreArchivo);
        String sha256 = calculateSha256YSave(archivo, rutaArchivo);

        fase.setArchivoPdfStudent(nombreArchivo);
        fase.setSha256Pdf(sha256);
        fase.setTamanoPdfBytes(archivo.getSize());
        fase.setEstado("PENDIENTE_TUTOR");
        fase = tutoringFaseRepository.save(fase);

        AppUser student = appUserRepository.findById(studentAppUserId)
                .orElseThrow(() -> new RuntimeException("Usuario estudiante no encontrado"));

        TutoringMensaje mensajeAuto = TutoringMensaje.builder()
                .fase(fase)
                .remitente(student)
                .contenido("He subido las correcciones solicitadas.")
                .tipo("RESPUESTA")
                .leido(false)
                .build();
        tutoringMensajeRepository.save(mensajeAuto);

        return mapFaseConMensajes(fase);
    }

    // ── Aprobación ────────────────────────────────────────────────────────────

    /**
     * @param faseId         id de la fase a approve
     * @param tutorAppUserId id del appUser teacher que aprueba
     * @param comentario     comentario opcional de aprobación
     * @return la fase actualizada en estado aprobado
     * @throws RuntimeException si la fase no existe
     */
    @Override
    @Transactional
    public TutoringFaseDTO approveFase(Long faseId, Long tutorAppUserId, String comentario) {
        TutoringFase fase = tutoringFaseRepository.findById(faseId)
                .orElseThrow(() -> new RuntimeException("Fase no encontrada"));

        if (!fase.getTutor().getTeacher().getAppUser().getId().equals(tutorAppUserId)) {
            throw new org.springframework.security.access.AccessDeniedException("No autorizado");
        }

        if (!"PENDIENTE_TUTOR".equals(fase.getEstado())) {
            throw new RuntimeException("No puedes aprobar una fase sin correcciones del estudiante");
        }

        if (fase.getArchivoPdfStudent() == null) {
            throw new RuntimeException("No existe un PDF del estudiante para aprobar");
        }

        fase.setEstado("APROBADA");
        fase.setFechaAprobacion(LocalDateTime.now());
        fase = tutoringFaseRepository.save(fase);

        AppUser tutorAppUser = appUserRepository.findById(tutorAppUserId)
                .orElseThrow(() -> new RuntimeException("Usuario tutor no encontrado"));

        TutoringMensaje mensajeAprobacion = TutoringMensaje.builder()
                .fase(fase)
                .remitente(tutorAppUser)
                .contenido(comentario != null && !comentario.isBlank() ? comentario : "Fase aprobada.")
                .tipo("APROBACION")
                .leido(false)
                .build();
        tutoringMensajeRepository.save(mensajeAprobacion);

        try {
            Long studentAppUserId = fase.getTutor().getSubmission().getStudent().getAppUser().getId();
            notificationService.createNotification(studentAppUserId,
                    String.format("Tu tutor aprobó la fase %d de tutoría.", fase.getNumeroFase()));
        } catch (Exception e) {
            log.warn("No se pudo notificar la aprobación de fase {}: {}", fase.getId(), e.getMessage());
        }

        // Si las 3 fases están APROBADAS, marcar tutor como COMPLETADA y update el Proposal
        Long tutorId = fase.getTutor().getId();
        long totalFases = tutoringFaseRepository.countByTutorId(tutorId);
        long fasesAprobadas = tutoringFaseRepository.countByTutorIdAndEstado(tutorId, "APROBADA");
        if (totalFases == 3 && fasesAprobadas == 3) {
            Tutor tutor = fase.getTutor();
            tutor.setEstado("COMPLETADA");
            tutorRepository.save(tutor);

            // Reemplazar el PDF del Proposal con el PDF final aprobado de la Fase 3
            TutoringFase fase3 = tutoringFaseRepository.findByTutorIdOrderByNumeroFaseAsc(tutorId)
                    .stream()
                    .filter(f -> f.getNumeroFase() == 3)
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Fase 3 no encontrada"));

            // Copiar físicamente el PDF de la Fase 3 a la carpeta de proposals
            Path origen = Paths.get(uploadDir, tutorId.toString(), "fase_3", fase3.getArchivoPdfStudent());
            Path destDir = Paths.get(uploadDirProposals);
            Path destino = destDir.resolve(fase3.getArchivoPdfStudent());
            try {
                Files.createDirectories(destDir);
                Files.copy(origen, destino, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException("No se pudo copiar el PDF de la Fase 3 al directorio de anteproyectos", e);
            }

            proposalRepository.findBySubmissionId(tutor.getSubmission().getId())
                    .ifPresent(proposal -> {
                        proposal.setArchivoPdf(fase3.getArchivoPdfStudent());
                        proposal.setSha256Hash(fase3.getSha256Pdf());
                        proposal.setTamanoBytes(fase3.getTamanoPdfBytes());
                        proposal.setEstado("APROBADO");
                        proposal.setObservaciones("PDF final aprobado tras completar las 3 fases de tutoría");
                        proposalRepository.save(proposal);
                    });
        }

        return mapFaseConMensajes(fase);
    }

    // ── Mensajes ──────────────────────────────────────────────────────────────

    /**
     * @param faseId      id de la fase de tutoría
     * @param remitenteId id del appUser que envía el mensaje
     * @param contenido   texto del mensaje
     * @param tipo        tipo de mensaje (p. ej. comentario, corrección)
     * @return el mensaje creado
     * @throws RuntimeException si la fase no existe
     */
    @Override
    @Transactional
    public TutoringMensajeDTO sendMensaje(Long faseId, Long remitenteId, String contenido, String tipo) {
        TutoringFase fase = tutoringFaseRepository.findById(faseId)
                .orElseThrow(() -> new RuntimeException("Fase no encontrada"));

        AppUser remitente = appUserRepository.findById(remitenteId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Long tutorUserId = (fase.getTutor() != null && fase.getTutor().getTeacher() != null && fase.getTutor().getTeacher().getAppUser() != null)
                ? fase.getTutor().getTeacher().getAppUser().getId() : null;
        Long studentUserId = (fase.getTutor() != null && fase.getTutor().getSubmission() != null && fase.getTutor().getSubmission().getStudent() != null && fase.getTutor().getSubmission().getStudent().getAppUser() != null)
                ? fase.getTutor().getSubmission().getStudent().getAppUser().getId() : null;
        boolean esPrivilegiado = remitente.getRole() != null &&
                ("ADMIN".equalsIgnoreCase(remitente.getRole()) || "COORDINADOR".equalsIgnoreCase(remitente.getRole()));

        if (!remitenteId.equals(tutorUserId) && !remitenteId.equals(studentUserId) && !esPrivilegiado) {
            throw new org.springframework.security.access.AccessDeniedException("No autorizado para enviar mensajes en esta tutoría");
        }

        TutoringMensaje mensaje = TutoringMensaje.builder()
                .fase(fase)
                .remitente(remitente)
                .contenido(contenido)
                .tipo(tipo)
                .leido(false)
                .build();

        return mapMensaje(tutoringMensajeRepository.save(mensaje));
    }

    /**
     * @param faseId    id de la fase de tutoría
     * @param appUserId id del appUser que marca los mensajes como leídos
     */
    @Override
    @Transactional
    public void marcarMensajesLeidos(Long faseId, Long appUserId) {
        List<TutoringMensaje> noLeidos = tutoringMensajeRepository
                .findByFaseIdAndLeidoFalseAndRemitenteIdNot(faseId, appUserId);
        noLeidos.forEach(m -> m.setLeido(true));
        tutoringMensajeRepository.saveAll(noLeidos);
    }

    // ── PDF ───────────────────────────────────────────────────────────────────

    /**
     * @param faseId    id de la fase de tutoría
     * @param appUserId id del appUser que solicita el PDF
     * @return el resource PDF de esa fase, para descarga
     * @throws RuntimeException si la fase no existe o no tiene PDF
     */
    @Override
    public Resource obtainPdfFase(Long faseId, Long appUserId) {
        TutoringFase fase = tutoringFaseRepository.findById(faseId)
                .orElseThrow(() -> new RuntimeException("Fase no encontrada"));

        validateAccesoATutoring(fase.getTutor(), appUserId);

        if (fase.getArchivoPdfStudent() == null) {
            throw new RuntimeException("Esta fase no tiene PDF cargado");
        }

        Path ruta = Paths.get(uploadDir,
                fase.getTutor().getId().toString(),
                "fase_" + fase.getNumeroFase(),
                fase.getArchivoPdfStudent()).normalize();

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

    // ── Listados por appUser ──────────────────────────────────────────────────

    /**
     * @param studentAppUserId id del appUser student
     * @return resúmenes de todas las tutorías de ese student
     */
    @Override
    public List<TutoringResumenDTO> obtainTutoringsStudent(Long studentAppUserId) {
        return tutorRepository.findBySubmissionStudentAppUserId(studentAppUserId).stream()
                .map(tutor -> buildResumenParaTutor(tutor, studentAppUserId))
                .collect(Collectors.toList());
    }

    /**
     * @param teacherAppUserId id del appUser teacher
     * @return resúmenes de todas las tutorías a cargo de ese teacher
     */
    @Override
    public List<TutoringResumenDTO> obtainTutoringsTeacher(Long teacherAppUserId) {
        return tutorRepository.findByTeacherAppUserId(teacherAppUserId).stream()
                .map(tutor -> buildResumenParaTutor(tutor, teacherAppUserId))
                .collect(Collectors.toList());
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    private void validateAccesoATutoring(Tutor tutor, Long appUserId) {
        AppUser appUser = appUserRepository.findById(appUserId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
                
        boolean esAdminOCoord = "ADMIN".equalsIgnoreCase(appUser.getRole())
                || "COORDINADOR".equalsIgnoreCase(appUser.getRole());
                
        if (esAdminOCoord) {
            return;
        }
        
        Long tutorUserId = (tutor.getTeacher() != null && tutor.getTeacher().getAppUser() != null)
                ? tutor.getTeacher().getAppUser().getId() : null;
        Long studentUserId = (tutor.getSubmission() != null && tutor.getSubmission().getStudent() != null && tutor.getSubmission().getStudent().getAppUser() != null)
                ? tutor.getSubmission().getStudent().getAppUser().getId() : null;
                
        if (!appUserId.equals(tutorUserId) && !appUserId.equals(studentUserId)) {
            throw new org.springframework.security.access.AccessDeniedException("No autorizado para acceder a esta tutoría");
        }
    }

    private TutoringResumenDTO buildResumenParaTutor(Tutor tutor, Long appUserId) {
        Long tutorId = tutor.getId();
        List<TutoringFase> fases = tutoringFaseRepository.findByTutorIdOrderByNumeroFaseAsc(tutorId);

        long mensajesNoLeidos = fases.stream()
                .mapToLong(fase -> tutoringMensajeRepository
                        .countByFaseIdAndLeidoFalseAndRemitenteIdNot(fase.getId(), appUserId))
                .sum();

        long fasesAprobadas = tutoringFaseRepository.countByTutorIdAndEstado(tutorId, "APROBADA");

        Submission submission = tutor.getSubmission();
        String nombreStudent = submission.getStudent().getAppUser().getNombre()
                + " " + submission.getStudent().getAppUser().getApellido();
        String nombreTutor = tutor.getTeacher().getAppUser().getNombre()
                + " " + tutor.getTeacher().getAppUser().getApellido();

        boolean submissionSuspendida = submission.getEstado() != null && "SUSPENDIDA".equals(submission.getEstado().getCodigo());

        return TutoringResumenDTO.builder()
                .tutorId(tutorId)
                .submissionId(submission.getId())
                .tituloTopic(submission.getTituloTopic())
                .nombreStudent(nombreStudent)
                .nombreTutor(nombreTutor)
                .totalFases(fases.size())
                .fasesAprobadas(fasesAprobadas)
                .estadoTutoring(tutor.getEstado())
                .mensajesNoLeidos(mensajesNoLeidos)
                .submissionSuspendida(submissionSuspendida)
                .build();
    }

    private TutoringFaseDTO mapFaseConMensajes(TutoringFase fase) {
        List<TutoringMensajeDTO> mensajes = tutoringMensajeRepository
                .findByFaseIdOrderByFechaEnvioAsc(fase.getId()).stream()
                .map(this::mapMensaje)
                .collect(Collectors.toList());

        return TutoringFaseDTO.builder()
                .id(fase.getId())
                .tutorId(fase.getTutor().getId())
                .numeroFase(fase.getNumeroFase())
                .estado(fase.getEstado())
                .fechaInicio(fase.getFechaInicio())
                .fechaAprobacion(fase.getFechaAprobacion())
                .archivoPdfStudent(fase.getArchivoPdfStudent())
                .tamanoPdfBytes(fase.getTamanoPdfBytes())
                .mensajes(mensajes)
                .build();
    }

    private TutoringMensajeDTO mapMensaje(TutoringMensaje m) {
        String nombreRemitente = m.getRemitente().getNombre() + " " + m.getRemitente().getApellido();
        return TutoringMensajeDTO.builder()
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

    private String calculateSha256YSave(MultipartFile archivo, Path destino) {
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
     * @param appUserId   id del appUser student que registra el avance (para validacion)
     */
    @Override
    @Transactional
    public void registerAvanceSP(Long tutorId, Integer numeroFase, String archivoPdf, Long tamanoBytes, String sha256, Long appUserId) {
        Tutor tutor = tutorRepository.findById(tutorId)
                .orElseThrow(() -> new RuntimeException("Tutor no encontrado"));
                
        validateAccesoATutoring(tutor, appUserId);

        tutoringFaseRepository.spRegisterTutoringAvance(tutorId, numeroFase, archivoPdf, tamanoBytes, sha256);
    }
}
