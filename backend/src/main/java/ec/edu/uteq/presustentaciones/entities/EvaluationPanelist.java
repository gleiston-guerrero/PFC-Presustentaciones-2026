package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "evaluaciones_jurado", schema = "presus")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EvaluationPanelist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nota_jurado", nullable = false)
    private Double notaPanelist;

    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;

    @Column(name = "resultado", length = 20)
    private String resultado;

    @Column(name = "comentario_preestablecido", columnDefinition = "TEXT")
    private String comentarioPreestablecido;

    @Column(name = "fecha_registro", nullable = false, updatable = false)
    private LocalDateTime fechaRegistro;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "solicitud_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "jurados", "tutor",
                           "evaluacion", "acta", "anteproyecto", "cronograma", "notificaciones"})
    @JsonProperty("solicitud")
    private Submission submission;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "jurado_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "solicitud"})
    @JsonProperty("jurado")
    private Panelist panelist;

    @PrePersist
    protected void onCreate() {
        fechaRegistro = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        if (this.notaPanelist != null) {
            this.resultado = this.notaPanelist >= 7 ? "APROBADO" : "REPROBADO";
        }
    }
}
