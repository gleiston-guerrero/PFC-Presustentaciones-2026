package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import ec.edu.uteq.presustentaciones.entities.Minutes;
import ec.edu.uteq.presustentaciones.entities.Panelist;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Detalle de un minutes para las vistas de teacher/coordinador/administrador. Incluye el
 * estado actual, las firmas y el tribunal, sin arrastrar el grafo completo de la
 * submission (evita ciclos y sobre-serialización).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MinutesDetalleDTO {

    @JsonProperty("id")
    private Long id;
    @JsonProperty("solicitudId")
    private Long submissionId;
    @JsonProperty("estudianteNombre")
    private String studentNombre;
    @JsonProperty("carrera")
    private String program;
    @JsonProperty("tituloTema")
    private String tituloTopic;
    @JsonProperty("estado")
    private String estado;
    @JsonProperty("estadoNombre")
    private String estadoNombre;
    @JsonProperty("fechaGeneracion")
    private LocalDate fechaGeneracion;
    @JsonProperty("observacionesActa")
    private String observacionesMinutes;
    @JsonProperty("archivoPdf")
    private String archivoPdf;

    @JsonProperty("firmada")
    private boolean firmada;
    @JsonProperty("firmadaPresidente")
    private boolean firmadaPresidente;
    @JsonProperty("firmadaVocal1")
    private boolean firmadaVocal1;
    @JsonProperty("firmadaVocal2")
    private boolean firmadaVocal2;
    @JsonProperty("firmadaTutor")
    private boolean firmadaTutor;
    @JsonProperty("fechaFirmaPresidente")
    private LocalDateTime fechaFirmaPresidente;
    @JsonProperty("fechaFirmaVocal1")
    private LocalDateTime fechaFirmaVocal1;
    @JsonProperty("fechaFirmaVocal2")
    private LocalDateTime fechaFirmaVocal2;
    @JsonProperty("fechaFirmaTutor")
    private LocalDateTime fechaFirmaTutor;
    @JsonProperty("firmantesPendientes")
    private String firmantesPendientes;

    @JsonProperty("tribunal")
    private List<MemberTribunalDTO> tribunal;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberTribunalDTO {
        @JsonProperty("docente")
        private String teacher;
        @JsonProperty("rol")
        private String role;
        @JsonProperty("confirmado")
        private boolean confirmado;
    }

    /**
     * De.
     * @param a a
     * @param panelists panelists
     * @return el MinutesDetalleDTO correspondiente
     */
    public static MinutesDetalleDTO de(Minutes a, List<Panelist> panelists) {
        var sol = a.getSubmission();
        var est = sol != null ? sol.getStudent() : null;
        var usr = est != null ? est.getAppUser() : null;
        List<MemberTribunalDTO> tribunal = panelists == null ? List.of() : panelists.stream()
                .map(j -> MemberTribunalDTO.builder()
                        .teacher(j.getTeacher() != null && j.getTeacher().getAppUser() != null
                                ? j.getTeacher().getAppUser().getNombre() + " " + j.getTeacher().getAppUser().getApellido()
                                : "—")
                        .role(j.getRole())
                        .confirmado(j.isConfirmado())
                        .build())
                .toList();
        return MinutesDetalleDTO.builder()
                .id(a.getId())
                .submissionId(sol != null ? sol.getId() : null)
                .studentNombre(usr != null ? (usr.getNombre() + " " + usr.getApellido()) : "—")
                .program(est != null ? est.getProgram() : "—")
                .tituloTopic(sol != null ? sol.getTituloTopic() : "—")
                .estado(a.getEstado() != null ? a.getEstado().getCodigo() : null)
                .estadoNombre(a.getEstado() != null ? a.getEstado().getNombre() : null)
                .fechaGeneracion(a.getFechaGeneracion())
                .observacionesMinutes(a.getObservacionesMinutes())
                .archivoPdf(a.getArchivoPdf())
                .firmada(a.isFirmada())
                .firmadaPresidente(a.isFirmadaPresidente())
                .firmadaVocal1(a.isFirmadaVocal1())
                .firmadaVocal2(a.isFirmadaVocal2())
                .firmadaTutor(a.isFirmadaTutor())
                .fechaFirmaPresidente(a.getFechaFirmaPresidente())
                .fechaFirmaVocal1(a.getFechaFirmaVocal1())
                .fechaFirmaVocal2(a.getFechaFirmaVocal2())
                .fechaFirmaTutor(a.getFechaFirmaTutor())
                .firmantesPendientes(a.getFirmantesPendientes())
                .tribunal(tribunal)
                .build();
    }
}
