package ec.edu.uteq.presustentaciones.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data @Builder
public class TutoringMensajeDTO {

    private Long id;
    private Long faseId;
    private Long remitenteId;
    private String nombreRemitente;
    private String contenido;
    private LocalDateTime fechaEnvio;
    private String tipo;
    private Boolean leido;
}
