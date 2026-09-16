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
 * presencia/ausencia de cada modulo (submission, proposal, schedule, evaluation, minutes).
 */
@ExtendWith(MockitoExtension.class)
class EstadoTiempoRealControllerTest {

    @Mock private SubmissionRepository submissionRepo;
    @Mock private ProposalRepository proposalRepo;
    @Mock private ScheduleRepository scheduleRepo;
    @Mock private MinutesRepository minutesRepo;
    @Mock private EvaluationRepository evaluationRepo;

    private EstadoTiempoRealController controller;

    @BeforeEach
    void setUp() {
        controller = new EstadoTiempoRealController(
                submissionRepo, proposalRepo, scheduleRepo, minutesRepo, evaluationRepo);
    }

    private void todoVacio(Long id) {
        when(submissionRepo.findById(id)).thenReturn(Optional.empty());
        when(proposalRepo.findBySubmissionId(id)).thenReturn(Optional.empty());
        when(scheduleRepo.findBySubmissionId(id)).thenReturn(Optional.empty());
        when(evaluationRepo.findBySubmissionId(id)).thenReturn(Optional.empty());
        when(minutesRepo.findBySubmissionId(id)).thenReturn(Optional.empty());
    }

    @Test
    void estadoSubmissionMarcaMinutesGeneradaFalseYTraeTimestampCuandoNoHayNadaTodavia() {
        todoVacio(1L);

        Map<String, Object> estado = controller.estadoSubmission(1L).getBody();

        assertEquals(false, estado.get("actaGenerada"));
        assertFalse(estado.containsKey("solicitudEstado"));
        assertTrue(estado.containsKey("timestamp"));
    }

    @Test
    void estadoSubmissionIncluyeElEstadoDeLaSubmissionCuandoExiste() {
        todoVacio(1L);
        Submission s = Submission.builder().id(1L).estado(EstadoSubmission.builder().codigo("EVALUACION").build()).build();
        when(submissionRepo.findById(1L)).thenReturn(Optional.of(s));

        Map<String, Object> estado = controller.estadoSubmission(1L).getBody();

        assertEquals("EVALUACION", ((EstadoSubmission) estado.get("solicitudEstado")).getCodigo());
        assertEquals(1L, estado.get("solicitudId"));
    }

    @Test
    void estadoSubmissionMarcaIntegridadVerificadaSegunHayaHashODeProposal() {
        todoVacio(1L);
        Proposal sinHash = Proposal.builder().id(2L).estado("PENDIENTE").sha256Hash(null).build();
        when(proposalRepo.findBySubmissionId(1L)).thenReturn(Optional.of(sinHash));

        Map<String, Object> estado = controller.estadoSubmission(1L).getBody();

        assertEquals(false, estado.get("anteproyectoIntegridadVerificada"));
        assertNull(estado.get("anteproyectoSha256"));
    }

    @Test
    void estadoSubmissionIncluyeLaRoomDelScheduleCuandoEstaAsignada() {
        todoVacio(1L);
        Room room = Room.builder().id(3L).nombre("Sala Magna").build();
        Schedule c = Schedule.builder().id(4L).room(room).build();
        when(scheduleRepo.findBySubmissionId(1L)).thenReturn(Optional.of(c));

        Map<String, Object> estado = controller.estadoSubmission(1L).getBody();

        assertEquals("Sala Magna", estado.get("cronogramaSala"));
    }

    @Test
    void estadoSubmissionNoIncluyeRoomSiElScheduleAunNoTieneUnaAsignada() {
        todoVacio(1L);
        Schedule c = Schedule.builder().id(4L).room(null).build();
        when(scheduleRepo.findBySubmissionId(1L)).thenReturn(Optional.of(c));

        Map<String, Object> estado = controller.estadoSubmission(1L).getBody();

        assertNull(estado.get("cronogramaSala"));
    }

    @Test
    void estadoSubmissionIncluyeLaNotaYResultadoDeLaEvaluation() {
        todoVacio(1L);
        Evaluation e = Evaluation.builder().id(5L).notaFinal(8.5).resultado("APROBADO").build();
        when(evaluationRepo.findBySubmissionId(1L)).thenReturn(Optional.of(e));

        Map<String, Object> estado = controller.estadoSubmission(1L).getBody();

        assertEquals(8.5, estado.get("evaluacionNota"));
        assertEquals("APROBADO", estado.get("evaluacionResultado"));
    }

    @Test
    void estadoSubmissionMarcaMinutesGeneradaTrueYReflejaLasFirmas() {
        todoVacio(1L);
        Minutes minutes = Minutes.builder().id(6L)
                .firmadaPresidente(true).firmadaVocal1(true).firmadaVocal2(false)
                .firmadaTutor(true).firmada(false).build();
        when(minutesRepo.findBySubmissionId(1L)).thenReturn(Optional.of(minutes));

        Map<String, Object> estado = controller.estadoSubmission(1L).getBody();

        assertEquals(true, estado.get("actaGenerada"));
        assertEquals(true, estado.get("actaFirmadaPresidente"));
        assertEquals(false, estado.get("actaFirmadaVocal2"));
        assertEquals(false, estado.get("actaCompleta"));
    }

    // ── estadoBatch ──────────────────────────────────────────────────────────

    @Test
    void estadoBatchMarcaEvaluadaFalseParaUnaSubmissionSinEvaluation() {
        when(submissionRepo.findById(1L)).thenReturn(Optional.of(Submission.builder().id(1L).estado(EstadoSubmission.builder().codigo("EVALUACION").build()).build()));
        when(evaluationRepo.findBySubmissionId(1L)).thenReturn(Optional.empty());

        Map<Long, Map<String, Object>> resultado = controller.estadoBatch(List.of(1L)).getBody();

        assertEquals(false, resultado.get(1L).get("evaluada"));
        assertEquals("EVALUACION", ((EstadoSubmission) resultado.get(1L).get("estado")).getCodigo());
    }

    @Test
    void estadoBatchMarcaEvaluadaTrueYManejaVariasSubmissionsALaVez() {
        when(submissionRepo.findById(1L)).thenReturn(Optional.of(Submission.builder().id(1L).build()));
        when(submissionRepo.findById(2L)).thenReturn(Optional.empty());
        when(evaluationRepo.findBySubmissionId(1L)).thenReturn(Optional.of(Evaluation.builder().id(9L).build()));
        when(evaluationRepo.findBySubmissionId(2L)).thenReturn(Optional.empty());

        Map<Long, Map<String, Object>> resultado = controller.estadoBatch(List.of(1L, 2L)).getBody();

        assertEquals(true, resultado.get(1L).get("evaluada"));
        assertEquals(false, resultado.get(2L).get("evaluada"));
        assertFalse(resultado.get(2L).containsKey("estado"));
    }
}
