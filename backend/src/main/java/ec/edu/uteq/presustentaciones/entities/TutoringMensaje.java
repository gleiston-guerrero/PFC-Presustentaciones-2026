package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tutoria_mensajes", schema = "presus")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TutoringMensaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fase_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "mensajes"})
    private TutoringFase fase;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "remitente_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password"})
    private AppUser remitente;

    @Column(name = "contenido", columnDefinition = "TEXT", nullable = false)
    private String contenido;

    @Column(name = "fecha_envio", nullable = false, updatable = false)
    private LocalDateTime fechaEnvio;

    /** Tipos: OBSERVACION | RESPUESTA | APROBACION */
    @Column(name = "tipo", nullable = false, length = 20)
    private String tipo;

    @Column(name = "leido", nullable = false)
    @Builder.Default
    private Boolean leido = false;

    /**
     * Columna "tipo_mensaje_id" (FK NOT NULL a tipos_mensaje) heredada del esquema
     * real, sincronizada a partir de "tipo" (mismo patrón aplicado en Submission,
     * Proposal, Tutor, TutoringFase y Schedule para el mismo problema).
     */
    @Column(name = "tipo_mensaje_id", nullable = false)
    private Short tipoMensajeId;

    @PrePersist
    @PreUpdate
    protected void onCreate() {
        if (fechaEnvio == null) {
            fechaEnvio = LocalDateTime.now();
        }
        tipoMensajeId = switch (tipo) {
            case "RESPUESTA" -> (short) 2;   // ARCHIVO (respuesta trae el PDF corregido)
            case "APROBACION" -> (short) 3;  // SISTEMA
            default -> (short) 1;            // TEXTO (OBSERVACION)
        };
    }
}