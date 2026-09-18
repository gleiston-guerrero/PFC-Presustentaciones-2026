package ec.edu.uteq.presustentaciones.controllers;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import ec.edu.uteq.presustentaciones.entities.Schedule;
import ec.edu.uteq.presustentaciones.entities.EvaluationFinal;
import ec.edu.uteq.presustentaciones.repositories.ScheduleRepository;
import ec.edu.uteq.presustentaciones.repositories.EvaluationFinalRepository;
import ec.edu.uteq.presustentaciones.repositories.SubmissionRepository;
import ec.edu.uteq.presustentaciones.services.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/reportes")
@RequiredArgsConstructor
@PreAuthorize("@permissionService.hasPermission(authentication, 'REPORTES_VER')")
public class ReportController {

    private final ScheduleRepository scheduleRepo;
    private final EvaluationFinalRepository evaluationFinalRepo;
    private final SubmissionRepository submissionRepo;
    private final ReportService reportService;

    // ── Colores — siempre new DeviceRgb para evitar conflicto con Color.WHITE ──
    private static DeviceRgb BLUE()       { return new DeviceRgb(0,   56,  101); }
    private static DeviceRgb GOLD()       { return new DeviceRgb(204, 153, 0);   }
    private static DeviceRgb WHITE()      { return new DeviceRgb(255, 255, 255); }
    private static DeviceRgb LIGHT_BG()   { return new DeviceRgb(240, 243, 248); }
    private static DeviceRgb GRAY_TEXT()  { return new DeviceRgb(120, 120, 120); }
    private static DeviceRgb DARK_TEXT()  { return new DeviceRgb(80,  80,  80);  }
    private static DeviceRgb GREEN()      { return new DeviceRgb(21,  128, 61);  }
    private static DeviceRgb RED()        { return new DeviceRgb(185, 28,  28);  }

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /**
     * RF-11: Genera el PDF con el schedule completo de pre-sustentaciones.
     *
     * @return 200 con el PDF como adjunto descargable
     * @throws Exception si iText falla al build el documento o las fuentes
     */
    @GetMapping("/cronograma/pdf")
    public ResponseEntity<byte[]> reportSchedule() throws Exception {
        List<Schedule> lista = scheduleRepo.findReportSchedule();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = openDoc(baos);
        PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        encabezado(doc, bold, regular,
                "Cronograma de Pre-Sustentaciones",
                "Trabajo de Integración Curricular — Décimo Semestre");

        Table table = new Table(UnitValue.createPercentArray(new float[]{4, 18, 16, 12, 8}))
                .useAllAvailableWidth();
        for (String h : new String[]{"#", "Estudiante / Tema", "Fecha y Hora", "Sala", "Estado"}) {
            table.addHeaderCell(new Cell()
                    .add(new Paragraph(h).setFont(bold).setFontSize(9).setFontColor(WHITE()))
                    .setBackgroundColor(BLUE()).setTextAlignment(TextAlignment.CENTER));
        }

        int i = 1;
        for (Schedule c : lista) {
            DeviceRgb bg = (i % 2 == 0) ? LIGHT_BG() : WHITE();
            String est = "—", topic = "—";
            if (c.getSubmission() != null) {
                var u = c.getSubmission().getStudent() != null
                        ? c.getSubmission().getStudent().getAppUser() : null;
                if (u != null) est = u.getNombre() + " " + u.getApellido();
                if (c.getSubmission().getTituloTopic() != null) topic = c.getSubmission().getTituloTopic();
            }
            table.addCell(cell(String.valueOf(i++), regular, bg, TextAlignment.CENTER));
            table.addCell(new Cell()
                    .add(new Paragraph(est).setFont(bold).setFontSize(8))
                    .add(new Paragraph(topic).setFont(regular).setFontSize(7).setFontColor(DARK_TEXT()))
                    .setBackgroundColor(bg));
            table.addCell(cell(c.getDateStart().format(FMT), regular, bg, TextAlignment.CENTER));
            table.addCell(cell(c.getRoom() != null ? c.getRoom().getNombre() : "—", regular, bg, TextAlignment.CENTER));
            table.addCell(cell(c.getStatus() != null ? c.getStatus().getNombre() : "—", regular, bg, TextAlignment.CENTER));
        }
        doc.add(table);
        doc.add(new Paragraph("Total: " + lista.size() + " pre-sustentación(es) programadas.")
                 .setFont(bold).setFontSize(9).setMarginTop(10));
        doc.close();

        return pdfResponse(baos, "cronograma_presustentaciones.pdf");
    }

    /**
     * RF-11: Genera el PDF de estadísticas de evaluations (totales, aprobados, reprobados,
     * nota promedio y detalle por student).
     *
     * @return 200 con el PDF como adjunto descargable
     * @throws Exception si iText falla al build el documento o las fuentes
     */
    @GetMapping("/estadisticas/pdf")
    public ResponseEntity<byte[]> reportStats() throws Exception {
        List<EvaluationFinal> evals = evaluationFinalRepo.findAllWithRelationships();

        long total      = evals.size();
        long aprobados  = evals.stream().filter(e -> e.getResult() != null && "APROBADO".equals(e.getResult().getCode())).count();
        long reprobados = evals.stream().filter(e -> e.getResult() != null && "REPROBADO".equals(e.getResult().getCode())).count();
        double average = evals.stream()
                .mapToDouble(e -> e.getGradeFinal() != null ? e.getGradeFinal() : 0)
                .filter(n -> n > 0).average().orElse(0);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = openDoc(baos);
        PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        encabezado(doc, bold, regular,
                "Estadísticas de Evaluaciones",
                "Pre-Sustentaciones TIC II — Carrera Software");

        // Resumen en 4 celdas
        Table summary = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1, 1})).useAllAvailableWidth();
        cellStat(summary, "Total evaluados", String.valueOf(total),      bold, regular, BLUE());
        cellStat(summary, "Aprobados",       String.valueOf(aprobados),  bold, regular, GREEN());
        cellStat(summary, "Reprobados",      String.valueOf(reprobados), bold, regular, RED());
        cellStat(summary, "Nota promedio",   String.format("%.2f", average), bold, regular, GOLD());
        doc.add(summary);
        doc.add(new Paragraph(" ").setMarginBottom(16));

        // Tabla detalle
        Table tabla = new Table(UnitValue.createPercentArray(new float[]{3, 14, 5, 5, 5, 5})).useAllAvailableWidth();
        for (String h : new String[]{"#", "Estudiante / Tema", "Nota Inst.", "Nota Trib.", "Nota Final", "Resultado"}) {
            tabla.addHeaderCell(new Cell()
                    .add(new Paragraph(h).setFont(bold).setFontSize(8).setFontColor(WHITE()))
                    .setBackgroundColor(BLUE()).setTextAlignment(TextAlignment.CENTER));
        }

        int idx = 1;
        for (EvaluationFinal e : evals) {
            DeviceRgb bg = (idx % 2 == 0) ? LIGHT_BG() : WHITE();
            String est = "—", topic = "—";
            if (e.getSubmission() != null) {
                var u = e.getSubmission().getStudent() != null
                        ? e.getSubmission().getStudent().getAppUser() : null;
                if (u != null) est = u.getNombre() + " " + u.getApellido();
                if (e.getSubmission().getTituloTopic() != null) topic = e.getSubmission().getTituloTopic();
            }
            tabla.addCell(cell(String.valueOf(idx++), regular, bg, TextAlignment.CENTER));
            tabla.addCell(new Cell()
                    .add(new Paragraph(est).setFont(bold).setFontSize(8))
                    .add(new Paragraph(topic).setFont(regular).setFontSize(7).setFontColor(DARK_TEXT()))
                    .setBackgroundColor(bg));
            tabla.addCell(cell(fmt(e.getGradeInstructor()), regular, bg, TextAlignment.CENTER));
            tabla.addCell(cell(fmt(e.getGradePanelistAverage()), regular, bg, TextAlignment.CENTER));
            tabla.addCell(cell(fmt(e.getGradeFinal()),      bold,    bg, TextAlignment.CENTER));
            
            String resCod = e.getResult() != null ? e.getResult().getCode() : "";
            DeviceRgb rc = "APROBADO".equals(resCod) ? GREEN() : RED();
            tabla.addCell(new Cell()
                    .add(new Paragraph(e.getResult() != null ? e.getResult().getNombre() : "—")
                            .setFont(bold).setFontSize(8).setFontColor(rc))
                    .setBackgroundColor(bg).setTextAlignment(TextAlignment.CENTER));
        }
        doc.add(tabla);
        doc.close();

        return pdfResponse(baos, "estadisticas_evaluaciones.pdf");
    }

    /**
     * Reporte consolidado de defensas por program vía sp_generate_reporte_defensas
     * (Fase 3 / Criterio P1, categoría "consultas multi-tabla"). @Transactional es
     * necesario aquí: el procedimiento devuelve un REFCURSOR y Postgres solo lo mantiene
     * abierto dentro de la misma transacción que lo abrió -- sin esto, Hibernate hace el
     * fetch del cursor en una transacción/conexión ya cerrada ("cursor ... does not exist").
     *
     * @param program nombre o parte del nombre de la program por la que se filtra
     * @return 200 con las filas del reporte que devuelve el procedimiento
     */
    @GetMapping("/defensas")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<List<ec.edu.uteq.presustentaciones.dto.ReportDefenseResult>> reportDefenses(
            @RequestParam("program") String program) {
        return ResponseEntity.ok(submissionRepo.generateReportDefenses(program));
    }

    /**
     * RF-11: Mismas estadísticas que el PDF pero en JSON, para las gráficas del dashboard.
     *
     * @return 200 con totales, aprobados, reprobados, nota promedio, tasa de aprobación y
     *         submissions pendientes; la tasa es 0 cuando todavía no hay evaluations
     */
    @GetMapping("/estadisticas/json")
    public ResponseEntity<Map<String, Object>> statsJson() {
        List<EvaluationFinal> evals = evaluationFinalRepo.findAllWithRelationships();
        long total      = evals.size();
        long aprobados  = evals.stream().filter(e -> e.getResult() != null && "APROBADO".equals(e.getResult().getCode())).count();
        long reprobados = evals.stream().filter(e -> e.getResult() != null && "REPROBADO".equals(e.getResult().getCode())).count();
        double average = evals.stream()
                .mapToDouble(e -> e.getGradeFinal() != null ? e.getGradeFinal() : 0)
                .filter(n -> n > 0).average().orElse(0);
        long pending = submissionRepo.countByStatusCode("APROBADA");

        return ResponseEntity.ok(Map.of(
                "totalEvaluados",       total,
                "aprobados",            aprobados,
                "reprobados",           reprobados,
                "notaPromedio",         Math.round(average * 100.0) / 100.0,
                "tasaAprobacion",       total > 0 ? Math.round((double) aprobados / total * 100) : 0,
                "solicitudesPendientes", pending
        ));
    }

    // ── Módulo de reportes JSON (COORDINADOR / ADMINISTRADOR) ─────────────────
    // El @PreAuthorize('REPORTES_VER') de la clase protege también estos endpoints.
    // Todo se agrega con COUNT/GROUP BY en la base (ver ReporteServiceImpl).

    /**
     * Resumen general del process de pre-sustentaciones para el dashboard.
     *
     * @param from   inicio del rango de fechas, opcional
     * @param to   fin del rango de fechas, opcional
     * @param program filtro por program, opcional
     * @return 200 con el resumen agregado
     */
    @GetMapping("/resumen")
    public ResponseEntity<?> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "carrera", required = false) String program) {
        return ResponseEntity.ok(reportService.summary(from, to, program));
    }

    /**
     * Cantidad de submissions/pre-sustentaciones agrupadas por estado.
     *
     * @param from   inicio del rango de fechas, opcional
     * @param to   fin del rango de fechas, opcional
     * @param program filtro por program, opcional
     * @return 200 con el count por estado
     */
    @GetMapping("/solicitudes-por-estado")
    public ResponseEntity<?> submissionsByStatus(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "carrera", required = false) String program) {
        return ResponseEntity.ok(reportService.submissionsByStatus(from, to, program));
    }

    /**
     * Cantidad de pre-sustentaciones agrupadas por período académico.
     *
     * @param from inicio del rango de fechas, opcional
     * @param to fin del rango de fechas, opcional
     * @return 200 con el count por período
     */
    @GetMapping("/sustentaciones-por-periodo")
    public ResponseEntity<?> defensesByPeriod(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportService.defensesByPeriod(from, to));
    }

    /**
     * Estado de las minutes: generadas, revisadas, observadas, finalizadas, anuladas y
     * pendientes de firma.
     *
     * @param from inicio del rango de fechas, opcional
     * @param to fin del rango de fechas, opcional
     * @return 200 con el count por estado de minutes
     */
    @GetMapping("/actas")
    public ResponseEntity<?> summaryMinutes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportService.summaryMinutes(from, to));
    }

    /**
     * Actividad por teacher: participaciones como panelist, como tutor y minutes firmadas.
     *
     * @return 200 con una fila por teacher
     */
    @GetMapping("/actividad-docente")
    public ResponseEntity<?> activityTeacher() {
        return ResponseEntity.ok(reportService.activityByTeacher());
    }

    /**
     * Estadísticas por program/programa: total, completadas y rechazadas.
     *
     * @return 200 con una fila por program
     */
    @GetMapping("/por-carrera")
    public ResponseEntity<?> byProgram() {
        return ResponseEntity.ok(reportService.statsByProgram());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Document openDoc(ByteArrayOutputStream baos) throws Exception {
        PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
        return new Document(pdf);
    }

    private void encabezado(Document doc, PdfFont bold, PdfFont regular,
                            String titulo, String subtitulo) throws Exception {
        doc.add(new Paragraph("UNIVERSIDAD TÉCNICA ESTATAL DE QUEVEDO")
                .setFont(bold).setFontSize(13).setFontColor(BLUE())
                .setTextAlignment(TextAlignment.CENTER));
        doc.add(new Paragraph(titulo)
                .setFont(bold).setFontSize(11).setFontColor(GOLD())
                .setTextAlignment(TextAlignment.CENTER));
        doc.add(new Paragraph(subtitulo)
                .setFont(regular).setFontSize(9).setFontColor(GRAY_TEXT())
                .setTextAlignment(TextAlignment.CENTER));
        doc.add(new Paragraph("Generado: " + LocalDateTime.now().format(FMT))
                .setFont(regular).setFontSize(8).setFontColor(GRAY_TEXT())
                .setTextAlignment(TextAlignment.CENTER).setMarginBottom(16));
    }

    private Cell cell(String txt, PdfFont font, DeviceRgb bg, TextAlignment align) {
        return new Cell()
                .add(new Paragraph(txt).setFont(font).setFontSize(8))
                .setBackgroundColor(bg).setTextAlignment(align);
    }

    private void cellStat(Table t, String label, String val,
                           PdfFont bold, PdfFont regular, DeviceRgb color) {
        t.addCell(new Cell()
                .add(new Paragraph(val).setFont(bold).setFontSize(20)
                        .setFontColor(color).setTextAlignment(TextAlignment.CENTER))
                .add(new Paragraph(label).setFont(regular).setFontSize(8)
                        .setFontColor(DARK_TEXT()).setTextAlignment(TextAlignment.CENTER))
                .setPadding(10));
    }

    private ResponseEntity<byte[]> pdfResponse(ByteArrayOutputStream baos, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(baos.toByteArray());
    }

    private String fmt(Double v) { return v != null ? String.format("%.2f", v) : "—"; }
}
