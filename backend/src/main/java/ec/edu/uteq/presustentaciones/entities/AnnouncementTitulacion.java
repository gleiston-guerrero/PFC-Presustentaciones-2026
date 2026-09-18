package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "convocatorias_titulacion", schema = "presus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnnouncementTitulacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    @JsonProperty("id")
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "periodo_academico_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonProperty("periodoAcademico")
    private PeriodAcademico periodAcademico;

    @Column(name = "codigo", nullable = false, unique = true, length = 30)
    @JsonProperty("codigo")
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 150)
    @JsonProperty("nombre")
    private String nombre;

    @Column(name = "fecha_inicio", nullable = false)
    @JsonProperty("fechaInicio")
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    @JsonProperty("fechaFin")
    private LocalDate fechaFin;

    @Column(name = "activa", nullable = false)
    @Builder.Default
    @JsonProperty("activa")
    private Boolean activa= true;
}
