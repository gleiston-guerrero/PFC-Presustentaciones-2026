package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "historial_estados_solicitud", schema = "presus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoryStatusesSubmission {

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
    @JoinColumn(name = "estado_anterior_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private StatusSubmission statusAnterior;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "estado_nuevo_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private StatusSubmission statusNew;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password"})
    @JsonProperty("usuario")
    private AppUser appUser;

    @Column(name = "comentario", columnDefinition = "TEXT")
    @JsonProperty("comentario")
    private String comment;

    @Column(name = "fecha_cambio", nullable = false, updatable = false)
    @Builder.Default
    @JsonProperty("fechaCambio")
    private LocalDateTime dateCambio= LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (dateCambio == null) {
            dateCambio = LocalDateTime.now();
        }
    }
}
