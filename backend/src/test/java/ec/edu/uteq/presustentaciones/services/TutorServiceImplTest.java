package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.MiStudentTutoradoDTO;
import ec.edu.uteq.presustentaciones.entities.*;
import ec.edu.uteq.presustentaciones.repositories.TeacherRepository;
import ec.edu.uteq.presustentaciones.repositories.EstadoSubmissionRepository;
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
    @Mock private EstadoSubmissionRepository estadoSubmissionRepository;

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
    void assignTutorLanzaSiSubmissionNoExiste() {
        when(submissionRepository.findById(10L)).thenReturn(Optional.empty());
        RuntimeException ex = assertThrows(RuntimeException.class, () -> tutorService.assignTutor(10L, 1L));
        assertTrue(ex.getMessage().contains("Solicitud no encontrada"));
        verifyNoInteractions(teacherRepository);
    }

    @Test
    void assignTutorLanzaSiTeacherNoExiste() {
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(submission));
        when(teacherRepository.findById(1L)).thenReturn(Optional.empty());
        RuntimeException ex = assertThrows(RuntimeException.class, () -> tutorService.assignTutor(10L, 1L));
        assertTrue(ex.getMessage().contains("Docente no encontrado"));
    }

    @Test
    void assignTutorSinTutorPrevioNoEliminaNada() {
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(submission));
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(teacher));
        when(tutorRepository.findBySubmissionId(10L)).thenReturn(Optional.empty());
        when(tutorRepository.save(any(Tutor.class))).thenAnswer(inv -> inv.getArgument(0));
        when(estadoSubmissionRepository.findByCodigo("TUTORIA"))
                .thenReturn(Optional.of(EstadoSubmission.builder().codigo("TUTORIA").nombre("Tutoria").build()));

        Tutor resultado = tutorService.assignTutor(10L, 1L);

        assertEquals("ACTIVO", resultado.getEstado());
        verify(tutorRepository, never()).delete(any());
        verify(submissionRepository).save(submission);
        assertEquals("TUTORIA", submission.getEstado().getCodigo());
    }

    @Test
    void assignTutorReemplazaTutorPrevio() {
        Tutor tutorPrevio = Tutor.builder().id(5L).estado("ACTIVO").build();
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(submission));
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(teacher));
        when(tutorRepository.findBySubmissionId(10L)).thenReturn(Optional.of(tutorPrevio));
        when(tutorRepository.save(any(Tutor.class))).thenAnswer(inv -> inv.getArgument(0));
        when(estadoSubmissionRepository.findByCodigo("TUTORIA"))
                .thenReturn(Optional.of(EstadoSubmission.builder().codigo("TUTORIA").build()));

        tutorService.assignTutor(10L, 1L);

        verify(tutorRepository).delete(tutorPrevio);
    }

    @Test
    void assignTutorCreaEstadoTutoringSiNoExisteEnCatalogo() {
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(submission));
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(teacher));
        when(tutorRepository.findBySubmissionId(10L)).thenReturn(Optional.empty());
        when(tutorRepository.save(any(Tutor.class))).thenAnswer(inv -> inv.getArgument(0));
        when(estadoSubmissionRepository.findByCodigo("TUTORIA")).thenReturn(Optional.empty());
        when(estadoSubmissionRepository.save(any(EstadoSubmission.class))).thenAnswer(inv -> inv.getArgument(0));

        tutorService.assignTutor(10L, 1L);

        verify(estadoSubmissionRepository).save(argThat(e -> "TUTORIA".equals(e.getCodigo())));
    }

    @Test
    void assignTutorNoPropagaFalloDeNotification() {
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(submission));
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(teacher));
        when(tutorRepository.findBySubmissionId(10L)).thenReturn(Optional.empty());
        when(tutorRepository.save(any(Tutor.class))).thenAnswer(inv -> inv.getArgument(0));
        when(estadoSubmissionRepository.findByCodigo("TUTORIA"))
                .thenReturn(Optional.of(EstadoSubmission.builder().codigo("TUTORIA").build()));
        doThrow(new RuntimeException("fallo notificacion")).when(notificationService)
                .createNotification(anyLong(), anyString());

        Tutor resultado = assertDoesNotThrow(() -> tutorService.assignTutor(10L, 1L));

        assertNotNull(resultado);
        verify(notificationService, times(2)).createNotification(anyLong(), anyString());
    }

    // ---- delegados simples ----

    @Test
    void searchPorSubmissionDelega() {
        Tutor tutor = Tutor.builder().id(1L).build();
        when(tutorRepository.findBySubmissionId(10L)).thenReturn(Optional.of(tutor));
        assertEquals(Optional.of(tutor), tutorService.searchPorSubmission(10L));
    }

    @Test
    void listTodosDelega() {
        Pageable pageable = mock(Pageable.class);
        Page<Tutor> pagina = new PageImpl<>(List.of());
        when(tutorRepository.findAll(pageable)).thenReturn(pagina);
        assertSame(pagina, tutorService.listTodos(pageable));
    }

    @Test
    void deleteTutorDelega() {
        tutorService.deleteTutor(5L);
        verify(tutorRepository).deleteById(5L);
    }

    // ---- misStudents ----

    @Test
    void misStudentsMapeaConProgramEntidadYEstadoAcademico() {
        Program program = Program.builder().id(1).nombre("Software").build();
        EstadoAcademico ea = EstadoAcademico.builder().codigo("ACTIVO").nombre("Activo").build();
        Student est = Student.builder().id(1L).appUser(appUserStudent).telefono("099")
                .expedienteCodigo("EXP-1").programEntidad(program).semestreActual((short) 3)
                .estadoAcademico(ea).build();
        Submission sol = Submission.builder().id(10L).tituloTopic("Tema").student(est)
                .estado(EstadoSubmission.builder().codigo("TUTORIA").nombre("Tutoria").build()).build();
        Tutor tutor = Tutor.builder().id(7L).submission(sol).teacher(teacher).estado("ACTIVO").build();
        when(tutorRepository.findByTeacherAppUserId(2L)).thenReturn(List.of(tutor));

        List<MiStudentTutoradoDTO> resultado = tutorService.misStudents(2L);

        assertEquals(1, resultado.size());
        MiStudentTutoradoDTO dto = resultado.get(0);
        assertEquals("Software", dto.getProgramNombre());
        assertEquals("ACTIVO", dto.getEstadoAcademicoCodigo());
        assertEquals("TUTORIA", dto.getEstadoSubmissionCodigo());
    }

    @Test
    void misStudentsUsaFallbacksSinProgramEntidadNiEstadoNiEstadoSubmission() {
        Student est = Student.builder().id(1L).appUser(appUserStudent).program("Carrera Legado").build();
        Submission sol = Submission.builder().id(10L).tituloTopic("Tema").student(est).estadoCodigo("BORRADOR").build();
        Tutor tutor = Tutor.builder().id(7L).submission(sol).teacher(teacher).estado("ACTIVO").build();
        when(tutorRepository.findByTeacherAppUserId(2L)).thenReturn(List.of(tutor));

        MiStudentTutoradoDTO dto = tutorService.misStudents(2L).get(0);

        assertEquals("Carrera Legado", dto.getProgramNombre());
        assertNull(dto.getEstadoAcademicoCodigo());
        assertEquals("BORRADOR", dto.getEstadoSubmissionCodigo());
        assertNull(dto.getEstadoSubmissionNombre());
    }

    // ---- obtainEstadisticasTutoresSP ----

    @Test
    void obtainEstadisticasTutoresSPMapeaCadaFila() {
        Object[] fila = {1L, "Carlos Ruiz", 3, 5, 12};
        when(tutorRepository.obtainEstadisticasTutoresSp()).thenReturn(List.<Object[]>of(fila));

        List<java.util.Map<String, Object>> resultado = tutorService.obtainEstadisticasTutoresSP();

        assertEquals(1, resultado.size());
        assertEquals(1L, resultado.get(0).get("tutorDocenteId"));
        assertEquals("Carlos Ruiz", resultado.get(0).get("tutorNombre"));
        assertEquals(3, resultado.get(0).get("tutoriasActivas"));
        assertEquals(5, resultado.get(0).get("tutoriasCompletadas"));
        assertEquals(12, resultado.get(0).get("totalFasesAprobadas"));
    }

    @Test
    void obtainEstadisticasTutoresSPDevuelveVacioSinFilas() {
        when(tutorRepository.obtainEstadisticasTutoresSp()).thenReturn(List.of());
        assertTrue(tutorService.obtainEstadisticasTutoresSP().isEmpty());
    }
}
