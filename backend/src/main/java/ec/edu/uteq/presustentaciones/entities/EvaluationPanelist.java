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
    @JsonProperty("id")
    private Long id;

    @Column(name = "nota_jurado", nullable = false)
    @JsonProperty("notaPanelist")
    private Double gradePanelist;

    @Column(name = "observaciones", columnDefinition = "TEXT")
    @JsonProperty("observaciones")
    private String observations;

    @Column(name = "resultado", length = 20)
    @JsonProperty("resultado")
    private String result;

    @Column(name = "comentario_preestablecido", columnDefinition = "TEXT")
    @JsonProperty("comentarioPreestablecido")
    private String commentPreestablecido;

    @Column(name = "fecha_registro", nullable = false, updatable = false)
    @JsonProperty("fechaRegistro")
    private LocalDateTime dateRecord;

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
        dateRecord = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        if (this.gradePanelist != null) {
            this.result = this.gradePanelist >= 7 ? "APROBADO" : "REPROBADO";
        }
    }
}
