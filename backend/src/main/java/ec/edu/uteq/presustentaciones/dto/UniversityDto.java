package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Objeto de transferencia de university.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UniversityDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Name.
     */
    @JsonProperty("name")
    private String name;
    /**
     * Cuenta los registros con ry.
     */
    @JsonProperty("country")
    private String country;
    
    /**
     * Domains.
     */
    @JsonProperty("domains")
    private List<String> domains;

    /**
     * Web pages.
     */
    @JsonProperty("web_pages")
    private List<String> webPages;
}
