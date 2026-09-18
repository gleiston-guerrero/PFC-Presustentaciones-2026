package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data @Builder
public class TutoringFaseDTO {

    @JsonProperty("id")
    private Long id;
    @JsonProperty("tutorId")
    private Long tutorId;
    @JsonProperty("numeroFase")
    private Integer numeroFase;
    @JsonProperty("estado")
    private String estado;
    @JsonProperty("fechaInicio")
    private LocalDateTime fechaInicio;
    @JsonProperty("fechaAprobacion")
    private LocalDateTime fechaAprobacion;
    @JsonProperty("archivoPdfEstudiante")
    private String archivoPdfStudent;
    @JsonProperty("tamanoPdfBytes")
    private Long tamanoPdfBytes;
    @JsonProperty("mensajes")
    private List<TutoringMensajeDTO> mensajes;
}
