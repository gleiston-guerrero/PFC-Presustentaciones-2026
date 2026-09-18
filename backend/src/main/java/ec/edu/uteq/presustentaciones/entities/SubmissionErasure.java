package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * RNF-19: submission de supresión de datos personales a instancia del titular. Deliberadamente
 * NO guarda el dato suprimido (solo referencia el id del appUser, que sigue existiendo,
 * seudonimizado -- ver {@code AppUserServiceImpl#pseudonymize}).
 */
@Entity
@Table(name = "solicitud_supresion", schema = "presus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmissionErasure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonProperty("id")
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    @JsonProperty("usuarioId")
    private Long appUserId;

    @Column(name = "fecha_solicitud", nullable = false)
    @JsonProperty("fechaSubmission")
    private LocalDateTime dateSubmission;

    /** PENDIENTE | RESUELTA | RECHAZADA */
    @Column(name = "estado", nullable = false, length = 20)
    @JsonProperty("estado")
    private String status;

    /** SEUDONIMIZACION | RECHAZADA -- null mientras esté PENDIENTE. */
    @Column(name = "tipo_resolucion", length = 20)
    @JsonProperty("tipoResolucion")
    private String kindResolucion;

    @Column(name = "resuelto_por")
    @JsonProperty("resueltoPor")
    private Long resueltoBy;

    @Column(name = "fecha_resolucion")
    @JsonProperty("fechaResolucion")
    private LocalDateTime dateResolucion;

    @Column(name = "notas", columnDefinition = "TEXT")
    @JsonProperty("notas")
    private String notas;
}
