package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * History schedule.
 */
@Entity
@Table(name = "historial_cronograma", schema = "presus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistorySchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    @JsonProperty("id")
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cronograma_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonProperty("cronograma")
    private Schedule schedule;

    @Column(name = "fecha_anterior", nullable = false)
    @JsonProperty("fechaAnterior")
    private LocalDateTime dateAnterior;

    @Column(name = "fecha_nueva", nullable = false)
    @JsonProperty("fechaNueva")
    private LocalDateTime dateNueva;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sala_anterior_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonProperty("salaAnterior")
    private Room roomAnterior;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sala_nueva_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonProperty("salaNueva")
    private Room roomNueva;

    @Column(name = "motivo", columnDefinition = "TEXT")
    @JsonProperty("motivo")
    private String motivo;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password"})
    @JsonProperty("usuario")
    private AppUser appUser;

    @Column(name = "fecha_cambio", nullable = false, updatable = false)
    @Builder.Default
    @JsonProperty("fechaCambio")
    private LocalDateTime dateCambio= LocalDateTime.now();

    /**
     * On create.
     */
    @PrePersist
    protected void onCreate() {
        if (dateCambio == null) {
            dateCambio = LocalDateTime.now();
        }
    }
}
