package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Trazabilidad persistente de las transiciones de estado de un minutes
 * (V19__history_minutes_y_reportes.sql). Análoga a {@link HistoryStatusesSubmission},
 * ampliada con {@code accion} y {@code roleAppUser} para el timeline del requerimiento.
 * La escribe {@code MinutesServiceImpl} en cada cambio; la auditoría genérica de V15
 * (trigger sobre presus.minutes) queda como backup a nivel de base.
 */
@Entity
@Table(name = "historial_estados_acta", schema = "presus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoryStatusMinutes {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    @JsonProperty("id")
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "acta_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonProperty("acta")
    private Minutes minutes;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "estado_anterior_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private StatusMinutes statusAnterior;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "estado_nuevo_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private StatusMinutes statusNew;

    /** Autor del cambio. {@code null} solo para los registros históricos sembrados por V19. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password"})
    @JsonProperty("usuario")
    private AppUser appUser;

    /** Role con el que actuó el appUser (ADMIN / COORDINADOR / DOCENTE), copiado al momento del cambio. */
    @Column(name = "rol_usuario", length = 30)
    @JsonProperty("rolUsuario")
    private String roleAppUser;

    /** CREAR, CAMBIO_ESTADO, FIRMA_COMPLETA, ... */
    @Column(name = "accion", nullable = false, length = 30)
    @JsonProperty("accion")
    private String accion;

    @Column(name = "comentario", columnDefinition = "TEXT")
    @JsonProperty("comentario")
    private String comment;

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
