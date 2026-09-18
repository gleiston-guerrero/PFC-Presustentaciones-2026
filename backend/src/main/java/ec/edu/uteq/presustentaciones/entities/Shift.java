package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "jornadas", schema = "presus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shift {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    @JsonProperty("id")
    private Short id;

    @Column(name = "codigo", nullable = false, unique = true, length = 20)
    @JsonProperty("codigo")
    private String code;

    @Column(name = "nombre", nullable = false, length = 60)
    @JsonProperty("nombre")
    private String nombre;
}
