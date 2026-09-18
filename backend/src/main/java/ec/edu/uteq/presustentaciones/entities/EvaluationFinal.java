package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "evaluaciones_finales", schema = "presus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluationFinal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    @JsonProperty("id")
    private Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "solicitud_id", nullable = false, unique = true)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "creadoPor", "actualizadoPor"})
    @JsonProperty("solicitud")
    private Submission submission;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "rubrica_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonProperty("rubrica")
    private Rubric rubric;

    @Column(name = "nota_instructor")
    @JsonProperty("notaInstructor")
    private Double notaInstructor;

    @Column(name = "nota_jurado_promedio")
    @JsonProperty("notaPanelistPromedio")
    private Double notaPanelistPromedio;

    @Column(name = "nota_final")
    @JsonProperty("notaFinal")
    private Double notaFinal;

    @Column(name = "peso_instructor", nullable = false)
    @Builder.Default
    @JsonProperty("pesoInstructor")
    private Double pesoInstructor= 0.4;

    @Column(name = "peso_jurado", nullable = false)
    @Builder.Default
    @JsonProperty("pesoPanelist")
    private Double pesoPanelist= 0.6;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "resultado_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private ResultadoEvaluation resultado;

    @Column(name = "comentario_preestablecido", columnDefinition = "TEXT")
    @JsonProperty("comentarioPreestablecido")
    private String comentarioPreestablecido;

    @Column(name = "observaciones", columnDefinition = "TEXT")
    @JsonProperty("observaciones")
    private String observaciones;

    @Column(name = "fecha_calculo", nullable = false)
    @Builder.Default
    @JsonProperty("fechaCalculo")
    private LocalDateTime fechaCalculo= LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (fechaCalculo == null) {
            fechaCalculo = LocalDateTime.now();
        }
    }

    /**
     * Calculate nota final.
     */
    public void calculateNotaFinal() {
        if (notaInstructor != null && notaPanelistPromedio != null) {
            this.notaFinal = (notaInstructor * pesoInstructor)
                           + (notaPanelistPromedio * pesoPanelist);
            // Scale de calificación
            this.notaFinal = Math.round(this.notaFinal * 100.0) / 100.0;
        }
    }
}
