package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tutoria_fases", schema = "presus")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TutoringFase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    @JsonProperty("id")
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tutor_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "tutoriaFases"})
    private Tutor tutor;

    /** Número de fase: 1, 2 o 3 */
    @Column(name = "numero_fase", nullable = false)
    @JsonProperty("numeroFase")
    private Integer numeroFase;

    /** Estados: PENDIENTE_ESTUDIANTE | PENDIENTE_TUTOR | APROBADA */
    @Column(name = "estado", nullable = false, length = 30)
    @Builder.Default
    @JsonProperty("estado")
    private String estado= "PENDIENTE_ESTUDIANTE";

    /**
     * Columna "estado_id" (FK NOT NULL a estados_process) heredada del esquema real,
     * sincronizada automáticamente a partir de "estado" (mismo patrón aplicado en
     * Submission.java, Proposal.java y Tutor.java para el mismo problema).
     */
    @Column(name = "estado_id", nullable = false)
    @JsonProperty("estadoProcessId")
    private Short estadoProcessId;

    @Column(name = "fecha_inicio", nullable = false, updatable = false)
    @JsonProperty("fechaInicio")
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_aprobacion")
    @JsonProperty("fechaAprobacion")
    private LocalDateTime fechaAprobacion;

    @Column(name = "archivo_pdf_estudiante")
    @JsonProperty("archivoPdfStudent")
    private String archivoPdfStudent;

    @Column(name = "sha256_pdf", length = 64)
    @JsonProperty("sha256Pdf")
    private String sha256Pdf;

    @Column(name = "tamano_pdf_bytes")
    @JsonProperty("tamanoPdfBytes")
    private Long tamanoPdfBytes;

    @PrePersist
    protected void onCreate() {
        fechaInicio = LocalDateTime.now();
        synchronizeEstadoProcess();
    }

    @PreUpdate
    protected void onUpdate() {
        synchronizeEstadoProcess();
    }

    private void synchronizeEstadoProcess() {
        estadoProcessId = switch (estado) {
            case "PENDIENTE_TUTOR" -> (short) 2;   // EN_PROCESO
            case "APROBADA" -> (short) 3;          // APROBADO
            default -> (short) 1;                  // PENDIENTE (PENDIENTE_ESTUDIANTE)
        };
    }
}