package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.Program;
import ec.edu.uteq.presustentaciones.entities.AnnouncementTitulacion;
import ec.edu.uteq.presustentaciones.entities.Student;
import ec.edu.uteq.presustentaciones.entities.ModalityTitulacion;
import ec.edu.uteq.presustentaciones.entities.PeriodAcademico;
import ec.edu.uteq.presustentaciones.entities.Submission;
import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.repositories.ProposalRepository;
import ec.edu.uteq.presustentaciones.repositories.AreaTematicaRepository;
import ec.edu.uteq.presustentaciones.repositories.ProgramRepository;
import ec.edu.uteq.presustentaciones.repositories.AnnouncementTitulacionRepository;
import ec.edu.uteq.presustentaciones.repositories.StudentRepository;
import ec.edu.uteq.presustentaciones.repositories.LineInvestigacionRepository;
import ec.edu.uteq.presustentaciones.repositories.ModalityTitulacionRepository;
import ec.edu.uteq.presustentaciones.repositories.PeriodAcademicoRepository;
import ec.edu.uteq.presustentaciones.repositories.SubmissionRepository;
import ec.edu.uteq.presustentaciones.repositories.AppUserRepository;
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
public class SubmissionServiceImpl implements SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final StudentRepository studentRepository;
    private final ProposalRepository proposalRepository;
    private final NotificationService notificationService;
    private final AppUserRepository appUserRepository;
    private final ec.edu.uteq.presustentaciones.repositories.EstadoSubmissionRepository estadoSubmissionRepository;
    private final ModalityTitulacionRepository modalityTitulacionRepository;
    private final AnnouncementTitulacionRepository announcementTitulacionRepository;
    private final ProgramRepository programRepository;
    private final PeriodAcademicoRepository periodAcademicoRepository;
    private final LineInvestigacionRepository lineInvestigacionRepository;
    private final AreaTematicaRepository areaTematicaRepository;
    private final AuditService auditService;
    private final ec.edu.uteq.presustentaciones.repositories.EstadoAcademicoRepository estadoAcademicoRepository;

    // ─── Helpers ────────────────────────────────────────────────────────────

    private void notifyAdmins(String mensaje) {
        List<AppUser> admins = appUserRepository.findByRole("ADMIN");
        for (AppUser admin : admins) {
            try {
                notificationService.createNotification(admin.getId(), mensaje);
            } catch (Exception e) {
                log.warn("No se pudo notificar al coordinador ID {}: {}", admin.getId(), e.getMessage());
            }
        }
    }

    private void notifyStudent(Submission submission, String mensaje) {
        try {
            Long appUserId = submission.getStudent().getAppUser().getId();
            notificationService.createNotification(appUserId, mensaje);
        } catch (Exception e) {
            log.warn("No se pudo notificar al estudiante de solicitud ID {}: {}", submission.getId(), e.getMessage());
        }
    }

    // ─── Métodos ─────────────────────────────────────────────────────────────

    /**
     * @param studentId id del {@code Student} propietario de la submission
     * @param datos        datos de la submission a create (título del topic, modality, etc.)
     * @return la submission creada y persistida, con su estado y student asociados
     * @throws RuntimeException si el student no existe o falta la modality de titulación
     */
    @Override
    @Transactional
    @CacheEvict(value = "solicitudes", allEntries = true)
    public Submission createSubmission(Long studentId, Submission datos) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Estudiante no encontrado con ID: " + studentId));

        if (datos.getTituloTopic() == null || datos.getTituloTopic().trim().isEmpty()) {
            throw new RuntimeException("El título del tema es obligatorio");
        }
        if (datos.getTituloTopic().length() > 300) {
            throw new RuntimeException("El título del tema no puede exceder los 300 caracteres");
        }

        // Resolve estado inicial
        ec.edu.uteq.presustentaciones.entities.EstadoSubmission estadoCreada = estadoSubmissionRepository.findByCodigo("CREADA")
                .orElseGet(() -> estadoSubmissionRepository.save(ec.edu.uteq.presustentaciones.entities.EstadoSubmission.builder()
                        .codigo("CREADA").nombre("Creada").build()));

        // Resolve modality: si el objeto ya viene completo (con id) úsalo; si no, error
        if (datos.getModalityTitulacion() == null || datos.getModalityTitulacion().getId() == null) {
            throw new RuntimeException("Debe seleccionar una modalidad de titulación válida");
        }
        ModalityTitulacion modality = modalityTitulacionRepository
                .findById(datos.getModalityTitulacion().getId())
                .orElseThrow(() -> new RuntimeException("Modalidad no encontrada con ID: " + datos.getModalityTitulacion().getId()));
        datos.setModalityTitulacion(modality);

        // Resolve announcement: si viene en el body úroom, si no search/create la activa
        if (datos.getAnnouncement() == null || datos.getAnnouncement().getId() == null) {
            AnnouncementTitulacion convActiva = announcementTitulacionRepository
                    .findFirstByActivaTrue()
                    .orElseGet(this::createAnnouncementDefault);
            datos.setAnnouncement(convActiva);
        } else {
            AnnouncementTitulacion announcement = announcementTitulacionRepository
                    .findById(datos.getAnnouncement().getId())
                    .orElseThrow(() -> new RuntimeException("Convocatoria no encontrada con ID: " + datos.getAnnouncement().getId()));
            datos.setAnnouncement(announcement);
        }

        // Resolve línea de investigación (opcional, igual que en la columna real de la BD)
        if (datos.getLineInvestigacion() != null && datos.getLineInvestigacion().getId() != null) {
            ec.edu.uteq.presustentaciones.entities.LineInvestigacion line = lineInvestigacionRepository
                    .findById(datos.getLineInvestigacion().getId())
                    .orElseThrow(() -> new RuntimeException("Línea de investigación no encontrada con ID: " + datos.getLineInvestigacion().getId()));
            datos.setLineInvestigacion(line);
        } else {
            datos.setLineInvestigacion(null);
        }

        // Resolve área temática (opcional); si viene, debe pertenecer a la línea seleccionada
        if (datos.getAreaTematica() != null && datos.getAreaTematica().getId() != null) {
            ec.edu.uteq.presustentaciones.entities.AreaTematica area = areaTematicaRepository
                    .findById(datos.getAreaTematica().getId())
                    .orElseThrow(() -> new RuntimeException("Área temática no encontrada con ID: " + datos.getAreaTematica().getId()));
            if (datos.getLineInvestigacion() != null
                    && !area.getLineInvestigacion().getId().equals(datos.getLineInvestigacion().getId())) {
                throw new RuntimeException("El área temática seleccionada no pertenece a la línea de investigación elegida");
            }
            datos.setAreaTematica(area);
        } else {
            datos.setAreaTematica(null);
        }

        datos.setEstado(estadoCreada);
        datos.setStudent(student);
        datos.setCreadoPor(student.getAppUser());
        datos.setActualizadoPor(student.getAppUser());
        datos.setFechaRegistro(LocalDateTime.now());
        datos.setActualizadoEn(LocalDateTime.now());
        auditService.marcarActorActual();
        return submissionRepository.save(datos);
    }

    /**
     * @param appUserId id del {@code AppUser} autenticado (role ESTUDIANTE)
     * @param datos     datos de la submission a create
     * @return la submission creada
     * @throws RuntimeException si el appUser no existe, no tiene role ESTUDIANTE, o no hay
     *                          programs configuradas para create el perfil automáticamente
     */
    @Override
    @Transactional
    @CacheEvict(value = "solicitudes", allEntries = true)
    public Submission createSubmissionPorAppUser(Long appUserId, Submission datos) {
        // NOTA: createSubmission(...) también tiene @CacheEvict, pero como se invoca aquí
        // como "this.crearSolicitud(...)" (autoinvocación dentro de la misma clase), el
        // proxy de Spring AOP se salta y esa anotación nunca se dispara. Por eso este
        // método necesita su propio @CacheEvict: es el que realmente atraviesa el proxy
        // cuando lo llama el controlador (POST /api/submissions/create-por-appUser/{id}).
        // Bug real: /mis-submissions seguía devolviendo la lista vacía cacheada después
        // de create una submission nueva.

        // Search perfil de student; si no existe, crearlo automáticamente
        Student student = studentRepository.findByAppUserId(appUserId)
                .orElseGet(() -> createPerfilStudent(appUserId));
        return createSubmission(student.getId(), datos);
    }

    /**
     * Crea automáticamente un PeriodAcademico + AnnouncementTitulacion activos
     * cuando la base de datos no tiene ninguno configurado (instalación inicial).
     */
    @Transactional
    private AnnouncementTitulacion createAnnouncementDefault() {
        int anio = java.time.Year.now().getValue();

        // Create o reusar período académico del año actual
        PeriodAcademico period = periodAcademicoRepository
                .findByCodigo("PA-" + anio)
                .orElseGet(() -> {
                    log.info("Creando período académico por defecto para año {}", anio);
                    return periodAcademicoRepository.save(PeriodAcademico.builder()
                            .codigo("PA-" + anio)
                            .nombre("Período Académico " + anio)
                            .fechaInicio(LocalDate.of(anio, 1, 1))
                            .fechaFin(LocalDate.of(anio, 12, 31))
                            .activo(true)
                            .build());
                });

        // Create announcement activa ligada al período
        log.info("Creando convocatoria activa por defecto para período {}", period.getCodigo());
        return announcementTitulacionRepository.save(AnnouncementTitulacion.builder()
                .codigo("CONV-" + anio + "-01")
                .nombre("Convocatoria " + anio + " – Período I")
                .periodAcademico(period)
                .fechaInicio(LocalDate.of(anio, 1, 1))
                .fechaFin(LocalDate.of(anio, 12, 31))
                .activa(true)
                .build());
    }

    /**
     * Crea automáticamente el perfil Student para un appUser con role ESTUDIANTE
     * que aún no tenga registro en la tabla student.
     */
    @Transactional
    private Student createPerfilStudent(Long appUserId) {
        AppUser appUser = appUserRepository.findById(appUserId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + appUserId));

        // Verify que realmente sea un student
        if (!"ESTUDIANTE".equalsIgnoreCase(appUser.getRole())) {
            throw new RuntimeException("El usuario no tiene rol de estudiante");
        }

        // Obtain la primera program disponible como default
        Program programDefault = programRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No hay carreras configuradas en el sistema. Contacte al administrador."));

        log.info("Creando perfil de estudiante automáticamente para usuario ID: {}", appUserId);

        // sp_generate_codigo_expediente (Fase 3 / Criterio P1, categoría "generación de
        // códigos secuenciales"): nextval() sobre una secuencia dedicada es atómico a nivel
        // de motor, así que dos altas concurrentes nunca reciben el mismo código.
        String expedienteCodigo = studentRepository.generateCodigoExpediente(null, null);

        ec.edu.uteq.presustentaciones.entities.EstadoAcademico estadoActivo = estadoAcademicoRepository.findByCodigo("ACTIVO")
                .orElseThrow(() -> new RuntimeException("Catálogo de estados académicos no sembrado"));

        Student nuevoStudent = Student.builder()
                .appUser(appUser)
                .program(programDefault.getNombre())
                .programEntidad(programDefault)
                .semestreActual((short) 1)
                .semestre("1ro")
                .expedienteCodigo(expedienteCodigo)
                .estadoAcademico(estadoActivo)
                .build();

        return studentRepository.save(nuevoStudent);
    }
 
    /**
     * @param appUserId id del appUser (se resuelve a su perfil de student internamente)
     * @return las submissions del student asociado a ese appUser, o lista vacía si no tiene
     *         perfil de student todavía
     */
    @Override
    @Cacheable(value = "solicitudes", key = "'usuario:' + #usuarioId")
    public List<Submission> listPorAppUser(Long appUserId) {
        return studentRepository.findByAppUserId(appUserId)
                .map(e -> submissionRepository.findByStudentId(e.getId()))
                .orElse(java.util.Collections.emptyList());
    }
 
    /**
     * @param submissionId id de la submission a send
     * @return la submission actualizada en estado "ENVIADA"
     * @throws RuntimeException si la submission no existe o no tiene proposal adjunto
     */
    @Override
    @Transactional
    @CacheEvict(value = "solicitudes", allEntries = true)
    public Submission sendSubmission(Long submissionId) {
        Submission s = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));
 
        boolean tienePdf = proposalRepository.findBySubmissionId(submissionId)
                .map(a -> a.getArchivoPdf() != null && !a.getArchivoPdf().isBlank())
                .orElse(false);
 
        if (!tienePdf) {
            throw new RuntimeException("Debes cargar el PDF del anteproyecto antes de enviar la solicitud a revisión.");
        }
 
        ec.edu.uteq.presustentaciones.entities.EstadoSubmission estadoEnviada = estadoSubmissionRepository.findByCodigo("ENVIADA")
                .orElseGet(() -> estadoSubmissionRepository.save(ec.edu.uteq.presustentaciones.entities.EstadoSubmission.builder()
                        .codigo("ENVIADA").nombre("Enviada").build()));

        s.setEstado(estadoEnviada);
        Submission guardada = submissionRepository.save(s);
 
        String nombreStudent = s.getStudent().getAppUser().getNombre()
                + " " + s.getStudent().getAppUser().getApellido();
 
        notifyAdmins(String.format(
                "📋 Nueva solicitud de %s: \"%s\" está pendiente de revisión.",
                nombreStudent, s.getTituloTopic()));
 
        return guardada;
    }
 
    /**
     * @param submissionId id de la submission a approve
     * @return la submission actualizada
     */
    @Override
    @Transactional
    @CacheEvict(value = "solicitudes", allEntries = true)
    public Submission approveSubmission(Long submissionId) {
        auditService.marcarActorActual();
        Submission s = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));

        ec.edu.uteq.presustentaciones.entities.EstadoSubmission estadoAprobada = estadoSubmissionRepository.findByCodigo("APROBADA")
                .orElseGet(() -> estadoSubmissionRepository.save(ec.edu.uteq.presustentaciones.entities.EstadoSubmission.builder()
                        .codigo("APROBADA").nombre("Aprobada").build()));

        s.setEstado(estadoAprobada);
        Submission guardada = submissionRepository.save(s);

        notifyStudent(s, String.format(
                "✅ Tu solicitud \"%s\" ha sido APROBADA. Pronto se te asignará fecha y tribunal.",
                s.getTituloTopic()));

        return guardada;
    }
 
    /**
     * @param submissionId id de la submission a reject
     * @return la submission actualizada en estado "RECHAZADA"
     */
    @Override
    @Transactional
    @CacheEvict(value = "solicitudes", allEntries = true)
    public Submission rejectSubmission(Long submissionId) {
        return rejectConObservacion(submissionId, null);
    }

    /**
     * @param submissionId  id de la submission a reject
     * @param observacion  motivo del rechazo, visible luego para el student
     * @return la submission actualizada en estado "RECHAZADA" con la observación guardada
     */
    @Override
    @Transactional
    @CacheEvict(value = "solicitudes", allEntries = true)
    public Submission rejectConObservacion(Long submissionId, String observacion) {
        auditService.marcarActorActual();
        Submission s = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));

        ec.edu.uteq.presustentaciones.entities.EstadoSubmission estadoRechazada = estadoSubmissionRepository.findByCodigo("RECHAZADA")
                .orElseGet(() -> estadoSubmissionRepository.save(ec.edu.uteq.presustentaciones.entities.EstadoSubmission.builder()
                        .codigo("RECHAZADA").nombre("Rechazada").build()));

        s.setEstado(estadoRechazada);
        if (observacion != null && !observacion.isBlank()) {
            s.setObservaciones(observacion);
        }
        Submission guardada = submissionRepository.save(s);
 
        String obs = (s.getObservaciones() != null && !s.getObservaciones().isBlank())
                ? " Motivo: " + s.getObservaciones() : "";
        notifyStudent(s, String.format(
                "❌ Tu solicitud \"%s\" ha sido RECHAZADA.%s Revisa las observaciones.",
                s.getTituloTopic(), obs));
 
        return guardada;
    }
 
    /** Cota dura del endpoint sin paginar: solo las submissions más recientes. Con el volumen
     *  real (44k+ submissions) traerlas todas son ~93 MB de JSON que congelan el navegador y
     *  llenan Redis. El listado completo navegable es GET /api/v1/submissions/paginado. */
    private static final int LIMITE_LISTADO_SIN_PAGINAR = 500;

    /** @return todas las submissions del sistema, sin paginar */
    @Override
    @Cacheable(value = "solicitudes", key = "'all'")
    public List<Submission> listSubmissions() {
        return submissionRepository.findAllWithStudent(
                PageRequest.of(0, LIMITE_LISTADO_SIN_PAGINAR));
    }

    /**
     * @param pagina       número de página, base 0
     * @param tamanio      tamaño de página
     * @param estado       código de estado por el que filtrar, o {@code null} para no filtrar
     * @param texto        texto libre de búsqueda (título/student), o {@code null}
     * @param fechaDesde   fecha mínima de registro, o {@code null} para no acotar
     * @param fechaHasta   fecha máxima de registro, o {@code null} para no acotar
     * @return página de submissions que cumplen los filtros
     */
    @Override
    public Page<Submission> listSubmissionsPaginado(int pagina, int tamanio, String estado, String texto,
                                                       LocalDate fechaDesde, LocalDate fechaHasta) {
        int paginaSegura = Math.max(pagina, 0);
        int tamanioSeguro = Math.min(Math.max(tamanio, 1), 100);
        PageRequest pageRequest = PageRequest.of(paginaSegura, tamanioSeguro, Sort.by(Sort.Direction.DESC, "fechaRegistro"));
        // fechaRegistro es timestamp -- se acota al día completo (00:00:00 a 23:59:59.999999999)
        // para que filtrar por "hoy" o por un día puntual incluya todas las horas de ese día.
        // Se usan centinelas (1900/2999) en vez de pasar null al JPQL: Hibernate no logra
        // inferir el tipo SQL de un parámetro null reutilizado dentro de "x IS NULL OR campo >= x"
        // contra Postgres (falla con "cannot cast type bytea to timestamp"), así que en vez de
        // ese patrón se acota siempre a un rango concreto, sin importar si el appUser filtró o no.
        LocalDateTime desde = fechaDesde != null ? fechaDesde.atStartOfDay() : LocalDateTime.of(1900, 1, 1, 0, 0);
        LocalDateTime hasta = fechaHasta != null ? fechaHasta.atTime(LocalTime.MAX) : LocalDateTime.of(2999, 12, 31, 23, 59, 59);
        return submissionRepository.searchConFiltros(estado, texto, desde, hasta, pageRequest);
    }

    /** @return count de submissions agrupado por código de estado, para el dashboard */
    @Override
    public Map<String, Long> countPorEstado() {
        Map<String, Long> counts = new LinkedHashMap<>();
        long total = submissionRepository.count();
        counts.put("TODAS", total);
        for (SubmissionRepository.EstadoCount c : submissionRepository.countAgrupadoPorEstado()) {
            counts.put(c.getCodigo(), c.getTotal());
        }
        return counts;
    }

    /**
     * @param studentId id del student
     * @return todas las submissions registradas por ese student
     */
    @Override
    @Cacheable(value = "solicitudes", key = "'estudiante:' + #estudianteId")
    public List<Submission> listPorStudent(Long studentId) {
        return submissionRepository.findByStudentId(studentId);
    }
 
    /**
     * @param id id de la submission
     * @return la submission si existe, o {@link Optional#empty()} en caso contrario
     */
    @Override
    @Cacheable(value = "solicitudes", key = "#id", unless = "#result == null")
    public Optional<Submission> obtainPorId(Long id) {
        return submissionRepository.findById(id);
    }
 
    /**
     * @param submissionId id de la submission a suspender
     * @param motivo      motivo de la suspensión; no puede estar vacío
     * @return la submission actualizada en estado "SUSPENDIDA"
     * @throws RuntimeException si la submission no existe, su estado actual no permite
     *                          suspensión, o el motivo está vacío
     */
    @Override
    @Transactional
    @CacheEvict(value = "solicitudes", allEntries = true)
    public Submission suspenderSubmission(Long submissionId, String motivo) {
        auditService.marcarActorActual();
        Submission s = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));
 
        String codEstado = s.getEstado() != null ? s.getEstado().getCodigo() : "";
        boolean esSuspendible = !"CREADA".equals(codEstado) && !"RECHAZADA".equals(codEstado) && !"SUSPENDIDA".equals(codEstado);
        if (!esSuspendible) {
            throw new RuntimeException("La solicitud no puede ser suspendida en su estado actual: " + codEstado);
        }
 
        if (motivo == null || motivo.isBlank()) {
            throw new RuntimeException("Debe especificar el motivo de la suspensión");
        }
 
        ec.edu.uteq.presustentaciones.entities.EstadoSubmission estadoSuspendida = estadoSubmissionRepository.findByCodigo("SUSPENDIDA")
                .orElseGet(() -> estadoSubmissionRepository.save(ec.edu.uteq.presustentaciones.entities.EstadoSubmission.builder()
                        .codigo("SUSPENDIDA").nombre("Suspendida").build()));

        s.setEstado(estadoSuspendida);
        s.setMotivoSuspension(motivo);
        s.setSuspendidoEn(LocalDateTime.now());

        Submission guardada = submissionRepository.save(s);
        log.info("Solicitud {} suspendida desde estado {} por motivo: {}", submissionId, codEstado, motivo);
 
        notifyStudent(s, String.format(
                "🚫 Tu trabajo \"%s\" ha sido SUSPENDIDO. Motivo: %s. No podrás continuar.",
                s.getTituloTopic(), motivo));

        return guardada;
    }

    /**
     * @param program nombre (o coincidencia parcial, {@code ILIKE}) de la program a filtrar
     * @return una fila por defensa, con las claves declaradas en
     *         {@code docs/basedatos/CATALOGO-SP.md} (submissionId, studentNombre, expediente,
     *         tituloTopic, estadoSubmission, fechaDefensa, roomNombre, notaFinal)
     */
    @Override
    public List<Map<String, Object>> generateReporteDefensasSP(String program) {
        List<Object[]> res = submissionRepository.generateReporteDefensasSp(program);
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
     * @param submissionId el ID de la submission
     * @return un DTO con el progress y etapas del process
     */
    @Override
    public ec.edu.uteq.presustentaciones.dto.TrackingDTO obtainTracking(Long submissionId) {
        Submission s = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));
                
        String codEstado = s.getEstado() != null ? s.getEstado().getCodigo() : "";
        
        List<ec.edu.uteq.presustentaciones.dto.EtapaTrackingDTO> etapas = new java.util.ArrayList<>();
        
        // Etapa 1: Submission registrada
        etapas.add(ec.edu.uteq.presustentaciones.dto.EtapaTrackingDTO.builder()
                .nombre("Solicitud registrada")
                .estadoVisual("COMPLETADO")
                .fecha(s.getFechaRegistro())
                .descripcion("Solicitud creada en el sistema.")
                .build());
                
        // Etapa 2: Revisión de submission
        String revEstado = "PENDIENTE";
        if (codEstado.equals("ENVIADA")) revEstado = "EN_PROCESO";
        else if (codEstado.equals("APROBADA") || codEstado.equals("RECHAZADA")) revEstado = "COMPLETADO";
        etapas.add(ec.edu.uteq.presustentaciones.dto.EtapaTrackingDTO.builder()
                .nombre("Revisión de solicitud")
                .estadoVisual(revEstado)
                .fecha(codEstado.equals("ENVIADA") ? s.getActualizadoEn() : null)
                .descripcion("Revisión por parte de coordinación.")
                .build());
                
        // Etapa 3: Submission aprobada
        String aprEstado = "PENDIENTE";
        if (codEstado.equals("APROBADA")) aprEstado = "COMPLETADO";
        else if (codEstado.equals("RECHAZADA")) aprEstado = "RECHAZADO";
        etapas.add(ec.edu.uteq.presustentaciones.dto.EtapaTrackingDTO.builder()
                .nombre("Aprobación de solicitud")
                .estadoVisual(aprEstado)
                .fecha(codEstado.equals("APROBADA") || codEstado.equals("RECHAZADA") ? s.getActualizadoEn() : null)
                .descripcion(codEstado.equals("RECHAZADA") ? "Rechazada: " + s.getObservaciones() : "Solicitud aprobada.")
                .build());
                
        // Etapa 4: Proposal
        boolean tienePdf = proposalRepository.findBySubmissionId(submissionId)
                .map(a -> a.getArchivoPdf() != null && !a.getArchivoPdf().isBlank())
                .orElse(false);
        etapas.add(ec.edu.uteq.presustentaciones.dto.EtapaTrackingDTO.builder()
                .nombre("Anteproyecto")
                .estadoVisual(tienePdf ? "COMPLETADO" : (codEstado.equals("APROBADA") ? "EN_PROCESO" : "PENDIENTE"))
                .fecha(null)
                .descripcion(tienePdf ? "Anteproyecto cargado." : "Pendiente de cargar.")
                .build());
                
        // Resto de etapas pendientes (simplificadas al no estar completamente desarrolladas en este nivel)
        etapas.add(ec.edu.uteq.presustentaciones.dto.EtapaTrackingDTO.builder().nombre("Observaciones").estadoVisual("PENDIENTE").build());
        etapas.add(ec.edu.uteq.presustentaciones.dto.EtapaTrackingDTO.builder().nombre("Tutoría").estadoVisual("PENDIENTE").build());
        etapas.add(ec.edu.uteq.presustentaciones.dto.EtapaTrackingDTO.builder().nombre("Asignación de jurados").estadoVisual("PENDIENTE").build());
        etapas.add(ec.edu.uteq.presustentaciones.dto.EtapaTrackingDTO.builder().nombre("Evaluación").estadoVisual("PENDIENTE").build());
        etapas.add(ec.edu.uteq.presustentaciones.dto.EtapaTrackingDTO.builder().nombre("Acta").estadoVisual("PENDIENTE").build());
        
        int progress = 10;
        if (codEstado.equals("ENVIADA")) progress = 20;
        if (codEstado.equals("APROBADA")) progress = 40;
        if (tienePdf) progress = 50;

        return ec.edu.uteq.presustentaciones.dto.TrackingDTO.builder()
                .submissionId(submissionId)
                .tituloProyecto(s.getTituloTopic())
                .estadoActual(s.getEstado().getNombre())
                .porcentajeProgress(progress)
                .etapas(etapas)
                .build();
    }
}