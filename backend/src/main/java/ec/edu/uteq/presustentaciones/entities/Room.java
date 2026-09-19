package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.*;
import lombok.*;

/**
 * Room.
 */
@Entity
@Table(name = "sala", schema = "presus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    @JsonProperty("id")
    private Long id;
    
    @Column(name = "codigo", unique = true, nullable = false, length = 40)
    @JsonProperty("codigo")
    private String code;
    
    @Column(name = "nombre", nullable = false, length = 120)
    @JsonProperty("nombre")
    private String nombre;
    
    @Column(name = "capacidad", nullable = false)
    @JsonProperty("capacidad")
    private Integer capacidad;
    
    @Column(name = "disponible", nullable = false)
    @JsonProperty("disponible")
    private Boolean available= true;
}
