package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notificaciones", schema = "presus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    @JsonProperty("id")
    private Long id;

    @Column(name = "mensaje", columnDefinition = "TEXT")
    @JsonProperty("mensaje")
    private String mensaje;

    @Column(name = "fecha")
    @JsonProperty("fecha")
    private LocalDateTime fecha;

    @Column(name = "leida", nullable = false)
    @Builder.Default
    @JsonProperty("leida")
    private boolean leida= false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    @JsonProperty("usuario")
    private AppUser appUser;
}