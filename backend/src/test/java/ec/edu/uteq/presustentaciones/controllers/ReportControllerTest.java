package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.dto.ReportCountDTO;
import ec.edu.uteq.presustentaciones.dto.ReportDefenseResult;
import ec.edu.uteq.presustentaciones.dto.ReportSummaryDTO;
import ec.edu.uteq.presustentaciones.entities.Schedule;
import ec.edu.uteq.presustentaciones.entities.StatusSchedule;
import ec.edu.uteq.presustentaciones.entities.Student;
import ec.edu.uteq.presustentaciones.entities.EvaluationFinal;
import ec.edu.uteq.presustentaciones.entities.ResultEvaluation;
import ec.edu.uteq.presustentaciones.entities.Room;
import ec.edu.uteq.presustentaciones.entities.Submission;
import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.repositories.ScheduleRepository;
import ec.edu.uteq.presustentaciones.repositories.EvaluationFinalRepository;
import ec.edu.uteq.presustentaciones.repositories.SubmissionRepository;
import ec.edu.uteq.presustentaciones.services.ReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ReporteController era el hueco de cobertura más grande del paquete de controladores
 * (2 de 148 líneas, 1.4 %, con 66 ramas sin ejercitar) pese a ser uno de los que exponen
 * procedimientos almacenados -- justamente los que la guía pide priorizar.
 *
 * Los reportes PDF se generan de verdad con iText contra un ByteArrayOutputStream en
 * memoria: se verifica que la salida sea un PDF real (cabecera %PDF-) y no solo que el
 * método no lance excepción. Cada caso incluye filas con relaciones nulas (submission,
 * student, room, estado, resultado, notas), que es donde vive la mayoría de las ramas
 * y donde un NullPointerException real rompería la descarga del reporte en producción.
 */
@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    @Mock private ScheduleRepository scheduleRepo;
    @Mock private EvaluationFinalRepository evaluationFinalRepo;
    @Mock private SubmissionRepository submissionRepo;
    @Mock private ReportService reportService;

    @InjectMocks
    private ReportController controller;

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private AppUser appUser(String nombre, String apellido) {
        return AppUser.builder().id(1L).nombre(nombre).apellido(apellido).build();
    }

    private Submission submissionComplete() {
        return Submission.builder()
                .id(1L)
                .tituloTopic("Sistema de gestión de pre-sustentaciones")
                .student(Student.builder().id(1L).appUser(appUser("Ana", "Pérez")).build())
                .build();
    }

    private void assertIsPdfDownloadable(ResponseEntity<byte[]> response, String filename) {
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains(filename));
        byte[] body = response.getBody();
        assertNotNull(body);
        assertTrue(body.length > 0, "el PDF no puede venir vacío");
        assertTrue(new String(body, 0, 5, StandardCharsets.ISO_8859_1).startsWith("%PDF-"),
                "la respuesta debe ser un PDF real generado por iText, no bytes arbitrarios");
    }

    // ── PDF de schedule ─────────────────────────────────────────────────────

    @Test
    void reportScheduleGeneratesPdfRealWithRowsCompleteAndWithRelationsNull() throws Exception {
        Schedule complete = Schedule.builder()
                .id(1L)
                .submission(submissionComplete())
                .room(Room.builder().id(1L).nombre("Aula 101").build())
                .status(StatusSchedule.builder().code("PROGRAMADO").nombre("Programado").build())
                .dateStart(LocalDateTime.of(2026, 9, 10, 9, 0))
                .build();
        // Fila sin submission, sin room y sin estado: ejercita las tres ramas de fallback "—"
        Schedule minimo = Schedule.builder()
                .id(2L)
                .dateStart(LocalDateTime.of(2026, 9, 11, 11, 30))
                .build();
        // Tercera fila para ejercitar también el alternado de color de fondo (i % 2)
        Schedule sinStudent = Schedule.builder()
                .id(3L)
                .submission(Submission.builder().id(2L).build())
                .dateStart(LocalDateTime.of(2026, 9, 12, 15, 0))
                .build();
        when(scheduleRepo.findReportSchedule()).thenReturn(List.of(complete, minimo, sinStudent));

        assertIsPdfDownloadable(controller.reportSchedule(), "cronograma_presustentaciones.pdf");
        verify(scheduleRepo).findReportSchedule();
    }

    @Test
    void reportScheduleWithoutDataGeneratesPdfWithTableEmpty() throws Exception {
        when(scheduleRepo.findReportSchedule()).thenReturn(List.of());

        assertIsPdfDownloadable(controller.reportSchedule(), "cronograma_presustentaciones.pdf");
    }

    // ── PDF de estadísticas ───────────────────────────────────────────────────

    @Test
    void reportStatsGeneratesPdfRealWithApprovedFailedAndWithoutResult() throws Exception {
        EvaluationFinal aprobado = EvaluationFinal.builder()
                .id(1L)
                .submission(submissionComplete())
                .gradeInstructor(9.0).gradePanelistAverage(8.5).gradeFinal(8.8)
                .result(ResultEvaluation.builder().code("APROBADO").nombre("Aprobado").build())
                .build();
        EvaluationFinal reprobado = EvaluationFinal.builder()
                .id(2L)
                .submission(Submission.builder().id(3L).build())
                .gradeInstructor(4.0).gradePanelistAverage(3.5).gradeFinal(3.8)
                .result(ResultEvaluation.builder().code("REPROBADO").nombre("Reprobado").build())
                .build();
        // Sin resultado y sin notas: ejercita el fallback "—" de fmt() y la rama de color rojo
        EvaluationFinal sinData = EvaluationFinal.builder().id(3L).build();
        when(evaluationFinalRepo.findAllWithRelationships())
                .thenReturn(List.of(aprobado, reprobado, sinData));

        assertIsPdfDownloadable(controller.reportStats(), "estadisticas_evaluaciones.pdf");
        verify(evaluationFinalRepo).findAllWithRelationships();
    }

    @Test
    void reportStatsWithoutEvaluationsUsesAverageZeroAndNotFails() throws Exception {
        when(evaluationFinalRepo.findAllWithRelationships()).thenReturn(List.of());

        assertIsPdfDownloadable(controller.reportStats(), "estadisticas_evaluaciones.pdf");
    }

    // ── Procedimiento almacenado sp_generate_reporte_defensas ──────────────────

    @Test
    void reportDefensesDelegatesInProcedureStored() {
        List<ReportDefenseResult> esperado = List.of(new ReportDefenseResult());
        when(submissionRepo.generateReportDefenses("Software")).thenReturn(esperado);

        ResponseEntity<List<ReportDefenseResult>> response = controller.reportDefenses("Software");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(esperado, response.getBody());
        verify(submissionRepo).generateReportDefenses("Software");
    }

    // ── Estadísticas JSON ─────────────────────────────────────────────────────

    @Test
    void statsJsonCalculatesTotalsAverageAndRateOfApproval() {
        EvaluationFinal aprobado1 = EvaluationFinal.builder().gradeFinal(8.0)
                .result(ResultEvaluation.builder().code("APROBADO").build()).build();
        EvaluationFinal aprobado2 = EvaluationFinal.builder().gradeFinal(9.0)
                .result(ResultEvaluation.builder().code("APROBADO").build()).build();
        EvaluationFinal reprobado = EvaluationFinal.builder().gradeFinal(4.0)
                .result(ResultEvaluation.builder().code("REPROBADO").build()).build();
        // notaFinal null y resultado null: no debe count en el promedio ni en los counts
        EvaluationFinal incompleto = EvaluationFinal.builder().build();
        when(evaluationFinalRepo.findAllWithRelationships())
                .thenReturn(List.of(aprobado1, aprobado2, reprobado, incompleto));
        when(submissionRepo.countByStatusCode("APROBADA")).thenReturn(5L);

        ResponseEntity<Map<String, Object>> response = controller.statsJson();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(4L, body.get("totalEvaluados"));
        assertEquals(2L, body.get("aprobados"));
        assertEquals(1L, body.get("reprobados"));
        // (8.0 + 9.0 + 4.0) / 3 = 7.0 -- el null queda fuera por el filtro n > 0
        assertEquals(7.0, body.get("notaPromedio"));
        assertEquals(50L, body.get("tasaAprobacion")); // 2 de 4
        assertEquals(5L, body.get("solicitudesPendientes"));
    }

    @Test
    void statsJsonWithoutEvaluationsReturnsRateZeroWithoutSplitByZero() {
        when(evaluationFinalRepo.findAllWithRelationships()).thenReturn(List.of());
        when(submissionRepo.countByStatusCode("APROBADA")).thenReturn(0L);

        Map<String, Object> body = controller.statsJson().getBody();

        assertNotNull(body);
        assertEquals(0L, body.get("totalEvaluados"));
        assertEquals(0.0, body.get("notaPromedio"));
        // El operador ternario del controlador promueve ambas ramas a long, asi que
        // la tasa viaja como Long incluso en el caso 0 -- el frontend recibe siempre
        // el mismo tipo JSON, sin importar si hay evaluations o no.
        assertEquals(0L, body.get("tasaAprobacion"));
    }

    // ── Módulo de reportes JSON (delegación en ReporteService) ────────────────

    @Test
    void summaryDelegatesInServiceWithFiltersOfDateAndProgram() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 12, 31);
        ReportSummaryDTO esperado = ReportSummaryDTO.builder().build();
        when(reportService.summary(from, to, "Software")).thenReturn(esperado);

        ResponseEntity<?> response = controller.summary(from, to, "Software");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(esperado, response.getBody());
    }

    @Test
    void summaryWithoutFiltersPassesNullsToService() {
        when(reportService.summary(null, null, null)).thenReturn(ReportSummaryDTO.builder().build());

        assertEquals(HttpStatus.OK, controller.summary(null, null, null).getStatusCode());
        verify(reportService).summary(null, null, null);
    }

    @Test
    void submissionsByStatusDelegatesInService() {
        List<ReportCountDTO> esperado = List.of(new ReportCountDTO());
        when(reportService.submissionsByStatus(null, null, null)).thenReturn(esperado);

        ResponseEntity<?> response = controller.submissionsByStatus(null, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(esperado, response.getBody());
    }

    @Test
    void defensesByPeriodDelegatesInService() {
        List<ReportCountDTO> esperado = List.of(new ReportCountDTO());
        when(reportService.defensesByPeriod(null, null)).thenReturn(esperado);

        ResponseEntity<?> response = controller.defensesByPeriod(null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(esperado, response.getBody());
    }

    @Test
    void summaryMinutesDelegatesInService() {
        Map<String, Long> esperado = Map.of("generadas", 3L);
        when(reportService.summaryMinutes(null, null)).thenReturn(esperado);

        ResponseEntity<?> response = controller.summaryMinutes(null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(esperado, response.getBody());
    }

    @Test
    void activityTeacherDelegatesInService() {
        when(reportService.activityByTeacher()).thenReturn(List.of());

        assertEquals(HttpStatus.OK, controller.activityTeacher().getStatusCode());
        verify(reportService).activityByTeacher();
    }

    @Test
    void byProgramDelegatesInService() {
        List<Map<String, Object>> esperado = List.of(Map.of("carrera", "Software"));
        when(reportService.statsByProgram()).thenReturn(esperado);

        ResponseEntity<?> response = controller.byProgram();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(esperado, response.getBody());
    }
}
