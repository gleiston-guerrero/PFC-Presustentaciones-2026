package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "facultades", schema = "presus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Faculty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    @JsonProperty("id")
    private Integer id;

    @Column(name = "codigo", nullable = false, unique = true, length = 20)
    @JsonProperty("codigo")
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 150)
    @JsonProperty("nombre")
    private String nombre;
}
