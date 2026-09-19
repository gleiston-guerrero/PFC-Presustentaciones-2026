package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.UpdateStudentRequest;
import ec.edu.uteq.presustentaciones.dto.CreateStudentRequest;
import ec.edu.uteq.presustentaciones.dto.StudentDTO;
import ec.edu.uteq.presustentaciones.entities.*;
import ec.edu.uteq.presustentaciones.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * "Gestión de estudiantes" -- antes de esto, un Student solo se creaba como
 * efecto secundario de la primera submission (SubmissionServiceImpl.createPerfilStudent),
 * con la primera program del catálogo y semestre fijo en 1, sin forma de corregirlo
 * después. Este servicio agrega el alta explícita (admin/coordinador) y la edición de
 * program, semestre, período de ingreso y estado académico.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class StudentService {

    private final StudentRepository studentRepository;
    private final AppUserRepository appUserRepository;
    private final RoleAppUserRepository roleAppUserRepository;
    private final ProgramRepository programRepository;
    private final PeriodAcademicRepository periodAcademicRepository;
    private final StatusAcademicRepository statusAcademicRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    /**
     * List paged.
     * @param page número de página, base 0
     * @param size tamaño de página (se acota a un máximo de 100)
     * @param q    texto libre de búsqueda por nombre/apellido/email, o {@code null}
     * @return página de students con su último proyecto de titulación (si tiene)
     */
    @Transactional(readOnly = true)
    public Page<StudentDTO> listPaged(int page, int size, String q) {
        int paginaSegura = Math.max(page, 0);
        int tamanioSeguro = Math.min(Math.max(size, 1), 100);
        Page<Student> pagina = studentRepository.searchPaged(q, PageRequest.of(paginaSegura, tamanioSeguro));

        List<Long> ids = pagina.getContent().stream().map(Student::getId).toList();
        Map<Long, Object[]> proyectos = ids.isEmpty() ? Map.of() : studentRepository
                .findLastProyectoByStudentIds(ids).stream()
                .collect(Collectors.toMap(r -> ((Number) r[0]).longValue(), r -> r));

        return pagina.map(e -> toDto(e, proyectos.get(e.getId())));
    }

    /**
     * Obtain by id.
     * @param id id del student
     * @return el student con su último proyecto de titulación (si tiene)
     * @throws RuntimeException si el student no existe
     */
    @Transactional(readOnly = true)
    public StudentDTO obtainById(Long id) {
        Student e = studentRepository.findByIdWithAppUser(id)
                .orElseThrow(() -> new RuntimeException("Estudiante no encontrado"));
        List<Object[]> proyectos = studentRepository.findLastProyectoByStudentIds(List.of(id));
        return toDto(e, proyectos.isEmpty() ? null : proyectos.get(0));
    }

    /**
     * Registra el appUser (role ESTUDIANTE) y su perfil académico en un solo paso.
     *
     * @param req datos del student a create (nombre, apellido, email, contraseña, program
     *            obligatorios; período de ingreso, teléfono y semestre opcionales)
     * @return el student creado
     * @throws RuntimeException si faltan campos obligatorios, el email ya está en uso, o la
     *                          program/período no existen
     */
    public StudentDTO create(CreateStudentRequest req) {
        auditService.markActorActual();

        if (req.getNombre() == null || req.getNombre().isBlank()
                || req.getApellido() == null || req.getApellido().isBlank()
                || req.getEmail() == null || req.getEmail().isBlank()
                || req.getPassword() == null || req.getPassword().isBlank()
                || req.getProgramId() == null) {
            throw new RuntimeException("Nombre, apellido, email, contraseña y carrera son obligatorios.");
        }
        if (appUserRepository.existsByEmail(req.getEmail())) {
            throw new RuntimeException("Ya existe un usuario con el email: " + req.getEmail());
        }

        Program program = programRepository.findById(req.getProgramId())
                .orElseThrow(() -> new RuntimeException("Carrera no encontrada"));
        PeriodAcademic period = req.getPeriodIngresoId() != null
                ? periodAcademicRepository.findById(req.getPeriodIngresoId())
                        .orElseThrow(() -> new RuntimeException("Período académico no encontrado"))
                : null;
        StatusAcademic activo = statusAcademicRepository.findByCode("ACTIVO")
                .orElseThrow(() -> new RuntimeException("Catálogo de estados académicos no sembrado"));
        RoleAppUser roleStudent = roleAppUserRepository.findByCode("ESTUDIANTE")
                .orElseThrow(() -> new RuntimeException("Rol ESTUDIANTE no existe en el catálogo"));

        AppUser appUser = AppUser.builder()
                .nombre(req.getNombre())
                .apellido(req.getApellido())
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .phone(req.getPhone())
                .role("ESTUDIANTE")
                .roleAppUser(roleStudent)
                .activo(true)
                .build();
        appUser = appUserRepository.save(appUser);

        short semestreActual = req.getSemestreActual() != null ? req.getSemestreActual() : (short) 1;
        String expedienteCode = studentRepository.generateCodeExpediente(null, null);

        Student student = Student.builder()
                .appUser(appUser)
                .program(program.getNombre())
                .programEntidad(program)
                .periodIngreso(period)
                .semestreActual(semestreActual)
                .semestre(semestreActual + "")
                .phone(req.getPhone())
                .expedienteCode(expedienteCode)
                .statusAcademic(activo)
                .build();
        student = studentRepository.save(student);

        return toDto(student, null);
    }

    /**
     * Actualiza solo los campos de perfil académico enviados (program, período de ingreso,
     * semestre, teléfono, estado académico); los campos {@code null} en {@code req} se dejan
     * sin tocar.
     *
     * @param id  id del student a update
     * @param req campos a update; cualquier campo en {@code null} no se modifica
     * @return el student actualizado
     * @throws RuntimeException si el student no existe, o la program/período/estado
     *                          académico indicados no existen
     */
    public StudentDTO update(Long id, UpdateStudentRequest req) {
        auditService.markActorActual();
        Student e = studentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Estudiante no encontrado"));

        if (req.getProgramId() != null) {
            Program program = programRepository.findById(req.getProgramId())
                    .orElseThrow(() -> new RuntimeException("Carrera no encontrada"));
            e.setProgramEntidad(program);
            e.setProgram(program.getNombre());
        }
        if (req.getPeriodIngresoId() != null) {
            PeriodAcademic period = periodAcademicRepository.findById(req.getPeriodIngresoId())
                    .orElseThrow(() -> new RuntimeException("Período académico no encontrado"));
            e.setPeriodIngreso(period);
        }
        if (req.getSemestreActual() != null) {
            e.setSemestreActual(req.getSemestreActual());
            e.setSemestre(req.getSemestreActual() + "");
        }
        if (req.getPhone() != null) {
            e.setPhone(req.getPhone());
        }
        if (req.getStatusAcademicCode() != null && !req.getStatusAcademicCode().isBlank()) {
            StatusAcademic status = statusAcademicRepository.findByCode(req.getStatusAcademicCode())
                    .orElseThrow(() -> new RuntimeException("Estado académico inválido: " + req.getStatusAcademicCode()));
            e.setStatusAcademic(status);
        }

        Student saved = studentRepository.save(e);
        return toDto(saved, null);
    }

    /**
     * List statuses academic.
     * @return el catálogo completo de estados académicos disponibles
     */
    @Transactional(readOnly = true)
    public List<StatusAcademic> listStatusesAcademic() {
        return statusAcademicRepository.findAll();
    }

    private StudentDTO toDto(Student e, Object[] proyecto) {
        AppUser u = e.getAppUser();
        return StudentDTO.builder()
                .id(e.getId())
                .appUserId(u.getId())
                .nombre(u.getNombre())
                .apellido(u.getApellido())
                .email(u.getEmail())
                .activo(u.getActivo())
                .phone(e.getPhone())
                .expedienteCode(e.getExpedienteCode())
                .programId(e.getProgramEntidad() != null ? e.getProgramEntidad().getId() : null)
                .programNombre(e.getProgramEntidad() != null ? e.getProgramEntidad().getNombre() : e.getProgram())
                .periodIngresoId(e.getPeriodIngreso() != null ? e.getPeriodIngreso().getId() : null)
                .periodIngresoNombre(e.getPeriodIngreso() != null ? e.getPeriodIngreso().getNombre() : null)
                .semestreActual(e.getSemestreActual())
                .statusAcademicCode(e.getStatusAcademic() != null ? e.getStatusAcademic().getCode() : null)
                .statusAcademicNombre(e.getStatusAcademic() != null ? e.getStatusAcademic().getNombre() : null)
                .proyectoTitulo(proyecto != null ? (String) proyecto[1] : null)
                .proyectoStatus(proyecto != null ? (String) proyecto[2] : null)
                .build();
    }
}
