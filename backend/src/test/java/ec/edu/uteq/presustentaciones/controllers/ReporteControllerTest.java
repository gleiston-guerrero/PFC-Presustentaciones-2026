package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.dto.ReporteCountDTO;
import ec.edu.uteq.presustentaciones.dto.ReporteDefensaResult;
import ec.edu.uteq.presustentaciones.dto.ReporteResumenDTO;
import ec.edu.uteq.presustentaciones.entities.Schedule;
import ec.edu.uteq.presustentaciones.entities.EstadoSchedule;
import ec.edu.uteq.presustentaciones.entities.Student;
import ec.edu.uteq.presustentaciones.entities.EvaluationFinal;
import ec.edu.uteq.presustentaciones.entities.ResultadoEvaluation;
import ec.edu.uteq.presustentaciones.entities.Room;
import ec.edu.uteq.presustentaciones.entities.Submission;
import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.repositories.ScheduleRepository;
import ec.edu.uteq.presustentaciones.repositories.EvaluationFinalRepository;
import ec.edu.uteq.presustentaciones.repositories.SubmissionRepository;
import ec.edu.uteq.presustentaciones.services.ReporteService;
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
class ReporteControllerTest {

    @Mock private ScheduleRepository scheduleRepo;
    @Mock private EvaluationFinalRepository evaluationFinalRepo;
    @Mock private SubmissionRepository submissionRepo;
    @Mock private ReporteService reporteService;

    @InjectMocks
    private ReporteController controller;

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private AppUser appUser(String nombre, String apellido) {
        return AppUser.builder().id(1L).nombre(nombre).apellido(apellido).build();
    }

    private Submission submissionCompleta() {
        return Submission.builder()
                .id(1L)
                .tituloTopic("Sistema de gestión de pre-sustentaciones")
                .student(Student.builder().id(1L).appUser(appUser("Ana", "Pérez")).build())
                .build();
    }

    private void assertEsPdfDescargable(ResponseEntity<byte[]> response, String filename) {
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
    void reporteScheduleGeneraPdfRealConFilasCompletasYConRelacionesNulas() throws Exception {
        Schedule completo = Schedule.builder()
                .id(1L)
                .submission(submissionCompleta())
                .room(Room.builder().id(1L).nombre("Aula 101").build())
                .estado(EstadoSchedule.builder().codigo("PROGRAMADO").nombre("Programado").build())
                .fechaInicio(LocalDateTime.of(2026, 9, 10, 9, 0))
                .build();
        // Fila sin submission, sin room y sin estado: ejercita las tres ramas de fallback "—"
        Schedule minimo = Schedule.builder()
                .id(2L)
                .fechaInicio(LocalDateTime.of(2026, 9, 11, 11, 30))
                .build();
        // Tercera fila para ejercitar también el alternado de color de fondo (i % 2)
        Schedule sinStudent = Schedule.builder()
                .id(3L)
                .submission(Submission.builder().id(2L).build())
                .fechaInicio(LocalDateTime.of(2026, 9, 12, 15, 0))
                .build();
        when(scheduleRepo.findReporteSchedule()).thenReturn(List.of(completo, minimo, sinStudent));

        assertEsPdfDescargable(controller.reporteSchedule(), "cronograma_presustentaciones.pdf");
        verify(scheduleRepo).findReporteSchedule();
    }

    @Test
    void reporteScheduleSinDatosGeneraPdfConTablaVacia() throws Exception {
        when(scheduleRepo.findReporteSchedule()).thenReturn(List.of());

        assertEsPdfDescargable(controller.reporteSchedule(), "cronograma_presustentaciones.pdf");
    }

    // ── PDF de estadísticas ───────────────────────────────────────────────────

    @Test
    void reporteEstadisticasGeneraPdfRealConAprobadosReprobadosYSinResultado() throws Exception {
        EvaluationFinal aprobado = EvaluationFinal.builder()
                .id(1L)
                .submission(submissionCompleta())
                .notaInstructor(9.0).notaPanelistPromedio(8.5).notaFinal(8.8)
                .resultado(ResultadoEvaluation.builder().codigo("APROBADO").nombre("Aprobado").build())
                .build();
        EvaluationFinal reprobado = EvaluationFinal.builder()
                .id(2L)
                .submission(Submission.builder().id(3L).build())
                .notaInstructor(4.0).notaPanelistPromedio(3.5).notaFinal(3.8)
                .resultado(ResultadoEvaluation.builder().codigo("REPROBADO").nombre("Reprobado").build())
                .build();
        // Sin resultado y sin notas: ejercita el fallback "—" de fmt() y la rama de color rojo
        EvaluationFinal sinDatos = EvaluationFinal.builder().id(3L).build();
        when(evaluationFinalRepo.findAllWithRelationships())
                .thenReturn(List.of(aprobado, reprobado, sinDatos));

        assertEsPdfDescargable(controller.reporteEstadisticas(), "estadisticas_evaluaciones.pdf");
        verify(evaluationFinalRepo).findAllWithRelationships();
    }

    @Test
    void reporteEstadisticasSinEvaluationsUsaPromedioCeroYNoFalla() throws Exception {
        when(evaluationFinalRepo.findAllWithRelationships()).thenReturn(List.of());

        assertEsPdfDescargable(controller.reporteEstadisticas(), "estadisticas_evaluaciones.pdf");
    }

    // ── Procedimiento almacenado sp_generate_reporte_defensas ──────────────────

    @Test
    void reporteDefensasDelegaEnElProcedimientoAlmacenado() {
        List<ReporteDefensaResult> esperado = List.of(new ReporteDefensaResult());
        when(submissionRepo.generateReporteDefensas("Software")).thenReturn(esperado);

        ResponseEntity<List<ReporteDefensaResult>> response = controller.reporteDefensas("Software");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(esperado, response.getBody());
        verify(submissionRepo).generateReporteDefensas("Software");
    }

    // ── Estadísticas JSON ─────────────────────────────────────────────────────

    @Test
    void estadisticasJsonCalculaTotalesPromedioYTasaDeAprobacion() {
        EvaluationFinal aprobado1 = EvaluationFinal.builder().notaFinal(8.0)
                .resultado(ResultadoEvaluation.builder().codigo("APROBADO").build()).build();
        EvaluationFinal aprobado2 = EvaluationFinal.builder().notaFinal(9.0)
                .resultado(ResultadoEvaluation.builder().codigo("APROBADO").build()).build();
        EvaluationFinal reprobado = EvaluationFinal.builder().notaFinal(4.0)
                .resultado(ResultadoEvaluation.builder().codigo("REPROBADO").build()).build();
        // notaFinal null y resultado null: no debe count en el promedio ni en los counts
        EvaluationFinal incompleto = EvaluationFinal.builder().build();
        when(evaluationFinalRepo.findAllWithRelationships())
                .thenReturn(List.of(aprobado1, aprobado2, reprobado, incompleto));
        when(submissionRepo.countByEstadoCodigo("APROBADA")).thenReturn(5L);

        ResponseEntity<Map<String, Object>> response = controller.estadisticasJson();

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
    void estadisticasJsonSinEvaluationsDevuelveTasaCeroSinDividirPorCero() {
        when(evaluationFinalRepo.findAllWithRelationships()).thenReturn(List.of());
        when(submissionRepo.countByEstadoCodigo("APROBADA")).thenReturn(0L);

        Map<String, Object> body = controller.estadisticasJson().getBody();

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
    void resumenDelegaEnElServicioConFiltrosDeFechaYProgram() {
        LocalDate desde = LocalDate.of(2026, 1, 1);
        LocalDate hasta = LocalDate.of(2026, 12, 31);
        ReporteResumenDTO esperado = ReporteResumenDTO.builder().build();
        when(reporteService.resumen(desde, hasta, "Software")).thenReturn(esperado);

        ResponseEntity<?> response = controller.resumen(desde, hasta, "Software");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(esperado, response.getBody());
    }

    @Test
    void resumenSinFiltrosPasaNullsAlServicio() {
        when(reporteService.resumen(null, null, null)).thenReturn(ReporteResumenDTO.builder().build());

        assertEquals(HttpStatus.OK, controller.resumen(null, null, null).getStatusCode());
        verify(reporteService).resumen(null, null, null);
    }

    @Test
    void submissionsPorEstadoDelegaEnElServicio() {
        List<ReporteCountDTO> esperado = List.of(new ReporteCountDTO());
        when(reporteService.submissionsPorEstado(null, null, null)).thenReturn(esperado);

        ResponseEntity<?> response = controller.submissionsPorEstado(null, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(esperado, response.getBody());
    }

    @Test
    void sustentacionesPorPeriodDelegaEnElServicio() {
        List<ReporteCountDTO> esperado = List.of(new ReporteCountDTO());
        when(reporteService.sustentacionesPorPeriod(null, null)).thenReturn(esperado);

        ResponseEntity<?> response = controller.sustentacionesPorPeriod(null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(esperado, response.getBody());
    }

    @Test
    void resumenMinutesDelegaEnElServicio() {
        Map<String, Long> esperado = Map.of("generadas", 3L);
        when(reporteService.resumenMinutes(null, null)).thenReturn(esperado);

        ResponseEntity<?> response = controller.resumenMinutes(null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(esperado, response.getBody());
    }

    @Test
    void actividadTeacherDelegaEnElServicio() {
        when(reporteService.actividadPorTeacher()).thenReturn(List.of());

        assertEquals(HttpStatus.OK, controller.actividadTeacher().getStatusCode());
        verify(reporteService).actividadPorTeacher();
    }

    @Test
    void porProgramDelegaEnElServicio() {
        List<Map<String, Object>> esperado = List.of(Map.of("carrera", "Software"));
        when(reporteService.estadisticasPorProgram()).thenReturn(esperado);

        ResponseEntity<?> response = controller.porProgram();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(esperado, response.getBody());
    }
}
