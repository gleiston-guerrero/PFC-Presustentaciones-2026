package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class SaveFacultyRequest {
    @JsonProperty("codigo")
    private String code;
    @JsonProperty("nombre")
    private String nombre;
}
