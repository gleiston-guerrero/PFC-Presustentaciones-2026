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
    private final PeriodAcademicoRepository periodAcademicoRepository;
    private final EstadoAcademicoRepository estadoAcademicoRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    /**
     * @param page número de página, base 0
     * @param size tamaño de página (se acota a un máximo de 100)
     * @param q    texto libre de búsqueda por nombre/apellido/email, o {@code null}
     * @return página de students con su último proyecto de titulación (si tiene)
     */
    @Transactional(readOnly = true)
    public Page<StudentDTO> listPaginado(int page, int size, String q) {
        int paginaSegura = Math.max(page, 0);
        int tamanioSeguro = Math.min(Math.max(size, 1), 100);
        Page<Student> pagina = studentRepository.searchPaginado(q, PageRequest.of(paginaSegura, tamanioSeguro));

        List<Long> ids = pagina.getContent().stream().map(Student::getId).toList();
        Map<Long, Object[]> proyectos = ids.isEmpty() ? Map.of() : studentRepository
                .findUltimoProyectoPorStudentIds(ids).stream()
                .collect(Collectors.toMap(r -> ((Number) r[0]).longValue(), r -> r));

        return pagina.map(e -> toDto(e, proyectos.get(e.getId())));
    }

    /**
     * @param id id del student
     * @return el student con su último proyecto de titulación (si tiene)
     * @throws RuntimeException si el student no existe
     */
    @Transactional(readOnly = true)
    public StudentDTO obtainPorId(Long id) {
        Student e = studentRepository.findByIdWithAppUser(id)
                .orElseThrow(() -> new RuntimeException("Estudiante no encontrado"));
        List<Object[]> proyectos = studentRepository.findUltimoProyectoPorStudentIds(List.of(id));
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
        auditService.marcarActorActual();

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
        PeriodAcademico period = req.getPeriodIngresoId() != null
                ? periodAcademicoRepository.findById(req.getPeriodIngresoId())
                        .orElseThrow(() -> new RuntimeException("Período académico no encontrado"))
                : null;
        EstadoAcademico activo = estadoAcademicoRepository.findByCodigo("ACTIVO")
                .orElseThrow(() -> new RuntimeException("Catálogo de estados académicos no sembrado"));
        RoleAppUser roleStudent = roleAppUserRepository.findByCodigo("ESTUDIANTE")
                .orElseThrow(() -> new RuntimeException("Rol ESTUDIANTE no existe en el catálogo"));

        AppUser appUser = AppUser.builder()
                .nombre(req.getNombre())
                .apellido(req.getApellido())
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .telefono(req.getTelefono())
                .role("ESTUDIANTE")
                .roleAppUser(roleStudent)
                .activo(true)
                .build();
        appUser = appUserRepository.save(appUser);

        short semestreActual = req.getSemestreActual() != null ? req.getSemestreActual() : (short) 1;
        String expedienteCodigo = studentRepository.generateCodigoExpediente(null, null);

        Student student = Student.builder()
                .appUser(appUser)
                .program(program.getNombre())
                .programEntidad(program)
                .periodIngreso(period)
                .semestreActual(semestreActual)
                .semestre(semestreActual + "")
                .telefono(req.getTelefono())
                .expedienteCodigo(expedienteCodigo)
                .estadoAcademico(activo)
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
        auditService.marcarActorActual();
        Student e = studentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Estudiante no encontrado"));

        if (req.getProgramId() != null) {
            Program program = programRepository.findById(req.getProgramId())
                    .orElseThrow(() -> new RuntimeException("Carrera no encontrada"));
            e.setProgramEntidad(program);
            e.setProgram(program.getNombre());
        }
        if (req.getPeriodIngresoId() != null) {
            PeriodAcademico period = periodAcademicoRepository.findById(req.getPeriodIngresoId())
                    .orElseThrow(() -> new RuntimeException("Período académico no encontrado"));
            e.setPeriodIngreso(period);
        }
        if (req.getSemestreActual() != null) {
            e.setSemestreActual(req.getSemestreActual());
            e.setSemestre(req.getSemestreActual() + "");
        }
        if (req.getTelefono() != null) {
            e.setTelefono(req.getTelefono());
        }
        if (req.getEstadoAcademicoCodigo() != null && !req.getEstadoAcademicoCodigo().isBlank()) {
            EstadoAcademico estado = estadoAcademicoRepository.findByCodigo(req.getEstadoAcademicoCodigo())
                    .orElseThrow(() -> new RuntimeException("Estado académico inválido: " + req.getEstadoAcademicoCodigo()));
            e.setEstadoAcademico(estado);
        }

        Student guardado = studentRepository.save(e);
        return toDto(guardado, null);
    }

    /** @return el catálogo completo de estados académicos disponibles */
    @Transactional(readOnly = true)
    public List<EstadoAcademico> listEstadosAcademicos() {
        return estadoAcademicoRepository.findAll();
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
                .telefono(e.getTelefono())
                .expedienteCodigo(e.getExpedienteCodigo())
                .programId(e.getProgramEntidad() != null ? e.getProgramEntidad().getId() : null)
                .programNombre(e.getProgramEntidad() != null ? e.getProgramEntidad().getNombre() : e.getProgram())
                .periodIngresoId(e.getPeriodIngreso() != null ? e.getPeriodIngreso().getId() : null)
                .periodIngresoNombre(e.getPeriodIngreso() != null ? e.getPeriodIngreso().getNombre() : null)
                .semestreActual(e.getSemestreActual())
                .estadoAcademicoCodigo(e.getEstadoAcademico() != null ? e.getEstadoAcademico().getCodigo() : null)
                .estadoAcademicoNombre(e.getEstadoAcademico() != null ? e.getEstadoAcademico().getNombre() : null)
                .proyectoTitulo(proyecto != null ? (String) proyecto[1] : null)
                .proyectoEstado(proyecto != null ? (String) proyecto[2] : null)
                .build();
    }
}
