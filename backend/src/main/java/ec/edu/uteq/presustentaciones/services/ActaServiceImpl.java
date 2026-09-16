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
import ec.edu.uteq.presustentaciones.entities.Acta;
import ec.edu.uteq.presustentaciones.entities.EstadoActa;
import ec.edu.uteq.presustentaciones.entities.EvaluacionFinal;
import ec.edu.uteq.presustentaciones.entities.HistorialEstadoActa;
import ec.edu.uteq.presustentaciones.entities.Jurado;
import ec.edu.uteq.presustentaciones.entities.Solicitud;
import ec.edu.uteq.presustentaciones.entities.Usuario;
import ec.edu.uteq.presustentaciones.dto.ActaDetalleDTO;
import ec.edu.uteq.presustentaciones.dto.ActaResumenDTO;
import ec.edu.uteq.presustentaciones.dto.HistorialActaDTO;
import ec.edu.uteq.presustentaciones.repositories.ActaRepository;
import ec.edu.uteq.presustentaciones.repositories.EstadoActaRepository;
import ec.edu.uteq.presustentaciones.repositories.EvaluacionFinalRepository;
import ec.edu.uteq.presustentaciones.repositories.HistorialEstadoActaRepository;
import ec.edu.uteq.presustentaciones.repositories.JuradoRepository;
import ec.edu.uteq.presustentaciones.repositories.SolicitudRepository;
import ec.edu.uteq.presustentaciones.repositories.TutorRepository;
import ec.edu.uteq.presustentaciones.repositories.UsuarioRepository;
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
public class ActaServiceImpl implements ActaService {

    private final ActaRepository actaRepository;
    private final SolicitudRepository solicitudRepository;
    private final EvaluacionFinalRepository evaluacionRepository;
    private final JuradoRepository juradoRepository;
    private final ec.edu.uteq.presustentaciones.repositories.EstadoSolicitudRepository estadoSolicitudRepository;
    private final jakarta.persistence.EntityManager entityManager;
    private final NotificacionService notificacionService;
    private final AuditoriaService auditoriaService;
    private final TutorRepository tutorRepository;
    private final EstadoActaRepository estadoActaRepository;
    private final HistorialEstadoActaRepository historialEstadoActaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PermisoService permisoService;

    // Flujo de estados del acta. ADMIN puede saltarse estas reglas para ANULAR.
    private static final Map<String, Set<String>> TRANSICIONES = Map.of(
            "GENERADA",   Set.of("REVISADA", "OBSERVADA", "ANULADA"),
            "REVISADA",   Set.of("FINALIZADA", "OBSERVADA", "ANULADA"),
            "OBSERVADA",  Set.of("REVISADA", "GENERADA", "ANULADA"),
            "FINALIZADA", Set.of("ANULADA"),
            "ANULADA",    Set.of());
    private static final Set<String> ESTADOS_QUE_EXIGEN_MOTIVO = Set.of("OBSERVADA", "ANULADA");

    @Value("${app.actas.dir:uploads/actas}")
    private String actasDir;

    // ── Colores institucionales UTEQ ─────────────────────────────────────────
    private static final DeviceRgb UTEQ_BLUE    = new DeviceRgb(0, 56, 101);
    private static final DeviceRgb UTEQ_GOLD    = new DeviceRgb(204, 153, 0);
    private static final DeviceRgb LIGHT_GRAY   = new DeviceRgb(245, 245, 245);
    private static final DeviceRgb MEDIUM_GRAY  = new DeviceRgb(200, 200, 200);

    private void validarAcceso(Acta acta) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("Usuario no autenticado");
        }
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) return;

        // ADMIN / COORDINADOR: acceso completo de lectura vía el sistema de permisos dinámico
        // (mismo mecanismo que @permisoService.tienePermiso en los controllers). Un DOCENTE
        // NO tiene ACTAS_VER, así que cae a la comprobación de propiedad de abajo.
        if (permisoService.tienePermiso(auth, "ACTAS_VER")
                || permisoService.tienePermiso(auth, "ACTAS_GESTIONAR")) {
            return;
        }

        String email = auth.getName();
        if (acta.getSolicitud().getEstudiante() != null &&
            acta.getSolicitud().getEstudiante().getUsuario().getEmail().equals(email)) {
            return;
        }

        List<Jurado> jurados = juradoRepository.findBySolicitudId(acta.getSolicitud().getId());
        boolean esJurado = jurados.stream().anyMatch(j -> j.getDocente() != null && j.getDocente().getUsuario().getEmail().equals(email));
        if (esJurado) return;

        Optional<ec.edu.uteq.presustentaciones.entities.Tutor> tutorOpt = tutorRepository.findBySolicitudId(acta.getSolicitud().getId());
        if (tutorOpt.isPresent() && tutorOpt.get().getDocente() != null && tutorOpt.get().getDocente().getUsuario().getEmail().equals(email)) {
            return;
        }

        throw new RuntimeException("No tienes permiso para acceder a esta acta");
    }

    /**
     * RF-11: Genera el acta y crea el PDF real en disco.
     *
     * @param solicitudId id de la solicitud a la que pertenece el acta
     * @return el acta existente si ya se había generado, o la recién creada (con su PDF)
     * @throws RuntimeException si la solicitud no existe, no tiene evaluación final, o no se
     *                          pudo crear el directorio de actas en disco
     */
    @Override
    @Transactional
    public Acta generarActa(Long solicitudId) {
        auditoriaService.marcarActorActual();
        Solicitud solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada: " + solicitudId));

        // Buscar evaluación y jurados
        Optional<EvaluacionFinal> evalOpt = evaluacionRepository.findBySolicitudId(solicitudId);
        if (evalOpt.isEmpty()) {
            throw new RuntimeException("No se puede generar el acta sin una evaluación final");
        }
        List<Jurado> jurados = juradoRepository.findBySolicitudId(solicitudId);

        // Si ya existe el acta, retornar la misma
        Optional<Acta> existente = actaRepository.findBySolicitudId(solicitudId);
        if (existente.isPresent()) {
            return existente.get();
        }

        String fileName = "acta_" + solicitudId + "_" + System.currentTimeMillis() + ".pdf";
        EstadoActa estadoGenerada = estadoActaRepository.findByCodigo("GENERADA")
                .orElseThrow(() -> new RuntimeException("Catálogo estados_acta sin 'GENERADA' (revisar migración V19)"));
        Acta acta = Acta.builder()
                .solicitud(solicitud)
                .archivoPdf(fileName)
                .fechaGeneracion(LocalDate.now())
                .estado(estadoGenerada)
                .build();

        // Crear directorio de subida si no existe
        try {
            Files.createDirectories(Paths.get(actasDir));
        } catch (IOException e) {
            throw new RuntimeException("No se pudo crear el directorio de actas: " + e.getMessage());
        }

        String rutaCompleta = actasDir + "/" + fileName;
        generarPdf(rutaCompleta, solicitud, evalOpt.orElse(null), jurados, acta);

        Acta guardada = actaRepository.save(acta);
        registrarHistorial(guardada, null, estadoGenerada, "CREAR", "Acta generada a partir de la evaluación final");
        return guardada;
    }

    /**
     * RF-08: Firma el acta por un actor específico (PRESIDENTE, VOCAL_1, VOCAL_2, TUTOR).
     *
     * @param actaId      id del acta a firmar
     * @param rol         rol que firma ({@code PRESIDENTE}, {@code VOCAL_1}, {@code VOCAL_2}
     *                    o {@code TUTOR}); no distingue mayúsculas/minúsculas
     * @param observacion observación opcional del firmante, o {@code null}
     * @return el acta actualizada; si con esta firma quedan las 4 completas, la solicitud pasa
     *         a "COMPLETADA" y el PDF se regenera con el estado final de las firmas
     * @throws RuntimeException si el acta no existe, {@code rol} no es uno de los 4 válidos, el
     *                          usuario no está autenticado, o no tiene permiso para firmar ese rol
     */
    @Override
    @Transactional
    public Acta firmarActa(Long actaId, String rol, String observacion) {
        auditoriaService.marcarActorActual();
        Acta acta = actaRepository.findById(actaId)
                .orElseThrow(() -> new RuntimeException("Acta no encontrada: " + actaId));

        String rolNormalizado = rol.toUpperCase();
        if (!java.util.Set.of("PRESIDENTE", "VOCAL_1", "VOCAL_2", "TUTOR").contains(rolNormalizado)) {
            throw new RuntimeException("Rol inválido: " + rol + ". Use: PRESIDENTE, VOCAL_1, VOCAL_2, TUTOR");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("Usuario no autenticado");
        }
        String email = auth.getName();
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin) {
            if (rolNormalizado.equals("TUTOR")) {
                Optional<ec.edu.uteq.presustentaciones.entities.Tutor> tutorOpt = tutorRepository.findBySolicitudId(acta.getSolicitud().getId());
                if (tutorOpt.isEmpty() || tutorOpt.get().getDocente() == null || !tutorOpt.get().getDocente().getUsuario().getEmail().equals(email)) {
                     throw new RuntimeException("No eres el tutor de esta solicitud");
                }
            } else {
                List<Jurado> jurados = juradoRepository.findBySolicitudId(acta.getSolicitud().getId());
                boolean esJuradoRol = jurados.stream().anyMatch(j -> j.getRol().equals(rolNormalizado) && j.getDocente() != null && j.getDocente().getUsuario().getEmail().equals(email));
                if (!esJuradoRol) {
                     throw new RuntimeException("No eres el " + rolNormalizado + " de esta solicitud");
                }
            }
        }

        // Persiste la firma vía sp_firmar_acta_digital (Fase 3 / Criterio P1) -- el
        // procedimiento es la fuente de verdad de firmada_*/fecha_firma_*/observaciones_acta;
        // se refresca la entidad para que el resto del método (incluida observaciones_acta,
        // que antes de esta fase no se escribía desde Java) vea lo que el SP realmente
        // persistió, en vez de sobreescribirlo con el save() final de abajo.
        actaRepository.firmarActaDigital(actaId, rolNormalizado, observacion);
        entityManager.refresh(acta);

        // firmada_*/fecha_firma_*/observaciones_acta ya quedaron persistidos y reflejados
        // en memoria por el refresh() de arriba; solo falta recalcular el flag agregado.
        acta.actualizarEstadoFirma();

        try {
            Long estudianteUsuarioId = acta.getSolicitud().getEstudiante().getUsuario().getId();
            notificacionService.crearNotificacion(estudianteUsuarioId,
                    String.format("El %s firmó el acta de tu pre-sustentación.", rolNormalizado));
        } catch (Exception e) {
            log.warn("No se pudo notificar la firma del acta {} (rol {}): {}", actaId, rolNormalizado, e.getMessage());
        }

        // Si el acta quedó completamente firmada, cambiar estado a COMPLETADA y regenerar PDF
        if (acta.isFirmada()) {
            Solicitud solicitud = acta.getSolicitud();

            // El acta pasa a FINALIZADA con la última firma (si no lo estaba ya). Queda en el historial.
            if (acta.getEstado() == null || !"FINALIZADA".equals(acta.getEstado().getCodigo())) {
                EstadoActa anterior = acta.getEstado();
                EstadoActa finalizada = estadoActaRepository.findByCodigo("FINALIZADA")
                        .orElseThrow(() -> new RuntimeException("Catálogo estados_acta sin 'FINALIZADA' (revisar migración V19)"));
                acta.setEstado(finalizada);
                registrarHistorial(acta, anterior, finalizada, "FIRMA_COMPLETA",
                        "Acta finalizada automáticamente: firmada por presidente, ambos vocales y tutor");
            }

            ec.edu.uteq.presustentaciones.entities.EstadoSolicitud estadoCompletada = estadoSolicitudRepository.findByCodigo("COMPLETADA")
                    .orElseGet(() -> estadoSolicitudRepository.save(ec.edu.uteq.presustentaciones.entities.EstadoSolicitud.builder()
                            .codigo("COMPLETADA").nombre("Completada").build()));
            solicitud.setEstado(estadoCompletada);
            solicitudRepository.save(solicitud);
            log.info("Solicitud {} completada - todas las firmas del acta han sido aplicadas", solicitud.getId());

            try {
                notificacionService.crearNotificacion(solicitud.getEstudiante().getUsuario().getId(),
                        "¡Tu acta de pre-sustentación fue firmada por todo el tribunal! El proceso ha finalizado.");
            } catch (Exception e) {
                log.warn("No se pudo notificar la finalización del acta {}: {}", actaId, e.getMessage());
            }

            if (acta.getArchivoPdf() != null) {
                Optional<EvaluacionFinal> evalOpt = evaluacionRepository.findBySolicitudId(solicitud.getId());
                List<Jurado> jurados = juradoRepository.findBySolicitudId(solicitud.getId());
                String rutaCompleta = actasDir + "/" + acta.getArchivoPdf();
                generarPdf(rutaCompleta, solicitud, evalOpt.orElse(null), jurados, acta);
            }
        }

        return actaRepository.save(acta);
    }

    /**
     * @param actaId id del acta
     * @return los bytes del PDF generado para esa acta
     * @throws RuntimeException si el acta no existe, el usuario no tiene acceso a ella, o
     *                          todavía no tiene PDF generado
     */
    @Override
    public byte[] obtenerPdfBytes(Long actaId) {
        Acta acta = actaRepository.findById(actaId)
                .orElseThrow(() -> new RuntimeException("Acta no encontrada"));
        validarAcceso(acta);
        if (acta.getArchivoPdf() == null) {
            throw new RuntimeException("El acta no tiene PDF generado aún.");
        }
        Path path = Paths.get(actasDir, acta.getArchivoPdf());
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo leer el PDF: " + e.getMessage());
        }
    }

    /**
     * @param pageable configuración de paginación
     * @return página de todas las actas del sistema
     */
    @Override
    public Page<Acta> listarActas(Pageable pageable) {
        return actaRepository.findAll(pageable);
    }

    /**
     * @param solicitudId id de la solicitud
     * @return el acta de esa solicitud, si ya fue generada
     */
    @Override
    public Optional<Acta> buscarPorSolicitud(Long solicitudId) {
        Optional<Acta> acta = actaRepository.findBySolicitudId(solicitudId);
        acta.ifPresent(this::validarAcceso);
        return acta;
    }

    // ── Módulo 2: gestión e historial de actas ───────────────────────────────

    /**
     * "Mis actas" del docente: actas de las pre-sustentaciones en las que es tutor o jurado.
     *
     * @param email    email del usuario autenticado
     * @param pageable configuración de paginación
     * @return página de resúmenes de acta correspondientes a ese docente
     */
    @Override
    @Transactional(readOnly = true)
    public Page<ActaResumenDTO> listarMisActas(String email, Pageable pageable) {
        return actaRepository.findMisActas(email, pageable).map(ActaResumenDTO::de);
    }

    /**
     * Búsqueda/filtrado administrativo de actas. Parámetros nulos/vacíos no filtran.
     *
     * @param estado   código de estado del acta a filtrar, o {@code null}/vacío
     * @param carrera  carrera de la solicitud a filtrar, o {@code null}/vacío
     * @param desde    fecha mínima de generación, o {@code null} para no acotar
     * @param hasta    fecha máxima de generación, o {@code null} para no acotar
     * @param q        texto libre de búsqueda, o {@code null}/vacío
     * @param pageable configuración de paginación
     * @return página de resúmenes de acta que cumplen los filtros
     */
    @Override
    @Transactional(readOnly = true)
    public Page<ActaResumenDTO> buscarActas(String estado, String carrera, LocalDate desde, LocalDate hasta,
                                            String q, Pageable pageable) {
        // Postgres no puede inferir el tipo de un parámetro de fecha que llega null dentro de
        // "(:desde IS NULL OR ...)" -> se sustituye por un rango abierto (mismo enfoque que
        // SolicitudRepository.buscarConFiltros).
        LocalDate desdeSeguro = desde != null ? desde : LocalDate.of(1900, 1, 1);
        LocalDate hastaSeguro = hasta != null ? hasta : LocalDate.of(9999, 12, 31);
        return actaRepository.buscarConFiltros(limpiar(estado), limpiar(carrera), desdeSeguro, hastaSeguro, limpiar(q), pageable)
                .map(ActaResumenDTO::de);
    }

    /**
     * Detalle de un acta. Aplica control de acceso: ADMIN/COORDINADOR (permiso ACTAS_VER),
     * o el estudiante dueño / jurado / tutor de la solicitud.
     *
     * @param actaId id del acta
     * @return el detalle del acta junto con su tribunal
     * @throws RuntimeException si el acta no existe, o el usuario no participa en ella
     *                          (previene IDOR/BOLA)
     */
    @Override
    @Transactional(readOnly = true)
    public ActaDetalleDTO obtenerDetalle(Long actaId) {
        Acta acta = actaRepository.findDetalleById(actaId)
                .orElseThrow(() -> new RuntimeException("Acta no encontrada: " + actaId));
        validarAcceso(acta); // ADMIN/COORDINADOR o participante (estudiante/jurado/tutor) -- previene IDOR
        List<Jurado> jurados = juradoRepository.findBySolicitudId(acta.getSolicitud().getId());
        return ActaDetalleDTO.de(acta, jurados);
    }

    /**
     * Historial de trazabilidad (timeline) del acta, más reciente primero. Mismo control
     * de acceso que {@link #obtenerDetalle(Long)}.
     *
     * @param actaId id del acta
     * @return los cambios de estado del acta, del más reciente al más antiguo
     * @throws RuntimeException si el acta no existe, o el usuario no participa en ella
     */
    @Override
    @Transactional(readOnly = true)
    public List<HistorialActaDTO> obtenerHistorial(Long actaId) {
        Acta acta = actaRepository.findDetalleById(actaId)
                .orElseThrow(() -> new RuntimeException("Acta no encontrada: " + actaId));
        validarAcceso(acta); // mismo control de acceso que el detalle
        return historialEstadoActaRepository.findByActaIdOrderByFechaCambioDesc(actaId).stream()
                .map(HistorialActaDTO::de)
                .toList();
    }

    /**
     * Cambia el estado del acta (GENERADA -> REVISADA -> FINALIZADA, u OBSERVADA/ANULADA)
     * validando la transición y registrando el cambio en historial_estados_acta con el
     * usuario, su rol, el estado anterior/nuevo y el motivo.
     *
     * @param actaId            id del acta
     * @param nuevoEstadoCodigo código del catálogo estados_acta
     * @param motivo            motivo/observación (obligatorio para OBSERVADA y ANULADA)
     * @return el acta con el nuevo estado aplicado
     * @throws RuntimeException si el acta no existe, el estado no es válido, la transición no
     *                          está permitida, o falta el motivo cuando es obligatorio
     */
    @Override
    @Transactional
    public Acta cambiarEstado(Long actaId, String nuevoEstadoCodigo, String motivo) {
        auditoriaService.marcarActorActual();
        if (nuevoEstadoCodigo == null || nuevoEstadoCodigo.isBlank()) {
            throw new RuntimeException("Debe indicar el nuevo estado del acta");
        }
        String destino = nuevoEstadoCodigo.trim().toUpperCase();

        Acta acta = actaRepository.findDetalleById(actaId)
                .orElseThrow(() -> new RuntimeException("Acta no encontrada: " + actaId));

        EstadoActa actual = acta.getEstado();
        String origen = actual != null ? actual.getCodigo() : "GENERADA";
        if (origen.equals(destino)) {
            throw new RuntimeException("El acta ya está en estado " + destino);
        }

        EstadoActa estadoDestino = estadoActaRepository.findByCodigo(destino)
                .orElseThrow(() -> new RuntimeException("Estado de acta inválido: " + nuevoEstadoCodigo
                        + ". Válidos: GENERADA, REVISADA, OBSERVADA, FINALIZADA, ANULADA"));

        boolean isAdmin = esAdminActual();
        Set<String> permitidas = TRANSICIONES.getOrDefault(origen, Set.of());
        // El ADMIN puede anular en cualquier momento (gestión completa); el resto sigue el flujo.
        if (!permitidas.contains(destino) && !(isAdmin && "ANULADA".equals(destino))) {
            throw new RuntimeException("Transición de estado no permitida: " + origen + " -> " + destino
                    + ". Desde " + origen + " solo se puede pasar a " + permitidas);
        }
        if (ESTADOS_QUE_EXIGEN_MOTIVO.contains(destino) && (motivo == null || motivo.isBlank())) {
            throw new RuntimeException("Debe indicar un motivo para pasar el acta a " + destino);
        }

        acta.setEstado(estadoDestino);
        if (motivo != null && !motivo.isBlank()) {
            acta.setObservacionesActa(motivo.trim());
        }
        Acta guardada = actaRepository.save(acta);
        registrarHistorial(guardada, actual, estadoDestino, "CAMBIO_ESTADO",
                motivo != null && !motivo.isBlank() ? motivo.trim() : null);

        try {
            Long estudianteUsuarioId = acta.getSolicitud().getEstudiante().getUsuario().getId();
            notificacionService.crearNotificacion(estudianteUsuarioId,
                    String.format("El acta de tu pre-sustentación cambió de estado: %s -> %s", origen, destino));
        } catch (Exception e) {
            log.warn("No se pudo notificar el cambio de estado del acta {}: {}", actaId, e.getMessage());
        }
        return guardada;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static String limpiar(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /** Escribe una fila en historial_estados_acta con el usuario autenticado y su rol. */
    private void registrarHistorial(Acta acta, EstadoActa anterior, EstadoActa nuevo, String accion, String comentario) {
        Usuario autor = null;
        String rol = null;
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(String.valueOf(auth.getPrincipal()))) {
                autor = usuarioRepository.findByEmail(auth.getName()).orElse(null);
                if (autor != null) {
                    rol = autor.getRolUsuario() != null ? autor.getRolUsuario().getCodigo() : autor.getRol();
                }
            }
        } catch (Exception e) {
            log.warn("No se pudo resolver el autor del historial del acta {}: {}", acta.getId(), e.getMessage());
        }
        historialEstadoActaRepository.save(HistorialEstadoActa.builder()
                .acta(acta)
                .estadoAnterior(anterior)
                .estadoNuevo(nuevo)
                .usuario(autor)
                .rolUsuario(rol)
                .accion(accion)
                .comentario(comentario)
                .fechaCambio(LocalDateTime.now())
                .build());
    }

    private boolean esAdminActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))
                || (auth != null && permisoService.tienePermiso(auth, "ACTAS_GESTIONAR"));
    }

    // ── Generación PDF ────────────────────────────────────────────────────────

    private void generarPdf(String ruta, Solicitud solicitud, EvaluacionFinal evaluacion, List<Jurado> jurados) {
        generarPdf(ruta, solicitud, evaluacion, jurados, null);
    }

    private void generarPdf(String ruta, Solicitud solicitud, EvaluacionFinal evaluacion,
                             List<Jurado> jurados, Acta acta) {
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

            // Número de acta
            Cell numCell = new Cell()
                    .add(new Paragraph("No. " + solicitud.getId())
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

            // ── Datos del estudiante ──────────────────────────────────────────
            document.add(sectionTitle("1. DATOS DEL ESTUDIANTE", fontBold));
            Table datosEstudiante = new Table(UnitValue.createPercentArray(new float[]{30f, 70f}))
                    .setWidth(UnitValue.createPercentValue(100)).setMarginBottom(10);
            String nombreEst = solicitud.getEstudiante() != null && solicitud.getEstudiante().getUsuario() != null
                    ? solicitud.getEstudiante().getUsuario().getNombre() + " "
                      + solicitud.getEstudiante().getUsuario().getApellido()
                    : "—";
            addRow(datosEstudiante, "Estudiante:", nombreEst, fontBold, fontRegular);
            addRow(datosEstudiante, "Carrera:", solicitud.getEstudiante() != null
                    ? nvl(solicitud.getEstudiante().getCarrera()) : "—", fontBold, fontRegular);
            addRow(datosEstudiante, "Título del tema:", nvl(solicitud.getTituloTema()), fontBold, fontRegular);
            addRow(datosEstudiante, "Modalidad:", solicitud.getModalidadTitulacion() != null ? nvl(solicitud.getModalidadTitulacion().getNombre()) : "—", fontBold, fontRegular);
            addRow(datosEstudiante, "Fecha de solicitud:",
                    solicitud.getFechaRegistro() != null ? solicitud.getFechaRegistro().format(fmtDt) : "—",
                    fontBold, fontRegular);
            document.add(datosEstudiante);

            // ── Tribunal ───────────────────────────────────────────────────────
            document.add(sectionTitle("2. TRIBUNAL EVALUADOR", fontBold));
            Table tribunal = new Table(UnitValue.createPercentArray(new float[]{40f, 40f, 20f}))
                    .setWidth(UnitValue.createPercentValue(100)).setMarginBottom(10);
            addHeaderRow(tribunal, new String[]{"Docente", "Rol", "Confirmado"}, fontBold);
            if (jurados.isEmpty()) {
                Cell noJurados = new Cell(1, 3)
                        .add(new Paragraph("No hay jurados asignados").setFont(fontRegular).setFontSize(9))
                        .setTextAlignment(TextAlignment.CENTER).setPadding(8).setBackgroundColor(LIGHT_GRAY);
                tribunal.addCell(noJurados);
            } else {
                for (Jurado j : jurados) {
                    String docNombre = j.getDocente() != null && j.getDocente().getUsuario() != null
                            ? j.getDocente().getUsuario().getNombre() + " " + j.getDocente().getUsuario().getApellido()
                            : "—";
                    tribunal.addCell(dataCell(docNombre, fontRegular));
                    tribunal.addCell(dataCell(j.getRol(), fontRegular));
                    tribunal.addCell(dataCell(j.isConfirmado() ? "✓" : "Pendiente", fontRegular));
                }
            }
            document.add(tribunal);

            // ── Evaluación y calificación ────────────────────────────────────
            document.add(sectionTitle("3. EVALUACIÓN Y CALIFICACIÓN", fontBold));
            if (evaluacion != null) {
                Table evalTable = new Table(UnitValue.createPercentArray(new float[]{50f, 25f, 25f}))
                        .setWidth(UnitValue.createPercentValue(100)).setMarginBottom(10);
                addHeaderRow(evalTable, new String[]{"Concepto", "Peso (%)", "Nota"}, fontBold);
                evalTable.addCell(dataCell("Instructor del curso (Titulación II)", fontRegular));
                evalTable.addCell(dataCell(String.format("%.0f%%", (evaluacion.getPesoInstructor() != null ? evaluacion.getPesoInstructor() : 0.6) * 100.0), fontRegular));
                evalTable.addCell(dataCell(evaluacion.getNotaInstructor() != null
                        ? String.format("%.2f", evaluacion.getNotaInstructor()) : "—", fontRegular));
 
                evalTable.addCell(dataCell("Tribunal evaluador", fontRegular));
                evalTable.addCell(dataCell(String.format("%.0f%%", (evaluacion.getPesoJurado() != null ? evaluacion.getPesoJurado() : 0.4) * 100.0), fontRegular));
                evalTable.addCell(dataCell(evaluacion.getNotaJuradoPromedio() != null
                        ? String.format("%.2f", evaluacion.getNotaJuradoPromedio()) : "—", fontRegular));
 
                // Fila de total
                Cell totalLabel = new Cell().add(new Paragraph("NOTA FINAL").setFont(fontBold).setFontSize(10))
                        .setBackgroundColor(UTEQ_BLUE).setFontColor(ColorConstants.WHITE)
                        .setPadding(6).setBorder(Border.NO_BORDER);
                Cell totalPeso = new Cell().add(new Paragraph("100%").setFont(fontBold).setFontSize(10)
                        .setFontColor(ColorConstants.WHITE))
                        .setBackgroundColor(UTEQ_BLUE).setPadding(6).setBorder(Border.NO_BORDER);
                Cell totalNota = new Cell().add(new Paragraph(evaluacion.getNotaFinal() != null
                        ? String.format("%.2f / 10", evaluacion.getNotaFinal()) : "—")
                        .setFont(fontBold).setFontSize(10).setFontColor(UTEQ_GOLD))
                        .setBackgroundColor(UTEQ_BLUE).setPadding(6).setBorder(Border.NO_BORDER);
                evalTable.addCell(totalLabel);
                evalTable.addCell(totalPeso);
                evalTable.addCell(totalNota);
                document.add(evalTable);
 
                // Resultado
                String resultado = evaluacion.getResultado() != null ? nvl(evaluacion.getResultado().getNombre()) : "—";
                String resultadoCodigo = evaluacion.getResultado() != null ? evaluacion.getResultado().getCodigo() : "";
                DeviceRgb resultColor = "APROBADO".equals(resultadoCodigo)
                        ? new DeviceRgb(0, 128, 0) : new DeviceRgb(180, 0, 0);
                document.add(new Paragraph("RESULTADO: " + resultado)
                        .setFont(fontBold).setFontSize(14).setFontColor(resultColor)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setBorder(new SolidBorder(resultColor, 2)).setPadding(8).setMarginBottom(10));

                if (evaluacion.getObservaciones() != null && !evaluacion.getObservaciones().isBlank()) {
                    document.add(sectionTitle("Observaciones del tribunal:", fontBold));
                    document.add(new Paragraph(evaluacion.getObservaciones())
                            .setFont(fontRegular).setFontSize(9).setBackgroundColor(LIGHT_GRAY)
                            .setPadding(8).setMarginBottom(10));
                }
            } else {
                document.add(new Paragraph("Evaluación pendiente de registro.")
                        .setFont(fontRegular).setFontSize(9).setFontColor(ColorConstants.GRAY));
            }

            // ── Firmas ────────────────────────────────────────────────────────
            document.add(sectionTitle("4. FIRMAS ELECTRÓNICAS", fontBold));
            Table firmasTable = new Table(UnitValue.createPercentArray(new float[]{25f, 25f, 25f, 25f}))
                    .setWidth(UnitValue.createPercentValue(100)).setMarginBottom(15);

            String[] rolesLabel = {"Presidente", "Vocal 1", "Vocal 2", "Tutor"};
            boolean[] firmados  = {
                acta != null && acta.isFirmadaPresidente(),
                acta != null && acta.isFirmadaVocal1(),
                acta != null && acta.isFirmadaVocal2(),
                acta != null && acta.isFirmadaTutor()
            };
            LocalDateTime[] fechasFirma = {
                acta != null ? acta.getFechaFirmaPresidente() : null,
                acta != null ? acta.getFechaFirmaVocal1() : null,
                acta != null ? acta.getFechaFirmaVocal2() : null,
                acta != null ? acta.getFechaFirmaTutor() : null
            };

            for (int i = 0; i < 4; i++) {
                boolean firmado = firmados[i];
                Cell firmaCell = new Cell()
                        .add(new Paragraph(rolesLabel[i]).setFont(fontBold).setFontSize(9)
                                .setTextAlignment(TextAlignment.CENTER))
                        .add(new Paragraph(firmado ? "✓ FIRMADO" : "PENDIENTE")
                                .setFont(fontBold).setFontSize(10)
                                .setFontColor(firmado ? new DeviceRgb(0, 128, 0) : new DeviceRgb(150, 150, 150))
                                .setTextAlignment(TextAlignment.CENTER))
                        .add(new Paragraph(firmado && fechasFirma[i] != null
                                ? fechasFirma[i].format(fmtDt) : " ")
                                .setFont(fontRegular).setFontSize(7)
                                .setTextAlignment(TextAlignment.CENTER))
                        .setBackgroundColor(firmado ? new DeviceRgb(230, 255, 230) : LIGHT_GRAY)
                        .setBorder(new SolidBorder(firmado ? new DeviceRgb(0, 128, 0) : MEDIUM_GRAY, 1))
                        .setPadding(10).setMargin(3);
                firmasTable.addCell(firmaCell);
            }
            document.add(firmasTable);

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
     * Elimina un acta si el usuario tiene permiso, incluido su archivo PDF en disco si existe.
     *
     * @param actaId id del acta
     * @throws RuntimeException si el acta no existe, o el usuario no tiene acceso a ella
     */
    @Override
    public void eliminarActa(Long actaId) {
        Acta acta = actaRepository.findById(actaId)
                .orElseThrow(() -> new RuntimeException("Acta no encontrada: " + actaId));
        // Validar que el usuario tenga permisos (ya sea admin, estudiante dueño, jurado o tutor)
        validarAcceso(acta);
        
        // Si hay un archivo físico, intentar eliminarlo
        if (acta.getArchivoPdf() != null) {
            Path path = Paths.get(actasDir, acta.getArchivoPdf());
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                log.warn("No se pudo eliminar el archivo físico del acta {}: {}", actaId, e.getMessage());
            }
        }
        
        actaRepository.delete(acta);
    }
}
