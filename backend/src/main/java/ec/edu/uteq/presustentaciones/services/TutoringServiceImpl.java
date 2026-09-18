package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.TutoringPhaseDTO;
import ec.edu.uteq.presustentaciones.dto.TutoringMessageDTO;
import ec.edu.uteq.presustentaciones.dto.TutoringSummaryDTO;
import ec.edu.uteq.presustentaciones.entities.*;
import ec.edu.uteq.presustentaciones.enums.StatusSubmission;
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
    private final TutoringPhaseRepository tutoringPhaseRepository;
    private final TutoringMessageRepository tutoringMessageRepository;
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
     * @param tutoringPhaseRepository tutoringPhaseRepository
     * @param tutoringMessageRepository tutoringMessageRepository
     * @param appUserRepository appUserRepository
     * @param proposalRepository proposalRepository
     * @param notificationService notificationService
     */
    public TutoringServiceImpl(TutorRepository tutorRepository,
                              TutoringPhaseRepository tutoringPhaseRepository,
                              TutoringMessageRepository tutoringMessageRepository,
                              AppUserRepository appUserRepository,
                              ProposalRepository proposalRepository,
                              NotificationService notificationService) {
        this.tutorRepository = tutorRepository;
        this.tutoringPhaseRepository = tutoringPhaseRepository;
        this.tutoringMessageRepository = tutoringMessageRepository;
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
    public TutoringSummaryDTO obtainSummary(Long tutorId, Long appUserId) {
        Tutor tutor = tutorRepository.findById(tutorId)
                .orElseThrow(() -> new RuntimeException("Tutor no encontrado"));

        validateAccessATutoring(tutor, appUserId);
        return buildSummaryForTutor(tutor, appUserId);
    }

    // ── Fases ─────────────────────────────────────────────────────────────────

    /**
     * @param tutorId   id del registro de tutoría
     * @param appUserId id del appUser que consulta (para resolve permissions)
     * @return las fases registradas de esa tutoría, en orden
     */
    @Override
    public List<TutoringPhaseDTO> obtainPhases(Long tutorId, Long appUserId) {
        Tutor tutor = tutorRepository.findById(tutorId)
                .orElseThrow(() -> new RuntimeException("Tutor no encontrado"));
                
        validateAccessATutoring(tutor, appUserId);
        
        return tutoringPhaseRepository.findByTutorIdOrderByNumeroPhaseAsc(tutorId).stream()
                .map(this::mapPhaseWithMessages)
                .collect(Collectors.toList());
    }

    /**
     * @param tutorId        id del registro de tutoría
     * @param tutorAppUserId id del appUser teacher que crea la fase
     * @param observation    observación inicial del teacher para esta fase
     * @return la fase creada
     * @throws RuntimeException si la tutoría no existe
     */
    @Override
    @Transactional
    public TutoringPhaseDTO createPhaseWithObservation(Long tutorId, Long tutorAppUserId, String observation) {
        Tutor tutor = tutorRepository.findById(tutorId)
                .orElseThrow(() -> new RuntimeException("Tutor no encontrado"));

        if (!tutor.getTeacher().getAppUser().getId().equals(tutorAppUserId)) {
            throw new org.springframework.security.access.AccessDeniedException("No autorizado");
        }

        long totalPhases = tutoringPhaseRepository.countByTutorId(tutorId);
        if (totalPhases >= 3) {
            throw new RuntimeException("No se pueden crear más de 3 fases de revisión");
        }

        if (totalPhases > 0) {
            List<TutoringPhase> phases = tutoringPhaseRepository.findByTutorIdOrderByNumeroPhaseAsc(tutorId);
            TutoringPhase last = phases.get(phases.size() - 1);
            if (!"APROBADA".equals(last.getStatus())) {
                throw new RuntimeException("Debes aprobar la fase actual antes de crear una nueva");
            }
        }

        TutoringPhase phase = TutoringPhase.builder()
                .tutor(tutor)
                .numeroPhase((int) totalPhases + 1)
                .status("PENDIENTE_ESTUDIANTE")
                .build();
        phase = tutoringPhaseRepository.save(phase);

        AppUser tutorAppUser = appUserRepository.findById(tutorAppUserId)
                .orElseThrow(() -> new RuntimeException("Usuario tutor no encontrado"));

        TutoringMessage message = TutoringMessage.builder()
                .phase(phase)
                .sender(tutorAppUser)
                .contenido(observation)
                .kind("OBSERVACION")
                .leido(false)
                .build();
        tutoringMessageRepository.save(message);

        return mapPhaseWithMessages(phase);
    }

    // ── Subida de PDF ─────────────────────────────────────────────────────────

    /**
     * @param phaseId              id de la fase de tutoría
     * @param file             PDF corregido subido por el student
     * @param studentAppUserId id del appUser student que sube el archivo
     * @return la fase actualizada con el nuevo PDF
     * @throws RuntimeException si la fase no existe o el archivo no es un PDF válido
     */
    @Override
    @Transactional
    public TutoringPhaseDTO uploadPdfCorrected(Long phaseId, MultipartFile file, Long studentAppUserId) {
        TutoringPhase phase = tutoringPhaseRepository.findById(phaseId)
                .orElseThrow(() -> new RuntimeException("Fase no encontrada"));

        Long studentAppUserReal = phase.getTutor().getSubmission().getStudent().getAppUser().getId();
        if (!studentAppUserReal.equals(studentAppUserId)) {
            throw new org.springframework.security.access.AccessDeniedException("No autorizado");
        }

        Submission submission = phase.getTutor().getSubmission();
        if (submission.getStatus() != null && "SUSPENDIDA".equalsIgnoreCase(submission.getStatus().getCode())) {
            throw new RuntimeException("No puedes subir más archivos. Este tema ha sido suspendido por: " + submission.getMotivoSuspension());
        }

        if (!"PENDIENTE_ESTUDIANTE".equals(phase.getStatus())) {
            throw new RuntimeException("Solo puedes subir el PDF cuando el tutor ha enviado observaciones");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("application/pdf")) {
            throw new RuntimeException("Solo se permiten archivos PDF");
        }
        if (file.getSize() > 10L * 1024 * 1024) {
            throw new RuntimeException("El archivo no puede superar los 10 MB");
        }

        Long tutorId = phase.getTutor().getId();
        int numeroPhase = phase.getNumeroPhase();

        Path dirPath = Paths.get(uploadDir, tutorId.toString(), "fase_" + numeroPhase);
        try {
            Files.createDirectories(dirPath);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo crear el directorio de uploads", e);
        }

        // Delete archivo anterior si existe
        if (phase.getFilePdfStudent() != null) {
            try {
                Files.deleteIfExists(dirPath.resolve(phase.getFilePdfStudent()));
            } catch (IOException e) {
                log.warn("No se pudo eliminar el archivo anterior: {}", e.getMessage());
            }
        }

        String nombreFile = "tutor_" + tutorId + "_fase" + numeroPhase + "_" + UUID.randomUUID() + ".pdf";
        Path rutaFile = dirPath.resolve(nombreFile);
        String sha256 = calculateSha256AndSave(file, rutaFile);

        phase.setFilePdfStudent(nombreFile);
        phase.setSha256Pdf(sha256);
        phase.setSizePdfBytes(file.getSize());
        phase.setStatus("PENDIENTE_TUTOR");
        phase = tutoringPhaseRepository.save(phase);

        AppUser student = appUserRepository.findById(studentAppUserId)
                .orElseThrow(() -> new RuntimeException("Usuario estudiante no encontrado"));

        TutoringMessage messageAuto = TutoringMessage.builder()
                .phase(phase)
                .sender(student)
                .contenido("He subido las correcciones solicitadas.")
                .kind("RESPUESTA")
                .leido(false)
                .build();
        tutoringMessageRepository.save(messageAuto);

        return mapPhaseWithMessages(phase);
    }

    // ── Aprobación ────────────────────────────────────────────────────────────

    /**
     * @param phaseId         id de la fase a approve
     * @param tutorAppUserId id del appUser teacher que aprueba
     * @param comment     comentario opcional de aprobación
     * @return la fase actualizada en estado aprobado
     * @throws RuntimeException si la fase no existe
     */
    @Override
    @Transactional
    public TutoringPhaseDTO approvePhase(Long phaseId, Long tutorAppUserId, String comment) {
        TutoringPhase phase = tutoringPhaseRepository.findById(phaseId)
                .orElseThrow(() -> new RuntimeException("Fase no encontrada"));

        if (!phase.getTutor().getTeacher().getAppUser().getId().equals(tutorAppUserId)) {
            throw new org.springframework.security.access.AccessDeniedException("No autorizado");
        }

        if (!"PENDIENTE_TUTOR".equals(phase.getStatus())) {
            throw new RuntimeException("No puedes aprobar una fase sin correcciones del estudiante");
        }

        if (phase.getFilePdfStudent() == null) {
            throw new RuntimeException("No existe un PDF del estudiante para aprobar");
        }

        phase.setStatus("APROBADA");
        phase.setDateAprobacion(LocalDateTime.now());
        phase = tutoringPhaseRepository.save(phase);

        AppUser tutorAppUser = appUserRepository.findById(tutorAppUserId)
                .orElseThrow(() -> new RuntimeException("Usuario tutor no encontrado"));

        TutoringMessage messageAprobacion = TutoringMessage.builder()
                .phase(phase)
                .sender(tutorAppUser)
                .contenido(comment != null && !comment.isBlank() ? comment : "Fase aprobada.")
                .kind("APROBACION")
                .leido(false)
                .build();
        tutoringMessageRepository.save(messageAprobacion);

        try {
            Long studentAppUserId = phase.getTutor().getSubmission().getStudent().getAppUser().getId();
            notificationService.createNotification(studentAppUserId,
                    String.format("Tu tutor aprobó la fase %d de tutoría.", phase.getNumeroPhase()));
        } catch (Exception e) {
            log.warn("No se pudo notificar la aprobación de fase {}: {}", phase.getId(), e.getMessage());
        }

        // Si las 3 fases están APROBADAS, marcar tutor como COMPLETADA y update el Proposal
        Long tutorId = phase.getTutor().getId();
        long totalPhases = tutoringPhaseRepository.countByTutorId(tutorId);
        long phasesAprobadas = tutoringPhaseRepository.countByTutorIdAndStatus(tutorId, "APROBADA");
        if (totalPhases == 3 && phasesAprobadas == 3) {
            Tutor tutor = phase.getTutor();
            tutor.setStatus("COMPLETADA");
            tutorRepository.save(tutor);

            // Reemplazar el PDF del Proposal con el PDF final aprobado de la Fase 3
            TutoringPhase phase3 = tutoringPhaseRepository.findByTutorIdOrderByNumeroPhaseAsc(tutorId)
                    .stream()
                    .filter(f -> f.getNumeroPhase() == 3)
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Fase 3 no encontrada"));

            // Copiar físicamente el PDF de la Fase 3 a la carpeta de proposals
            Path source = Paths.get(uploadDir, tutorId.toString(), "fase_3", phase3.getFilePdfStudent());
            Path destDir = Paths.get(uploadDirProposals);
            Path destino = destDir.resolve(phase3.getFilePdfStudent());
            try {
                Files.createDirectories(destDir);
                Files.copy(source, destino, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException("No se pudo copiar el PDF de la Fase 3 al directorio de anteproyectos", e);
            }

            proposalRepository.findBySubmissionId(tutor.getSubmission().getId())
                    .ifPresent(proposal -> {
                        proposal.setFilePdf(phase3.getFilePdfStudent());
                        proposal.setSha256Hash(phase3.getSha256Pdf());
                        proposal.setSizeBytes(phase3.getSizePdfBytes());
                        proposal.setStatus("APROBADO");
                        proposal.setObservations("PDF final aprobado tras completar las 3 fases de tutoría");
                        proposalRepository.save(proposal);
                    });
        }

        return mapPhaseWithMessages(phase);
    }

    // ── Mensajes ──────────────────────────────────────────────────────────────

    /**
     * @param phaseId      id de la fase de tutoría
     * @param senderId id del appUser que envía el mensaje
     * @param contenido   texto del mensaje
     * @param kind        tipo de mensaje (p. ej. comentario, corrección)
     * @return el mensaje creado
     * @throws RuntimeException si la fase no existe
     */
    @Override
    @Transactional
    public TutoringMessageDTO sendMessage(Long phaseId, Long senderId, String contenido, String kind) {
        TutoringPhase phase = tutoringPhaseRepository.findById(phaseId)
                .orElseThrow(() -> new RuntimeException("Fase no encontrada"));

        AppUser sender = appUserRepository.findById(senderId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Long tutorUserId = (phase.getTutor() != null && phase.getTutor().getTeacher() != null && phase.getTutor().getTeacher().getAppUser() != null)
                ? phase.getTutor().getTeacher().getAppUser().getId() : null;
        Long studentUserId = (phase.getTutor() != null && phase.getTutor().getSubmission() != null && phase.getTutor().getSubmission().getStudent() != null && phase.getTutor().getSubmission().getStudent().getAppUser() != null)
                ? phase.getTutor().getSubmission().getStudent().getAppUser().getId() : null;
        boolean esPrivilegiado = sender.getRole() != null &&
                ("ADMIN".equalsIgnoreCase(sender.getRole()) || "COORDINADOR".equalsIgnoreCase(sender.getRole()));

        if (!senderId.equals(tutorUserId) && !senderId.equals(studentUserId) && !esPrivilegiado) {
            throw new org.springframework.security.access.AccessDeniedException("No autorizado para enviar mensajes en esta tutoría");
        }

        TutoringMessage message = TutoringMessage.builder()
                .phase(phase)
                .sender(sender)
                .contenido(contenido)
                .kind(kind)
                .leido(false)
                .build();

        return mapMessage(tutoringMessageRepository.save(message));
    }

    /**
     * @param phaseId    id de la fase de tutoría
     * @param appUserId id del appUser que marca los mensajes como leídos
     */
    @Override
    @Transactional
    public void markMessagesRead(Long phaseId, Long appUserId) {
        List<TutoringMessage> noRead = tutoringMessageRepository
                .findByPhaseIdAndLeidoFalseAndSenderIdNot(phaseId, appUserId);
        noRead.forEach(m -> m.setLeido(true));
        tutoringMessageRepository.saveAll(noRead);
    }

    // ── PDF ───────────────────────────────────────────────────────────────────

    /**
     * @param phaseId    id de la fase de tutoría
     * @param appUserId id del appUser que solicita el PDF
     * @return el resource PDF de esa fase, para descarga
     * @throws RuntimeException si la fase no existe o no tiene PDF
     */
    @Override
    public Resource obtainPdfPhase(Long phaseId, Long appUserId) {
        TutoringPhase phase = tutoringPhaseRepository.findById(phaseId)
                .orElseThrow(() -> new RuntimeException("Fase no encontrada"));

        validateAccessATutoring(phase.getTutor(), appUserId);

        if (phase.getFilePdfStudent() == null) {
            throw new RuntimeException("Esta fase no tiene PDF cargado");
        }

        Path ruta = Paths.get(uploadDir,
                phase.getTutor().getId().toString(),
                "fase_" + phase.getNumeroPhase(),
                phase.getFilePdfStudent()).normalize();

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
    public List<TutoringSummaryDTO> obtainTutoringsStudent(Long studentAppUserId) {
        return tutorRepository.findBySubmissionStudentAppUserId(studentAppUserId).stream()
                .map(tutor -> buildSummaryForTutor(tutor, studentAppUserId))
                .collect(Collectors.toList());
    }

    /**
     * @param teacherAppUserId id del appUser teacher
     * @return resúmenes de todas las tutorías a cargo de ese teacher
     */
    @Override
    public List<TutoringSummaryDTO> obtainTutoringsTeacher(Long teacherAppUserId) {
        return tutorRepository.findByTeacherAppUserId(teacherAppUserId).stream()
                .map(tutor -> buildSummaryForTutor(tutor, teacherAppUserId))
                .collect(Collectors.toList());
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    private void validateAccessATutoring(Tutor tutor, Long appUserId) {
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

    private TutoringSummaryDTO buildSummaryForTutor(Tutor tutor, Long appUserId) {
        Long tutorId = tutor.getId();
        List<TutoringPhase> phases = tutoringPhaseRepository.findByTutorIdOrderByNumeroPhaseAsc(tutorId);

        long messagesNoRead = phases.stream()
                .mapToLong(phase -> tutoringMessageRepository
                        .countByPhaseIdAndLeidoFalseAndSenderIdNot(phase.getId(), appUserId))
                .sum();

        long phasesAprobadas = tutoringPhaseRepository.countByTutorIdAndStatus(tutorId, "APROBADA");

        Submission submission = tutor.getSubmission();
        String nombreStudent = submission.getStudent().getAppUser().getNombre()
                + " " + submission.getStudent().getAppUser().getApellido();
        String nombreTutor = tutor.getTeacher().getAppUser().getNombre()
                + " " + tutor.getTeacher().getAppUser().getApellido();

        boolean submissionSuspendida = submission.getStatus() != null && "SUSPENDIDA".equals(submission.getStatus().getCode());

        return TutoringSummaryDTO.builder()
                .tutorId(tutorId)
                .submissionId(submission.getId())
                .tituloTopic(submission.getTituloTopic())
                .nombreStudent(nombreStudent)
                .nombreTutor(nombreTutor)
                .totalPhases(phases.size())
                .phasesAprobadas(phasesAprobadas)
                .statusTutoring(tutor.getStatus())
                .messagesNoRead(messagesNoRead)
                .submissionSuspendida(submissionSuspendida)
                .build();
    }

    private TutoringPhaseDTO mapPhaseWithMessages(TutoringPhase phase) {
        List<TutoringMessageDTO> messages = tutoringMessageRepository
                .findByPhaseIdOrderByDateEnvioAsc(phase.getId()).stream()
                .map(this::mapMessage)
                .collect(Collectors.toList());

        return TutoringPhaseDTO.builder()
                .id(phase.getId())
                .tutorId(phase.getTutor().getId())
                .numeroPhase(phase.getNumeroPhase())
                .status(phase.getStatus())
                .dateStart(phase.getDateStart())
                .dateAprobacion(phase.getDateAprobacion())
                .filePdfStudent(phase.getFilePdfStudent())
                .sizePdfBytes(phase.getSizePdfBytes())
                .messages(messages)
                .build();
    }

    private TutoringMessageDTO mapMessage(TutoringMessage m) {
        String nombreSender = m.getSender().getNombre() + " " + m.getSender().getApellido();
        return TutoringMessageDTO.builder()
                .id(m.getId())
                .phaseId(m.getPhase().getId())
                .senderId(m.getSender().getId())
                .nombreSender(nombreSender)
                .contenido(m.getContenido())
                .dateEnvio(m.getDateEnvio())
                .kind(m.getKind())
                .leido(m.getLeido())
                .build();
    }

    private String calculateSha256AndSave(MultipartFile file, Path destino) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = file.getInputStream();
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
     * @param numeroPhase  número de fase que avanza
     * @param filePdf  nombre del archivo PDF asociado al avance
     * @param sizeBytes tamaño en bytes del archivo
     * @param sha256      hash SHA-256 del archivo, para verificación de integridad posterior
     * @param appUserId   id del appUser student que registra el avance (para validacion)
     */
    @Override
    @Transactional
    public void registerProgressSP(Long tutorId, Integer numeroPhase, String filePdf, Long sizeBytes, String sha256, Long appUserId) {
        Tutor tutor = tutorRepository.findById(tutorId)
                .orElseThrow(() -> new RuntimeException("Tutor no encontrado"));
                
        validateAccessATutoring(tutor, appUserId);

        tutoringPhaseRepository.spRegisterTutoringProgress(tutorId, numeroPhase, filePdf, sizeBytes, sha256);
    }
}
