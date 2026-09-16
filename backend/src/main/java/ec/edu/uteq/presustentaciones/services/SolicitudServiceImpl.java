package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.Carrera;
import ec.edu.uteq.presustentaciones.entities.ConvocatoriaTitulacion;
import ec.edu.uteq.presustentaciones.entities.Estudiante;
import ec.edu.uteq.presustentaciones.entities.ModalidadTitulacion;
import ec.edu.uteq.presustentaciones.entities.PeriodoAcademico;
import ec.edu.uteq.presustentaciones.entities.Solicitud;
import ec.edu.uteq.presustentaciones.entities.Usuario;
import ec.edu.uteq.presustentaciones.repositories.AnteproyectoRepository;
import ec.edu.uteq.presustentaciones.repositories.AreaTematicaRepository;
import ec.edu.uteq.presustentaciones.repositories.CarreraRepository;
import ec.edu.uteq.presustentaciones.repositories.ConvocatoriaTitulacionRepository;
import ec.edu.uteq.presustentaciones.repositories.EstudianteRepository;
import ec.edu.uteq.presustentaciones.repositories.LineaInvestigacionRepository;
import ec.edu.uteq.presustentaciones.repositories.ModalidadTitulacionRepository;
import ec.edu.uteq.presustentaciones.repositories.PeriodoAcademicoRepository;
import ec.edu.uteq.presustentaciones.repositories.SolicitudRepository;
import ec.edu.uteq.presustentaciones.repositories.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class SolicitudServiceImpl implements SolicitudService {

    private final SolicitudRepository solicitudRepository;
    private final EstudianteRepository estudianteRepository;
    private final AnteproyectoRepository anteproyectoRepository;
    private final NotificacionService notificacionService;
    private final UsuarioRepository usuarioRepository;
    private final ec.edu.uteq.presustentaciones.repositories.EstadoSolicitudRepository estadoSolicitudRepository;
    private final ModalidadTitulacionRepository modalidadTitulacionRepository;
    private final ConvocatoriaTitulacionRepository convocatoriaTitulacionRepository;
    private final CarreraRepository carreraRepository;
    private final PeriodoAcademicoRepository periodoAcademicoRepository;
    private final LineaInvestigacionRepository lineaInvestigacionRepository;
    private final AreaTematicaRepository areaTematicaRepository;
    private final AuditoriaService auditoriaService;
    private final ec.edu.uteq.presustentaciones.repositories.EstadoAcademicoRepository estadoAcademicoRepository;

    // ─── Helpers ────────────────────────────────────────────────────────────

    private void notificarAdmins(String mensaje) {
        List<Usuario> admins = usuarioRepository.findByRol("ADMIN");
        for (Usuario admin : admins) {
            try {
                notificacionService.crearNotificacion(admin.getId(), mensaje);
            } catch (Exception e) {
                log.warn("No se pudo notificar al coordinador ID {}: {}", admin.getId(), e.getMessage());
            }
        }
    }

    private void notificarEstudiante(Solicitud solicitud, String mensaje) {
        try {
            Long usuarioId = solicitud.getEstudiante().getUsuario().getId();
            notificacionService.crearNotificacion(usuarioId, mensaje);
        } catch (Exception e) {
            log.warn("No se pudo notificar al estudiante de solicitud ID {}: {}", solicitud.getId(), e.getMessage());
        }
    }

    // ─── Métodos ─────────────────────────────────────────────────────────────

    /**
     * @param estudianteId id del {@code Estudiante} propietario de la solicitud
     * @param datos        datos de la solicitud a crear (título del tema, modalidad, etc.)
     * @return la solicitud creada y persistida, con su estado y estudiante asociados
     * @throws RuntimeException si el estudiante no existe o falta la modalidad de titulación
     */
    @Override
    @Transactional
    @CacheEvict(value = "solicitudes", allEntries = true)
    public Solicitud crearSolicitud(Long estudianteId, Solicitud datos) {
        Estudiante estudiante = estudianteRepository.findById(estudianteId)
                .orElseThrow(() -> new RuntimeException("Estudiante no encontrado con ID: " + estudianteId));

        if (datos.getTituloTema() == null || datos.getTituloTema().trim().isEmpty()) {
            throw new RuntimeException("El título del tema es obligatorio");
        }
        if (datos.getTituloTema().length() > 300) {
            throw new RuntimeException("El título del tema no puede exceder los 300 caracteres");
        }

        // Resolver estado inicial
        ec.edu.uteq.presustentaciones.entities.EstadoSolicitud estadoCreada = estadoSolicitudRepository.findByCodigo("CREADA")
                .orElseGet(() -> estadoSolicitudRepository.save(ec.edu.uteq.presustentaciones.entities.EstadoSolicitud.builder()
                        .codigo("CREADA").nombre("Creada").build()));

        // Resolver modalidad: si el objeto ya viene completo (con id) úsalo; si no, error
        if (datos.getModalidadTitulacion() == null || datos.getModalidadTitulacion().getId() == null) {
            throw new RuntimeException("Debe seleccionar una modalidad de titulación válida");
        }
        ModalidadTitulacion modalidad = modalidadTitulacionRepository
                .findById(datos.getModalidadTitulacion().getId())
                .orElseThrow(() -> new RuntimeException("Modalidad no encontrada con ID: " + datos.getModalidadTitulacion().getId()));
        datos.setModalidadTitulacion(modalidad);

        // Resolver convocatoria: si viene en el body úsala, si no buscar/crear la activa
        if (datos.getConvocatoria() == null || datos.getConvocatoria().getId() == null) {
            ConvocatoriaTitulacion convActiva = convocatoriaTitulacionRepository
                    .findFirstByActivaTrue()
                    .orElseGet(this::crearConvocatoriaDefault);
            datos.setConvocatoria(convActiva);
        } else {
            ConvocatoriaTitulacion convocatoria = convocatoriaTitulacionRepository
                    .findById(datos.getConvocatoria().getId())
                    .orElseThrow(() -> new RuntimeException("Convocatoria no encontrada con ID: " + datos.getConvocatoria().getId()));
            datos.setConvocatoria(convocatoria);
        }

        // Resolver línea de investigación (opcional, igual que en la columna real de la BD)
        if (datos.getLineaInvestigacion() != null && datos.getLineaInvestigacion().getId() != null) {
            ec.edu.uteq.presustentaciones.entities.LineaInvestigacion linea = lineaInvestigacionRepository
                    .findById(datos.getLineaInvestigacion().getId())
                    .orElseThrow(() -> new RuntimeException("Línea de investigación no encontrada con ID: " + datos.getLineaInvestigacion().getId()));
            datos.setLineaInvestigacion(linea);
        } else {
            datos.setLineaInvestigacion(null);
        }

        // Resolver área temática (opcional); si viene, debe pertenecer a la línea seleccionada
        if (datos.getAreaTematica() != null && datos.getAreaTematica().getId() != null) {
            ec.edu.uteq.presustentaciones.entities.AreaTematica area = areaTematicaRepository
                    .findById(datos.getAreaTematica().getId())
                    .orElseThrow(() -> new RuntimeException("Área temática no encontrada con ID: " + datos.getAreaTematica().getId()));
            if (datos.getLineaInvestigacion() != null
                    && !area.getLineaInvestigacion().getId().equals(datos.getLineaInvestigacion().getId())) {
                throw new RuntimeException("El área temática seleccionada no pertenece a la línea de investigación elegida");
            }
            datos.setAreaTematica(area);
        } else {
            datos.setAreaTematica(null);
        }

        datos.setEstado(estadoCreada);
        datos.setEstudiante(estudiante);
        datos.setCreadoPor(estudiante.getUsuario());
        datos.setActualizadoPor(estudiante.getUsuario());
        datos.setFechaRegistro(LocalDateTime.now());
        datos.setActualizadoEn(LocalDateTime.now());
        auditoriaService.marcarActorActual();
        return solicitudRepository.save(datos);
    }

    /**
     * @param usuarioId id del {@code Usuario} autenticado (rol ESTUDIANTE)
     * @param datos     datos de la solicitud a crear
     * @return la solicitud creada
     * @throws RuntimeException si el usuario no existe, no tiene rol ESTUDIANTE, o no hay
     *                          carreras configuradas para crear el perfil automáticamente
     */
    @Override
    @Transactional
    @CacheEvict(value = "solicitudes", allEntries = true)
    public Solicitud crearSolicitudPorUsuario(Long usuarioId, Solicitud datos) {
        // NOTA: crearSolicitud(...) también tiene @CacheEvict, pero como se invoca aquí
        // como "this.crearSolicitud(...)" (autoinvocación dentro de la misma clase), el
        // proxy de Spring AOP se salta y esa anotación nunca se dispara. Por eso este
        // método necesita su propio @CacheEvict: es el que realmente atraviesa el proxy
        // cuando lo llama el controlador (POST /api/solicitudes/crear-por-usuario/{id}).
        // Bug real: /mis-solicitudes seguía devolviendo la lista vacía cacheada después
        // de crear una solicitud nueva.

        // Buscar perfil de estudiante; si no existe, crearlo automáticamente
        Estudiante estudiante = estudianteRepository.findByUsuarioId(usuarioId)
                .orElseGet(() -> crearPerfilEstudiante(usuarioId));
        return crearSolicitud(estudiante.getId(), datos);
    }

    /**
     * Crea automáticamente un PeriodoAcademico + ConvocatoriaTitulacion activos
     * cuando la base de datos no tiene ninguno configurado (instalación inicial).
     */
    @Transactional
    private ConvocatoriaTitulacion crearConvocatoriaDefault() {
        int anio = java.time.Year.now().getValue();

        // Crear o reusar período académico del año actual
        PeriodoAcademico periodo = periodoAcademicoRepository
                .findByCodigo("PA-" + anio)
                .orElseGet(() -> {
                    log.info("Creando período académico por defecto para año {}", anio);
                    return periodoAcademicoRepository.save(PeriodoAcademico.builder()
                            .codigo("PA-" + anio)
                            .nombre("Período Académico " + anio)
                            .fechaInicio(LocalDate.of(anio, 1, 1))
                            .fechaFin(LocalDate.of(anio, 12, 31))
                            .activo(true)
                            .build());
                });

        // Crear convocatoria activa ligada al período
        log.info("Creando convocatoria activa por defecto para período {}", periodo.getCodigo());
        return convocatoriaTitulacionRepository.save(ConvocatoriaTitulacion.builder()
                .codigo("CONV-" + anio + "-01")
                .nombre("Convocatoria " + anio + " – Período I")
                .periodoAcademico(periodo)
                .fechaInicio(LocalDate.of(anio, 1, 1))
                .fechaFin(LocalDate.of(anio, 12, 31))
                .activa(true)
                .build());
    }

    /**
     * Crea automáticamente el perfil Estudiante para un usuario con rol ESTUDIANTE
     * que aún no tenga registro en la tabla estudiante.
     */
    @Transactional
    private Estudiante crearPerfilEstudiante(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + usuarioId));

        // Verificar que realmente sea un estudiante
        if (!"ESTUDIANTE".equalsIgnoreCase(usuario.getRol())) {
            throw new RuntimeException("El usuario no tiene rol de estudiante");
        }

        // Obtener la primera carrera disponible como default
        Carrera carreraDefault = carreraRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No hay carreras configuradas en el sistema. Contacte al administrador."));

        log.info("Creando perfil de estudiante automáticamente para usuario ID: {}", usuarioId);

        // sp_generar_codigo_expediente (Fase 3 / Criterio P1, categoría "generación de
        // códigos secuenciales"): nextval() sobre una secuencia dedicada es atómico a nivel
        // de motor, así que dos altas concurrentes nunca reciben el mismo código.
        String expedienteCodigo = estudianteRepository.generarCodigoExpediente(null, null);

        ec.edu.uteq.presustentaciones.entities.EstadoAcademico estadoActivo = estadoAcademicoRepository.findByCodigo("ACTIVO")
                .orElseThrow(() -> new RuntimeException("Catálogo de estados académicos no sembrado"));

        Estudiante nuevoEstudiante = Estudiante.builder()
                .usuario(usuario)
                .carrera(carreraDefault.getNombre())
                .carreraEntidad(carreraDefault)
                .semestreActual((short) 1)
                .semestre("1ro")
                .expedienteCodigo(expedienteCodigo)
                .estadoAcademico(estadoActivo)
                .build();

        return estudianteRepository.save(nuevoEstudiante);
    }
 
    /**
     * @param usuarioId id del usuario (se resuelve a su perfil de estudiante internamente)
     * @return las solicitudes del estudiante asociado a ese usuario, o lista vacía si no tiene
     *         perfil de estudiante todavía
     */
    @Override
    @Cacheable(value = "solicitudes", key = "'usuario:' + #usuarioId")
    public List<Solicitud> listarPorUsuario(Long usuarioId) {
        return estudianteRepository.findByUsuarioId(usuarioId)
                .map(e -> solicitudRepository.findByEstudianteId(e.getId()))
                .orElse(java.util.Collections.emptyList());
    }
 
    /**
     * @param solicitudId id de la solicitud a enviar
     * @return la solicitud actualizada en estado "ENVIADA"
     * @throws RuntimeException si la solicitud no existe o no tiene anteproyecto adjunto
     */
    @Override
    @Transactional
    @CacheEvict(value = "solicitudes", allEntries = true)
    public Solicitud enviarSolicitud(Long solicitudId) {
        Solicitud s = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));
 
        boolean tienePdf = anteproyectoRepository.findBySolicitudId(solicitudId)
                .map(a -> a.getArchivoPdf() != null && !a.getArchivoPdf().isBlank())
                .orElse(false);
 
        if (!tienePdf) {
            throw new RuntimeException("Debes cargar el PDF del anteproyecto antes de enviar la solicitud a revisión.");
        }
 
        ec.edu.uteq.presustentaciones.entities.EstadoSolicitud estadoEnviada = estadoSolicitudRepository.findByCodigo("ENVIADA")
                .orElseGet(() -> estadoSolicitudRepository.save(ec.edu.uteq.presustentaciones.entities.EstadoSolicitud.builder()
                        .codigo("ENVIADA").nombre("Enviada").build()));

        s.setEstado(estadoEnviada);
        Solicitud guardada = solicitudRepository.save(s);
 
        String nombreEstudiante = s.getEstudiante().getUsuario().getNombre()
                + " " + s.getEstudiante().getUsuario().getApellido();
 
        notificarAdmins(String.format(
                "📋 Nueva solicitud de %s: \"%s\" está pendiente de revisión.",
                nombreEstudiante, s.getTituloTema()));
 
        return guardada;
    }
 
    /**
     * @param solicitudId id de la solicitud a aprobar
     * @return la solicitud actualizada
     */
    @Override
    @Transactional
    @CacheEvict(value = "solicitudes", allEntries = true)
    public Solicitud aprobarSolicitud(Long solicitudId) {
        auditoriaService.marcarActorActual();
        Solicitud s = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));

        ec.edu.uteq.presustentaciones.entities.EstadoSolicitud estadoAprobada = estadoSolicitudRepository.findByCodigo("APROBADA")
                .orElseGet(() -> estadoSolicitudRepository.save(ec.edu.uteq.presustentaciones.entities.EstadoSolicitud.builder()
                        .codigo("APROBADA").nombre("Aprobada").build()));

        s.setEstado(estadoAprobada);
        Solicitud guardada = solicitudRepository.save(s);

        notificarEstudiante(s, String.format(
                "✅ Tu solicitud \"%s\" ha sido APROBADA. Pronto se te asignará fecha y tribunal.",
                s.getTituloTema()));

        return guardada;
    }
 
    /**
     * @param solicitudId id de la solicitud a rechazar
     * @return la solicitud actualizada en estado "RECHAZADA"
     */
    @Override
    @Transactional
    @CacheEvict(value = "solicitudes", allEntries = true)
    public Solicitud rechazarSolicitud(Long solicitudId) {
        return rechazarConObservacion(solicitudId, null);
    }

    /**
     * @param solicitudId  id de la solicitud a rechazar
     * @param observacion  motivo del rechazo, visible luego para el estudiante
     * @return la solicitud actualizada en estado "RECHAZADA" con la observación guardada
     */
    @Override
    @Transactional
    @CacheEvict(value = "solicitudes", allEntries = true)
    public Solicitud rechazarConObservacion(Long solicitudId, String observacion) {
        auditoriaService.marcarActorActual();
        Solicitud s = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));

        ec.edu.uteq.presustentaciones.entities.EstadoSolicitud estadoRechazada = estadoSolicitudRepository.findByCodigo("RECHAZADA")
                .orElseGet(() -> estadoSolicitudRepository.save(ec.edu.uteq.presustentaciones.entities.EstadoSolicitud.builder()
                        .codigo("RECHAZADA").nombre("Rechazada").build()));

        s.setEstado(estadoRechazada);
        if (observacion != null && !observacion.isBlank()) {
            s.setObservaciones(observacion);
        }
        Solicitud guardada = solicitudRepository.save(s);
 
        String obs = (s.getObservaciones() != null && !s.getObservaciones().isBlank())
                ? " Motivo: " + s.getObservaciones() : "";
        notificarEstudiante(s, String.format(
                "❌ Tu solicitud \"%s\" ha sido RECHAZADA.%s Revisa las observaciones.",
                s.getTituloTema(), obs));
 
        return guardada;
    }
 
    /** Cota dura del endpoint sin paginar: solo las solicitudes más recientes. Con el volumen
     *  real (44k+ solicitudes) traerlas todas son ~93 MB de JSON que congelan el navegador y
     *  llenan Redis. El listado completo navegable es GET /api/v1/solicitudes/paginado. */
    private static final int LIMITE_LISTADO_SIN_PAGINAR = 500;

    /** @return todas las solicitudes del sistema, sin paginar */
    @Override
    @Cacheable(value = "solicitudes", key = "'all'")
    public List<Solicitud> listarSolicitudes() {
        return solicitudRepository.findAllWithEstudiante(
                PageRequest.of(0, LIMITE_LISTADO_SIN_PAGINAR));
    }

    /**
     * @param pagina       número de página, base 0
     * @param tamanio      tamaño de página
     * @param estado       código de estado por el que filtrar, o {@code null} para no filtrar
     * @param texto        texto libre de búsqueda (título/estudiante), o {@code null}
     * @param fechaDesde   fecha mínima de registro, o {@code null} para no acotar
     * @param fechaHasta   fecha máxima de registro, o {@code null} para no acotar
     * @return página de solicitudes que cumplen los filtros
     */
    @Override
    public Page<Solicitud> listarSolicitudesPaginado(int pagina, int tamanio, String estado, String texto,
                                                       LocalDate fechaDesde, LocalDate fechaHasta) {
        int paginaSegura = Math.max(pagina, 0);
        int tamanioSeguro = Math.min(Math.max(tamanio, 1), 100);
        PageRequest pageRequest = PageRequest.of(paginaSegura, tamanioSeguro, Sort.by(Sort.Direction.DESC, "fechaRegistro"));
        // fechaRegistro es timestamp -- se acota al día completo (00:00:00 a 23:59:59.999999999)
        // para que filtrar por "hoy" o por un día puntual incluya todas las horas de ese día.
        // Se usan centinelas (1900/2999) en vez de pasar null al JPQL: Hibernate no logra
        // inferir el tipo SQL de un parámetro null reutilizado dentro de "x IS NULL OR campo >= x"
        // contra Postgres (falla con "cannot cast type bytea to timestamp"), así que en vez de
        // ese patrón se acota siempre a un rango concreto, sin importar si el usuario filtró o no.
        LocalDateTime desde = fechaDesde != null ? fechaDesde.atStartOfDay() : LocalDateTime.of(1900, 1, 1, 0, 0);
        LocalDateTime hasta = fechaHasta != null ? fechaHasta.atTime(LocalTime.MAX) : LocalDateTime.of(2999, 12, 31, 23, 59, 59);
        return solicitudRepository.buscarConFiltros(estado, texto, desde, hasta, pageRequest);
    }

    /** @return conteo de solicitudes agrupado por código de estado, para el dashboard */
    @Override
    public Map<String, Long> contarPorEstado() {
        Map<String, Long> conteos = new LinkedHashMap<>();
        long total = solicitudRepository.count();
        conteos.put("TODAS", total);
        for (SolicitudRepository.EstadoConteo c : solicitudRepository.contarAgrupadoPorEstado()) {
            conteos.put(c.getCodigo(), c.getTotal());
        }
        return conteos;
    }

    /**
     * @param estudianteId id del estudiante
     * @return todas las solicitudes registradas por ese estudiante
     */
    @Override
    @Cacheable(value = "solicitudes", key = "'estudiante:' + #estudianteId")
    public List<Solicitud> listarPorEstudiante(Long estudianteId) {
        return solicitudRepository.findByEstudianteId(estudianteId);
    }
 
    /**
     * @param id id de la solicitud
     * @return la solicitud si existe, o {@link Optional#empty()} en caso contrario
     */
    @Override
    @Cacheable(value = "solicitudes", key = "#id", unless = "#result == null")
    public Optional<Solicitud> obtenerPorId(Long id) {
        return solicitudRepository.findById(id);
    }
 
    /**
     * @param solicitudId id de la solicitud a suspender
     * @param motivo      motivo de la suspensión; no puede estar vacío
     * @return la solicitud actualizada en estado "SUSPENDIDA"
     * @throws RuntimeException si la solicitud no existe, su estado actual no permite
     *                          suspensión, o el motivo está vacío
     */
    @Override
    @Transactional
    @CacheEvict(value = "solicitudes", allEntries = true)
    public Solicitud suspenderSolicitud(Long solicitudId, String motivo) {
        auditoriaService.marcarActorActual();
        Solicitud s = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));
 
        String codEstado = s.getEstado() != null ? s.getEstado().getCodigo() : "";
        boolean esSuspendible = !"CREADA".equals(codEstado) && !"RECHAZADA".equals(codEstado) && !"SUSPENDIDA".equals(codEstado);
        if (!esSuspendible) {
            throw new RuntimeException("La solicitud no puede ser suspendida en su estado actual: " + codEstado);
        }
 
        if (motivo == null || motivo.isBlank()) {
            throw new RuntimeException("Debe especificar el motivo de la suspensión");
        }
 
        ec.edu.uteq.presustentaciones.entities.EstadoSolicitud estadoSuspendida = estadoSolicitudRepository.findByCodigo("SUSPENDIDA")
                .orElseGet(() -> estadoSolicitudRepository.save(ec.edu.uteq.presustentaciones.entities.EstadoSolicitud.builder()
                        .codigo("SUSPENDIDA").nombre("Suspendida").build()));

        s.setEstado(estadoSuspendida);
        s.setMotivoSuspension(motivo);
        s.setSuspendidoEn(LocalDateTime.now());

        Solicitud guardada = solicitudRepository.save(s);
        log.info("Solicitud {} suspendida desde estado {} por motivo: {}", solicitudId, codEstado, motivo);
 
        notificarEstudiante(s, String.format(
                "🚫 Tu trabajo \"%s\" ha sido SUSPENDIDO. Motivo: %s. No podrás continuar.",
                s.getTituloTema(), motivo));

        return guardada;
    }

    /**
     * @param carrera nombre (o coincidencia parcial, {@code ILIKE}) de la carrera a filtrar
     * @return una fila por defensa, con las claves declaradas en
     *         {@code docs/basedatos/CATALOGO-SP.md} (solicitudId, estudianteNombre, expediente,
     *         tituloTema, estadoSolicitud, fechaDefensa, salaNombre, notaFinal)
     */
    @Override
    public List<Map<String, Object>> generarReporteDefensasSP(String carrera) {
        List<Object[]> res = solicitudRepository.generarReporteDefensasSp(carrera);
        List<Map<String, Object>> list = new java.util.ArrayList<>();
        for (Object[] row : res) {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("solicitudId", row[0]);
            map.put("estudianteNombre", row[1]);
            map.put("expediente", row[2]);
            map.put("tituloTema", row[3]);
            map.put("estadoSolicitud", row[4]);
            map.put("fechaDefensa", row[5]);
            map.put("salaNombre", row[6]);
            map.put("notaFinal", row[7]);
            list.add(map);
        }
        return list;
    }

    /**
     * @param solicitudId el ID de la solicitud
     * @return un DTO con el progreso y etapas del proceso
     */
    @Override
    public ec.edu.uteq.presustentaciones.dto.SeguimientoDTO obtenerSeguimiento(Long solicitudId) {
        Solicitud s = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));
                
        String codEstado = s.getEstado() != null ? s.getEstado().getCodigo() : "";
        
        List<ec.edu.uteq.presustentaciones.dto.EtapaSeguimientoDTO> etapas = new java.util.ArrayList<>();
        
        // Etapa 1: Solicitud registrada
        etapas.add(ec.edu.uteq.presustentaciones.dto.EtapaSeguimientoDTO.builder()
                .nombre("Solicitud registrada")
                .estadoVisual("COMPLETADO")
                .fecha(s.getFechaRegistro())
                .descripcion("Solicitud creada en el sistema.")
                .build());
                
        // Etapa 2: Revisión de solicitud
        String revEstado = "PENDIENTE";
        if (codEstado.equals("ENVIADA")) revEstado = "EN_PROCESO";
        else if (codEstado.equals("APROBADA") || codEstado.equals("RECHAZADA")) revEstado = "COMPLETADO";
        etapas.add(ec.edu.uteq.presustentaciones.dto.EtapaSeguimientoDTO.builder()
                .nombre("Revisión de solicitud")
                .estadoVisual(revEstado)
                .fecha(codEstado.equals("ENVIADA") ? s.getActualizadoEn() : null)
                .descripcion("Revisión por parte de coordinación.")
                .build());
                
        // Etapa 3: Solicitud aprobada
        String aprEstado = "PENDIENTE";
        if (codEstado.equals("APROBADA")) aprEstado = "COMPLETADO";
        else if (codEstado.equals("RECHAZADA")) aprEstado = "RECHAZADO";
        etapas.add(ec.edu.uteq.presustentaciones.dto.EtapaSeguimientoDTO.builder()
                .nombre("Aprobación de solicitud")
                .estadoVisual(aprEstado)
                .fecha(codEstado.equals("APROBADA") || codEstado.equals("RECHAZADA") ? s.getActualizadoEn() : null)
                .descripcion(codEstado.equals("RECHAZADA") ? "Rechazada: " + s.getObservaciones() : "Solicitud aprobada.")
                .build());
                
        // Etapa 4: Anteproyecto
        boolean tienePdf = anteproyectoRepository.findBySolicitudId(solicitudId)
                .map(a -> a.getArchivoPdf() != null && !a.getArchivoPdf().isBlank())
                .orElse(false);
        etapas.add(ec.edu.uteq.presustentaciones.dto.EtapaSeguimientoDTO.builder()
                .nombre("Anteproyecto")
                .estadoVisual(tienePdf ? "COMPLETADO" : (codEstado.equals("APROBADA") ? "EN_PROCESO" : "PENDIENTE"))
                .fecha(null)
                .descripcion(tienePdf ? "Anteproyecto cargado." : "Pendiente de cargar.")
                .build());
                
        // Resto de etapas pendientes (simplificadas al no estar completamente desarrolladas en este nivel)
        etapas.add(ec.edu.uteq.presustentaciones.dto.EtapaSeguimientoDTO.builder().nombre("Observaciones").estadoVisual("PENDIENTE").build());
        etapas.add(ec.edu.uteq.presustentaciones.dto.EtapaSeguimientoDTO.builder().nombre("Tutoría").estadoVisual("PENDIENTE").build());
        etapas.add(ec.edu.uteq.presustentaciones.dto.EtapaSeguimientoDTO.builder().nombre("Asignación de jurados").estadoVisual("PENDIENTE").build());
        etapas.add(ec.edu.uteq.presustentaciones.dto.EtapaSeguimientoDTO.builder().nombre("Evaluación").estadoVisual("PENDIENTE").build());
        etapas.add(ec.edu.uteq.presustentaciones.dto.EtapaSeguimientoDTO.builder().nombre("Acta").estadoVisual("PENDIENTE").build());
        
        int progreso = 10;
        if (codEstado.equals("ENVIADA")) progreso = 20;
        if (codEstado.equals("APROBADA")) progreso = 40;
        if (tienePdf) progreso = 50;

        return ec.edu.uteq.presustentaciones.dto.SeguimientoDTO.builder()
                .solicitudId(solicitudId)
                .tituloProyecto(s.getTituloTema())
                .estadoActual(s.getEstado().getNombre())
                .porcentajeProgreso(progreso)
                .etapas(etapas)
                .build();
    }
}