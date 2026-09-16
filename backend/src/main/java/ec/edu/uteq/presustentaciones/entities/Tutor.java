package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tutores", schema = "presus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tutor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "docente_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "jurados", "tutores"})
    @JsonProperty("docente")
    private Teacher teacher;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "solicitud_id", nullable = false, unique = true)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "jurados", "tutor", "evaluacion", "acta", "anteproyecto", "cronograma", "notificaciones"})
    @JsonProperty("solicitud")
    private Submission submission;

    @Column(name = "fecha_asignacion", nullable = false, updatable = false)
    private LocalDateTime fechaAsignacion;

    /** Estado de la tutoría: ACTIVO, COMPLETADA, FINALIZADO, REEMPLAZADO */
    @Column(name = "estado", nullable = false, length = 20)
    @Builder.Default
    private String estado = "ACTIVO";

    /**
     * Columna "estado_id" (FK NOT NULL a estados_process) heredada del esquema real,
     * en paralelo a "estado" (texto), que es el que usa la lógica de la aplicación.
     * Se sincroniza automáticamente a partir de "estado" (mismo patrón aplicado en
     * Submission.java y Proposal.java para el mismo problema).
     */
    @Column(name = "estado_id", nullable = false)
    private Short estadoProcessId;

    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;

    @PrePersist
    protected void onCreate() {
        fechaAsignacion = LocalDateTime.now();
        synchronizeEstadoProcess();
    }

    @PreUpdate
    protected void onUpdate() {
        synchronizeEstadoProcess();
    }

    private void synchronizeEstadoProcess() {
        estadoProcessId = switch (estado) {
            case "ACTIVO" -> (short) 2;                         // EN_PROCESO
            case "COMPLETADA", "FINALIZADO" -> (short) 3;       // APROBADO
            case "REEMPLAZADO" -> (short) 5;                    // RECHAZADO
            default -> (short) 1;                               // PENDIENTE
        };
    }
}
