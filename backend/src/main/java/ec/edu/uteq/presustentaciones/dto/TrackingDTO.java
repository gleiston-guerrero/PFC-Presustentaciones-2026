package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackingDTO {
    @JsonProperty("solicitudId")
    private Long submissionId;
    private String tituloProyecto;
    private String estadoActual; // Estado general de la submission
    @JsonProperty("porcentajeProgreso")
    private int porcentajeProgress;
    private List<EtapaTrackingDTO> etapas;
}
