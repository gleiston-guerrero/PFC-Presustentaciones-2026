package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.MyStudentTuteeDTO;
import ec.edu.uteq.presustentaciones.entities.*;
import ec.edu.uteq.presustentaciones.repositories.TeacherRepository;
import ec.edu.uteq.presustentaciones.repositories.StatusSubmissionRepository;
import ec.edu.uteq.presustentaciones.repositories.SubmissionRepository;
import ec.edu.uteq.presustentaciones.repositories.TutorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TutorServiceImpl no tenia ninguna prueba (2.67% lines, 0% ramas antes de este archivo).
 */
@ExtendWith(MockitoExtension.class)
class TutorServiceImplTest {

    @Mock private TutorRepository tutorRepository;
    @Mock private SubmissionRepository submissionRepository;
    @Mock private TeacherRepository teacherRepository;
    @Mock private NotificationService notificationService;
    @Mock private StatusSubmissionRepository statusSubmissionRepository;

    @InjectMocks
    private TutorServiceImpl tutorService;

    private AppUser appUserStudent;
    private AppUser appUserTeacher;
    private Student student;
    private Teacher teacher;
    private Submission submission;

    @BeforeEach
    void setUp() {
        appUserStudent = AppUser.builder().id(1L).nombre("Ana").apellido("Torres").build();
        appUserTeacher = AppUser.builder().id(2L).nombre("Carlos").apellido("Ruiz").build();
        student = Student.builder().id(1L).appUser(appUserStudent).build();
        teacher = Teacher.builder().id(1L).appUser(appUserTeacher).build();
        submission = Submission.builder().id(10L).tituloTopic("Sistema X").student(student).build();
    }

    // ---- assignTutor ----

    @Test
    void assignTutorThrowsIfSubmissionNotExists() {
        when(submissionRepository.findById(10L)).thenReturn(Optional.empty());
        RuntimeException ex = assertThrows(RuntimeException.class, () -> tutorService.assignTutor(10L, 1L));
        assertTrue(ex.getMessage().contains("Solicitud no encontrada"));
        verifyNoInteractions(teacherRepository);
    }

    @Test
    void assignTutorThrowsIfTeacherNotExists() {
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(submission));
        when(teacherRepository.findById(1L)).thenReturn(Optional.empty());
        RuntimeException ex = assertThrows(RuntimeException.class, () -> tutorService.assignTutor(10L, 1L));
        assertTrue(ex.getMessage().contains("Docente no encontrado"));
    }

    @Test
    void assignTutorWithoutTutorPreviousNotRemovesNothing() {
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(submission));
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(teacher));
        when(tutorRepository.findBySubmissionId(10L)).thenReturn(Optional.empty());
        when(tutorRepository.save(any(Tutor.class))).thenAnswer(inv -> inv.getArgument(0));
        when(statusSubmissionRepository.findByCode("TUTORIA"))
                .thenReturn(Optional.of(StatusSubmission.builder().code("TUTORIA").nombre("Tutoria").build()));

        Tutor result = tutorService.assignTutor(10L, 1L);

        assertEquals("ACTIVO", result.getStatus());
        verify(tutorRepository, never()).delete(any());
        verify(submissionRepository).save(submission);
        assertEquals("TUTORIA", submission.getStatus().getCode());
    }

    @Test
    void assignTutorReplacesTutorPrevious() {
        Tutor tutorPrevio = Tutor.builder().id(5L).status("ACTIVO").build();
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(submission));
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(teacher));
        when(tutorRepository.findBySubmissionId(10L)).thenReturn(Optional.of(tutorPrevio));
        when(tutorRepository.save(any(Tutor.class))).thenAnswer(inv -> inv.getArgument(0));
        when(statusSubmissionRepository.findByCode("TUTORIA"))
                .thenReturn(Optional.of(StatusSubmission.builder().code("TUTORIA").build()));

        tutorService.assignTutor(10L, 1L);

        verify(tutorRepository).delete(tutorPrevio);
    }

    @Test
    void assignTutorCreatesStatusTutoringIfNotExistsInCatalog() {
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(submission));
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(teacher));
        when(tutorRepository.findBySubmissionId(10L)).thenReturn(Optional.empty());
        when(tutorRepository.save(any(Tutor.class))).thenAnswer(inv -> inv.getArgument(0));
        when(statusSubmissionRepository.findByCode("TUTORIA")).thenReturn(Optional.empty());
        when(statusSubmissionRepository.save(any(StatusSubmission.class))).thenAnswer(inv -> inv.getArgument(0));

        tutorService.assignTutor(10L, 1L);

        verify(statusSubmissionRepository).save(argThat(e -> "TUTORIA".equals(e.getCode())));
    }

    @Test
    void assignTutorNotPropagatesFailureOfNotification() {
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(submission));
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(teacher));
        when(tutorRepository.findBySubmissionId(10L)).thenReturn(Optional.empty());
        when(tutorRepository.save(any(Tutor.class))).thenAnswer(inv -> inv.getArgument(0));
        when(statusSubmissionRepository.findByCode("TUTORIA"))
                .thenReturn(Optional.of(StatusSubmission.builder().code("TUTORIA").build()));
        doThrow(new RuntimeException("fallo notificacion")).when(notificationService)
                .createNotification(anyLong(), anyString());

        Tutor result = assertDoesNotThrow(() -> tutorService.assignTutor(10L, 1L));

        assertNotNull(result);
        verify(notificationService, times(2)).createNotification(anyLong(), anyString());
    }

    // ---- delegados simples ----

    @Test
    void searchBySubmissionDelegates() {
        Tutor tutor = Tutor.builder().id(1L).build();
        when(tutorRepository.findBySubmissionId(10L)).thenReturn(Optional.of(tutor));
        assertEquals(Optional.of(tutor), tutorService.searchBySubmission(10L));
    }

    @Test
    void listAllDelegates() {
        Pageable pageable = mock(Pageable.class);
        Page<Tutor> pagina = new PageImpl<>(List.of());
        when(tutorRepository.findAll(pageable)).thenReturn(pagina);
        assertSame(pagina, tutorService.listAll(pageable));
    }

    @Test
    void deleteTutorDelegates() {
        tutorService.deleteTutor(5L);
        verify(tutorRepository).deleteById(5L);
    }

    // ---- misStudents ----

    @Test
    void myStudentsMapsWithProgramEntityAndStatusAcademic() {
        Program program = Program.builder().id(1).nombre("Software").build();
        StatusAcademic ea = StatusAcademic.builder().code("ACTIVO").nombre("Activo").build();
        Student est = Student.builder().id(1L).appUser(appUserStudent).phone("099")
                .expedienteCode("EXP-1").programEntidad(program).semestreActual((short) 3)
                .statusAcademic(ea).build();
        Submission sol = Submission.builder().id(10L).tituloTopic("Tema").student(est)
                .status(StatusSubmission.builder().code("TUTORIA").nombre("Tutoria").build()).build();
        Tutor tutor = Tutor.builder().id(7L).submission(sol).teacher(teacher).status("ACTIVO").build();
        when(tutorRepository.findByTeacherAppUserId(2L)).thenReturn(List.of(tutor));

        List<MyStudentTuteeDTO> result = tutorService.myStudents(2L);

        assertEquals(1, result.size());
        MyStudentTuteeDTO dto = result.get(0);
        assertEquals("Software", dto.getProgramNombre());
        assertEquals("ACTIVO", dto.getStatusAcademicCode());
        assertEquals("TUTORIA", dto.getStatusSubmissionCode());
    }

    @Test
    void myStudentsUsesFallbacksWithoutProgramEntityOrStatusOrStatusSubmission() {
        Student est = Student.builder().id(1L).appUser(appUserStudent).program("Carrera Legado").build();
        Submission sol = Submission.builder().id(10L).tituloTopic("Tema").student(est).statusCode("BORRADOR").build();
        Tutor tutor = Tutor.builder().id(7L).submission(sol).teacher(teacher).status("ACTIVO").build();
        when(tutorRepository.findByTeacherAppUserId(2L)).thenReturn(List.of(tutor));

        MyStudentTuteeDTO dto = tutorService.myStudents(2L).get(0);

        assertEquals("Carrera Legado", dto.getProgramNombre());
        assertNull(dto.getStatusAcademicCode());
        assertEquals("BORRADOR", dto.getStatusSubmissionCode());
        assertNull(dto.getStatusSubmissionNombre());
    }

    // ---- obtainEstadisticasTutoresSP ----

    @Test
    void obtainStatsTutorsSPMapsEachRow() {
        Object[] row = {1L, "Carlos Ruiz", 3, 5, 12};
        when(tutorRepository.obtainStatsTutorsSp()).thenReturn(List.<Object[]>of(row));

        List<java.util.Map<String, Object>> result = tutorService.obtainStatsTutorsSP();

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).get("tutorDocenteId"));
        assertEquals("Carlos Ruiz", result.get(0).get("tutorNombre"));
        assertEquals(3, result.get(0).get("tutoriasActivas"));
        assertEquals(5, result.get(0).get("tutoriasCompletadas"));
        assertEquals(12, result.get(0).get("totalFasesAprobadas"));
    }

    @Test
    void obtainStatsTutorsSPReturnsEmptyWithoutRows() {
        when(tutorRepository.obtainStatsTutorsSp()).thenReturn(List.of());
        assertTrue(tutorService.obtainStatsTutorsSP().isEmpty());
    }
}
