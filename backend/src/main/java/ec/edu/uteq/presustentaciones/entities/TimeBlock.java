package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;

@Entity
@Table(name = "bloques_horarios", schema = "presus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    @JsonProperty("id")
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "jornada_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonProperty("jornada")
    private Shift shift;

    @Column(name = "nombre", nullable = false, length = 60)
    @JsonProperty("nombre")
    private String nombre;

    @Column(name = "hora_inicio", nullable = false)
    @JsonProperty("horaInicio")
    private LocalTime horaStart;

    @Column(name = "hora_fin", nullable = false)
    @JsonProperty("horaFin")
    private LocalTime horaEnd;
}
