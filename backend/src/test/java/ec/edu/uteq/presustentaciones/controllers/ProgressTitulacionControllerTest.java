package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.dto.UpdateProgressRequest;
import ec.edu.uteq.presustentaciones.dto.ProgressTitulacionDTO;
import ec.edu.uteq.presustentaciones.entities.Student;
import ec.edu.uteq.presustentaciones.security.service.AppUserActualService;
import ec.edu.uteq.presustentaciones.services.ProgressTitulacionService;
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
class ProgressTitulacionControllerTest {

    @Mock private ProgressTitulacionService progressService;
    @Mock private AppUserActualService appUserActual;

    @InjectMocks private ProgressTitulacionController controller;

    private final ProgressTitulacionDTO dto = ProgressTitulacionDTO.builder()
            .total(8).completados(1).porcentaje(13).build();

    @BeforeEach
    void setUp() {
        Student e = new Student();
        e.setId(3L);
        when(appUserActual.student()).thenReturn(e);
    }

    @Test
    void miProgressUsaElStudentDelToken() {
        when(progressService.obtain(3L)).thenReturn(dto);

        ResponseEntity<ProgressTitulacionDTO> r = controller.miProgress();

        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals(13, r.getBody().getPorcentaje());
        verify(progressService).obtain(3L);
    }

    @Test
    void updatePasaElMapaYResuelveElStudent() {
        UpdateProgressRequest req = new UpdateProgressRequest();
        req.setPasos(Map.of("tema_definido", true));
        when(progressService.update(3L, req.getPasos())).thenReturn(dto);

        ResponseEntity<ProgressTitulacionDTO> r = controller.update(req);

        assertEquals(HttpStatus.OK, r.getStatusCode());
        verify(progressService).update(3L, req.getPasos());
    }
}
