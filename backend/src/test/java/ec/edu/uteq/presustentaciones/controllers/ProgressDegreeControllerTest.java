package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.dto.UpdateProgressRequest;
import ec.edu.uteq.presustentaciones.dto.ProgressDegreeDTO;
import ec.edu.uteq.presustentaciones.entities.Student;
import ec.edu.uteq.presustentaciones.security.service.CurrentAppUserService;
import ec.edu.uteq.presustentaciones.services.ProgressDegreeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProgressDegreeControllerTest {

    @Mock private ProgressDegreeService progressService;
    @Mock private CurrentAppUserService currentAppUser;

    @InjectMocks private ProgressDegreeController controller;

    private final ProgressDegreeDTO dto = ProgressDegreeDTO.builder()
            .total(8).completados(1).porcentaje(13).build();

    @BeforeEach
    void setUp() {
        Student e = new Student();
        e.setId(3L);
        when(currentAppUser.student()).thenReturn(e);
    }

    @Test
    void myProgressUsesStudentOfToken() {
        when(progressService.obtain(3L)).thenReturn(dto);

        ResponseEntity<ProgressDegreeDTO> r = controller.myProgress();

        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals(13, r.getBody().getPorcentaje());
        verify(progressService).obtain(3L);
    }

    @Test
    void updatePassesMapAndResolvesStudent() {
        UpdateProgressRequest req = new UpdateProgressRequest();
        req.setPasos(Map.of("tema_definido", true));
        when(progressService.update(3L, req.getPasos())).thenReturn(dto);

        ResponseEntity<ProgressDegreeDTO> r = controller.update(req);

        assertEquals(HttpStatus.OK, r.getStatusCode());
        verify(progressService).update(3L, req.getPasos());
    }
}
