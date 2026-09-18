package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.dto.GenerateTopicRequest;
import ec.edu.uteq.presustentaciones.dto.TopicProposedDTO;
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

    private TopicProposedDTO topicMock;

    @BeforeEach
    void setUp() {
        topicMock = TopicProposedDTO.builder().id(1).titulo("Tema Prueba").build();

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
    void explorePasaElIdDelStudentAuthenticated() {
        when(topicService.explore(eq(1), eq(2), eq(3), eq("BASICO"), eq(7L)))
                .thenReturn(Collections.singletonList(topicMock));

        ResponseEntity<List<TopicProposedDTO>> response =
                topicController.explore(1, 2, 3, "BASICO");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        verify(topicService).explore(1, 2, 3, "BASICO", 7L);
    }

    @Test
    void detailDelegaEnElServicio() {
        when(topicService.obtainDetail(1)).thenReturn(topicMock);

        ResponseEntity<TopicProposedDTO> response = topicController.detail(1);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Tema Prueba", response.getBody().getTitulo());
    }

    @Test
    void generateSuggestionsDevuelveLaLista() {
        GenerateTopicRequest request = new GenerateTopicRequest();
        request.setProgramId(1);
        when(topicService.generateSuggestions(any(GenerateTopicRequest.class)))
                .thenReturn(Collections.singletonList(topicMock));

        ResponseEntity<List<TopicProposedDTO>> response = topicController.generateSuggestions(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void myTopicsSavedUsaElStudentAuthenticated() {
        when(topicService.obtainTopicsSaved(7L)).thenReturn(Collections.singletonList(topicMock));

        ResponseEntity<List<TopicProposedDTO>> response = topicController.myTopicsSaved();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(topicService).obtainTopicsSaved(7L);
    }

    @Test
    void saveDevuelve201YResuelveElStudentDelToken() {
        ResponseEntity<Void> response = topicController.save(9);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(topicService).saveTopicStudent(7L, 9);
    }

    @Test
    void removeSavedDevuelve204() {
        ResponseEntity<Void> response = topicController.removeSaved(9);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(topicService).removeTopicSaved(7L, 9);
    }

    @Test
    void saveFallaSiElAppUserNoTieneProfileDeStudent() {
        when(studentRepository.findByAppUserId(50L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> topicController.save(9));
        verify(topicService, never()).saveTopicStudent(anyLong(), anyInt());
    }

    @Test
    void createDelegaYDevuelve201() {
        var req = new ec.edu.uteq.presustentaciones.dto.SaveTopicProposedRequest();
        req.setTitulo("Tema");
        when(topicService.create(req)).thenReturn(topicMock);

        ResponseEntity<TopicProposedDTO> r = topicController.create(req);

        assertEquals(HttpStatus.CREATED, r.getStatusCode());
        verify(topicService).create(req);
    }

    @Test
    void updateDelega() {
        var req = new ec.edu.uteq.presustentaciones.dto.SaveTopicProposedRequest();
        req.setTitulo("Tema");
        when(topicService.update(3, req)).thenReturn(topicMock);

        ResponseEntity<TopicProposedDTO> r = topicController.update(3, req);

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
