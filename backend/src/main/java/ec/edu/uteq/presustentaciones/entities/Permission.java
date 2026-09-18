package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "permisos", schema = "presus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Permission {

    @Id
    @JsonProperty("id")
    private Short id;

    @Column(name = "codigo", nullable = false, unique = true, length = 60)
    @JsonProperty("codigo")
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 150)
    @JsonProperty("nombre")
    private String nombre;

    @Column(name = "categoria", nullable = false, length = 60)
    @JsonProperty("categoria")
    private String categoria;

    @Column(name = "descripcion")
    @JsonProperty("descripcion")
    private String descripcion;
}
