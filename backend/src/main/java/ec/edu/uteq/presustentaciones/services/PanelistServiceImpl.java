package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.Teacher;
import ec.edu.uteq.presustentaciones.entities.Panelist;
import ec.edu.uteq.presustentaciones.entities.Submission;
import ec.edu.uteq.presustentaciones.entities.Tutor;
import ec.edu.uteq.presustentaciones.repositories.TeacherRepository;
import ec.edu.uteq.presustentaciones.repositories.PanelistRepository;
import ec.edu.uteq.presustentaciones.repositories.SubmissionRepository;
import ec.edu.uteq.presustentaciones.repositories.TutorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PanelistServiceImpl implements PanelistService {

    private final PanelistRepository panelistRepository;
    private final TutorRepository tutorRepository;
    private final TeacherRepository teacherRepository;
    private final SubmissionRepository submissionRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final ec.edu.uteq.presustentaciones.repositories.RolePanelistRepository rolePanelistRepository;
    private final ec.edu.uteq.presustentaciones.repositories.EstadoSubmissionRepository estadoSubmissionRepository;

    // ── Panelists ───────────────────────────────────────────────────────────────

    /**
     * Asigna (upsert) un teacher como panelist de una submission con el role indicado.
     *
     * @param submissionId id de la submission
     * @param teacherId   id del teacher a assign
     * @param role         código de role de panelist ({@code PRESIDENTE}, {@code VOCAL_1} o
     *                    {@code VOCAL_2})
     * @return el registro de panelist creado o actualizado
     * @throws RuntimeException si el role no es uno de los tres válidos
     */
    @Override
    @Transactional
    public Panelist assignPanelist(Long submissionId, Long teacherId, String role) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada: " + submissionId));
        Teacher teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new RuntimeException("Docente no encontrado: " + teacherId));

        // Validate que la tutoría esté COMPLETADA antes de assign tribunal
        Tutor tutor = tutorRepository.findBySubmissionId(submissionId)
                .orElseThrow(() -> new RuntimeException(
                        "No puedes asignar tribunal: esta solicitud no tiene tutor asignado"));
        if (!"COMPLETADA".equals(tutor.getEstado())) {
            throw new RuntimeException(
                    "No puedes asignar tribunal: la tutoría aún no ha completado las 3 revisiones obligatorias");
        }

        boolean yaAsignado = panelistRepository.findBySubmissionId(submissionId).stream()
                .anyMatch(j -> j.getTeacher().getId().equals(teacherId));
        if (yaAsignado) {
            throw new RuntimeException("El docente ya está asignado como jurado en esta solicitud.");
        }

        // ERR-03: el teacher ya asignado como tutor de esta misma submission no puede
        // además ser member del tribunal (conflicto de interés).
        if (tutor.getTeacher() != null && tutor.getTeacher().getId().equals(teacherId)) {
            throw new RuntimeException("El docente ya es el tutor de esta solicitud y no puede además ser jurado (conflicto de interés).");
        }

        if (teacher.getDisponible() != null && !teacher.getDisponible()) {
            throw new RuntimeException("El docente no está disponible para ser asignado como jurado.");
        }

        // ERR-03: normalizado una sola vez -- se usa tanto para validate como para save,
        // así el chequeo de "rol duplicado" compara contra el mismo código que quedará en BD
        // (antes comparaba el string crudo de entrada contra un código ya colapsado distinto,
        // por lo que nunca coincidían).
        String roleNormalizado = role == null ? null : role.trim().toUpperCase();
        List<String> rolesValidos = List.of("PRESIDENTE", "VOCAL_1", "VOCAL_2");
        if (roleNormalizado == null || !rolesValidos.contains(roleNormalizado)) {
            throw new RuntimeException("Rol inválido. Use: PRESIDENTE, VOCAL_1 o VOCAL_2");
        }

        boolean roleOcupado = panelistRepository.findBySubmissionId(submissionId).stream()
                .anyMatch(j -> roleNormalizado.equalsIgnoreCase(j.getRole()));
        if (roleOcupado) {
            throw new RuntimeException("El rol '" + roleNormalizado + "' ya está asignado en esta solicitud.");
        }

        Panelist guardado = createPanelistSinNotify(submission, teacher, roleNormalizado);

        // Change estado a EVALUACION
        ec.edu.uteq.presustentaciones.entities.EstadoSubmission estadoEvaluation = estadoSubmissionRepository.findByCodigo("EVALUACION")
                .orElseGet(() -> estadoSubmissionRepository.save(ec.edu.uteq.presustentaciones.entities.EstadoSubmission.builder()
                        .codigo("EVALUACION").nombre("Evaluacion").build()));

        submission.setEstado(estadoEvaluation);
        submissionRepository.save(submission);

        // Notify al teacher asignado como panelist
        notifyTeacherPanelist(teacher, submission, roleNormalizado);

        // Notify al student que se le asignó un panelist
        notifyStudentPanelist(submission, teacher, roleNormalizado);

        return guardado;
    }

    /**
     * @param submissionId id de la submission
     * @return los panelists asignados a esa submission (0 a 3 registros)
     */
    @Override
    public List<Panelist> listPorSubmission(Long submissionId) {
        return panelistRepository.findBySubmissionId(submissionId);
    }

    /**
     * @param pageable configuración de paginación
     * @return página de todos los registros de panelist del sistema
     */
    @Override
    public Page<Panelist> listTodos(Pageable pageable) {
        return panelistRepository.findAll(pageable);
    }

    /** @param panelistId id del registro de panelist a delete */
    @Override
    @Transactional
    public void deletePanelist(Long panelistId) {
        Panelist panelist = panelistRepository.findById(panelistId)
                .orElseThrow(() -> new RuntimeException("Jurado no encontrado: " + panelistId));
        Teacher teacher = panelist.getTeacher();
        int nuevaCarga = Math.max(0, teacher.getCargaHorariaSemanal() - 1);
        teacher.setCargaHorariaSemanal(nuevaCarga);
        teacherRepository.save(teacher);
        panelistRepository.deleteById(panelistId);
    }

    // ── Tutor ─────────────────────────────────────────────────────────────────

    /**
     * @param submissionId id de la submission
     * @param teacherId   id del teacher que actuará como tutor
     * @return el registro de tutoría creado
     */
    @Override
    @Transactional
    public Tutor assignTutor(Long submissionId, Long teacherId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada: " + submissionId));
        Teacher teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new RuntimeException("Docente no encontrado: " + teacherId));

        Tutor tutor = tutorRepository.findBySubmissionId(submissionId)
                .orElse(Tutor.builder().submission(submission).build());

        tutor.setTeacher(teacher);
        tutor.setEstado("ACTIVO");

        // Re-fetch tras save para garantizar que todas las asociaciones estén cargadas
        Tutor guardado = tutorRepository.findById(tutorRepository.save(tutor).getId())
                .orElseThrow(() -> new RuntimeException("Error al recuperar el tutor guardado"));

        // Change estado a TUTORIA
        ec.edu.uteq.presustentaciones.entities.EstadoSubmission estadoTutoring = estadoSubmissionRepository.findByCodigo("TUTORIA")
                .orElseGet(() -> estadoSubmissionRepository.save(ec.edu.uteq.presustentaciones.entities.EstadoSubmission.builder()
                        .codigo("TUTORIA").nombre("Tutoria").build()));
 
        submission.setEstado(estadoTutoring);
        submissionRepository.save(submission);

        // Notify al teacher asignado como tutor
        notifyTeacherTutor(teacher, submission);

        // Notify al student que tiene tutor asignado
        notifyStudentTutor(submission, teacher);

        return guardado;
    }

    /**
     * @param submissionId id de la submission
     * @return el tutor asignado, si existe
     */
    @Override
    public Optional<Tutor> obtainTutorDeSubmission(Long submissionId) {
        // Antes filtraba estado == "ACTIVO", pero cuando la tutoría termina el registro pasa a
        // "COMPLETADA" y ESE es justo el estado normal cuando ya hay minutes que sign. El filtro
        // hacía que "Firmar Acta" respondiera 404 ("No tienes un rol asignado") a todo tutor de
        // un proyecto ya calificado. El tutor se quita con un DELETE, así que si la fila existe,
        // ese teacher es el tutor — sin importar si la tutoría sigue en curso o ya cerró.
        return tutorRepository.findBySubmissionId(submissionId);
    }

    /** @param tutorId id del registro de tutoría a delete */
    @Override
    @Transactional
    public void deleteTutor(Long tutorId) {
        tutorRepository.deleteById(tutorId);
    }

    // ── Sugerencia automática ─────────────────────────────────────────────────

    /**
     * Sugiere teachers candidatos a panelist para una submission (excluyendo al tutor asignado y a
     * quienes ya tengan conflicto de horario), sin assignlos todavía.
     *
     * @param submissionId id de la submission
     * @param cantidad    número máximo de teachers a sugerir
     * @return lista de teachers candidatos, tamaño ≤ {@code cantidad}
     */
    @Override
    public List<Teacher> sugerirTeachers(Long submissionId, int cantidad) {
        List<Long> idsOcupados = new ArrayList<>();
        panelistRepository.findBySubmissionId(submissionId)
                .forEach(j -> idsOcupados.add(j.getTeacher().getId()));
        tutorRepository.findBySubmissionId(submissionId)
                .ifPresent(t -> idsOcupados.add(t.getTeacher().getId()));

        List<Teacher> candidatos = teacherRepository.findDisponiblesOrdenadosPorCarga().stream()
                .filter(d -> !idsOcupados.contains(d.getId()))
                .collect(Collectors.toList());

        if (candidatos.size() < cantidad) {
            candidatos = teacherRepository.findTodosOrdenadosPorCarga().stream()
                    .filter(d -> !idsOcupados.contains(d.getId()))
                    .collect(Collectors.toList());
        }

        return candidatos.stream().limit(cantidad).collect(Collectors.toList());
    }

    /**
     * Asigna automáticamente los 3 roles de tribunal (PRESIDENTE, VOCAL_1, VOCAL_2) para una
     * submission, usando la misma lógica de sugerencia que {@link #sugerirTeachers}.
     *
     * @param submissionId id de la submission
     * @throws RuntimeException si no hay suficientes teachers disponibles para completar el
     *                          tribunal
     */
    @Override
    @Transactional
    public void assignPanelistsAutomaticamente(Long submissionId) {
        // Validate tutoría completada (assignPanelist ya no se llama, validamos aquí)
        Tutor tutor = tutorRepository.findBySubmissionId(submissionId)
                .orElseThrow(() -> new RuntimeException(
                        "No puedes asignar tribunal: esta solicitud no tiene tutor asignado"));
        if (!"COMPLETADA".equals(tutor.getEstado())) {
            throw new RuntimeException(
                    "No puedes asignar tribunal: la tutoría aún no ha completado las 3 revisiones obligatorias");
        }

        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada: " + submissionId));

        List<String> rolesOcupados = panelistRepository.findBySubmissionId(submissionId)
                .stream().map(Panelist::getRole).collect(Collectors.toList());
        List<String> rolesFaltantes = new ArrayList<>(List.of("PRESIDENTE", "VOCAL_1", "VOCAL_2"))
                .stream().filter(r -> !rolesOcupados.contains(r)).collect(Collectors.toList());

        if (rolesFaltantes.isEmpty()) return;

        List<Teacher> sugeridos = sugerirTeachers(submissionId, rolesFaltantes.size());
        if (sugeridos.size() < rolesFaltantes.size()) {
            throw new RuntimeException(
                    "No hay suficientes docentes para asignar automáticamente. " +
                            "Disponibles: " + sugeridos.size() + ", requeridos: " + rolesFaltantes.size());
        }

        // Assign sin notify al student en cada iteración
        for (int i = 0; i < rolesFaltantes.size(); i++) {
            Teacher teacher = sugeridos.get(i);
            String role = rolesFaltantes.get(i);
            // ERR-03: sugerirTeachers() ya excluye al tutor de esta submission de idsOcupados,
            // así que el conflicto de interés tutor==panelist no puede ocurrir aquí. El pool de
            // backup (findTodosOrdenadosPorCarga) ignora "disponible" a propósito -- es el
            // fallback para cuando no hay suficientes teachers disponibles y completar el
            // tribunal es preferible a fallar -- pero se deja constancia en el log.
            if (teacher.getDisponible() != null && !teacher.getDisponible()) {
                log.warn("Asignación automática de jurado usó un docente no disponible (id={}) " +
                        "por falta de suficientes docentes disponibles para la solicitud {}.", teacher.getId(), submissionId);
            }
            createPanelistSinNotify(submission, teacher, role);
            notifyTeacherPanelist(teacher, submission, role);  // cada teacher es destinatario distinto
        }
 
        // Change estado a EVALUACION
        ec.edu.uteq.presustentaciones.entities.EstadoSubmission estadoEvaluation = estadoSubmissionRepository.findByCodigo("EVALUACION")
                .orElseGet(() -> estadoSubmissionRepository.save(ec.edu.uteq.presustentaciones.entities.EstadoSubmission.builder()
                        .codigo("EVALUACION").nombre("Evaluacion").build()));
 
        submission.setEstado(estadoEvaluation);
        submissionRepository.save(submission);

        // Una sola notificación + correo agrupado al student
        List<Panelist> todosPanelists = panelistRepository.findBySubmissionId(submissionId);
        notifyStudentTribunalCompleto(submission, todosPanelists);
    }

    // ── Asignación masiva vía procedimiento almacenado ──────────────────────────

    /**
     * Asigna en lote pares (submissionId, teacherId) al role indicado, invocando
     * sp_assign_panelist_masivo una vez por par. Toda la operación corre dentro
     * de una única transacción: si un par falla (role inválido, FK inexistente),
     * se revierten también los pares ya procesados en esa misma llamada.
     *
     * @param submissionIds ids de las submissions, en el mismo orden que {@code teacherIds}
     * @param teacherIds   ids de los teachers a assign, uno por cada submission del arreglo
     * @param roleCodigo    código de role aplicado a todos los pares del lote
     * @throws RuntimeException si los dos arreglos no tienen la misma longitud, o si el
     *                          procedimiento almacenado rechaza algún par (role inválido, FK
     *                          inexistente, o conflicto de horario)
     */
    @Override
    @Transactional
    public void assignPanelistMasivo(List<Long> submissionIds, List<Long> teacherIds, String roleCodigo) {
        if (submissionIds == null || teacherIds == null || submissionIds.size() != teacherIds.size()) {
            throw new RuntimeException("Los arreglos de solicitudes y docentes deben tener la misma longitud");
        }
        for (int i = 0; i < submissionIds.size(); i++) {
            panelistRepository.spAssignPanelistMasivo(submissionIds.get(i), teacherIds.get(i), roleCodigo);
        }
    }

    // ── Vista del teacher ─────────────────────────────────────────────────────

    /**
     * @param teacherId id del teacher
     * @return las asignaciones de panelist de ese teacher, en cualquier submission
     */
    @Override
    public List<Panelist> listPorTeacher(Long teacherId) {
        return panelistRepository.findByTeacherId(teacherId);
    }

    /**
     * @param teacherId id del teacher
     * @return las tutorías activas de ese teacher
     */
    @Override
    public List<Tutor> listTutoringsPorTeacher(Long teacherId) {
        return tutorRepository.findByTeacherId(teacherId);
    }

    /**
     * @param submissionId id de la submission
     * @param appUserId   id del appUser autenticado (se resuelve contra el teacher vinculado)
     * @return la asignación de panelist de ese appUser en esa submission, si existe
     */
    @Override
    public Optional<Panelist> obtainInfoPanelist(Long submissionId, Long appUserId) {
        return panelistRepository.findBySubmissionIdAndAppUserId(submissionId, appUserId);
    }

    // ── Helpers internos ─────────────────────────────────────────────────────

    /**
     * Guarda el panelist y actualiza la carga del teacher. No envía ninguna notificación.
     * ERR-03: antes colapsaba cualquier role que empezara con "VOCAL" al código genérico
     * "VOCAL" antes de save, así que VOCAL_1/VOCAL_2 nunca quedaban en BD tal cual --
     * rompía la pantalla de Evaluar (busca literalmente esos códigos) y el chequeo de role
     * duplicado. Se guarda el código ya normalizado por el llamador, sin transformarlo.
     */
    private Panelist createPanelistSinNotify(Submission submission, Teacher teacher, String role) {
        teacher.setCargaHorariaSemanal(teacher.getCargaHorariaSemanal() + 1);
        teacherRepository.save(teacher);

        String codigoRole = role.toUpperCase();
        final String finalCodigoRole = codigoRole;
        ec.edu.uteq.presustentaciones.entities.RolePanelist rolePanelist = rolePanelistRepository.findByCodigo(codigoRole)
                .orElseGet(() -> {
                    return rolePanelistRepository.save(ec.edu.uteq.presustentaciones.entities.RolePanelist.builder()
                            .codigo(finalCodigoRole)
                            .nombre(finalCodigoRole.substring(0, 1).toUpperCase() + finalCodigoRole.substring(1).toLowerCase())
                            .build());
                });

        return panelistRepository.save(Panelist.builder()
                .submission(submission)
                .teacher(teacher)
                .rolePanelist(rolePanelist)
                .confirmado(false)
                .build());
    }

    /** Una sola notificación en BD + un solo correo al student con el tribunal completo. */
    private void notifyStudentTribunalCompleto(Submission submission, List<Panelist> panelists) {
        try {
            String presidente = panelists.stream().filter(j -> "PRESIDENTE".equals(j.getRole()))
                    .map(j -> j.getTeacher().getAppUser().getNombre() + " " + j.getTeacher().getAppUser().getApellido())
                    .findFirst().orElse("-");
            String vocal1 = panelists.stream().filter(j -> "VOCAL_1".equals(j.getRole()))
                    .map(j -> j.getTeacher().getAppUser().getNombre() + " " + j.getTeacher().getAppUser().getApellido())
                    .findFirst().orElse("-");
            String vocal2 = panelists.stream().filter(j -> "VOCAL_2".equals(j.getRole()))
                    .map(j -> j.getTeacher().getAppUser().getNombre() + " " + j.getTeacher().getAppUser().getApellido())
                    .findFirst().orElse("-");

            String mensaje = String.format(
                    "⚖️ Se ha asignado tu tribunal completo para tu pre-sustentación \"%s\". " +
                    "Presidente: %s, Vocal 1: %s, Vocal 2: %s. Tu solicitud ahora está en fase de evaluación.",
                    submission.getTituloTopic(), presidente, vocal1, vocal2);

            Long studentAppUserId = submission.getStudent().getAppUser().getId();
            notificationService.createNotification(studentAppUserId, mensaje);

            String email = submission.getStudent().getAppUser().getEmailNotifications();
            if (email == null || email.isBlank()) {
                email = submission.getStudent().getAppUser().getEmail();
            }
            emailService.sendNotification(email, mensaje);
        } catch (Exception e) {
            log.warn("No se pudo notificar al estudiante sobre tribunal completo: {}", e.getMessage());
        }
    }

    // ── Helpers de notificación ───────────────────────────────────────────────

    private void notifyTeacherPanelist(Teacher teacher, Submission submission, String role) {
        try {
            String roleLabel = switch (role.toUpperCase()) {
                case "PRESIDENTE" -> "Presidente del tribunal";
                case "VOCAL_1"    -> "Vocal 1 del tribunal";
                case "VOCAL_2"    -> "Vocal 2 del tribunal";
                default           -> role;
            };
            notificationService.createNotification(teacher.getAppUser().getId(),
                    String.format("⚖️ Has sido asignado como %s para evaluar la pre-sustentación \"%s\" " +
                                    "del estudiante %s %s. Por favor ingresa al sistema para confirmar tu participación.",
                            roleLabel,
                            submission.getTituloTopic(),
                            submission.getStudent().getAppUser().getNombre(),
                            submission.getStudent().getAppUser().getApellido()));
        } catch (Exception e) {
            log.warn("No se pudo notificar al docente jurado: {}", e.getMessage());
        }
    }

    private void notifyStudentPanelist(Submission submission, Teacher teacher, String role) {
        try {
            String roleLabel = switch (role.toUpperCase()) {
                case "PRESIDENTE" -> "Presidente";
                case "VOCAL_1"    -> "Vocal 1";
                case "VOCAL_2"    -> "Vocal 2";
                default           -> role;
            };
            notificationService.createNotification(submission.getStudent().getAppUser().getId(),
                    String.format("👨‍🏫 Se ha asignado al docente %s %s como %s del tribunal para tu pre-sustentación \"%s\".",
                            teacher.getAppUser().getNombre(),
                            teacher.getAppUser().getApellido(),
                            roleLabel,
                            submission.getTituloTopic()));
        } catch (Exception e) {
            log.warn("No se pudo notificar al estudiante sobre jurado: {}", e.getMessage());
        }
    }

    private void notifyTeacherTutor(Teacher teacher, Submission submission) {
        try {
            notificationService.createNotification(teacher.getAppUser().getId(),
                    String.format("📚 Has sido asignado como tutor del anteproyecto \"%s\" " +
                                    "del estudiante %s %s. Ingresa al sistema para revisar los detalles.",
                            submission.getTituloTopic(),
                            submission.getStudent().getAppUser().getNombre(),
                            submission.getStudent().getAppUser().getApellido()));
        } catch (Exception e) {
            log.warn("No se pudo notificar al docente tutor: {}", e.getMessage());
        }
    }

    private void notifyStudentTutor(Submission submission, Teacher teacher) {
        try {
            notificationService.createNotification(submission.getStudent().getAppUser().getId(),
                    String.format("🎓 El docente %s %s ha sido asignado como tu tutor para el anteproyecto \"%s\". " +
                                    "Tu solicitud ahora está en fase de tutoría. Puedes ponerte en contacto con él a través del sistema.",
                            teacher.getAppUser().getNombre(),
                            teacher.getAppUser().getApellido(),
                            submission.getTituloTopic()));
        } catch (Exception e) {
            log.warn("No se pudo notificar al estudiante sobre tutor: {}", e.getMessage());
        }
    }

    /**
     * Variante de {@link #assignPanelistMasivo} que invoca directamente la sobrecarga de
     * {@code sp_assign_panelist_masivo} que recibe arreglos SQL ({@code BIGINT[]}) en una sola
     * llamada, en vez de iterar en Java. Ver la nota de fusión de ramas en
     * {@code docs/basedatos/CATALOGO-SP.md} sobre por qué la variante scaler (iterando en
     * Java) es la que queda verificada end-to-end, no esta.
     *
     * @param submissionIds arreglo de ids de submission
     * @param teacherIds   arreglo de ids de teacher, en el mismo orden
     * @param role          código de role aplicado a todo el lote
     */
    @Override
    @Transactional
    public void assignPanelistMasivoSP(Long[] submissionIds, Long[] teacherIds, String role) {
        panelistRepository.spAssignPanelistMasivo(submissionIds, teacherIds, role);
    }
}
