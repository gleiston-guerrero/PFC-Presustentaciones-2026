package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "docente", schema = "presus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Teacher {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonProperty("usuario")
    private AppUser appUser;
    
    @Column(name = "area_especialidad", length = 180)
    private String areaEspecialidad;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "facultad_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonProperty("facultad")
    private Faculty faculty;
    
    @Column(name = "carga_horaria_semanal", nullable = false)
    private Integer cargaHorariaSemanal = 0;
    
    @Column(name = "disponible", nullable = false)
    private Boolean disponible = true;
    
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;
    
    @PrePersist
    protected void onCreate() {
        creadoEn = LocalDateTime.now();
    }
}
