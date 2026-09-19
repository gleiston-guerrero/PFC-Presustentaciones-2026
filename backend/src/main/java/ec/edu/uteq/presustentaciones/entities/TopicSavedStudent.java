package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Topic saved student.
 */
@Entity
@Table(name = "temas_guardados", schema = "presus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicSavedStudent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    @JsonProperty("id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estudiante_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonProperty("estudiante")
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tema_propuesto_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonProperty("temaPropuesto")
    private TopicProposed topicProposed;

    @Column(name = "fecha_guardado", nullable = false, updatable = false)
    @JsonProperty("fechaGuardado")
    private LocalDateTime dateSaved;

    /**
     * On create.
     */
    @PrePersist
    protected void onCreate() {
        if (this.dateSaved == null) {
            this.dateSaved = LocalDateTime.now();
        }
    }
}
