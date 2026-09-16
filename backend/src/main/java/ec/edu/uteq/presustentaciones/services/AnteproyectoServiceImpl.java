package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.Anteproyecto;
import ec.edu.uteq.presustentaciones.entities.Solicitud;
import ec.edu.uteq.presustentaciones.entities.Usuario;
import ec.edu.uteq.presustentaciones.repositories.AnteproyectoRepository;
import ec.edu.uteq.presustentaciones.repositories.SolicitudRepository;
import ec.edu.uteq.presustentaciones.repositories.UsuarioRepository;
import ec.edu.uteq.presustentaciones.security.service.SolicitudAccessService;
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
public class AnteproyectoServiceImpl implements AnteproyectoService {

    private final AnteproyectoRepository anteproyectoRepository;
    private final SolicitudRepository solicitudRepository;
    private final NotificacionService notificacionService;
    private final UsuarioRepository usuarioRepository;
    private final SolicitudAccessService solicitudAccessService;

    @Value("${app.upload.dir:uploads/anteproyectos}")
    private String uploadDir;

    public AnteproyectoServiceImpl(AnteproyectoRepository ar, SolicitudRepository sr,
                                   NotificacionService ns, UsuarioRepository ur,
                                   SolicitudAccessService sas) {
        this.anteproyectoRepository = ar;
        this.solicitudRepository = sr;
        this.notificacionService = ns;
        this.usuarioRepository = ur;
        this.solicitudAccessService = sas;
    }

    /** Solo el estudiante dueño de la solicitud puede subir su propio anteproyecto (ADMIN
     * incluido como vía administrativa, igual que en el resto de los servicios de este
     * bloque) -- evita que un tercero suba/reemplace el PDF de otra solicitud (IDOR de
     * escritura). */
    private void validarPuedeSubir(Solicitud solicitud) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Usuario no autenticado");
        }
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) return;

        String email = auth.getName();
        boolean esDueno = solicitud.getEstudiante() != null && solicitud.getEstudiante().getUsuario() != null
                && solicitud.getEstudiante().getUsuario().getEmail().equals(email);
        if (!esDueno) {
            throw new AccessDeniedException("Solo el estudiante propietario de la solicitud puede subir su anteproyecto");
        }
    }

    /**
     * Sube el PDF del anteproyecto de una solicitud, calcula y persiste su hash SHA-256, y
     * deja el anteproyecto en estado pendiente de revisión.
     *
     * @param solicitudId id de la solicitud a la que pertenece el anteproyecto
     * @param archivo     archivo PDF subido por el estudiante
     * @return el anteproyecto creado o actualizado
     * @throws RuntimeException si la solicitud no existe o el archivo no es un PDF válido
     */
    @Override
    public Anteproyecto enviarAnteproyecto(Long solicitudId, MultipartFile archivo) {
        Solicitud solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));

        validarPuedeSubir(solicitud);

        if (solicitud.getEstado() != null && "SUSPENDIDA".equalsIgnoreCase(solicitud.getEstado().getCodigo())) {
            throw new RuntimeException("Tu trabajo ha sido suspendido y no puedes subir archivos. Motivo: " + solicitud.getMotivoSuspension());
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

        String nombreArchivo = "solicitud_" + solicitudId + "_" + UUID.randomUUID() + ".pdf";
        Path rutaArchivo = dirPath.resolve(nombreArchivo);
        String sha256 = calcularSha256YGuardar(archivo, rutaArchivo);

        Anteproyecto anteproyecto = anteproyectoRepository
                .findBySolicitudId(solicitudId).orElse(null);

        if (anteproyecto != null
                && anteproyecto.getArchivoPdf() != null
                && !"RECHAZADO".equals(anteproyecto.getEstado())) {
            throw new RuntimeException(
                    "No puedes reemplazar el PDF. El coordinador debe rechazar el anteproyecto para permitir una nueva carga.");
        }

        if (anteproyecto == null) anteproyecto = new Anteproyecto();

        anteproyecto.setArchivoPdf(nombreArchivo);
        anteproyecto.setFechaEnvio(LocalDate.now());
        anteproyecto.setEstado("ENVIADO");
        anteproyecto.setSolicitud(solicitud);
        anteproyecto.setSha256Hash(sha256);
        anteproyecto.setTamanoBytes(archivo.getSize());

        Anteproyecto guardado = anteproyectoRepository.save(anteproyecto);

        // Notificar a los admins que hay un anteproyecto nuevo para revisar
        notificarAdminsNuevoAnteproyecto(solicitud);

        return guardado;
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
     * RF-02: Verifica que el archivo en disco coincida con el hash SHA-256 almacenado.
     *
     * @param solicitudId id de la solicitud cuyo anteproyecto se va a verificar
     * @return {@code true} si el hash SHA-256 del archivo en disco coincide con el
     *         almacenado en base de datos (comparación con {@code MessageDigest.isEqual},
     *         resistente a ataques de timing)
     * @throws RuntimeException si la solicitud no tiene anteproyecto o el archivo no existe
     *                          en disco
     */
    @Override
    public boolean verificarIntegridad(Long solicitudId) {
        Anteproyecto ap = anteproyectoRepository.findBySolicitudId(solicitudId)
                .orElseThrow(() -> new RuntimeException("Anteproyecto no encontrado"));
        solicitudAccessService.validarAcceso(ap.getSolicitud(), "ANTEPROYECTO_REVISAR");

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
     * @param id  id del anteproyecto a aprobar
     * @param obs observaciones opcionales del revisor
     * @return el anteproyecto actualizado en estado "APROBADO"
     * @throws RuntimeException si el anteproyecto no existe
     */
    @Override
    public Anteproyecto aprobarAnteproyecto(Long id, String obs) {
        Anteproyecto ap = anteproyectoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Anteproyecto no encontrado"));
        ap.setEstado("APROBADO");
        ap.setObservaciones(obs);
        Anteproyecto guardado = anteproyectoRepository.save(ap);

        // Notificar al estudiante
        notificarEstudianteAnteproyecto(ap, true, obs);

        return guardado;
    }

    /**
     * @param id  id del anteproyecto a rechazar
     * @param obs motivo del rechazo
     * @return el anteproyecto actualizado en estado "RECHAZADO"
     * @throws RuntimeException si el anteproyecto no existe
     */
    @Override
    public Anteproyecto rechazarAnteproyecto(Long id, String obs) {
        Anteproyecto ap = anteproyectoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Anteproyecto no encontrado"));
        ap.setEstado("RECHAZADO");
        ap.setObservaciones(obs);
        Anteproyecto guardado = anteproyectoRepository.save(ap);

        // Notificar al estudiante
        notificarEstudianteAnteproyecto(ap, false, obs);

        return guardado;
    }

    /**
     * @param solicitudId id de la solicitud
     * @return el anteproyecto de esa solicitud, si ya fue enviado
     */
    @Override
    public Optional<Anteproyecto> buscarPorSolicitud(Long solicitudId) {
        Optional<Anteproyecto> anteproyecto = anteproyectoRepository.findBySolicitudId(solicitudId);
        anteproyecto.ifPresent(ap -> solicitudAccessService.validarAcceso(ap.getSolicitud(), "ANTEPROYECTO_REVISAR"));
        return anteproyecto;
    }

    // ── Helpers de notificación ───────────────────────────────────────────────

    private void notificarAdminsNuevoAnteproyecto(Solicitud solicitud) {
        try {
            List<Usuario> admins = usuarioRepository.findByRol("ADMIN");
            String nombreEst = solicitud.getEstudiante().getUsuario().getNombre()
                    + " " + solicitud.getEstudiante().getUsuario().getApellido();
            for (Usuario admin : admins) {
                notificacionService.crearNotificacion(admin.getId(),
                        String.format("📄 El estudiante %s ha subido el PDF del anteproyecto \"%s\". " +
                                        "Está pendiente de revisión y aprobación.",
                                nombreEst, solicitud.getTituloTema()));
            }
        } catch (Exception e) {
            log.warn("No se pudo notificar a admins sobre nuevo anteproyecto: {}", e.getMessage());
        }
    }

    private void notificarEstudianteAnteproyecto(Anteproyecto ap, boolean aprobado, String obs) {
        try {
            Long usuarioId = ap.getSolicitud().getEstudiante().getUsuario().getId();
            String titulo  = ap.getSolicitud().getTituloTema();
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
            notificacionService.crearNotificacion(usuarioId, msg);
        } catch (Exception e) {
            log.warn("No se pudo notificar al estudiante sobre anteproyecto: {}", e.getMessage());
        }
    }
}