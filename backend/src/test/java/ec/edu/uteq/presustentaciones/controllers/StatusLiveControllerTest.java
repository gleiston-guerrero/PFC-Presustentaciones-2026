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
class StatusLiveControllerTest {

    @Mock private SubmissionRepository submissionRepo;
    @Mock private ProposalRepository proposalRepo;
    @Mock private ScheduleRepository scheduleRepo;
    @Mock private MinutesRepository minutesRepo;
    @Mock private EvaluationRepository evaluationRepo;

    private StatusLiveController controller;

    @BeforeEach
    void setUp() {
        controller = new StatusLiveController(
                submissionRepo, proposalRepo, scheduleRepo, minutesRepo, evaluationRepo);
    }

    private void allEmpty(Long id) {
        when(submissionRepo.findById(id)).thenReturn(Optional.empty());
        when(proposalRepo.findBySubmissionId(id)).thenReturn(Optional.empty());
        when(scheduleRepo.findBySubmissionId(id)).thenReturn(Optional.empty());
        when(evaluationRepo.findBySubmissionId(id)).thenReturn(Optional.empty());
        when(minutesRepo.findBySubmissionId(id)).thenReturn(Optional.empty());
    }

    @Test
    void statusSubmissionMarcaMinutesGeneradaFalseYTraeTimestampCuandoNoHayNadaTodavia() {
        allEmpty(1L);

        Map<String, Object> status = controller.statusSubmission(1L).getBody();

        assertEquals(false, status.get("actaGenerada"));
        assertFalse(status.containsKey("solicitudEstado"));
        assertTrue(status.containsKey("timestamp"));
    }

    @Test
    void statusSubmissionIncluyeElStatusDeLaSubmissionCuandoExists() {
        allEmpty(1L);
        Submission s = Submission.builder().id(1L).status(StatusSubmission.builder().code("EVALUACION").build()).build();
        when(submissionRepo.findById(1L)).thenReturn(Optional.of(s));

        Map<String, Object> status = controller.statusSubmission(1L).getBody();

        assertEquals("EVALUACION", ((StatusSubmission) status.get("solicitudEstado")).getCode());
        assertEquals(1L, status.get("solicitudId"));
    }

    @Test
    void statusSubmissionMarcaIntegrityVerificadaSegunHayaHashODeProposal() {
        allEmpty(1L);
        Proposal sinHash = Proposal.builder().id(2L).status("PENDIENTE").sha256Hash(null).build();
        when(proposalRepo.findBySubmissionId(1L)).thenReturn(Optional.of(sinHash));

        Map<String, Object> status = controller.statusSubmission(1L).getBody();

        assertEquals(false, status.get("anteproyectoIntegridadVerificada"));
        assertNull(status.get("anteproyectoSha256"));
    }

    @Test
    void statusSubmissionIncluyeLaRoomDelScheduleCuandoIsAsignada() {
        allEmpty(1L);
        Room room = Room.builder().id(3L).nombre("Sala Magna").build();
        Schedule c = Schedule.builder().id(4L).room(room).build();
        when(scheduleRepo.findBySubmissionId(1L)).thenReturn(Optional.of(c));

        Map<String, Object> status = controller.statusSubmission(1L).getBody();

        assertEquals("Sala Magna", status.get("cronogramaSala"));
    }

    @Test
    void statusSubmissionNoIncluyeRoomSiElScheduleAunNoTieneUnaAsignada() {
        allEmpty(1L);
        Schedule c = Schedule.builder().id(4L).room(null).build();
        when(scheduleRepo.findBySubmissionId(1L)).thenReturn(Optional.of(c));

        Map<String, Object> status = controller.statusSubmission(1L).getBody();

        assertNull(status.get("cronogramaSala"));
    }

    @Test
    void statusSubmissionIncluyeLaGradeYResultDeLaEvaluation() {
        allEmpty(1L);
        Evaluation e = Evaluation.builder().id(5L).gradeFinal(8.5).result("APROBADO").build();
        when(evaluationRepo.findBySubmissionId(1L)).thenReturn(Optional.of(e));

        Map<String, Object> status = controller.statusSubmission(1L).getBody();

        assertEquals(8.5, status.get("evaluacionNota"));
        assertEquals("APROBADO", status.get("evaluacionResultado"));
    }

    @Test
    void statusSubmissionMarcaMinutesGeneradaTrueYReflejaLasSignatures() {
        allEmpty(1L);
        Minutes minutes = Minutes.builder().id(6L)
                .firmadaPresidente(true).firmadaVocal1(true).firmadaVocal2(false)
                .firmadaTutor(true).firmada(false).build();
        when(minutesRepo.findBySubmissionId(1L)).thenReturn(Optional.of(minutes));

        Map<String, Object> status = controller.statusSubmission(1L).getBody();

        assertEquals(true, status.get("actaGenerada"));
        assertEquals(true, status.get("actaFirmadaPresidente"));
        assertEquals(false, status.get("actaFirmadaVocal2"));
        assertEquals(false, status.get("actaCompleta"));
    }

    // ── estadoBatch ──────────────────────────────────────────────────────────

    @Test
    void statusBatchMarcaEvaluadaFalseForUnaSubmissionWithoutEvaluation() {
        when(submissionRepo.findById(1L)).thenReturn(Optional.of(Submission.builder().id(1L).status(StatusSubmission.builder().code("EVALUACION").build()).build()));
        when(evaluationRepo.findBySubmissionId(1L)).thenReturn(Optional.empty());

        Map<Long, Map<String, Object>> result = controller.statusBatch(List.of(1L)).getBody();

        assertEquals(false, result.get(1L).get("evaluada"));
        assertEquals("EVALUACION", ((StatusSubmission) result.get(1L).get("estado")).getCode());
    }

    @Test
    void statusBatchMarcaEvaluadaTrueYManejaVariasSubmissionsALaVez() {
        when(submissionRepo.findById(1L)).thenReturn(Optional.of(Submission.builder().id(1L).build()));
        when(submissionRepo.findById(2L)).thenReturn(Optional.empty());
        when(evaluationRepo.findBySubmissionId(1L)).thenReturn(Optional.of(Evaluation.builder().id(9L).build()));
        when(evaluationRepo.findBySubmissionId(2L)).thenReturn(Optional.empty());

        Map<Long, Map<String, Object>> result = controller.statusBatch(List.of(1L, 2L)).getBody();

        assertEquals(true, result.get(1L).get("evaluada"));
        assertEquals(false, result.get(2L).get("evaluada"));
        assertFalse(result.get(2L).containsKey("estado"));
    }
}
