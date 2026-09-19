package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Objeto de transferencia de tracking.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackingDTO {
    @JsonProperty("solicitudId")
    private Long submissionId;
    @JsonProperty("tituloProyecto")
    private String tituloProyecto;
    @JsonProperty("estadoActual")
    private String statusActual; // Estado general de la submission
    @JsonProperty("porcentajeProgreso")
    private int porcentajeProgress;
    @JsonProperty("etapas")
    private List<StageTrackingDTO> etapas;
}
