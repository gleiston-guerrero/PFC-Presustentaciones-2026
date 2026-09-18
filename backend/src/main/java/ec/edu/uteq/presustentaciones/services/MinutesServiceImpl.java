package ec.edu.uteq.presustentaciones.services;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.io.font.constants.StandardFonts;
import ec.edu.uteq.presustentaciones.entities.Minutes;
import ec.edu.uteq.presustentaciones.entities.StatusMinutes;
import ec.edu.uteq.presustentaciones.entities.EvaluationFinal;
import ec.edu.uteq.presustentaciones.entities.HistoryStatusMinutes;
import ec.edu.uteq.presustentaciones.entities.Panelist;
import ec.edu.uteq.presustentaciones.entities.Submission;
import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.dto.MinutesDetailDTO;
import ec.edu.uteq.presustentaciones.dto.MinutesSummaryDTO;
import ec.edu.uteq.presustentaciones.dto.HistoryMinutesDTO;
import ec.edu.uteq.presustentaciones.repositories.MinutesRepository;
import ec.edu.uteq.presustentaciones.repositories.StatusMinutesRepository;
import ec.edu.uteq.presustentaciones.repositories.EvaluationFinalRepository;
import ec.edu.uteq.presustentaciones.repositories.HistoryStatusMinutesRepository;
import ec.edu.uteq.presustentaciones.repositories.PanelistRepository;
import ec.edu.uteq.presustentaciones.repositories.SubmissionRepository;
import ec.edu.uteq.presustentaciones.repositories.TutorRepository;
import ec.edu.uteq.presustentaciones.repositories.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinutesServiceImpl implements MinutesService {

    private final MinutesRepository minutesRepository;
    private final SubmissionRepository submissionRepository;
    private final EvaluationFinalRepository evaluationRepository;
    private final PanelistRepository panelistRepository;
    private final ec.edu.uteq.presustentaciones.repositories.StatusSubmissionRepository statusSubmissionRepository;
    private final jakarta.persistence.EntityManager entityManager;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final TutorRepository tutorRepository;
    private final StatusMinutesRepository statusMinutesRepository;
    private final HistoryStatusMinutesRepository historyStatusMinutesRepository;
    private final AppUserRepository appUserRepository;
    private final PermissionService permissionService;

    // Flujo de estados del minutes. ADMIN puede saltarse estas reglas para ANULAR.
    private static final Map<String, Set<String>> TRANSICIONES = Map.of(
            "GENERADA",   Set.of("REVISADA", "OBSERVADA", "ANULADA"),
            "REVISADA",   Set.of("FINALIZADA", "OBSERVADA", "ANULADA"),
            "OBSERVADA",  Set.of("REVISADA", "GENERADA", "ANULADA"),
            "FINALIZADA", Set.of("ANULADA"),
            "ANULADA",    Set.of());
    private static final Set<String> ESTADOS_QUE_EXIGEN_MOTIVO = Set.of("OBSERVADA", "ANULADA");

    @Value("${app.actas.dir:uploads/actas}")
    private String minutesDir;

    // ── Colores institucionales UTEQ ─────────────────────────────────────────
    private static final DeviceRgb UTEQ_BLUE    = new DeviceRgb(0, 56, 101);
    private static final DeviceRgb UTEQ_GOLD    = new DeviceRgb(204, 153, 0);
    private static final DeviceRgb LIGHT_GRAY   = new DeviceRgb(245, 245, 245);
    private static final DeviceRgb MEDIUM_GRAY  = new DeviceRgb(200, 200, 200);

    private void validateAccess(Minutes minutes) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("Usuario no autenticado");
        }
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) return;

        // ADMIN / COORDINADOR: acceso completo de lectura vía el sistema de permissions dinámico
        // (mismo mecanismo que @permissionService.hasPermission en los controllers). Un DOCENTE
        // NO tiene ACTAS_VER, así que cae a la comprobación de propiedad de abajo.
        if (permissionService.hasPermission(auth, "ACTAS_VER")
                || permissionService.hasPermission(auth, "ACTAS_GESTIONAR")) {
            return;
        }

        String email = auth.getName();
        if (minutes.getSubmission().getStudent() != null &&
            minutes.getSubmission().getStudent().getAppUser().getEmail().equals(email)) {
            return;
        }

        List<Panelist> panelists = panelistRepository.findBySubmissionId(minutes.getSubmission().getId());
        boolean esPanelist = panelists.stream().anyMatch(j -> j.getTeacher() != null && j.getTeacher().getAppUser().getEmail().equals(email));
        if (esPanelist) return;

        Optional<ec.edu.uteq.presustentaciones.entities.Tutor> tutorOpt = tutorRepository.findBySubmissionId(minutes.getSubmission().getId());
        if (tutorOpt.isPresent() && tutorOpt.get().getTeacher() != null && tutorOpt.get().getTeacher().getAppUser().getEmail().equals(email)) {
            return;
        }

        throw new RuntimeException("No tienes permiso para acceder a esta acta");
    }

    /**
     * RF-11: Genera el minutes y crea el PDF real en disco.
     *
     * @param submissionId id de la submission a la que pertenece el minutes
     * @return el minutes existente si ya se había generado, o la recién creada (con su PDF)
     * @throws RuntimeException si la submission no existe, no tiene evaluación final, o no se
     *                          pudo create el directorio de minutes en disco
     */
    @Override
    @Transactional
    public Minutes generateMinutes(Long submissionId) {
        auditService.markActorActual();
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada: " + submissionId));

        // Search evaluación y panelists
        Optional<EvaluationFinal> evalOpt = evaluationRepository.findBySubmissionId(submissionId);
        if (evalOpt.isEmpty()) {
            throw new RuntimeException("No se puede generar el acta sin una evaluación final");
        }
        List<Panelist> panelists = panelistRepository.findBySubmissionId(submissionId);

        // Si ya existe el minutes, retornar la misma
        Optional<Minutes> existing = minutesRepository.findBySubmissionId(submissionId);
        if (existing.isPresent()) {
            return existing.get();
        }

        String fileName = "acta_" + submissionId + "_" + System.currentTimeMillis() + ".pdf";
        StatusMinutes statusGenerada = statusMinutesRepository.findByCode("GENERADA")
                .orElseThrow(() -> new RuntimeException("Catálogo estados_acta sin 'GENERADA' (revisar migración V19)"));
        Minutes minutes = Minutes.builder()
                .submission(submission)
                .filePdf(fileName)
                .dateGeneracion(LocalDate.now())
                .status(statusGenerada)
                .build();

        // Create directorio de subida si no existe
        try {
            Files.createDirectories(Paths.get(minutesDir));
        } catch (IOException e) {
            throw new RuntimeException("No se pudo crear el directorio de actas: " + e.getMessage());
        }

        String rutaComplete = minutesDir + "/" + fileName;
        generatePdf(rutaComplete, submission, evalOpt.orElse(null), panelists, minutes);

        Minutes guardada = minutesRepository.save(minutes);
        registerHistory(guardada, null, statusGenerada, "CREAR", "Acta generada a partir de la evaluación final");
        return guardada;
    }

    /**
     * RF-08: Firma el minutes por un actor específico (PRESIDENTE, VOCAL_1, VOCAL_2, TUTOR).
     *
     * @param minutesId      id del minutes a sign
     * @param role         role que firma ({@code PRESIDENTE}, {@code VOCAL_1}, {@code VOCAL_2}
     *                    o {@code TUTOR}); no distingue mayúsculas/minúsculas
     * @param observation observación opcional del firmante, o {@code null}
     * @return el minutes actualizada; si con esta firma quedan las 4 completas, la submission pasa
     *         a "COMPLETADA" y el PDF se regenera con el estado final de las firmas
     * @throws RuntimeException si el minutes no existe, {@code role} no es uno de los 4 válidos, el
     *                          appUser no está autenticado, o no tiene permission para sign ese role
     */
    @Override
    @Transactional
    public Minutes signMinutes(Long minutesId, String role, String observation) {
        auditService.markActorActual();
        Minutes minutes = minutesRepository.findById(minutesId)
                .orElseThrow(() -> new RuntimeException("Acta no encontrada: " + minutesId));

        String roleNormalizado = role.toUpperCase();
        if (!java.util.Set.of("PRESIDENTE", "VOCAL_1", "VOCAL_2", "TUTOR").contains(roleNormalizado)) {
            throw new RuntimeException("Rol inválido: " + role + ". Use: PRESIDENTE, VOCAL_1, VOCAL_2, TUTOR");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("Usuario no autenticado");
        }
        String email = auth.getName();
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin) {
            if (roleNormalizado.equals("TUTOR")) {
                Optional<ec.edu.uteq.presustentaciones.entities.Tutor> tutorOpt = tutorRepository.findBySubmissionId(minutes.getSubmission().getId());
                if (tutorOpt.isEmpty() || tutorOpt.get().getTeacher() == null || !tutorOpt.get().getTeacher().getAppUser().getEmail().equals(email)) {
                     throw new RuntimeException("No eres el tutor de esta solicitud");
                }
            } else {
                List<Panelist> panelists = panelistRepository.findBySubmissionId(minutes.getSubmission().getId());
                boolean esPanelistRole = panelists.stream().anyMatch(j -> j.getRole().equals(roleNormalizado) && j.getTeacher() != null && j.getTeacher().getAppUser().getEmail().equals(email));
                if (!esPanelistRole) {
                     throw new RuntimeException("No eres el " + roleNormalizado + " de esta solicitud");
                }
            }
        }

        // Persiste la firma vía sp_sign_minutes_digital (Fase 3 / Criterio P1) -- el
        // procedimiento es la fuente de verdad de firmada_*/fecha_firma_*/observaciones_minutes;
        // se refresca la entidad para que el resto del método (incluida observaciones_minutes,
        // que antes de esta fase no se escribía desde Java) vea lo que el SP realmente
        // persistió, en vez de sobreescribirlo con el save() final de abajo.
        minutesRepository.signMinutesDigital(minutesId, roleNormalizado, observation);
        entityManager.refresh(minutes);

        // firmada_*/fecha_firma_*/observaciones_minutes ya quedaron persistidos y reflejados
        // en memoria por el refresh() de arriba; solo falta recalculate el flag agregado.
        minutes.updateStatusSignature();

        try {
            Long studentAppUserId = minutes.getSubmission().getStudent().getAppUser().getId();
            notificationService.createNotification(studentAppUserId,
                    String.format("El %s firmó el acta de tu pre-sustentación.", roleNormalizado));
        } catch (Exception e) {
            log.warn("No se pudo notificar la firma del acta {} (rol {}): {}", minutesId, roleNormalizado, e.getMessage());
        }

        // Si el minutes quedó completamente firmada, change estado a COMPLETADA y regenerate PDF
        if (minutes.isFirmada()) {
            Submission submission = minutes.getSubmission();

            // El minutes pasa a FINALIZADA con la última firma (si no lo estaba ya). Queda en el history.
            if (minutes.getStatus() == null || !"FINALIZADA".equals(minutes.getStatus().getCode())) {
                StatusMinutes anterior = minutes.getStatus();
                StatusMinutes finalizada = statusMinutesRepository.findByCode("FINALIZADA")
                        .orElseThrow(() -> new RuntimeException("Catálogo estados_acta sin 'FINALIZADA' (revisar migración V19)"));
                minutes.setStatus(finalizada);
                registerHistory(minutes, anterior, finalizada, "FIRMA_COMPLETA",
                        "Acta finalizada automáticamente: firmada por presidente, ambos vocales y tutor");
            }

            ec.edu.uteq.presustentaciones.entities.StatusSubmission statusCompletada = statusSubmissionRepository.findByCode("COMPLETADA")
                    .orElseGet(() -> statusSubmissionRepository.save(ec.edu.uteq.presustentaciones.entities.StatusSubmission.builder()
                            .code("COMPLETADA").nombre("Completada").build()));
            submission.setStatus(statusCompletada);
            submissionRepository.save(submission);
            log.info("Solicitud {} completada - todas las firmas del acta han sido aplicadas", submission.getId());

            try {
                notificationService.createNotification(submission.getStudent().getAppUser().getId(),
                        "¡Tu acta de pre-sustentación fue firmada por todo el tribunal! El proceso ha finalizado.");
            } catch (Exception e) {
                log.warn("No se pudo notificar la finalización del acta {}: {}", minutesId, e.getMessage());
            }

            if (minutes.getFilePdf() != null) {
                Optional<EvaluationFinal> evalOpt = evaluationRepository.findBySubmissionId(submission.getId());
                List<Panelist> panelists = panelistRepository.findBySubmissionId(submission.getId());
                String rutaComplete = minutesDir + "/" + minutes.getFilePdf();
                generatePdf(rutaComplete, submission, evalOpt.orElse(null), panelists, minutes);
            }
        }

        return minutesRepository.save(minutes);
    }

    /**
     * @param minutesId id del minutes
     * @return los bytes del PDF generado para esa minutes
     * @throws RuntimeException si el minutes no existe, el appUser no tiene acceso a ella, o
     *                          todavía no tiene PDF generado
     */
    @Override
    public byte[] obtainPdfBytes(Long minutesId) {
        Minutes minutes = minutesRepository.findById(minutesId)
                .orElseThrow(() -> new RuntimeException("Acta no encontrada"));
        validateAccess(minutes);
        if (minutes.getFilePdf() == null) {
            throw new RuntimeException("El acta no tiene PDF generado aún.");
        }
        Path path = Paths.get(minutesDir, minutes.getFilePdf());
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo leer el PDF: " + e.getMessage());
        }
    }

    /**
     * @param pageable configuración de paginación
     * @return página de todas las minutes del sistema
     */
    @Override
    public Page<Minutes> listMinutes(Pageable pageable) {
        return minutesRepository.findAll(pageable);
    }

    /**
     * @param submissionId id de la submission
     * @return el minutes de esa submission, si ya fue generada
     */
    @Override
    public Optional<Minutes> searchBySubmission(Long submissionId) {
        Optional<Minutes> minutes = minutesRepository.findBySubmissionId(submissionId);
        minutes.ifPresent(this::validateAccess);
        return minutes;
    }

    // ── Módulo 2: gestión e history de minutes ───────────────────────────────

    /**
     * "Mis actas" del teacher: minutes de las pre-sustentaciones en las que es tutor o panelist.
     *
     * @param email    email del appUser autenticado
     * @param pageable configuración de paginación
     * @return página de resúmenes de minutes correspondientes a ese teacher
     */
    @Override
    @Transactional(readOnly = true)
    public Page<MinutesSummaryDTO> listMyMinutes(String email, Pageable pageable) {
        return minutesRepository.findMyMinutes(email, pageable).map(MinutesSummaryDTO::from);
    }

    /**
     * Búsqueda/filtrado administrativo de minutes. Parámetros nulos/vacíos no filtran.
     *
     * @param status   código de estado del minutes a filtrar, o {@code null}/vacío
     * @param program  program de la submission a filtrar, o {@code null}/vacío
     * @param from    fecha mínima de generación, o {@code null} para no acotar
     * @param to    fecha máxima de generación, o {@code null} para no acotar
     * @param q        texto libre de búsqueda, o {@code null}/vacío
     * @param pageable configuración de paginación
     * @return página de resúmenes de minutes que cumplen los filtros
     */
    @Override
    @Transactional(readOnly = true)
    public Page<MinutesSummaryDTO> searchMinutes(String status, String program, LocalDate from, LocalDate to,
                                            String q, Pageable pageable) {
        // Postgres no puede inferir el tipo de un parámetro de fecha que llega null dentro de
        // "(:desde IS NULL OR ...)" -> se sustituye por un rango abierto (mismo enfoque que
        // SubmissionRepository.searchConFiltros).
        LocalDate fromSeguro = from != null ? from : LocalDate.of(1900, 1, 1);
        LocalDate toSeguro = to != null ? to : LocalDate.of(9999, 12, 31);
        return minutesRepository.searchWithFiltros(clean(status), clean(program), fromSeguro, toSeguro, clean(q), pageable)
                .map(MinutesSummaryDTO::from);
    }

    /**
     * Detalle de un minutes. Aplica control de acceso: ADMIN/COORDINADOR (permission ACTAS_VER),
     * o el student dueño / panelist / tutor de la submission.
     *
     * @param minutesId id del minutes
     * @return el detalle del minutes junto con su tribunal
     * @throws RuntimeException si el minutes no existe, o el appUser no participa en ella
     *                          (previene IDOR/BOLA)
     */
    @Override
    @Transactional(readOnly = true)
    public MinutesDetailDTO obtainDetail(Long minutesId) {
        Minutes minutes = minutesRepository.findDetailById(minutesId)
                .orElseThrow(() -> new RuntimeException("Acta no encontrada: " + minutesId));
        validateAccess(minutes); // ADMIN/COORDINADOR o participante (student/panelist/tutor) -- previene IDOR
        List<Panelist> panelists = panelistRepository.findBySubmissionId(minutes.getSubmission().getId());
        return MinutesDetailDTO.from(minutes, panelists);
    }

    /**
     * History de trazabilidad (timeline) del minutes, más reciente primero. Mismo control
     * de acceso que {@link #obtainDetail(Long)}.
     *
     * @param minutesId id del minutes
     * @return los cambios de estado del minutes, del más reciente al más antiguo
     * @throws RuntimeException si el minutes no existe, o el appUser no participa en ella
     */
    @Override
    @Transactional(readOnly = true)
    public List<HistoryMinutesDTO> obtainHistory(Long minutesId) {
        Minutes minutes = minutesRepository.findDetailById(minutesId)
                .orElseThrow(() -> new RuntimeException("Acta no encontrada: " + minutesId));
        validateAccess(minutes); // mismo control de acceso que el detalle
        return historyStatusMinutesRepository.findByMinutesIdOrderByDateCambioDesc(minutesId).stream()
                .map(HistoryMinutesDTO::from)
                .toList();
    }

    /**
     * Cambia el estado del minutes (GENERADA -> REVISADA -> FINALIZADA, u OBSERVADA/ANULADA)
     * validando la transición y registrando el cambio en history_estados_minutes con el
     * appUser, su role, el estado anterior/nuevo y el motivo.
     *
     * @param minutesId            id del minutes
     * @param targetStatusCode código del catálogo estados_minutes
     * @param motivo            motivo/observación (obligatorio para OBSERVADA y ANULADA)
     * @return el minutes con el nuevo estado aplicado
     * @throws RuntimeException si el minutes no existe, el estado no es válido, la transición no
     *                          está permitida, o falta el motivo cuando es obligatorio
     */
    @Override
    @Transactional
    public Minutes changeStatus(Long minutesId, String targetStatusCode, String motivo) {
        auditService.markActorActual();
        if (targetStatusCode == null || targetStatusCode.isBlank()) {
            throw new RuntimeException("Debe indicar el nuevo estado del acta");
        }
        String destino = targetStatusCode.trim().toUpperCase();

        Minutes minutes = minutesRepository.findDetailById(minutesId)
                .orElseThrow(() -> new RuntimeException("Acta no encontrada: " + minutesId));

        StatusMinutes actual = minutes.getStatus();
        String source = actual != null ? actual.getCode() : "GENERADA";
        if (source.equals(destino)) {
            throw new RuntimeException("El acta ya está en estado " + destino);
        }

        StatusMinutes statusDestino = statusMinutesRepository.findByCode(destino)
                .orElseThrow(() -> new RuntimeException("Estado de acta inválido: " + targetStatusCode
                        + ". Válidos: GENERADA, REVISADA, OBSERVADA, FINALIZADA, ANULADA"));

        boolean isAdmin = isCurrentAdmin();
        Set<String> permitidas = TRANSICIONES.getOrDefault(source, Set.of());
        // El ADMIN puede anular en cualquier momento (gestión completa); el resto sigue el flujo.
        if (!permitidas.contains(destino) && !(isAdmin && "ANULADA".equals(destino))) {
            throw new RuntimeException("Transición de estado no permitida: " + source + " -> " + destino
                    + ". Desde " + source + " solo se puede pasar a " + permitidas);
        }
        if (ESTADOS_QUE_EXIGEN_MOTIVO.contains(destino) && (motivo == null || motivo.isBlank())) {
            throw new RuntimeException("Debe indicar un motivo para pasar el acta a " + destino);
        }

        minutes.setStatus(statusDestino);
        if (motivo != null && !motivo.isBlank()) {
            minutes.setObservationsMinutes(motivo.trim());
        }
        Minutes guardada = minutesRepository.save(minutes);
        registerHistory(guardada, actual, statusDestino, "CAMBIO_ESTADO",
                motivo != null && !motivo.isBlank() ? motivo.trim() : null);

        try {
            Long studentAppUserId = minutes.getSubmission().getStudent().getAppUser().getId();
            notificationService.createNotification(studentAppUserId,
                    String.format("El acta de tu pre-sustentación cambió de estado: %s -> %s", source, destino));
        } catch (Exception e) {
            log.warn("No se pudo notificar el cambio de estado del acta {}: {}", minutesId, e.getMessage());
        }
        return guardada;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static String clean(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /** Escribe una fila en history_estados_minutes con el appUser autenticado y su role. */
    private void registerHistory(Minutes minutes, StatusMinutes anterior, StatusMinutes target, String accion, String comment) {
        AppUser autor = null;
        String role = null;
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(String.valueOf(auth.getPrincipal()))) {
                autor = appUserRepository.findByEmail(auth.getName()).orElse(null);
                if (autor != null) {
                    role = autor.getRoleAppUser() != null ? autor.getRoleAppUser().getCode() : autor.getRole();
                }
            }
        } catch (Exception e) {
            log.warn("No se pudo resolver el autor del historial del acta {}: {}", minutes.getId(), e.getMessage());
        }
        historyStatusMinutesRepository.save(HistoryStatusMinutes.builder()
                .minutes(minutes)
                .statusAnterior(anterior)
                .statusNew(target)
                .appUser(autor)
                .roleAppUser(role)
                .accion(accion)
                .comment(comment)
                .dateCambio(LocalDateTime.now())
                .build());
    }

    private boolean isCurrentAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))
                || (auth != null && permissionService.hasPermission(auth, "ACTAS_GESTIONAR"));
    }

    // ── Generación PDF ────────────────────────────────────────────────────────

    private void generatePdf(String ruta, Submission submission, EvaluationFinal evaluation, List<Panelist> panelists) {
        generatePdf(ruta, submission, evaluation, panelists, null);
    }

    private void generatePdf(String ruta, Submission submission, EvaluationFinal evaluation,
                             List<Panelist> panelists, Minutes minutes) {
        try {
            PdfFont fontRegular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont fontBold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

            PdfWriter writer     = new PdfWriter(ruta);
            PdfDocument pdfDoc   = new PdfDocument(writer);
            Document document    = new Document(pdfDoc);
            document.setMargins(40, 50, 40, 50);

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            DateTimeFormatter fmtDt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

            // ── Encabezado ────────────────────────────────────────────────────
            Table header = new Table(UnitValue.createPercentArray(new float[]{20f, 60f, 20f}))
                    .setWidth(UnitValue.createPercentValue(100));

            // Logo placeholder (azul UTEQ)
            Cell logoCell = new Cell()
                    .add(new Paragraph("UTEQ").setFont(fontBold).setFontSize(18)
                            .setFontColor(ColorConstants.WHITE).setTextAlignment(TextAlignment.CENTER))
                    .setBackgroundColor(UTEQ_BLUE)
                    .setBorder(Border.NO_BORDER)
                    .setPadding(15);
            header.addCell(logoCell);

            // Título central
            Cell titleCell = new Cell()
                    .add(new Paragraph("ACTA DE PRE-SUSTENTACIÓN")
                            .setFont(fontBold).setFontSize(14).setFontColor(UTEQ_BLUE)
                            .setTextAlignment(TextAlignment.CENTER))
                    .add(new Paragraph("Universidad Técnica Estatal de Quevedo")
                            .setFont(fontRegular).setFontSize(9).setFontColor(ColorConstants.DARK_GRAY)
                            .setTextAlignment(TextAlignment.CENTER))
                    .add(new Paragraph("Facultad de Ciencias de la Computación y Diseño Digital")
                            .setFont(fontRegular).setFontSize(8).setFontColor(ColorConstants.DARK_GRAY)
                            .setTextAlignment(TextAlignment.CENTER))
                    .setBorder(Border.NO_BORDER).setPadding(10);
            header.addCell(titleCell);

            // Número de minutes
            Cell numCell = new Cell()
                    .add(new Paragraph("No. " + submission.getId())
                            .setFont(fontBold).setFontSize(12).setFontColor(UTEQ_GOLD)
                            .setTextAlignment(TextAlignment.CENTER))
                    .add(new Paragraph(LocalDate.now().format(fmt))
                            .setFont(fontRegular).setFontSize(9)
                            .setTextAlignment(TextAlignment.CENTER))
                    .setBackgroundColor(LIGHT_GRAY).setBorder(Border.NO_BORDER).setPadding(10);
            header.addCell(numCell);
            document.add(header);

            // Línea separadora dorada
            document.add(new LineSeparator(new com.itextpdf.kernel.pdf.canvas.draw.SolidLine(3f))
                    .setStrokeColor(UTEQ_GOLD));
            document.add(new Paragraph("\n").setMargin(2));

            // ── Datos del student ──────────────────────────────────────────
            document.add(sectionTitle("1. DATOS DEL ESTUDIANTE", fontBold));
            Table dataStudent = new Table(UnitValue.createPercentArray(new float[]{30f, 70f}))
                    .setWidth(UnitValue.createPercentValue(100)).setMarginBottom(10);
            String nombreEst = submission.getStudent() != null && submission.getStudent().getAppUser() != null
                    ? submission.getStudent().getAppUser().getNombre() + " "
                      + submission.getStudent().getAppUser().getApellido()
                    : "—";
            addRow(dataStudent, "Estudiante:", nombreEst, fontBold, fontRegular);
            addRow(dataStudent, "Carrera:", submission.getStudent() != null
                    ? nvl(submission.getStudent().getProgram()) : "—", fontBold, fontRegular);
            addRow(dataStudent, "Título del tema:", nvl(submission.getTituloTopic()), fontBold, fontRegular);
            addRow(dataStudent, "Modalidad:", submission.getModalityDegree() != null ? nvl(submission.getModalityDegree().getNombre()) : "—", fontBold, fontRegular);
            addRow(dataStudent, "Fecha de solicitud:",
                    submission.getDateRecord() != null ? submission.getDateRecord().format(fmtDt) : "—",
                    fontBold, fontRegular);
            document.add(dataStudent);

            // ── Tribunal ───────────────────────────────────────────────────────
            document.add(sectionTitle("2. TRIBUNAL EVALUADOR", fontBold));
            Table panel = new Table(UnitValue.createPercentArray(new float[]{40f, 40f, 20f}))
                    .setWidth(UnitValue.createPercentValue(100)).setMarginBottom(10);
            addHeaderRow(panel, new String[]{"Docente", "Rol", "Confirmado"}, fontBold);
            if (panelists.isEmpty()) {
                Cell noPanelists = new Cell(1, 3)
                        .add(new Paragraph("No hay jurados asignados").setFont(fontRegular).setFontSize(9))
                        .setTextAlignment(TextAlignment.CENTER).setPadding(8).setBackgroundColor(LIGHT_GRAY);
                panel.addCell(noPanelists);
            } else {
                for (Panelist j : panelists) {
                    String docNombre = j.getTeacher() != null && j.getTeacher().getAppUser() != null
                            ? j.getTeacher().getAppUser().getNombre() + " " + j.getTeacher().getAppUser().getApellido()
                            : "—";
                    panel.addCell(dataCell(docNombre, fontRegular));
                    panel.addCell(dataCell(j.getRole(), fontRegular));
                    panel.addCell(dataCell(j.isConfirmado() ? "✓" : "Pendiente", fontRegular));
                }
            }
            document.add(panel);

            // ── Evaluación y calificación ────────────────────────────────────
            document.add(sectionTitle("3. EVALUACIÓN Y CALIFICACIÓN", fontBold));
            if (evaluation != null) {
                Table evalTable = new Table(UnitValue.createPercentArray(new float[]{50f, 25f, 25f}))
                        .setWidth(UnitValue.createPercentValue(100)).setMarginBottom(10);
                addHeaderRow(evalTable, new String[]{"Concepto", "Peso (%)", "Nota"}, fontBold);
                evalTable.addCell(dataCell("Instructor del curso (Titulación II)", fontRegular));
                evalTable.addCell(dataCell(String.format("%.0f%%", (evaluation.getPesoInstructor() != null ? evaluation.getPesoInstructor() : 0.6) * 100.0), fontRegular));
                evalTable.addCell(dataCell(evaluation.getGradeInstructor() != null
                        ? String.format("%.2f", evaluation.getGradeInstructor()) : "—", fontRegular));
 
                evalTable.addCell(dataCell("Tribunal evaluador", fontRegular));
                evalTable.addCell(dataCell(String.format("%.0f%%", (evaluation.getPesoPanelist() != null ? evaluation.getPesoPanelist() : 0.4) * 100.0), fontRegular));
                evalTable.addCell(dataCell(evaluation.getGradePanelistAverage() != null
                        ? String.format("%.2f", evaluation.getGradePanelistAverage()) : "—", fontRegular));
 
                // Fila de total
                Cell totalLabel = new Cell().add(new Paragraph("NOTA FINAL").setFont(fontBold).setFontSize(10))
                        .setBackgroundColor(UTEQ_BLUE).setFontColor(ColorConstants.WHITE)
                        .setPadding(6).setBorder(Border.NO_BORDER);
                Cell totalPeso = new Cell().add(new Paragraph("100%").setFont(fontBold).setFontSize(10)
                        .setFontColor(ColorConstants.WHITE))
                        .setBackgroundColor(UTEQ_BLUE).setPadding(6).setBorder(Border.NO_BORDER);
                Cell totalGrade = new Cell().add(new Paragraph(evaluation.getGradeFinal() != null
                        ? String.format("%.2f / 10", evaluation.getGradeFinal()) : "—")
                        .setFont(fontBold).setFontSize(10).setFontColor(UTEQ_GOLD))
                        .setBackgroundColor(UTEQ_BLUE).setPadding(6).setBorder(Border.NO_BORDER);
                evalTable.addCell(totalLabel);
                evalTable.addCell(totalPeso);
                evalTable.addCell(totalGrade);
                document.add(evalTable);
 
                // Resultado
                String result = evaluation.getResult() != null ? nvl(evaluation.getResult().getNombre()) : "—";
                String resultCode = evaluation.getResult() != null ? evaluation.getResult().getCode() : "";
                DeviceRgb resultColor = "APROBADO".equals(resultCode)
                        ? new DeviceRgb(0, 128, 0) : new DeviceRgb(180, 0, 0);
                document.add(new Paragraph("RESULTADO: " + result)
                        .setFont(fontBold).setFontSize(14).setFontColor(resultColor)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setBorder(new SolidBorder(resultColor, 2)).setPadding(8).setMarginBottom(10));

                if (evaluation.getObservations() != null && !evaluation.getObservations().isBlank()) {
                    document.add(sectionTitle("Observaciones del tribunal:", fontBold));
                    document.add(new Paragraph(evaluation.getObservations())
                            .setFont(fontRegular).setFontSize(9).setBackgroundColor(LIGHT_GRAY)
                            .setPadding(8).setMarginBottom(10));
                }
            } else {
                document.add(new Paragraph("Evaluación pendiente de registro.")
                        .setFont(fontRegular).setFontSize(9).setFontColor(ColorConstants.GRAY));
            }

            // ── Firmas ────────────────────────────────────────────────────────
            document.add(sectionTitle("4. FIRMAS ELECTRÓNICAS", fontBold));
            Table signaturesTable = new Table(UnitValue.createPercentArray(new float[]{25f, 25f, 25f, 25f}))
                    .setWidth(UnitValue.createPercentValue(100)).setMarginBottom(15);

            String[] rolesLabel = {"Presidente", "Vocal 1", "Vocal 2", "Tutor"};
            boolean[] firmados  = {
                minutes != null && minutes.isFirmadaPresidente(),
                minutes != null && minutes.isFirmadaVocal1(),
                minutes != null && minutes.isFirmadaVocal2(),
                minutes != null && minutes.isFirmadaTutor()
            };
            LocalDateTime[] fechasSignature = {
                minutes != null ? minutes.getDateSignaturePresidente() : null,
                minutes != null ? minutes.getDateSignatureVocal1() : null,
                minutes != null ? minutes.getDateSignatureVocal2() : null,
                minutes != null ? minutes.getDateSignatureTutor() : null
            };

            for (int i = 0; i < 4; i++) {
                boolean firmado = firmados[i];
                Cell signatureCell = new Cell()
                        .add(new Paragraph(rolesLabel[i]).setFont(fontBold).setFontSize(9)
                                .setTextAlignment(TextAlignment.CENTER))
                        .add(new Paragraph(firmado ? "✓ FIRMADO" : "PENDIENTE")
                                .setFont(fontBold).setFontSize(10)
                                .setFontColor(firmado ? new DeviceRgb(0, 128, 0) : new DeviceRgb(150, 150, 150))
                                .setTextAlignment(TextAlignment.CENTER))
                        .add(new Paragraph(firmado && fechasSignature[i] != null
                                ? fechasSignature[i].format(fmtDt) : " ")
                                .setFont(fontRegular).setFontSize(7)
                                .setTextAlignment(TextAlignment.CENTER))
                        .setBackgroundColor(firmado ? new DeviceRgb(230, 255, 230) : LIGHT_GRAY)
                        .setBorder(new SolidBorder(firmado ? new DeviceRgb(0, 128, 0) : MEDIUM_GRAY, 1))
                        .setPadding(10).setMargin(3);
                signaturesTable.addCell(signatureCell);
            }
            document.add(signaturesTable);

            // ── Pie de página ─────────────────────────────────────────────────
            document.add(new LineSeparator(new com.itextpdf.kernel.pdf.canvas.draw.SolidLine(1f))
                    .setStrokeColor(UTEQ_GOLD));
            document.add(new Paragraph("Generado el " + LocalDateTime.now().format(fmtDt)
                    + " | Sistema de Gestión de Pre-Sustentaciones UTEQ | Documento oficial")
                    .setFont(fontRegular).setFontSize(7).setFontColor(ColorConstants.GRAY)
                    .setTextAlignment(TextAlignment.CENTER));

            document.close();

        } catch (IOException e) {
            throw new RuntimeException("Error generando PDF: " + e.getMessage(), e);
        }
    }

    // ── Helpers de construcción de tablas ─────────────────────────────────────

    private Paragraph sectionTitle(String text, PdfFont fontBold) {
        return new Paragraph(text).setFont(fontBold).setFontSize(10)
                .setFontColor(UTEQ_BLUE)
                .setBorderBottom(new SolidBorder(UTEQ_GOLD, 1.5f))
                .setMarginTop(8).setMarginBottom(4);
    }

    private void addRow(Table table, String label, String value, PdfFont fontBold, PdfFont fontRegular) {
        table.addCell(new Cell()
                .add(new Paragraph(label).setFont(fontBold).setFontSize(9))
                .setBackgroundColor(LIGHT_GRAY).setBorder(Border.NO_BORDER).setPadding(5));
        table.addCell(new Cell()
                .add(new Paragraph(value).setFont(fontRegular).setFontSize(9))
                .setBorder(Border.NO_BORDER).setPadding(5));
    }

    private void addHeaderRow(Table table, String[] headers, PdfFont fontBold) {
        for (String h : headers) {
            table.addCell(new Cell()
                    .add(new Paragraph(h).setFont(fontBold).setFontSize(9).setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(UTEQ_BLUE).setPadding(6).setBorder(Border.NO_BORDER));
        }
    }

    private Cell dataCell(String text, PdfFont font) {
        return new Cell()
                .add(new Paragraph(text).setFont(font).setFontSize(9))
                .setPadding(5)
                .setBorderBottom(new SolidBorder(MEDIUM_GRAY, 0.5f))
                .setBorderTop(Border.NO_BORDER).setBorderLeft(Border.NO_BORDER).setBorderRight(Border.NO_BORDER);
    }

    private String nvl(String s) {
        return s != null ? s : "—";
    }

    /**
     * Elimina un minutes si el appUser tiene permission, incluido su archivo PDF en disco si existe.
     *
     * @param minutesId id del minutes
     * @throws RuntimeException si el minutes no existe, o el appUser no tiene acceso a ella
     */
    @Override
    public void deleteMinutes(Long minutesId) {
        Minutes minutes = minutesRepository.findById(minutesId)
                .orElseThrow(() -> new RuntimeException("Acta no encontrada: " + minutesId));
        // Validate que el appUser tenga permissions (ya sea admin, student dueño, panelist o tutor)
        validateAccess(minutes);
        
        // Si hay un archivo físico, intentar eliminarlo
        if (minutes.getFilePdf() != null) {
            Path path = Paths.get(minutesDir, minutes.getFilePdf());
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                log.warn("No se pudo eliminar el archivo físico del acta {}: {}", minutesId, e.getMessage());
            }
        }
        
        minutesRepository.delete(minutes);
    }
}
