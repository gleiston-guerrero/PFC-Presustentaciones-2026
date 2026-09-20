package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.UpdateStudentRequest;
import ec.edu.uteq.presustentaciones.dto.CreateStudentRequest;
import ec.edu.uteq.presustentaciones.dto.StudentDTO;
import ec.edu.uteq.presustentaciones.entities.*;
import ec.edu.uteq.presustentaciones.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * StudentService no tenia ninguna prueba (0.98% lines, 0% ramas antes de este archivo) --
 * es el servicio de alta/edicion explicita de students (admin/coordinador), separado de la
 * creacion implicita que hace SubmissionServiceImpl en la primera submission.
 */
@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    @Mock private StudentRepository studentRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private RoleAppUserRepository roleAppUserRepository;
    @Mock private ProgramRepository programRepository;
    @Mock private PeriodAcademicRepository periodAcademicRepository;
    @Mock private StatusAcademicRepository statusAcademicRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditService auditService;

    @InjectMocks
    private StudentService studentService;

    private AppUser appUser;
    private Student student;
    private Program program;
    private PeriodAcademic period;
    private StatusAcademic activo;
    private RoleAppUser roleStudent;

    @BeforeEach
    void setUp() {
        appUser = AppUser.builder().id(1L).nombre("Ana").apellido("Torres").email("ana@uteq.edu.ec").activo(true).build();
        program = Program.builder().id(1).nombre("Ingeniería de Software").build();
        period = PeriodAcademic.builder().id(1).nombre("2026-1").build();
        activo = StatusAcademic.builder().code("ACTIVO").nombre("Activo").build();
        roleStudent = RoleAppUser.builder().code("ESTUDIANTE").build();
        student = Student.builder().id(1L).appUser(appUser).programEntidad(program)
                .periodIngreso(period).semestreActual((short) 3).phone("0999999999")
                .expedienteCode("EXP-001").statusAcademic(activo).build();
    }

    // ---- listPaginado ----

    @Test
    void listPagedReturnsDtosWithProjectWhenExists() {
        Page<Student> pagina = new PageImpl<>(List.of(student));
        when(studentRepository.searchPaged(eq("ana"), any(PageRequest.class))).thenReturn(pagina);
        when(studentRepository.findLastProyectoByStudentIds(List.of(1L)))
                .thenReturn(List.<Object[]>of(new Object[]{1L, "Tema X", "EN_EVALUACION"}));

        Page<StudentDTO> result = studentService.listPaged(0, 10, "ana");

        assertEquals(1, result.getTotalElements());
        StudentDTO dto = result.getContent().get(0);
        assertEquals("Tema X", dto.getProyectoTitulo());
        assertEquals("EN_EVALUACION", dto.getProyectoStatus());
        assertEquals("Ingeniería de Software", dto.getProgramNombre());
    }

    @Test
    void listPagedWithoutResultsNotQueryProjects() {
        when(studentRepository.searchPaged(isNull(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));

        Page<StudentDTO> result = studentService.listPaged(0, 10, null);

        assertTrue(result.getContent().isEmpty());
        verify(studentRepository, never()).findLastProyectoByStudentIds(any());
    }

    @Test
    void listPagedBoundsPageAndSizeOutsideOfRange() {
        when(studentRepository.searchPaged(any(), any(PageRequest.class))).thenReturn(new PageImpl<>(List.of()));

        studentService.listPaged(-5, 500, null);

        verify(studentRepository).searchPaged(any(), eq(PageRequest.of(0, 100)));
    }

    // ---- obtainPorId ----

    @Test
    void obtainByIdReturnsDtoWithProject() {
        when(studentRepository.findByIdWithAppUser(1L)).thenReturn(Optional.of(student));
        when(studentRepository.findLastProyectoByStudentIds(List.of(1L)))
                .thenReturn(List.<Object[]>of(new Object[]{1L, "Tema Y", "APROBADA"}));

        StudentDTO dto = studentService.obtainById(1L);

        assertEquals("Tema Y", dto.getProyectoTitulo());
    }

    @Test
    void obtainByIdReturnsDtoWithoutProjectIfNotHas() {
        when(studentRepository.findByIdWithAppUser(1L)).thenReturn(Optional.of(student));
        when(studentRepository.findLastProyectoByStudentIds(List.of(1L))).thenReturn(List.of());

        StudentDTO dto = studentService.obtainById(1L);

        assertNull(dto.getProyectoTitulo());
    }

    @Test
    void obtainByIdThrowsIfNotExists() {
        when(studentRepository.findByIdWithAppUser(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> studentService.obtainById(99L));
        assertEquals("Estudiante no encontrado", ex.getMessage());
    }

    // ---- create ----

    private CreateStudentRequest requestValid() {
        CreateStudentRequest req = new CreateStudentRequest();
        req.setNombre("Ana");
        req.setApellido("Torres");
        req.setEmail("ana@uteq.edu.ec");
        req.setPassword("secreto123");
        req.setProgramId(1);
        return req;
    }

    @Test
    void createThrowsIfMissingFieldsRequired() {
        CreateStudentRequest req = new CreateStudentRequest();
        RuntimeException ex = assertThrows(RuntimeException.class, () -> studentService.create(req));
        assertTrue(ex.getMessage().contains("obligatorios"));
        verify(auditService).markActorActual();
        verifyNoInteractions(appUserRepository);
    }

    @Test
    void createThrowsIfEmailAlreadyExists() {
        CreateStudentRequest req = requestValid();
        when(appUserRepository.existsByEmail("ana@uteq.edu.ec")).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> studentService.create(req));
        assertTrue(ex.getMessage().contains("Ya existe un usuario"));
    }

    @Test
    void createThrowsIfProgramNotExists() {
        CreateStudentRequest req = requestValid();
        when(appUserRepository.existsByEmail(anyString())).thenReturn(false);
        when(programRepository.findById(1)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> studentService.create(req));
        assertEquals("Carrera no encontrada", ex.getMessage());
    }

    @Test
    void createThrowsIfPeriodIncomeGivenNotExists() {
        CreateStudentRequest req = requestValid();
        req.setPeriodIngresoId(5);
        when(appUserRepository.existsByEmail(anyString())).thenReturn(false);
        when(programRepository.findById(1)).thenReturn(Optional.of(program));
        when(periodAcademicRepository.findById(5)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> studentService.create(req));
        assertEquals("Período académico no encontrado", ex.getMessage());
    }

    @Test
    void createThrowsIfStatusActiveNotSeeded() {
        CreateStudentRequest req = requestValid();
        when(appUserRepository.existsByEmail(anyString())).thenReturn(false);
        when(programRepository.findById(1)).thenReturn(Optional.of(program));
        when(statusAcademicRepository.findByCode("ACTIVO")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> studentService.create(req));
        assertEquals("Catálogo de estados académicos no sembrado", ex.getMessage());
    }

    @Test
    void createThrowsIfRoleStudentNotExists() {
        CreateStudentRequest req = requestValid();
        when(appUserRepository.existsByEmail(anyString())).thenReturn(false);
        when(programRepository.findById(1)).thenReturn(Optional.of(program));
        when(statusAcademicRepository.findByCode("ACTIVO")).thenReturn(Optional.of(activo));
        when(roleAppUserRepository.findByCode("ESTUDIANTE")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> studentService.create(req));
        assertEquals("Rol ESTUDIANTE no existe en el catálogo", ex.getMessage());
    }

    @Test
    void createSuccessfulWithoutPeriodUsesSemesterByDefault() {
        CreateStudentRequest req = requestValid(); // sin periodIngresoId ni semestreActual
        when(appUserRepository.existsByEmail(anyString())).thenReturn(false);
        when(programRepository.findById(1)).thenReturn(Optional.of(program));
        when(statusAcademicRepository.findByCode("ACTIVO")).thenReturn(Optional.of(activo));
        when(roleAppUserRepository.findByCode("ESTUDIANTE")).thenReturn(Optional.of(roleStudent));
        when(passwordEncoder.encode("secreto123")).thenReturn("hash");
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> {
            AppUser u = inv.getArgument(0);
            u.setId(10L);
            return u;
        });
        when(studentRepository.generateCodeExpediente(null, null)).thenReturn("EXP-010");
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentDTO dto = studentService.create(req);

        assertEquals((short) 1, dto.getSemestreActual());
        assertEquals("Ingeniería de Software", dto.getProgramNombre());
        assertNull(dto.getProyectoTitulo());
        verify(appUserRepository).save(argThat(u -> "hash".equals(u.getPassword()) && u.getActivo()));
    }

    @Test
    void createSuccessfulWithPeriodAndSemesterExplicit() {
        CreateStudentRequest req = requestValid();
        req.setPeriodIngresoId(1);
        req.setSemestreActual((short) 4);
        when(appUserRepository.existsByEmail(anyString())).thenReturn(false);
        when(programRepository.findById(1)).thenReturn(Optional.of(program));
        when(periodAcademicRepository.findById(1)).thenReturn(Optional.of(period));
        when(statusAcademicRepository.findByCode("ACTIVO")).thenReturn(Optional.of(activo));
        when(roleAppUserRepository.findByCode("ESTUDIANTE")).thenReturn(Optional.of(roleStudent));
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(studentRepository.generateCodeExpediente(null, null)).thenReturn("EXP-011");
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentDTO dto = studentService.create(req);

        assertEquals((short) 4, dto.getSemestreActual());
        assertEquals("2026-1", dto.getPeriodIngresoNombre());
    }

    // ---- update ----

    @Test
    void updateThrowsIfStudentNotExists() {
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> studentService.update(99L, new UpdateStudentRequest()));
        assertEquals("Estudiante no encontrado", ex.getMessage());
    }

    @Test
    void updateNotTouchesFieldsInNull() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentDTO dto = studentService.update(1L, new UpdateStudentRequest());

        assertEquals((short) 3, dto.getSemestreActual());
        assertEquals("Ingeniería de Software", dto.getProgramNombre());
        verifyNoInteractions(programRepository, periodAcademicRepository);
    }

    @Test
    void updateThrowsIfProgramNewNotExists() {
        UpdateStudentRequest req = new UpdateStudentRequest();
        req.setProgramId(99);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(programRepository.findById(99)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> studentService.update(1L, req));
        assertEquals("Carrera no encontrada", ex.getMessage());
    }

    @Test
    void updateThrowsIfPeriodNewNotExists() {
        UpdateStudentRequest req = new UpdateStudentRequest();
        req.setPeriodIngresoId(77);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(periodAcademicRepository.findById(77)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> studentService.update(1L, req));
        assertEquals("Período académico no encontrado", ex.getMessage());
    }

    @Test
    void updateThrowsIfStatusAcademicInvalid() {
        UpdateStudentRequest req = new UpdateStudentRequest();
        req.setStatusAcademicCode("INEXISTENTE");
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(statusAcademicRepository.findByCode("INEXISTENTE")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> studentService.update(1L, req));
        assertTrue(ex.getMessage().contains("INEXISTENTE"));
    }

    @Test
    void updateAppliesAllFieldsWhenComeAll() {
        Program nuevaProgram = Program.builder().id(2).nombre("Sistemas").build();
        PeriodAcademic targetPeriod = PeriodAcademic.builder().id(2).nombre("2026-2").build();
        StatusAcademic suspendido = StatusAcademic.builder().code("SUSPENDIDO").nombre("Suspendido").build();
        UpdateStudentRequest req = new UpdateStudentRequest();
        req.setProgramId(2);
        req.setPeriodIngresoId(2);
        req.setSemestreActual((short) 6);
        req.setPhone("0888888888");
        req.setStatusAcademicCode("SUSPENDIDO");

        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(programRepository.findById(2)).thenReturn(Optional.of(nuevaProgram));
        when(periodAcademicRepository.findById(2)).thenReturn(Optional.of(targetPeriod));
        when(statusAcademicRepository.findByCode("SUSPENDIDO")).thenReturn(Optional.of(suspendido));
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentDTO dto = studentService.update(1L, req);

        assertEquals("Sistemas", dto.getProgramNombre());
        assertEquals("2026-2", dto.getPeriodIngresoNombre());
        assertEquals((short) 6, dto.getSemestreActual());
        assertEquals("SUSPENDIDO", dto.getStatusAcademicCode());
    }

    // ---- listEstadosAcademicos ----

    @Test
    void listStatusesAcademicDelegatesToRepository() {
        when(statusAcademicRepository.findAll()).thenReturn(List.of(activo));
        List<StatusAcademic> result = studentService.listStatusesAcademic();
        assertEquals(1, result.size());
        assertEquals("ACTIVO", result.get(0).getCode());
    }
}
