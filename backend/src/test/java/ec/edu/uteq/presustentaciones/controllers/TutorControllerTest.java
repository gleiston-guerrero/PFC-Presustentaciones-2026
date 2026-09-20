package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.dto.MyStudentTuteeDTO;
import ec.edu.uteq.presustentaciones.entities.Tutor;
import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.repositories.AppUserRepository;
import ec.edu.uteq.presustentaciones.services.TutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * TutorController expone sp_obtain_estadisticas_tutores y tenía 4 de 21 líneas
 * cubiertas. Se cubre además el caso en que el appUser autenticado no existe en la
 * base (token válido de un appUser ya borrado), que hoy revienta con RuntimeException
 * y conviene dejar fijado como comportamiento conocido.
 */
@ExtendWith(MockitoExtension.class)
class TutorControllerTest {

    @Mock private TutorService tutorService;
    @Mock private AppUserRepository appUserRepository;

    @InjectMocks
    private TutorController controller;

    @BeforeEach
    void authenticateTeacher() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("docente@uteq.edu.ec", null, List.of()));
    }

    @AfterEach
    void cleanContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void myStudentsResolvesTeacherAuthenticatedBeforeOfView() {
        AppUser teacher = AppUser.builder().id(50L).email("docente@uteq.edu.ec").build();
        List<MyStudentTuteeDTO> roster = List.of();
        when(appUserRepository.findByEmail("docente@uteq.edu.ec")).thenReturn(Optional.of(teacher));
        when(tutorService.myStudents(50L)).thenReturn(roster);

        ResponseEntity<List<MyStudentTuteeDTO>> response = controller.myStudents();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(roster, response.getBody());
    }

    @Test
    void myStudentsFailsIfAppUserOfTokenAlreadyNotExists() {
        when(appUserRepository.findByEmail("docente@uteq.edu.ec")).thenReturn(Optional.empty());

        RuntimeException error = assertThrows(RuntimeException.class, () -> controller.myStudents());

        assertEquals("Usuario autenticado no encontrado", error.getMessage());
        verify(tutorService, never()).myStudents(any());
    }

    @Test
    void assignReturnsTutorCreated() {
        Tutor tutor = Tutor.builder().id(1L).build();
        when(tutorService.assignTutor(1L, 2L)).thenReturn(tutor);

        ResponseEntity<Tutor> response = controller.assign(1L, 2L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(tutor, response.getBody());
    }

    @Test
    void assignReturns400WithoutBodyWhenServiceRejects() {
        when(tutorService.assignTutor(1L, 2L)).thenThrow(new RuntimeException("La solicitud ya tiene tutor"));

        ResponseEntity<Tutor> response = controller.assign(1L, 2L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    void bySubmissionReturns404WhenNotHasTutorAssigned() {
        when(tutorService.searchBySubmission(1L)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, controller.bySubmission(1L).getStatusCode());
    }

    @Test
    void bySubmissionReturnsTutorWhenExists() {
        Tutor tutor = Tutor.builder().id(1L).build();
        when(tutorService.searchBySubmission(1L)).thenReturn(Optional.of(tutor));

        assertSame(tutor, controller.bySubmission(1L).getBody());
    }

    @Test
    void listPropagatesPaginationReceived() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Tutor> pagina = new PageImpl<>(List.of(Tutor.builder().id(1L).build()));
        when(tutorService.listAll(pageable)).thenReturn(pagina);

        assertSame(pagina, controller.list(pageable).getBody());
    }

    @Test
    void deleteReturns204() {
        assertEquals(HttpStatus.NO_CONTENT, controller.delete(3L).getStatusCode());
        verify(tutorService).deleteTutor(3L);
    }

    // ── sp_obtain_estadisticas_tutores ───────────────────────────────────────

    @Test
    void statsReturnsRowsOfProcedureStored() {
        List<Map<String, Object>> stats = List.of(Map.of(
                "tutorDocenteId", 1L, "tutorNombre", "Ana Pérez",
                "tutoriasActivas", 3L, "tutoriasCompletadas", 5L, "totalFasesAprobadas", 12L));
        when(tutorService.obtainStatsTutorsSP()).thenReturn(stats);

        ResponseEntity<?> response = controller.stats();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(stats, response.getBody());
    }

    @Test
    void statsTranslatesFailureOfProcedureTo400() {
        when(tutorService.obtainStatsTutorsSP())
                .thenThrow(new RuntimeException("function presus.sp_obtener_estadisticas_tutores() does not exist"));

        ResponseEntity<?> response = controller.stats();

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> error = (Map<String, String>) response.getBody();
        assertTrue(error.get("error").contains("sp_obtener_estadisticas_tutores"));
    }
}
