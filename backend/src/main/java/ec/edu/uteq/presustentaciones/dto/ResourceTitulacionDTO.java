package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceTitulacionDTO {
    @JsonProperty("id")
    private Integer id;
    @JsonProperty("titulo")
    private String titulo;
    @JsonProperty("categoria")
    private String categoria;
    @JsonProperty("urlArchivo")
    private String urlArchivo;
    @JsonProperty("carreraId")
    private Integer programId;
    @JsonProperty("carreraNombre")
    private String programNombre;
}
