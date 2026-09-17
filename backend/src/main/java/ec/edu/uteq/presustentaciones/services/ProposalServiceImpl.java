package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.Proposal;
import ec.edu.uteq.presustentaciones.entities.Submission;
import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.repositories.ProposalRepository;
import ec.edu.uteq.presustentaciones.repositories.SubmissionRepository;
import ec.edu.uteq.presustentaciones.repositories.AppUserRepository;
import ec.edu.uteq.presustentaciones.security.service.SubmissionAccessService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
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
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class ProposalServiceImpl implements ProposalService {

    private final ProposalRepository proposalRepository;
    private final SubmissionRepository submissionRepository;
    private final NotificationService notificationService;
    private final AppUserRepository appUserRepository;
    private final SubmissionAccessService submissionAccessService;

    @Value("${app.upload.dir:uploads/anteproyectos}")
    private String uploadDir;

    /**
     * Construye ProposalServiceImpl, inyectando ar, sr, ns, ur, sas.
     * @param ar ar
     * @param sr sr
     * @param ns ns
     * @param ur ur
     * @param sas sas
     */
    public ProposalServiceImpl(ProposalRepository ar, SubmissionRepository sr,
                                   NotificationService ns, AppUserRepository ur,
                                   SubmissionAccessService sas) {
        this.proposalRepository = ar;
        this.submissionRepository = sr;
        this.notificationService = ns;
        this.appUserRepository = ur;
        this.submissionAccessService = sas;
    }

    /** Solo el student dueño de la submission puede upload su propio proposal (ADMIN
     * incluido como vía administrativa, igual que en el resto de los servicios de este
     * block) -- evita que un tercero suba/reemplace el PDF de otra submission (IDOR de
     * escritura). */
    private void validatePuedeUpload(Submission submission) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Usuario no autenticado");
        }
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) return;

        String email = auth.getName();
        boolean esDueno = submission.getStudent() != null && submission.getStudent().getAppUser() != null
                && submission.getStudent().getAppUser().getEmail().equals(email);
        if (!esDueno) {
            throw new AccessDeniedException("Solo el estudiante propietario de la solicitud puede subir su anteproyecto");
        }
    }

    /**
     * Sube el PDF del proposal de una submission, calcula y persiste su hash SHA-256, y
     * deja el proposal en estado pendiente de revisión.
     *
     * @param submissionId id de la submission a la que pertenece el proposal
     * @param archivo     archivo PDF subido por el student
     * @return el proposal creado o actualizado
     * @throws RuntimeException si la submission no existe o el archivo no es un PDF válido
     */
    @Override
    public Proposal sendProposal(Long submissionId, MultipartFile archivo) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));

        validatePuedeUpload(submission);

        if (submission.getEstado() != null && "SUSPENDIDA".equalsIgnoreCase(submission.getEstado().getCodigo())) {
            throw new RuntimeException("Tu trabajo ha sido suspendido y no puedes subir archivos. Motivo: " + submission.getMotivoSuspension());
        }

        String contentType = archivo.getContentType();
        if (contentType == null || !contentType.equals("application/pdf")) {
            throw new RuntimeException("Solo se permiten archivos PDF");
        }
        if (archivo.getSize() > 10L * 1024 * 1024) {
            throw new RuntimeException("El archivo no puede superar los 10 MB");
        }

        Path dirPath = Paths.get(uploadDir);
        try { Files.createDirectories(dirPath); } catch (IOException e) {
            throw new RuntimeException("No se pudo crear el directorio de uploads", e);
        }

        String nombreArchivo = "solicitud_" + submissionId + "_" + UUID.randomUUID() + ".pdf";
        Path rutaArchivo = dirPath.resolve(nombreArchivo);
        String sha256 = calculateSha256YSave(archivo, rutaArchivo);

        Proposal proposal = proposalRepository
                .findBySubmissionId(submissionId).orElse(null);

        if (proposal != null
                && proposal.getArchivoPdf() != null
                && !"RECHAZADO".equals(proposal.getEstado())) {
            throw new RuntimeException(
                    "No puedes reemplazar el PDF. El coordinador debe rechazar el anteproyecto para permitir una nueva carga.");
        }

        if (proposal == null) proposal = new Proposal();

        proposal.setArchivoPdf(nombreArchivo);
        proposal.setFechaEnvio(LocalDate.now());
        proposal.setEstado("ENVIADO");
        proposal.setSubmission(submission);
        proposal.setSha256Hash(sha256);
        proposal.setTamanoBytes(archivo.getSize());

        Proposal guardado = proposalRepository.save(proposal);

        // Notify a los admins que hay un proposal nuevo para revisar
        notifyAdminsNuevoProposal(submission);

        return guardado;
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
     * RF-02: Verifica que el archivo en disco coincida con el hash SHA-256 almacenado.
     *
     * @param submissionId id de la submission cuyo proposal se va a verify
     * @return {@code true} si el hash SHA-256 del archivo en disco coincide con el
     *         almacenado en base de datos (comparación con {@code MessageDigest.isEqual},
     *         resistente a ataques de timing)
     * @throws RuntimeException si la submission no tiene proposal o el archivo no existe
     *                          en disco
     */
    @Override
    public boolean verifyIntegridad(Long submissionId) {
        Proposal ap = proposalRepository.findBySubmissionId(submissionId)
                .orElseThrow(() -> new RuntimeException("Anteproyecto no encontrado"));
        submissionAccessService.validateAcceso(ap.getSubmission(), "ANTEPROYECTO_REVISAR");

        if (ap.getSha256Hash() == null) return false;

        Path rutaArchivo = Paths.get(uploadDir).resolve(ap.getArchivoPdf()).normalize();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(rutaArchivo);
            byte[] hashActual = digest.digest(bytes);
            byte[] hashEsperado = HexFormat.of().parseHex(ap.getSha256Hash());
            // Comparacion en tiempo constante para evitar timing attacks (find-sec-bugs: UNSAFE_HASH_EQUALS)
            return MessageDigest.isEqual(hashActual, hashEsperado);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * @param id  id del proposal a approve
     * @param obs observaciones opcionales del revisor
     * @return el proposal actualizado en estado "APROBADO"
     * @throws RuntimeException si el proposal no existe
     */
    @Override
    public Proposal approveProposal(Long id, String obs) {
        Proposal ap = proposalRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Anteproyecto no encontrado"));
        ap.setEstado("APROBADO");
        ap.setObservaciones(obs);
        Proposal guardado = proposalRepository.save(ap);

        // Notify al student
        notifyStudentProposal(ap, true, obs);

        return guardado;
    }

    /**
     * @param id  id del proposal a reject
     * @param obs motivo del rechazo
     * @return el proposal actualizado en estado "RECHAZADO"
     * @throws RuntimeException si el proposal no existe
     */
    @Override
    public Proposal rejectProposal(Long id, String obs) {
        Proposal ap = proposalRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Anteproyecto no encontrado"));
        ap.setEstado("RECHAZADO");
        ap.setObservaciones(obs);
        Proposal guardado = proposalRepository.save(ap);

        // Notify al student
        notifyStudentProposal(ap, false, obs);

        return guardado;
    }

    /**
     * @param submissionId id de la submission
     * @return el proposal de esa submission, si ya fue enviado
     */
    @Override
    public Optional<Proposal> searchPorSubmission(Long submissionId) {
        Optional<Proposal> proposal = proposalRepository.findBySubmissionId(submissionId);
        proposal.ifPresent(ap -> submissionAccessService.validateAcceso(ap.getSubmission(), "ANTEPROYECTO_REVISAR"));
        return proposal;
    }

    // ── Helpers de notificación ───────────────────────────────────────────────

    private void notifyAdminsNuevoProposal(Submission submission) {
        try {
            List<AppUser> admins = appUserRepository.findByRole("ADMIN");
            String nombreEst = submission.getStudent().getAppUser().getNombre()
                    + " " + submission.getStudent().getAppUser().getApellido();
            for (AppUser admin : admins) {
                notificationService.createNotification(admin.getId(),
                        String.format("📄 El estudiante %s ha subido el PDF del anteproyecto \"%s\". " +
                                        "Está pendiente de revisión y aprobación.",
                                nombreEst, submission.getTituloTopic()));
            }
        } catch (Exception e) {
            log.warn("No se pudo notificar a admins sobre nuevo anteproyecto: {}", e.getMessage());
        }
    }

    private void notifyStudentProposal(Proposal ap, boolean aprobado, String obs) {
        try {
            Long appUserId = ap.getSubmission().getStudent().getAppUser().getId();
            String titulo  = ap.getSubmission().getTituloTopic();
            String msg;
            if (aprobado) {
                msg = String.format("✅ Tu anteproyecto \"%s\" ha sido APROBADO. " +
                        "Ya puedes proceder con el siguiente paso del proceso.", titulo);
            } else {
                msg = String.format("❌ Tu anteproyecto \"%s\" ha sido RECHAZADO. " +
                        "Debes corregirlo y volver a cargarlo.", titulo);
            }
            if (obs != null && !obs.isBlank()) {
                msg += " Observaciones del coordinador: " + obs;
            }
            notificationService.createNotification(appUserId, msg);
        } catch (Exception e) {
            log.warn("No se pudo notificar al estudiante sobre anteproyecto: {}", e.getMessage());
        }
    }
}