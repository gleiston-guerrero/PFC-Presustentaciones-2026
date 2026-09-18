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
    @JsonProperty("id")
    private Long id;
    @Column(nullable = false)
    @JsonProperty("nombre")
    private String nombre;

    @Column(nullable = false)
    @JsonProperty("apellido")
    private String apellido;

    @Column(unique = true, nullable = false)
    @JsonProperty("email")
    private String email;

    @Column(nullable = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @Column(name = "rol", nullable = false)
    @JsonProperty("rol")
    private String role; // ESTUDIANTE, DOCENTE, ADMIN

    @Column(nullable = false)
    @JsonProperty("activo")
    private Boolean activo= true;

    @Column
    @JsonProperty("telefono")
    private String telefono;

    /** Correo donde llegan las notifications del sistema (puede ser distinto al de login) */
    @Column(name = "email_notificaciones")
    @JsonProperty("emailNotificaciones")
    private String emailNotifications;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "rol_id", nullable = false)
    @JsonProperty("rolUsuario")
    private RoleAppUser roleAppUser;

    @Column(name = "creado_en", nullable = false, updatable = false)
    @JsonProperty("creadoEn")
    private java.time.LocalDateTime creadoEn;

    @PrePersist
    protected void onCreate() {
        creadoEn = java.time.LocalDateTime.now();
    }
}