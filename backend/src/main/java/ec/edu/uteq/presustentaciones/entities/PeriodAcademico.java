package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "periodos_academicos", schema = "presus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PeriodAcademico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    @JsonProperty("id")
    private Integer id;

    @Column(name = "codigo", nullable = false, unique = true, length = 20)
    @JsonProperty("codigo")
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 100)
    @JsonProperty("nombre")
    private String nombre;

    @Column(name = "fecha_inicio", nullable = false)
    @JsonProperty("fechaInicio")
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    @JsonProperty("fechaFin")
    private LocalDate fechaFin;

    @Column(name = "activo", nullable = false)
    @Builder.Default
    @JsonProperty("activo")
    private Boolean activo= true;
}
