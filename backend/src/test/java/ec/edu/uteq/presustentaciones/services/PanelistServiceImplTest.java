package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.*;
import ec.edu.uteq.presustentaciones.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PanelistServiceImplTest {

    @Mock
    private PanelistRepository panelistRepository;

    @Mock
    private TutorRepository tutorRepository;

    @Mock
    private TeacherRepository teacherRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private EmailService emailService;

    @Mock
    private RolePanelistRepository rolePanelistRepository;

    @Mock
    private EstadoSubmissionRepository estadoSubmissionRepository;

    @InjectMocks
    private PanelistServiceImpl panelistService;

    private Submission submission;
    private Student student;
    private AppUser appUserStudent;
    private Teacher teacher1;
    private Teacher teacher2;
    private AppUser appUserTeacher1;
    private AppUser appUserTeacher2;
    private Tutor tutorCompletado;

    @BeforeEach
    void setUp() {
        appUserTeacher1 = AppUser.builder().id(101L).nombre("Ana").apellido("Gomez").email("agomez@uteq.edu.ec").build();
        teacher1 = Teacher.builder().id(1L).appUser(appUserTeacher1).disponible(true).cargaHorariaSemanal(0).build();

        appUserTeacher2 = AppUser.builder().id(102L).nombre("Luis").apellido("Vera").email("lvera@uteq.edu.ec").build();
        teacher2 = Teacher.builder().id(2L).appUser(appUserTeacher2).disponible(true).cargaHorariaSemanal(0).build();

        appUserStudent = AppUser.builder().id(201L).nombre("Mario").apellido("Alvarado").email("malvarado@uteq.edu.ec").build();
        student = Student.builder().id(5L).appUser(appUserStudent).build();

        submission = Submission.builder().id(50L).student(student).tituloTopic("Tesis Inteligencia Artificial").build();
        tutorCompletado = Tutor.builder().id(1L).submission(submission).teacher(teacher1).estado("COMPLETADA").build();

        lenient().when(rolePanelistRepository.findByCodigo(anyString()))
                .thenAnswer(inv -> Optional.of(RolePanelist.builder().codigo(inv.getArgument(0)).build()));
    }

    @Test
    void testAssignPanelistExitoso() {
        when(submissionRepository.findById(50L)).thenReturn(Optional.of(submission));
        when(teacherRepository.findById(2L)).thenReturn(Optional.of(teacher2));
        when(tutorRepository.findBySubmissionId(50L)).thenReturn(Optional.of(tutorCompletado));
        when(panelistRepository.findBySubmissionId(50L)).thenReturn(new ArrayList<>());
        when(panelistRepository.save(any(Panelist.class))).thenAnswer(inv -> {
            Panelist j = inv.getArgument(0);
            j.setId(1L);
            return j;
        });

        Panelist panelist = panelistService.assignPanelist(50L, 2L, "PRESIDENTE");

        assertNotNull(panelist);
        assertEquals("PRESIDENTE", panelist.getRole());
        assertEquals(teacher2, panelist.getTeacher());
        verify(panelistRepository).save(any(Panelist.class));
    }

    @Test
    void testAssignPanelistFallaSiTutoringNoEstaCompletada() {
        tutorCompletado.setEstado("EN_PROCESO");
        when(submissionRepository.findById(50L)).thenReturn(Optional.of(submission));
        when(teacherRepository.findById(2L)).thenReturn(Optional.of(teacher2));
        when(tutorRepository.findBySubmissionId(50L)).thenReturn(Optional.of(tutorCompletado));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                panelistService.assignPanelist(50L, 2L, "PRESIDENTE"));
        assertTrue(ex.getMessage().contains("la tutoría aún no ha completado las 3 revisiones"));
    }

    @Test
    void testAssignPanelistFallaSiTeacherYaEstaAsignado() {
        Panelist existente = Panelist.builder().id(10L).submission(submission).teacher(teacher2)
                .rolePanelist(RolePanelist.builder().codigo("VOCAL").build()).build();
        when(submissionRepository.findById(50L)).thenReturn(Optional.of(submission));
        when(teacherRepository.findById(2L)).thenReturn(Optional.of(teacher2));
        when(tutorRepository.findBySubmissionId(50L)).thenReturn(Optional.of(tutorCompletado));
        when(panelistRepository.findBySubmissionId(50L)).thenReturn(List.of(existente));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                panelistService.assignPanelist(50L, 2L, "PRESIDENTE"));
        assertTrue(ex.getMessage().contains("ya está asignado como jurado"));
    }

    @Test
    void testDeletePanelist() {
        Panelist existente = Panelist.builder().id(10L).submission(submission).teacher(teacher2).build();
        when(panelistRepository.findById(10L)).thenReturn(Optional.of(existente));

        panelistService.deletePanelist(10L);

        verify(panelistRepository).deleteById(10L);
    }

    @Test
    void testAssignTutorExitoso() {
        when(submissionRepository.findById(50L)).thenReturn(Optional.of(submission));
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(teacher1));
        when(tutorRepository.findBySubmissionId(50L)).thenReturn(Optional.empty());
        when(tutorRepository.save(any(Tutor.class))).thenAnswer(inv -> {
            Tutor t = inv.getArgument(0);
            t.setId(1L);
            return t;
        });
        when(tutorRepository.findById(1L)).thenReturn(Optional.of(tutorCompletado));

        Tutor tutor = panelistService.assignTutor(50L, 1L);

        assertNotNull(tutor);
        verify(tutorRepository).save(any(Tutor.class));
    }

    // sp_assign_panelist_masivo (Fase 3 / Criterio P1) -- sin test dedicado pese a ser el
    // unico punto del codigo que invoca ese procedimiento.
    @Test
    void testAssignPanelistMasivoRechazaArreglosDeLongitudDistinta() {
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                panelistService.assignPanelistMasivo(List.of(50L, 51L), List.of(1L), "PRESIDENTE"));

        assertTrue(ex.getMessage().contains("misma longitud"));
        verify(panelistRepository, never()).spAssignPanelistMasivo(anyLong(), anyLong(), anyString());
    }

    @Test
    void testAssignPanelistMasivoInvocaElProcedimientoUnaVezPorPar() {
        panelistService.assignPanelistMasivo(List.of(50L, 51L), List.of(1L, 2L), "VOCAL_1");

        verify(panelistRepository).spAssignPanelistMasivo(50L, 1L, "VOCAL_1");
        verify(panelistRepository).spAssignPanelistMasivo(51L, 2L, "VOCAL_1");
        verify(panelistRepository, times(2)).spAssignPanelistMasivo(anyLong(), anyLong(), anyString());
    }

    @Test
    void testAssignPanelistMasivoSiUnParFallaNoSigueConLosSiguientes() {
        // Simula el rollback transaccional real: si el SP lanza excepcion en el segundo par,
        // el metodo debe propagarla (Spring revierte la transaccion @Transactional completa).
        // Mockito en modo estricto (default) exige stubear tambien la primera llamada:
        // sin esto, la interpreta como un posible error del test en vez de "sin comportamiento
        // especial" y lanza su propia excepcion de "stubbing argument mismatch" en su lugar.
        doNothing().when(panelistRepository).spAssignPanelistMasivo(50L, 1L, "VOCAL_2");
        doThrow(new RuntimeException("El docente ya tiene otra defensa en ese horario"))
                .when(panelistRepository).spAssignPanelistMasivo(51L, 2L, "VOCAL_2");

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                panelistService.assignPanelistMasivo(List.of(50L, 51L), List.of(1L, 2L), "VOCAL_2"));
        assertEquals("El docente ya tiene otra defensa en ese horario", ex.getMessage());

        verify(panelistRepository).spAssignPanelistMasivo(50L, 1L, "VOCAL_2");
        verify(panelistRepository).spAssignPanelistMasivo(51L, 2L, "VOCAL_2");
    }

    // ── assignPanelist: validaciones restantes ───────────────────────────────

    @Test
    void assignPanelistLanzaSiSubmissionNoExiste() {
        when(submissionRepository.findById(50L)).thenReturn(Optional.empty());
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> panelistService.assignPanelist(50L, 2L, "PRESIDENTE"));
        assertTrue(ex.getMessage().contains("Solicitud no encontrada"));
    }

    @Test
    void assignPanelistLanzaSiTeacherNoExiste() {
        when(submissionRepository.findById(50L)).thenReturn(Optional.of(submission));
        when(teacherRepository.findById(2L)).thenReturn(Optional.empty());
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> panelistService.assignPanelist(50L, 2L, "PRESIDENTE"));
        assertTrue(ex.getMessage().contains("Docente no encontrado"));
    }

    @Test
    void assignPanelistLanzaSiNoHayTutorAsignado() {
        when(submissionRepository.findById(50L)).thenReturn(Optional.of(submission));
        when(teacherRepository.findById(2L)).thenReturn(Optional.of(teacher2));
        when(tutorRepository.findBySubmissionId(50L)).thenReturn(Optional.empty());
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> panelistService.assignPanelist(50L, 2L, "PRESIDENTE"));
        assertTrue(ex.getMessage().contains("no tiene tutor asignado"));
    }

    @Test
    void assignPanelistLanzaSiElTeacherEsElTutorDeLaSubmission() {
        when(submissionRepository.findById(50L)).thenReturn(Optional.of(submission));
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(teacher1));
        when(tutorRepository.findBySubmissionId(50L)).thenReturn(Optional.of(tutorCompletado)); // tutor = teacher1
        when(panelistRepository.findBySubmissionId(50L)).thenReturn(new ArrayList<>());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> panelistService.assignPanelist(50L, 1L, "PRESIDENTE"));
        assertTrue(ex.getMessage().contains("conflicto de interés"));
    }

    @Test
    void assignPanelistLanzaSiTeacherNoDisponible() {
        teacher2.setDisponible(false);
        when(submissionRepository.findById(50L)).thenReturn(Optional.of(submission));
        when(teacherRepository.findById(2L)).thenReturn(Optional.of(teacher2));
        when(tutorRepository.findBySubmissionId(50L)).thenReturn(Optional.of(tutorCompletado));
        when(panelistRepository.findBySubmissionId(50L)).thenReturn(new ArrayList<>());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> panelistService.assignPanelist(50L, 2L, "PRESIDENTE"));
        assertTrue(ex.getMessage().contains("no está disponible"));
    }

    @Test
    void assignPanelistLanzaSiRoleEsNuloOInvalido() {
        when(submissionRepository.findById(50L)).thenReturn(Optional.of(submission));
        when(teacherRepository.findById(2L)).thenReturn(Optional.of(teacher2));
        when(tutorRepository.findBySubmissionId(50L)).thenReturn(Optional.of(tutorCompletado));
        when(panelistRepository.findBySubmissionId(50L)).thenReturn(new ArrayList<>());

        RuntimeException ex1 = assertThrows(RuntimeException.class,
                () -> panelistService.assignPanelist(50L, 2L, null));
        assertTrue(ex1.getMessage().contains("Rol inválido"));

        RuntimeException ex2 = assertThrows(RuntimeException.class,
                () -> panelistService.assignPanelist(50L, 2L, "SECRETARIO"));
        assertTrue(ex2.getMessage().contains("Rol inválido"));
    }

    @Test
    void assignPanelistLanzaSiRoleYaOcupado() {
        Panelist presidenteExistente = Panelist.builder().id(9L).submission(submission).teacher(teacher1)
                .rolePanelist(RolePanelist.builder().codigo("PRESIDENTE").build()).build();
        when(submissionRepository.findById(50L)).thenReturn(Optional.of(submission));
        when(teacherRepository.findById(2L)).thenReturn(Optional.of(teacher2));
        when(tutorRepository.findBySubmissionId(50L)).thenReturn(Optional.of(tutorCompletado));
        when(panelistRepository.findBySubmissionId(50L)).thenReturn(List.of(presidenteExistente));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> panelistService.assignPanelist(50L, 2L, "presidente"));
        assertTrue(ex.getMessage().contains("ya está asignado en esta solicitud"));
    }

    @Test
    void assignPanelistCreaRolePanelistNuevoSiNoEstaEnElCatalogo() {
        when(submissionRepository.findById(50L)).thenReturn(Optional.of(submission));
        when(teacherRepository.findById(2L)).thenReturn(Optional.of(teacher2));
        when(tutorRepository.findBySubmissionId(50L)).thenReturn(Optional.of(tutorCompletado));
        when(panelistRepository.findBySubmissionId(50L)).thenReturn(new ArrayList<>());
        when(rolePanelistRepository.findByCodigo("VOCAL_1")).thenReturn(Optional.empty());
        when(rolePanelistRepository.save(any(RolePanelist.class))).thenAnswer(inv -> inv.getArgument(0));
        when(panelistRepository.save(any(Panelist.class))).thenAnswer(inv -> {
            Panelist j = inv.getArgument(0);
            j.setId(1L);
            return j;
        });

        Panelist panelist = panelistService.assignPanelist(50L, 2L, "vocal_1");

        assertEquals("VOCAL_1", panelist.getRolePanelist().getCodigo());
        verify(rolePanelistRepository).save(argThat(r -> "VOCAL_1".equals(r.getCodigo()) && "Vocal_1".equals(r.getNombre())));
    }

    // ── obtainTutorDeSubmission / deleteTutor / delegados simples ─────────

    @Test
    void obtainTutorDeSubmissionDevuelveTutorCompletado() {
        // El tutor de un proyecto ya calificado está en "COMPLETADA" y sigue siendo el tutor
        // que debe sign el minutes: no se filtra por estado.
        when(tutorRepository.findBySubmissionId(50L)).thenReturn(Optional.of(tutorCompletado));
        assertEquals(Optional.of(tutorCompletado), panelistService.obtainTutorDeSubmission(50L));
    }

    @Test
    void obtainTutorDeSubmissionDevuelveElActivo() {
        Tutor activo = Tutor.builder().id(2L).estado("ACTIVO").build();
        when(tutorRepository.findBySubmissionId(50L)).thenReturn(Optional.of(activo));
        assertEquals(Optional.of(activo), panelistService.obtainTutorDeSubmission(50L));
    }

    @Test
    void obtainTutorDeSubmissionVacioSiNoHay() {
        when(tutorRepository.findBySubmissionId(50L)).thenReturn(Optional.empty());
        assertTrue(panelistService.obtainTutorDeSubmission(50L).isEmpty());
    }

    @Test
    void deleteTutorDelega() {
        panelistService.deleteTutor(3L);
        verify(tutorRepository).deleteById(3L);
    }

    @Test
    void listPorSubmissionDelega() {
        when(panelistRepository.findBySubmissionId(50L)).thenReturn(List.of());
        assertTrue(panelistService.listPorSubmission(50L).isEmpty());
    }

    @Test
    void listPorTeacherDelega() {
        when(panelistRepository.findByTeacherId(1L)).thenReturn(List.of());
        assertTrue(panelistService.listPorTeacher(1L).isEmpty());
    }

    @Test
    void listTutoringsPorTeacherDelega() {
        when(tutorRepository.findByTeacherId(1L)).thenReturn(List.of());
        assertTrue(panelistService.listTutoringsPorTeacher(1L).isEmpty());
    }

    @Test
    void obtainInfoPanelistDelega() {
        when(panelistRepository.findBySubmissionIdAndAppUserId(50L, 101L)).thenReturn(Optional.empty());
        assertTrue(panelistService.obtainInfoPanelist(50L, 101L).isEmpty());
    }

    @Test
    void assignPanelistMasivoSPDelega() {
        Long[] submissions = {50L, 51L};
        Long[] teachers = {1L, 2L};
        panelistService.assignPanelistMasivoSP(submissions, teachers, "PRESIDENTE");
        verify(panelistRepository).spAssignPanelistMasivo(submissions, teachers, "PRESIDENTE");
    }

    // ── sugerirTeachers ──────────────────────────────────────────────────────

    @Test
    void sugerirTeachersExcluyePanelistsYTutorYaAsignados() {
        Panelist panelistExistente = Panelist.builder().id(1L).teacher(teacher1).build();
        when(panelistRepository.findBySubmissionId(50L)).thenReturn(List.of(panelistExistente));
        when(tutorRepository.findBySubmissionId(50L)).thenReturn(Optional.of(tutorCompletado)); // teacher1 tambien
        when(teacherRepository.findDisponiblesOrdenadosPorCarga()).thenReturn(List.of(teacher1, teacher2));

        // cantidad=1: con teacher1 excluido queda exactamente 1 candidato, sin activate el
        // fallback a findTodosOrdenadosPorCarga() (ese camino se prueba aparte).
        List<Teacher> sugeridos = panelistService.sugerirTeachers(50L, 1);

        assertEquals(1, sugeridos.size());
        assertEquals(teacher2, sugeridos.get(0));
    }

    @Test
    void sugerirTeachersUsaPoolCompletoSiNoHaySuficientesDisponibles() {
        when(panelistRepository.findBySubmissionId(50L)).thenReturn(List.of());
        when(tutorRepository.findBySubmissionId(50L)).thenReturn(Optional.empty());
        when(teacherRepository.findDisponiblesOrdenadosPorCarga()).thenReturn(List.of(teacher1));
        when(teacherRepository.findTodosOrdenadosPorCarga()).thenReturn(List.of(teacher1, teacher2));

        List<Teacher> sugeridos = panelistService.sugerirTeachers(50L, 2);

        assertEquals(2, sugeridos.size());
        verify(teacherRepository).findTodosOrdenadosPorCarga();
    }

    // ── assignPanelistsAutomaticamente ────────────────────────────────────────

    @Test
    void assignPanelistsAutomaticamenteLanzaSiNoHayTutor() {
        when(tutorRepository.findBySubmissionId(50L)).thenReturn(Optional.empty());
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> panelistService.assignPanelistsAutomaticamente(50L));
        assertTrue(ex.getMessage().contains("no tiene tutor asignado"));
    }

    @Test
    void assignPanelistsAutomaticamenteLanzaSiTutoringNoCompletada() {
        tutorCompletado.setEstado("EN_PROCESO");
        when(tutorRepository.findBySubmissionId(50L)).thenReturn(Optional.of(tutorCompletado));
        assertThrows(RuntimeException.class, () -> panelistService.assignPanelistsAutomaticamente(50L));
    }

    @Test
    void assignPanelistsAutomaticamenteNoHaceNadaSiTribunalYaCompleto() {
        // Submission SI se consulta antes de revisar los roles (orden real del metodo);
        // el early-return ocurre despues, al ver que rolesFaltantes esta vacio.
        when(tutorRepository.findBySubmissionId(50L)).thenReturn(Optional.of(tutorCompletado));
        when(submissionRepository.findById(50L)).thenReturn(Optional.of(submission));
        Panelist p = Panelist.builder().id(1L).rolePanelist(RolePanelist.builder().codigo("PRESIDENTE").build()).build();
        Panelist v1 = Panelist.builder().id(2L).rolePanelist(RolePanelist.builder().codigo("VOCAL_1").build()).build();
        Panelist v2 = Panelist.builder().id(3L).rolePanelist(RolePanelist.builder().codigo("VOCAL_2").build()).build();
        when(panelistRepository.findBySubmissionId(50L)).thenReturn(List.of(p, v1, v2));

        panelistService.assignPanelistsAutomaticamente(50L);

        verify(panelistRepository, never()).save(any());
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void assignPanelistsAutomaticamenteLanzaSiNoHaySuficientesTeachers() {
        when(tutorRepository.findBySubmissionId(50L)).thenReturn(Optional.of(tutorCompletado));
        when(panelistRepository.findBySubmissionId(50L)).thenReturn(List.of()); // faltan los 3 roles
        when(submissionRepository.findById(50L)).thenReturn(Optional.of(submission));
        when(teacherRepository.findDisponiblesOrdenadosPorCarga()).thenReturn(List.of(teacher1));
        when(teacherRepository.findTodosOrdenadosPorCarga()).thenReturn(List.of(teacher1));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> panelistService.assignPanelistsAutomaticamente(50L));
        assertTrue(ex.getMessage().contains("No hay suficientes docentes"));
    }

    @Test
    void assignPanelistsAutomaticamenteAsignaLosTresRolesYNotificaUnaVez() {
        Teacher teacher3 = Teacher.builder().id(3L).appUser(AppUser.builder().id(103L).nombre("Rosa").apellido("Diaz").build())
                .disponible(true).cargaHorariaSemanal(0).build();
        // teacher1 es el tutor de la submission -- sugerirTeachers lo excluye tambien via
        // tutorRepository, asi que hacen falta 3 disponibles ADEMAS de el para cubrir los 3 roles.
        Teacher teacher4 = Teacher.builder().id(4L).appUser(AppUser.builder().id(104L).nombre("Ivan").apellido("Solis").build())
                .disponible(true).cargaHorariaSemanal(0).build();
        when(tutorRepository.findBySubmissionId(50L)).thenReturn(Optional.of(tutorCompletado));
        when(submissionRepository.findById(50L)).thenReturn(Optional.of(submission));
        when(teacherRepository.findDisponiblesOrdenadosPorCarga()).thenReturn(List.of(teacher1, teacher2, teacher3, teacher4));
        when(panelistRepository.save(any(Panelist.class))).thenAnswer(inv -> {
            Panelist j = inv.getArgument(0);
            j.setId((long) (Math.random() * 1000));
            return j;
        });
        // 3 llamadas a findBySubmissionId: 1ra (roles ocupados, al inicio), 2da (idsOcupados
        // dentro de sugerirTeachers), 3ra (armar el tribunal ya completo, al final para notify).
        Panelist pFinal = Panelist.builder().id(1L).teacher(teacher1).rolePanelist(RolePanelist.builder().codigo("PRESIDENTE").build()).build();
        when(panelistRepository.findBySubmissionId(50L)).thenReturn(List.of(), List.of(), List.of(pFinal));

        panelistService.assignPanelistsAutomaticamente(50L);

        verify(panelistRepository, times(3)).save(any(Panelist.class));
        verify(notificationService, atLeastOnce()).createNotification(anyLong(), anyString());
        verify(emailService).sendNotification(anyString(), anyString());
    }

    @Test
    void notifyStudentTribunalCompletoNoPropagaExcepcionSiFallaLaNotification() {
        Teacher teacher3 = Teacher.builder().id(3L).appUser(AppUser.builder().id(103L).nombre("Rosa").apellido("Diaz").build())
                .disponible(true).cargaHorariaSemanal(0).build();
        // teacher1 es el tutor de la submission -- sugerirTeachers lo excluye tambien via
        // tutorRepository, asi que hacen falta 3 disponibles ADEMAS de el para cubrir los 3 roles.
        Teacher teacher4 = Teacher.builder().id(4L).appUser(AppUser.builder().id(104L).nombre("Ivan").apellido("Solis").build())
                .disponible(true).cargaHorariaSemanal(0).build();
        when(tutorRepository.findBySubmissionId(50L)).thenReturn(Optional.of(tutorCompletado));
        when(submissionRepository.findById(50L)).thenReturn(Optional.of(submission));
        when(teacherRepository.findDisponiblesOrdenadosPorCarga()).thenReturn(List.of(teacher1, teacher2, teacher3, teacher4));
        when(panelistRepository.save(any(Panelist.class))).thenAnswer(inv -> inv.getArgument(0));
        Panelist pFinal = Panelist.builder().id(1L).teacher(teacher1).rolePanelist(RolePanelist.builder().codigo("PRESIDENTE").build()).build();
        when(panelistRepository.findBySubmissionId(50L)).thenReturn(List.of(), List.of(), List.of(pFinal));
        doThrow(new RuntimeException("fallo notificacion")).when(notificationService)
                .createNotification(eq(201L), anyString());

        assertDoesNotThrow(() -> panelistService.assignPanelistsAutomaticamente(50L));
    }
}
