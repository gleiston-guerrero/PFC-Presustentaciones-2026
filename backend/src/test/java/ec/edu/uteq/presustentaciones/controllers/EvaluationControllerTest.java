package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.entities.EvaluationFinal;
import ec.edu.uteq.presustentaciones.services.EvaluationService;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * EvaluationController expone sp_calculate_promedio_evaluation (categoría "cálculos
 * agregados" del catálogo de procedimientos) y tenía 3 de 18 líneas cubiertas.
 * Se cubre tanto la ruta feliz como la traducción de errores del servicio a 400,
 * incluida la del endpoint del procedimiento almacenado.
 */
@ExtendWith(MockitoExtension.class)
class EvaluationControllerTest {

    @Mock private EvaluationService evaluationService;

    @InjectMocks
    private EvaluationController controller;

    @SuppressWarnings("unchecked")
    private String errorDe(ResponseEntity<?> response) {
        return ((Map<String, String>) response.getBody()).get("error");
    }

    @Test
    void evaluarPonderadoDevuelveLaEvaluationCalculada() {
        EvaluationFinal evaluation = EvaluationFinal.builder().id(1L).notaFinal(8.6).build();
        when(evaluationService.evaluarSubmission(1L, 2L, 9.0, 8.0, "Buen trabajo", 60.0, 40.0))
                .thenReturn(evaluation);

        ResponseEntity<?> response = controller.evaluarPonderado(1L, 2L, 9.0, 8.0, "Buen trabajo", 60.0, 40.0);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(evaluation, response.getBody());
    }

    @Test
    void evaluarPonderadoConPesosInvalidosDevuelve400ConElMensajeDelServicio() {
        when(evaluationService.evaluarSubmission(1L, 2L, 9.0, 8.0, "obs", 70.0, 40.0))
                .thenThrow(new RuntimeException("Los pesos deben sumar 100"));

        ResponseEntity<?> response = controller.evaluarPonderado(1L, 2L, 9.0, 8.0, "obs", 70.0, 40.0);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Los pesos deben sumar 100", errorDe(response));
    }

    @Test
    void evaluarLegadoDelegaEnElServicioConLaNotaFinalDirecta() {
        EvaluationFinal evaluation = EvaluationFinal.builder().id(1L).build();
        when(evaluationService.evaluarSubmission(1L, 2L, 7.5, "obs")).thenReturn(evaluation);

        assertSame(evaluation, controller.evaluar(1L, 2L, 7.5, "obs"));
    }

    @Test
    void listPropagaLaPaginacionRecibida() {
        PageRequest pageable = PageRequest.of(0, 20);
        Page<EvaluationFinal> pagina = new PageImpl<>(List.of(EvaluationFinal.builder().id(1L).build()));
        when(evaluationService.listEvaluations(pageable)).thenReturn(pagina);

        assertSame(pagina, controller.list(pageable).getBody());
    }

    @Test
    void listPorStudentYPorAppUserDeleganEnElServicio() {
        List<EvaluationFinal> porStudent = List.of(EvaluationFinal.builder().id(1L).build());
        List<EvaluationFinal> porAppUser = List.of(EvaluationFinal.builder().id(2L).build());
        when(evaluationService.listPorStudent(7L)).thenReturn(porStudent);
        when(evaluationService.listPorAppUser(50L)).thenReturn(porAppUser);

        assertSame(porStudent, controller.listPorStudent(7L));
        assertSame(porAppUser, controller.listPorAppUser(50L));
    }

    @Test
    void porSubmissionDevuelve404CuandoLaSubmissionNoTieneEvaluation() {
        when(evaluationService.searchPorSubmission(1L)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, controller.porSubmission(1L).getStatusCode());
    }

    @Test
    void porSubmissionDevuelveLaEvaluationCuandoExiste() {
        EvaluationFinal evaluation = EvaluationFinal.builder().id(1L).build();
        when(evaluationService.searchPorSubmission(1L)).thenReturn(Optional.of(evaluation));

        assertSame(evaluation, controller.porSubmission(1L).getBody());
    }

    // ── sp_calculate_promedio_evaluation ───────────────────────────────────────

    @Test
    void calculatePromedioDevuelveElResultadoDelProcedimientoAlmacenado() {
        Map<String, Object> resultado = Map.of(
                "solicitudId", 1L, "notaFinal", 8.6, "estadoResultado", "APROBADO");
        when(evaluationService.calculatePromedioSP(1L)).thenReturn(resultado);

        ResponseEntity<?> response = controller.calculatePromedio(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(resultado, response.getBody());
    }

    @Test
    void calculatePromedioTraduceElErrorDelProcedimientoA400() {
        when(evaluationService.calculatePromedioSP(99L))
                .thenThrow(new RuntimeException("La solicitud 99 no tiene evaluaciones por criterio"));

        ResponseEntity<?> response = controller.calculatePromedio(99L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("La solicitud 99 no tiene evaluaciones por criterio", errorDe(response));
    }
}
