package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data @Builder
public class TutoringPhaseDTO {

    @JsonProperty("id")
    private Long id;
    @JsonProperty("tutorId")
    private Long tutorId;
    @JsonProperty("numeroFase")
    private Integer numeroPhase;
    @JsonProperty("estado")
    private String status;
    @JsonProperty("fechaInicio")
    private LocalDateTime dateStart;
    @JsonProperty("fechaAprobacion")
    private LocalDateTime dateAprobacion;
    @JsonProperty("archivoPdfEstudiante")
    private String filePdfStudent;
    @JsonProperty("tamanoPdfBytes")
    private Long sizePdfBytes;
    @JsonProperty("mensajes")
    private List<TutoringMessageDTO> messages;
}
