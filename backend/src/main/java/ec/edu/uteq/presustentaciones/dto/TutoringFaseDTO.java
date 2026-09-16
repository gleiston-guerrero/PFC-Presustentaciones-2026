package ec.edu.uteq.presustentaciones.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data @Builder
public class TutoringFaseDTO {

    private Long id;
    private Long tutorId;
    private Integer numeroFase;
    private String estado;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaAprobacion;
    private String archivoPdfStudent;
    private Long tamanoPdfBytes;
    private List<TutoringMensajeDTO> mensajes;
}
