package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "carreras", schema = "presus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Program {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    @JsonProperty("id")
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "facultad_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonProperty("facultad")
    private Faculty faculty;

    @Column(name = "codigo", nullable = false, unique = true, length = 20)
    @JsonProperty("codigo")
    private String code;

    @Column(name = "nombre", nullable = false, length = 150)
    @JsonProperty("nombre")
    private String nombre;

    @Column(name = "modalidad_estudio", length = 40)
    @JsonProperty("modalidadEstudio")
    private String modalityEstudio;
}
