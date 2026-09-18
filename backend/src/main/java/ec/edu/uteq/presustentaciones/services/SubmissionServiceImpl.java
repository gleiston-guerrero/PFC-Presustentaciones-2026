package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.Program;
import ec.edu.uteq.presustentaciones.entities.AnnouncementDegree;
import ec.edu.uteq.presustentaciones.entities.Student;
import ec.edu.uteq.presustentaciones.entities.ModalityDegree;
import ec.edu.uteq.presustentaciones.entities.PeriodAcademic;
import ec.edu.uteq.presustentaciones.entities.Submission;
import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.repositories.ProposalRepository;
import ec.edu.uteq.presustentaciones.repositories.SubjectRepository;
import ec.edu.uteq.presustentaciones.repositories.ProgramRepository;
import ec.edu.uteq.presustentaciones.repositories.AnnouncementDegreeRepository;
import ec.edu.uteq.presustentaciones.repositories.StudentRepository;
import ec.edu.uteq.presustentaciones.repositories.ResearchLineRepository;
import ec.edu.uteq.presustentaciones.repositories.ModalityDegreeRepository;
import ec.edu.uteq.presustentaciones.repositories.PeriodAcademicRepository;
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
    private final ec.edu.uteq.presustentaciones.repositories.StatusSubmissionRepository statusSubmissionRepository;
    private final ModalityDegreeRepository modalityDegreeRepository;
    private final AnnouncementDegreeRepository announcementDegreeRepository;
    private final ProgramRepository programRepository;
    private final PeriodAcademicRepository periodAcademicRepository;
    private final ResearchLineRepository researchLineRepository;
    private final SubjectRepository subjectRepository;
    private final AuditService auditService;
    private final ec.edu.uteq.presustentaciones.repositories.StatusAcademicRepository statusAcademicRepository;

    // ─── Helpers ────────────────────────────────────────────────────────────

    private void notifyAdmins(String message) {
        List<AppUser> admins = appUserRepository.findByRole("ADMIN");
        for (AppUser admin : admins) {
            try {
                notificationService.createNotification(admin.getId(), message);
            } catch (Exception e) {
                log.warn("No se pudo notificar al coordinador ID {}: {}", admin.getId(), e.getMessage());
            }
        }
    }

    private void notifyStudent(Submission submission, String message) {
        try {
            Long appUserId = submission.getStudent().getAppUser().getId();
            notificationService.createNotification(appUserId, message);
        } catch (Exception e) {
            log.warn("No se pudo notificar al estudiante de solicitud ID {}: {}", submission.getId(), e.getMessage());
        }
    }

    // ─── Métodos ─────────────────────────────────────────────────────────────

    /**
     * @param studentId id del {@code Student} propietario de la submission
     * @param data        datos de la submission a create (título del topic, modality, etc.)
     * @return la submission creada y persistida, con su estado y student asociados
     * @throws RuntimeException si el student no existe o falta la modality de titulación
     */
    @Override
    @Transactional
    @CacheEvict(value = "solicitudes", allEntries = true)
    public Submission createSubmission(Long studentId, Submission data) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Estudiante no encontrado con ID: " + studentId));

        if (data.getTituloTopic() == null || data.getTituloTopic().trim().isEmpty()) {
            throw new RuntimeException("El título del tema es obligatorio");
        }
        if (data.getTituloTopic().length() > 300) {
            throw new RuntimeException("El título del tema no puede exceder los 300 caracteres");
        }

        // Resolve estado inicial
        ec.edu.uteq.presustentaciones.entities.StatusSubmission statusCreada = statusSubmissionRepository.findByCode("CREADA")
                .orElseGet(() -> statusSubmissionRepository.save(ec.edu.uteq.presustentaciones.entities.StatusSubmission.builder()
                        .code("CREADA").nombre("Creada").build()));

        // Resolve modality: si el objeto ya viene completo (con id) úsalo; si no, error
        if (data.getModalityDegree() == null || data.getModalityDegree().getId() == null) {
            throw new RuntimeException("Debe seleccionar una modalidad de titulación válida");
        }
        ModalityDegree modality = modalityDegreeRepository
                .findById(data.getModalityDegree().getId())
                .orElseThrow(() -> new RuntimeException("Modalidad no encontrada con ID: " + data.getModalityDegree().getId()));
        data.setModalityDegree(modality);

        // Resolve announcement: si viene en el body úroom, si no search/create la activa
        if (data.getAnnouncement() == null || data.getAnnouncement().getId() == null) {
            AnnouncementDegree convActive = announcementDegreeRepository
                    .findFirstByActiveTrue()
                    .orElseGet(this::createAnnouncementDefault);
            data.setAnnouncement(convActive);
        } else {
            AnnouncementDegree announcement = announcementDegreeRepository
                    .findById(data.getAnnouncement().getId())
                    .orElseThrow(() -> new RuntimeException("Convocatoria no encontrada con ID: " + data.getAnnouncement().getId()));
            data.setAnnouncement(announcement);
        }

        // Resolve línea de investigación (opcional, igual que en la columna real de la BD)
        if (data.getResearchLine() != null && data.getResearchLine().getId() != null) {
            ec.edu.uteq.presustentaciones.entities.ResearchLine line = researchLineRepository
                    .findById(data.getResearchLine().getId())
                    .orElseThrow(() -> new RuntimeException("Línea de investigación no encontrada con ID: " + data.getResearchLine().getId()));
            data.setResearchLine(line);
        } else {
            data.setResearchLine(null);
        }

        // Resolve área temática (opcional); si viene, debe pertenecer a la línea seleccionada
        if (data.getSubject() != null && data.getSubject().getId() != null) {
            ec.edu.uteq.presustentaciones.entities.Subject area = subjectRepository
                    .findById(data.getSubject().getId())
                    .orElseThrow(() -> new RuntimeException("Área temática no encontrada con ID: " + data.getSubject().getId()));
            if (data.getResearchLine() != null
                    && !area.getResearchLine().getId().equals(data.getResearchLine().getId())) {
                throw new RuntimeException("El área temática seleccionada no pertenece a la línea de investigación elegida");
            }
            data.setSubject(area);
        } else {
            data.setSubject(null);
        }

        data.setStatus(statusCreada);
        data.setStudent(student);
        data.setCreadoBy(student.getAppUser());
        data.setActualizadoBy(student.getAppUser());
        data.setDateRecord(LocalDateTime.now());
        data.setActualizadoEn(LocalDateTime.now());
        auditService.markActorActual();
        return submissionRepository.save(data);
    }

    /**
     * @param appUserId id del {@code AppUser} autenticado (role ESTUDIANTE)
     * @param data     datos de la submission a create
     * @return la submission creada
     * @throws RuntimeException si el appUser no existe, no tiene role ESTUDIANTE, o no hay
     *                          programs configuradas para create el perfil automáticamente
     */
    @Override
    @Transactional
    @CacheEvict(value = "solicitudes", allEntries = true)
    public Submission createSubmissionByAppUser(Long appUserId, Submission data) {
        // NOTA: createSubmission(...) también tiene @CacheEvict, pero como se invoca aquí
        // como "this.crearSolicitud(...)" (autoinvocación dentro de la misma clase), el
        // proxy de Spring AOP se salta y esa anotación nunca se dispara. Por eso este
        // método necesita su propio @CacheEvict: es el que realmente atraviesa el proxy
        // cuando lo llama el controlador (POST /api/submissions/create-por-appUser/{id}).
        // Bug real: /mis-submissions seguía devolviendo la lista vacía cacheada después
        // de create una submission nueva.

        // Search perfil de student; si no existe, crearlo automáticamente
        Student student = studentRepository.findByAppUserId(appUserId)
                .orElseGet(() -> createProfileStudent(appUserId));
        return createSubmission(student.getId(), data);
    }

    /**
     * Crea automáticamente un PeriodAcademico + AnnouncementTitulacion activos
     * cuando la base de datos no tiene ninguno configurado (instalación inicial).
     */
    @Transactional
    private AnnouncementDegree createAnnouncementDefault() {
        int anio = java.time.Year.now().getValue();

        // Create o reusar período académico del año actual
        PeriodAcademic period = periodAcademicRepository
                .findByCode("PA-" + anio)
                .orElseGet(() -> {
                    log.info("Creando período académico por defecto para año {}", anio);
                    return periodAcademicRepository.save(PeriodAcademic.builder()
                            .code("PA-" + anio)
                            .nombre("Período Académico " + anio)
                            .dateStart(LocalDate.of(anio, 1, 1))
                            .dateEnd(LocalDate.of(anio, 12, 31))
                            .activo(true)
                            .build());
                });

        // Create announcement activa ligada al período
        log.info("Creando convocatoria activa por defecto para período {}", period.getCode());
        return announcementDegreeRepository.save(AnnouncementDegree.builder()
                .code("CONV-" + anio + "-01")
                .nombre("Convocatoria " + anio + " – Período I")
                .periodAcademic(period)
                .dateStart(LocalDate.of(anio, 1, 1))
                .dateEnd(LocalDate.of(anio, 12, 31))
                .active(true)
                .build());
    }

    /**
     * Crea automáticamente el perfil Student para un appUser con role ESTUDIANTE
     * que aún no tenga registro en la tabla student.
     */
    @Transactional
    private Student createProfileStudent(Long appUserId) {
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
        String expedienteCode = studentRepository.generateCodeExpediente(null, null);

        ec.edu.uteq.presustentaciones.entities.StatusAcademic statusActivo = statusAcademicRepository.findByCode("ACTIVO")
                .orElseThrow(() -> new RuntimeException("Catálogo de estados académicos no sembrado"));

        Student targetStudent = Student.builder()
                .appUser(appUser)
                .program(programDefault.getNombre())
                .programEntidad(programDefault)
                .semestreActual((short) 1)
                .semestre("1ro")
                .expedienteCode(expedienteCode)
                .statusAcademic(statusActivo)
                .build();

        return studentRepository.save(targetStudent);
    }
 
    /**
     * @param appUserId id del appUser (se resuelve a su perfil de student internamente)
     * @return las submissions del student asociado a ese appUser, o lista vacía si no tiene
     *         perfil de student todavía
     */
    @Override
    @Cacheable(value = "solicitudes", key = "'usuario:' + #usuarioId")
    public List<Submission> listByAppUser(Long appUserId) {
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
                .map(a -> a.getFilePdf() != null && !a.getFilePdf().isBlank())
                .orElse(false);
 
        if (!tienePdf) {
            throw new RuntimeException("Debes cargar el PDF del anteproyecto antes de enviar la solicitud a revisión.");
        }
 
        ec.edu.uteq.presustentaciones.entities.StatusSubmission statusEnviada = statusSubmissionRepository.findByCode("ENVIADA")
                .orElseGet(() -> statusSubmissionRepository.save(ec.edu.uteq.presustentaciones.entities.StatusSubmission.builder()
                        .code("ENVIADA").nombre("Enviada").build()));

        s.setStatus(statusEnviada);
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
        auditService.markActorActual();
        Submission s = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));

        ec.edu.uteq.presustentaciones.entities.StatusSubmission statusAprobada = statusSubmissionRepository.findByCode("APROBADA")
                .orElseGet(() -> statusSubmissionRepository.save(ec.edu.uteq.presustentaciones.entities.StatusSubmission.builder()
                        .code("APROBADA").nombre("Aprobada").build()));

        s.setStatus(statusAprobada);
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
        return rejectWithObservation(submissionId, null);
    }

    /**
     * @param submissionId  id de la submission a reject
     * @param observation  motivo del rechazo, visible luego para el student
     * @return la submission actualizada en estado "RECHAZADA" con la observación guardada
     */
    @Override
    @Transactional
    @CacheEvict(value = "solicitudes", allEntries = true)
    public Submission rejectWithObservation(Long submissionId, String observation) {
        auditService.markActorActual();
        Submission s = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));

        ec.edu.uteq.presustentaciones.entities.StatusSubmission statusRechazada = statusSubmissionRepository.findByCode("RECHAZADA")
                .orElseGet(() -> statusSubmissionRepository.save(ec.edu.uteq.presustentaciones.entities.StatusSubmission.builder()
                        .code("RECHAZADA").nombre("Rechazada").build()));

        s.setStatus(statusRechazada);
        if (observation != null && !observation.isBlank()) {
            s.setObservations(observation);
        }
        Submission guardada = submissionRepository.save(s);
 
        String obs = (s.getObservations() != null && !s.getObservations().isBlank())
                ? " Motivo: " + s.getObservations() : "";
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
     * @param status       código de estado por el que filtrar, o {@code null} para no filtrar
     * @param texto        texto libre de búsqueda (título/student), o {@code null}
     * @param dateFrom   fecha mínima de registro, o {@code null} para no acotar
     * @param dateTo   fecha máxima de registro, o {@code null} para no acotar
     * @return página de submissions que cumplen los filtros
     */
    @Override
    public Page<Submission> listSubmissionsPaged(int pagina, int tamanio, String status, String texto,
                                                       LocalDate dateFrom, LocalDate dateTo) {
        int paginaSegura = Math.max(pagina, 0);
        int tamanioSeguro = Math.min(Math.max(tamanio, 1), 100);
        PageRequest pageRequest = PageRequest.of(paginaSegura, tamanioSeguro, Sort.by(Sort.Direction.DESC, "dateRegistro"));
        // fechaRegistro es timestamp -- se acota al día completo (00:00:00 a 23:59:59.999999999)
        // para que filtrar por "hoy" o por un día puntual incluya todas las horas de ese día.
        // Se usan centinelas (1900/2999) en vez de pasar null al JPQL: Hibernate no logra
        // inferir el tipo SQL de un parámetro null reutilizado dentro de "x IS NULL OR campo >= x"
        // contra Postgres (falla con "cannot cast type bytea to timestamp"), así que en vez de
        // ese patrón se acota siempre a un rango concreto, sin importar si el appUser filtró o no.
        LocalDateTime from = dateFrom != null ? dateFrom.atStartOfDay() : LocalDateTime.of(1900, 1, 1, 0, 0);
        LocalDateTime to = dateTo != null ? dateTo.atTime(LocalTime.MAX) : LocalDateTime.of(2999, 12, 31, 23, 59, 59);
        return submissionRepository.searchWithFiltros(status, texto, from, to, pageRequest);
    }

    /** @return count de submissions agrupado por código de estado, para el dashboard */
    @Override
    public Map<String, Long> countByStatus() {
        Map<String, Long> counts = new LinkedHashMap<>();
        long total = submissionRepository.count();
        counts.put("TODAS", total);
        for (SubmissionRepository.StatusCount c : submissionRepository.countAgrupadoByStatus()) {
            counts.put(c.getCode(), c.getTotal());
        }
        return counts;
    }

    /**
     * @param studentId id del student
     * @return todas las submissions registradas por ese student
     */
    @Override
    @Cacheable(value = "solicitudes", key = "'estudiante:' + #estudianteId")
    public List<Submission> listByStudent(Long studentId) {
        return submissionRepository.findByStudentId(studentId);
    }
 
    /**
     * @param id id de la submission
     * @return la submission si existe, o {@link Optional#empty()} en caso contrario
     */
    @Override
    @Cacheable(value = "solicitudes", key = "#id", unless = "#result == null")
    public Optional<Submission> obtainById(Long id) {
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
    public Submission suspendSubmission(Long submissionId, String motivo) {
        auditService.markActorActual();
        Submission s = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));
 
        String codStatus = s.getStatus() != null ? s.getStatus().getCode() : "";
        boolean isSuspendable = !"CREADA".equals(codStatus) && !"RECHAZADA".equals(codStatus) && !"SUSPENDIDA".equals(codStatus);
        if (!isSuspendable) {
            throw new RuntimeException("La solicitud no puede ser suspendida en su estado actual: " + codStatus);
        }
 
        if (motivo == null || motivo.isBlank()) {
            throw new RuntimeException("Debe especificar el motivo de la suspensión");
        }
 
        ec.edu.uteq.presustentaciones.entities.StatusSubmission statusSuspendida = statusSubmissionRepository.findByCode("SUSPENDIDA")
                .orElseGet(() -> statusSubmissionRepository.save(ec.edu.uteq.presustentaciones.entities.StatusSubmission.builder()
                        .code("SUSPENDIDA").nombre("Suspendida").build()));

        s.setStatus(statusSuspendida);
        s.setMotivoSuspension(motivo);
        s.setSuspendidoEn(LocalDateTime.now());

        Submission guardada = submissionRepository.save(s);
        log.info("Solicitud {} suspendida desde estado {} por motivo: {}", submissionId, codStatus, motivo);
 
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
    public List<Map<String, Object>> generateReportDefensesSP(String program) {
        List<Object[]> res = submissionRepository.generateReportDefensesSp(program);
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
                
        String codStatus = s.getStatus() != null ? s.getStatus().getCode() : "";
        
        List<ec.edu.uteq.presustentaciones.dto.StageTrackingDTO> etapas = new java.util.ArrayList<>();
        
        // Etapa 1: Submission registrada
        etapas.add(ec.edu.uteq.presustentaciones.dto.StageTrackingDTO.builder()
                .nombre("Solicitud registrada")
                .statusVisual("COMPLETADO")
                .date(s.getDateRecord())
                .description("Solicitud creada en el sistema.")
                .build());
                
        // Etapa 2: Revisión de submission
        String revStatus = "PENDIENTE";
        if (codStatus.equals("ENVIADA")) revStatus = "EN_PROCESO";
        else if (codStatus.equals("APROBADA") || codStatus.equals("RECHAZADA")) revStatus = "COMPLETADO";
        etapas.add(ec.edu.uteq.presustentaciones.dto.StageTrackingDTO.builder()
                .nombre("Revisión de solicitud")
                .statusVisual(revStatus)
                .date(codStatus.equals("ENVIADA") ? s.getActualizadoEn() : null)
                .description("Revisión por parte de coordinación.")
                .build());
                
        // Etapa 3: Submission aprobada
        String aprStatus = "PENDIENTE";
        if (codStatus.equals("APROBADA")) aprStatus = "COMPLETADO";
        else if (codStatus.equals("RECHAZADA")) aprStatus = "RECHAZADO";
        etapas.add(ec.edu.uteq.presustentaciones.dto.StageTrackingDTO.builder()
                .nombre("Aprobación de solicitud")
                .statusVisual(aprStatus)
                .date(codStatus.equals("APROBADA") || codStatus.equals("RECHAZADA") ? s.getActualizadoEn() : null)
                .description(codStatus.equals("RECHAZADA") ? "Rechazada: " + s.getObservations() : "Solicitud aprobada.")
                .build());
                
        // Etapa 4: Proposal
        boolean tienePdf = proposalRepository.findBySubmissionId(submissionId)
                .map(a -> a.getFilePdf() != null && !a.getFilePdf().isBlank())
                .orElse(false);
        etapas.add(ec.edu.uteq.presustentaciones.dto.StageTrackingDTO.builder()
                .nombre("Anteproyecto")
                .statusVisual(tienePdf ? "COMPLETADO" : (codStatus.equals("APROBADA") ? "EN_PROCESO" : "PENDIENTE"))
                .date(null)
                .description(tienePdf ? "Anteproyecto cargado." : "Pendiente de cargar.")
                .build());
                
        // Resto de etapas pendientes (simplificadas al no estar completamente desarrolladas en este nivel)
        etapas.add(ec.edu.uteq.presustentaciones.dto.StageTrackingDTO.builder().nombre("Observaciones").statusVisual("PENDIENTE").build());
        etapas.add(ec.edu.uteq.presustentaciones.dto.StageTrackingDTO.builder().nombre("Tutoría").statusVisual("PENDIENTE").build());
        etapas.add(ec.edu.uteq.presustentaciones.dto.StageTrackingDTO.builder().nombre("Asignación de jurados").statusVisual("PENDIENTE").build());
        etapas.add(ec.edu.uteq.presustentaciones.dto.StageTrackingDTO.builder().nombre("Evaluación").statusVisual("PENDIENTE").build());
        etapas.add(ec.edu.uteq.presustentaciones.dto.StageTrackingDTO.builder().nombre("Acta").statusVisual("PENDIENTE").build());
        
        int progress = 10;
        if (codStatus.equals("ENVIADA")) progress = 20;
        if (codStatus.equals("APROBADA")) progress = 40;
        if (tienePdf) progress = 50;

        return ec.edu.uteq.presustentaciones.dto.TrackingDTO.builder()
                .submissionId(submissionId)
                .tituloProyecto(s.getTituloTopic())
                .statusActual(s.getStatus().getNombre())
                .porcentajeProgress(progress)
                .etapas(etapas)
                .build();
    }
}