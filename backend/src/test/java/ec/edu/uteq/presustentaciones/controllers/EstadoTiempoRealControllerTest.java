package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.entities.*;
import ec.edu.uteq.presustentaciones.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * EstadoTiempoRealController no tenia ningun test (0% de ramas segun JaCoCo) pese a ser el
 * endpoint de polling que consulta el frontend cada 15s. Cubre las combinaciones de
 * presencia/ausencia de cada modulo (solicitud, anteproyecto, cronograma, evaluacion, acta).
 */
@ExtendWith(MockitoExtension.class)
class EstadoTiempoRealControllerTest {

    @Mock private SolicitudRepository solicitudRepo;
    @Mock private AnteproyectoRepository anteproyectoRepo;
    @Mock private CronogramaRepository cronogramaRepo;
    @Mock private ActaRepository actaRepo;
    @Mock private EvaluacionRepository evaluacionRepo;

    private EstadoTiempoRealController controller;

    @BeforeEach
    void setUp() {
        controller = new EstadoTiempoRealController(
                solicitudRepo, anteproyectoRepo, cronogramaRepo, actaRepo, evaluacionRepo);
    }

    private void todoVacio(Long id) {
        when(solicitudRepo.findById(id)).thenReturn(Optional.empty());
        when(anteproyectoRepo.findBySolicitudId(id)).thenReturn(Optional.empty());
        when(cronogramaRepo.findBySolicitudId(id)).thenReturn(Optional.empty());
        when(evaluacionRepo.findBySolicitudId(id)).thenReturn(Optional.empty());
        when(actaRepo.findBySolicitudId(id)).thenReturn(Optional.empty());
    }

    @Test
    void estadoSolicitudMarcaActaGeneradaFalseYTraeTimestampCuandoNoHayNadaTodavia() {
        todoVacio(1L);

        Map<String, Object> estado = controller.estadoSolicitud(1L).getBody();

        assertEquals(false, estado.get("actaGenerada"));
        assertFalse(estado.containsKey("solicitudEstado"));
        assertTrue(estado.containsKey("timestamp"));
    }

    @Test
    void estadoSolicitudIncluyeElEstadoDeLaSolicitudCuandoExiste() {
        todoVacio(1L);
        Solicitud s = Solicitud.builder().id(1L).estado(EstadoSolicitud.builder().codigo("EVALUACION").build()).build();
        when(solicitudRepo.findById(1L)).thenReturn(Optional.of(s));

        Map<String, Object> estado = controller.estadoSolicitud(1L).getBody();

        assertEquals("EVALUACION", ((EstadoSolicitud) estado.get("solicitudEstado")).getCodigo());
        assertEquals(1L, estado.get("solicitudId"));
    }

    @Test
    void estadoSolicitudMarcaIntegridadVerificadaSegunHayaHashODeAnteproyecto() {
        todoVacio(1L);
        Anteproyecto sinHash = Anteproyecto.builder().id(2L).estado("PENDIENTE").sha256Hash(null).build();
        when(anteproyectoRepo.findBySolicitudId(1L)).thenReturn(Optional.of(sinHash));

        Map<String, Object> estado = controller.estadoSolicitud(1L).getBody();

        assertEquals(false, estado.get("anteproyectoIntegridadVerificada"));
        assertNull(estado.get("anteproyectoSha256"));
    }

    @Test
    void estadoSolicitudIncluyeLaSalaDelCronogramaCuandoEstaAsignada() {
        todoVacio(1L);
        Sala sala = Sala.builder().id(3L).nombre("Sala Magna").build();
        Cronograma c = Cronograma.builder().id(4L).sala(sala).build();
        when(cronogramaRepo.findBySolicitudId(1L)).thenReturn(Optional.of(c));

        Map<String, Object> estado = controller.estadoSolicitud(1L).getBody();

        assertEquals("Sala Magna", estado.get("cronogramaSala"));
    }

    @Test
    void estadoSolicitudNoIncluyeSalaSiElCronogramaAunNoTieneUnaAsignada() {
        todoVacio(1L);
        Cronograma c = Cronograma.builder().id(4L).sala(null).build();
        when(cronogramaRepo.findBySolicitudId(1L)).thenReturn(Optional.of(c));

        Map<String, Object> estado = controller.estadoSolicitud(1L).getBody();

        assertNull(estado.get("cronogramaSala"));
    }

    @Test
    void estadoSolicitudIncluyeLaNotaYResultadoDeLaEvaluacion() {
        todoVacio(1L);
        Evaluacion e = Evaluacion.builder().id(5L).notaFinal(8.5).resultado("APROBADO").build();
        when(evaluacionRepo.findBySolicitudId(1L)).thenReturn(Optional.of(e));

        Map<String, Object> estado = controller.estadoSolicitud(1L).getBody();

        assertEquals(8.5, estado.get("evaluacionNota"));
        assertEquals("APROBADO", estado.get("evaluacionResultado"));
    }

    @Test
    void estadoSolicitudMarcaActaGeneradaTrueYReflejaLasFirmas() {
        todoVacio(1L);
        Acta acta = Acta.builder().id(6L)
                .firmadaPresidente(true).firmadaVocal1(true).firmadaVocal2(false)
                .firmadaTutor(true).firmada(false).build();
        when(actaRepo.findBySolicitudId(1L)).thenReturn(Optional.of(acta));

        Map<String, Object> estado = controller.estadoSolicitud(1L).getBody();

        assertEquals(true, estado.get("actaGenerada"));
        assertEquals(true, estado.get("actaFirmadaPresidente"));
        assertEquals(false, estado.get("actaFirmadaVocal2"));
        assertEquals(false, estado.get("actaCompleta"));
    }

    // ── estadoBatch ──────────────────────────────────────────────────────────

    @Test
    void estadoBatchMarcaEvaluadaFalseParaUnaSolicitudSinEvaluacion() {
        when(solicitudRepo.findById(1L)).thenReturn(Optional.of(Solicitud.builder().id(1L).estado(EstadoSolicitud.builder().codigo("EVALUACION").build()).build()));
        when(evaluacionRepo.findBySolicitudId(1L)).thenReturn(Optional.empty());

        Map<Long, Map<String, Object>> resultado = controller.estadoBatch(List.of(1L)).getBody();

        assertEquals(false, resultado.get(1L).get("evaluada"));
        assertEquals("EVALUACION", ((EstadoSolicitud) resultado.get(1L).get("estado")).getCodigo());
    }

    @Test
    void estadoBatchMarcaEvaluadaTrueYManejaVariasSolicitudesALaVez() {
        when(solicitudRepo.findById(1L)).thenReturn(Optional.of(Solicitud.builder().id(1L).build()));
        when(solicitudRepo.findById(2L)).thenReturn(Optional.empty());
        when(evaluacionRepo.findBySolicitudId(1L)).thenReturn(Optional.of(Evaluacion.builder().id(9L).build()));
        when(evaluacionRepo.findBySolicitudId(2L)).thenReturn(Optional.empty());

        Map<Long, Map<String, Object>> resultado = controller.estadoBatch(List.of(1L, 2L)).getBody();

        assertEquals(true, resultado.get(1L).get("evaluada"));
        assertEquals(false, resultado.get(2L).get("evaluada"));
        assertFalse(resultado.get(2L).containsKey("estado"));
    }
}
