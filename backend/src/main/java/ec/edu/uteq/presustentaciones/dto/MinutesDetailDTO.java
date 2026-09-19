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
public class MinutesDetailDTO {

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
    private String status;
    @JsonProperty("estadoNombre")
    private String statusNombre;
    @JsonProperty("fechaGeneracion")
    private LocalDate dateGeneracion;
    @JsonProperty("observacionesActa")
    private String observationsMinutes;
    @JsonProperty("archivoPdf")
    private String filePdf;

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
    private LocalDateTime dateSignaturePresidente;
    @JsonProperty("fechaFirmaVocal1")
    private LocalDateTime dateSignatureVocal1;
    @JsonProperty("fechaFirmaVocal2")
    private LocalDateTime dateSignatureVocal2;
    @JsonProperty("fechaFirmaTutor")
    private LocalDateTime dateSignatureTutor;
    @JsonProperty("firmantesPendientes")
    private String signersPending;

    @JsonProperty("tribunal")
    private List<MemberPanelDTO> panel;

    /**
     * Objeto de transferencia de member panel.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberPanelDTO {
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
    public static MinutesDetailDTO from(Minutes a, List<Panelist> panelists) {
        var sol = a.getSubmission();
        var est = sol != null ? sol.getStudent() : null;
        var usr = est != null ? est.getAppUser() : null;
        List<MemberPanelDTO> panel = panelists == null ? List.of() : panelists.stream()
                .map(j -> MemberPanelDTO.builder()
                        .teacher(j.getTeacher() != null && j.getTeacher().getAppUser() != null
                                ? j.getTeacher().getAppUser().getNombre() + " " + j.getTeacher().getAppUser().getApellido()
                                : "—")
                        .role(j.getRole())
                        .confirmado(j.isConfirmado())
                        .build())
                .toList();
        return MinutesDetailDTO.builder()
                .id(a.getId())
                .submissionId(sol != null ? sol.getId() : null)
                .studentNombre(usr != null ? (usr.getNombre() + " " + usr.getApellido()) : "—")
                .program(est != null ? est.getProgram() : "—")
                .tituloTopic(sol != null ? sol.getTituloTopic() : "—")
                .status(a.getStatus() != null ? a.getStatus().getCode() : null)
                .statusNombre(a.getStatus() != null ? a.getStatus().getNombre() : null)
                .dateGeneracion(a.getDateGeneracion())
                .observationsMinutes(a.getObservationsMinutes())
                .filePdf(a.getFilePdf())
                .firmada(a.isFirmada())
                .firmadaPresidente(a.isFirmadaPresidente())
                .firmadaVocal1(a.isFirmadaVocal1())
                .firmadaVocal2(a.isFirmadaVocal2())
                .firmadaTutor(a.isFirmadaTutor())
                .dateSignaturePresidente(a.getDateSignaturePresidente())
                .dateSignatureVocal1(a.getDateSignatureVocal1())
                .dateSignatureVocal2(a.getDateSignatureVocal2())
                .dateSignatureTutor(a.getDateSignatureTutor())
                .signersPending(a.getSignersPending())
                .panel(panel)
                .build();
    }
}
