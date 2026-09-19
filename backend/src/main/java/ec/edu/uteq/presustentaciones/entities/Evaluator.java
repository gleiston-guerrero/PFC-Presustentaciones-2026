package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Evaluator.
 */
@Entity
@Table(name = "evaluadores", schema = "presus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Evaluator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    @JsonProperty("id")
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "solicitud_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonProperty("solicitud")
    private Submission submission;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tipo_evaluador_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private KindEvaluator kindEvaluator;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "docente_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "jurados", "tutores"})
    @JsonProperty("docente")
    private Teacher teacher;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "miembro_tribunal_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "solicitud"})
    @JsonProperty("miembroTribunal")
    private Panelist memberPanel;

    @Column(name = "peso", nullable = false)
    @Builder.Default
    @JsonProperty("peso")
    private Double peso= 1.0;

    @Column(name = "fecha_asignacion", nullable = false, updatable = false)
    @Builder.Default
    @JsonProperty("fechaAsignacion")
    private LocalDateTime dateAsignacion= LocalDateTime.now();

    /**
     * On create.
     */
    @PrePersist
    protected void onCreate() {
        if (dateAsignacion == null) {
            dateAsignacion = LocalDateTime.now();
        }
    }
}
