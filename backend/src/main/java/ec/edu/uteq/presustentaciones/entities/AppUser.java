package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "usuarios", schema = "presus")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String apellido;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @Column(name = "rol", nullable = false)
    @JsonProperty("rol")
    private String role; // ESTUDIANTE, DOCENTE, ADMIN

    @Column(nullable = false)
    private Boolean activo = true;

    @Column
    private String telefono;

    /** Correo donde llegan las notifications del sistema (puede ser distinto al de login) */
    @Column(name = "email_notificaciones")
    private String emailNotifications;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "rol_id", nullable = false)
    @JsonProperty("rolUsuario")
    private RoleAppUser roleAppUser;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private java.time.LocalDateTime creadoEn;

    @PrePersist
    protected void onCreate() {
        creadoEn = java.time.LocalDateTime.now();
    }
}