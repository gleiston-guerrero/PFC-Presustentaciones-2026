package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "anteproyectos", schema = "presus")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Proposal {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    @JsonProperty("id")
    private Long id;

    @Column(name = "archivo_pdf")
    @JsonProperty("archivoPdf")
    private String filePdf;

    @Column(name = "fecha_envio")
    @JsonProperty("fechaEnvio")
    private LocalDate dateEnvio;

    /** Estados: ENVIADO, APROBADO, RECHAZADO */
    @Column(name = "estado", length = 30)
    @JsonProperty("estado")
    private String status;

    /**
     * Columna "estado_id" (FK NOT NULL a estados_process) heredada del esquema real
     * de la base de datos, en paralelo al campo "estado" (texto) de arriba, que es
     * el que usa toda la lógica de la aplicación. Nada la poblaba, causando un error
     * al upload un proposal ("null value in column estado_id"). Se sincroniza
     * automáticamente a partir de "estado" en @PrePersist/@PreUpdate, mapeando al
     * catálogo estados_process (PENDIENTE=1, EN_PROCESO=2, APROBADO=3, OBSERVADO=4,
     * RECHAZADO=5), sin necesidad de tocar los puntos del servicio que la usan.
     */
    @Column(name = "estado_id", nullable = false)
    @JsonProperty("estadoProcessId")
    private Short statusProcessId;

    @Column(name = "observaciones", columnDefinition = "TEXT")
    @JsonProperty("observaciones")
    private String observations;

    /** RF-02: Hash SHA-256 del archivo para verificación de integridad */
    @Column(name = "sha256_hash", length = 64)
    @JsonProperty("sha256Hash")
    private String sha256Hash;

    @Column(name = "tamano_bytes")
    @JsonProperty("tamanoBytes")
    private Long sizeBytes;

    @OneToOne
    @JoinColumn(name = "solicitud_id")
    @JsonProperty("solicitud")
    private Submission submission;

    @PrePersist
    @PreUpdate
    private void synchronizeStatusProcess() {
        if (status == null) {
            statusProcessId = 1; // PENDIENTE
            return;
        }
        statusProcessId = switch (status) {
            case "ENVIADO" -> (short) 2;   // EN_PROCESO
            case "APROBADO" -> (short) 3;
            case "OBSERVADO" -> (short) 4;
            case "RECHAZADO" -> (short) 5;
            default -> (short) 1;          // PENDIENTE
        };
    }
}
