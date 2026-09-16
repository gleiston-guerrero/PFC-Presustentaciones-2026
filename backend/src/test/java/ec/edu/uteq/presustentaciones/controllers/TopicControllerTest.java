package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.dto.GenerateTopicRequest;
import ec.edu.uteq.presustentaciones.dto.TopicPropuestoDTO;
import ec.edu.uteq.presustentaciones.entities.Student;
import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.repositories.StudentRepository;
import ec.edu.uteq.presustentaciones.repositories.AppUserRepository;
import ec.edu.uteq.presustentaciones.services.TopicService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TopicControllerTest {

    @Mock private TopicService topicService;
    @Mock private AppUserRepository appUserRepository;
    @Mock private StudentRepository studentRepository;

    @InjectMocks private TopicController topicController;

    private TopicPropuestoDTO topicMock;

    @BeforeEach
    void setUp() {
        topicMock = TopicPropuestoDTO.builder().id(1).titulo("Tema Prueba").build();

        AppUser appUser = new AppUser();
        appUser.setId(50L);
        appUser.setEmail("est@uteq.edu.ec");
        Student student = new Student();
        student.setId(7L);

        when(appUserRepository.findByEmail("est@uteq.edu.ec")).thenReturn(Optional.of(appUser));
        when(studentRepository.findByAppUserId(50L)).thenReturn(Optional.of(student));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("est@uteq.edu.ec", null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void explorarPasaElIdDelStudentAutenticado() {
        when(topicService.explorar(eq(1), eq(2), eq(3), eq("BASICO"), eq(7L)))
                .thenReturn(Collections.singletonList(topicMock));

        ResponseEntity<List<TopicPropuestoDTO>> response =
                topicController.explorar(1, 2, 3, "BASICO");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        verify(topicService).explorar(1, 2, 3, "BASICO", 7L);
    }

    @Test
    void detalleDelegaEnElServicio() {
        when(topicService.obtainDetalle(1)).thenReturn(topicMock);

        ResponseEntity<TopicPropuestoDTO> response = topicController.detalle(1);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Tema Prueba", response.getBody().getTitulo());
    }

    @Test
    void generateIdeasDevuelveLaLista() {
        GenerateTopicRequest request = new GenerateTopicRequest();
        request.setProgramId(1);
        when(topicService.generateIdeas(any(GenerateTopicRequest.class)))
                .thenReturn(Collections.singletonList(topicMock));

        ResponseEntity<List<TopicPropuestoDTO>> response = topicController.generateIdeas(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void misTopicsGuardadosUsaElStudentAutenticado() {
        when(topicService.obtainTopicsGuardados(7L)).thenReturn(Collections.singletonList(topicMock));

        ResponseEntity<List<TopicPropuestoDTO>> response = topicController.misTopicsGuardados();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(topicService).obtainTopicsGuardados(7L);
    }

    @Test
    void saveDevuelve201YResuelveElStudentDelToken() {
        ResponseEntity<Void> response = topicController.save(9);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(topicService).saveTopicStudent(7L, 9);
    }

    @Test
    void removeGuardadoDevuelve204() {
        ResponseEntity<Void> response = topicController.removeGuardado(9);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(topicService).removeTopicGuardado(7L, 9);
    }

    @Test
    void saveFallaSiElAppUserNoTienePerfilDeStudent() {
        when(studentRepository.findByAppUserId(50L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> topicController.save(9));
        verify(topicService, never()).saveTopicStudent(anyLong(), anyInt());
    }

    @Test
    void createDelegaYDevuelve201() {
        var req = new ec.edu.uteq.presustentaciones.dto.SaveTopicPropuestoRequest();
        req.setTitulo("Tema");
        when(topicService.create(req)).thenReturn(topicMock);

        ResponseEntity<TopicPropuestoDTO> r = topicController.create(req);

        assertEquals(HttpStatus.CREATED, r.getStatusCode());
        verify(topicService).create(req);
    }

    @Test
    void updateDelega() {
        var req = new ec.edu.uteq.presustentaciones.dto.SaveTopicPropuestoRequest();
        req.setTitulo("Tema");
        when(topicService.update(3, req)).thenReturn(topicMock);

        ResponseEntity<TopicPropuestoDTO> r = topicController.update(3, req);

        assertEquals(HttpStatus.OK, r.getStatusCode());
        verify(topicService).update(3, req);
    }

    @Test
    void deleteDelegaYDevuelve204() {
        ResponseEntity<Void> r = topicController.delete(3);

        assertEquals(HttpStatus.NO_CONTENT, r.getStatusCode());
        verify(topicService).delete(3);
    }
}
