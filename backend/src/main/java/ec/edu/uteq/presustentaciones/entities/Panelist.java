package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * sp_validate_conflicto_panelist (PROCEDURE con parámetro INOUT, categoría "validaciones
 * cruzadas" del Block A.2) -- ver la nota completa en Student.java sobre por qué es
 * PROCEDURE+INOUT y no FUNCTION+OUT (Postgres rechaza CALL para funciones). Fase 3 / P1.
 */
@NamedStoredProcedureQuery(
        name = "Jurado.validarConflictoJurado",
        procedureName = "presus.sp_validar_conflicto_jurado",
        parameters = {
                @StoredProcedureParameter(mode = ParameterMode.IN, name = "p_solicitud_id", type = Long.class),
                @StoredProcedureParameter(mode = ParameterMode.IN, name = "p_docente_id", type = Long.class),
                @StoredProcedureParameter(mode = ParameterMode.IN, name = "p_fecha_inicio", type = LocalDateTime.class),
                @StoredProcedureParameter(mode = ParameterMode.IN, name = "p_duracion_min", type = Integer.class),
                @StoredProcedureParameter(mode = ParameterMode.INOUT, name = "p_disponible", type = Boolean.class)
        }
)
@Entity
@Table(name = "miembros_tribunal", schema = "presus",
       uniqueConstraints = @UniqueConstraint(columnNames = {"solicitud_id", "docente_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Panelist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "docente_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "jurados", "tutores"})
    @JsonProperty("docente")
    private Teacher teacher;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "solicitud_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "jurados", "tutor", "evaluacion", "acta", "anteproyecto", "cronograma", "notificaciones"})
    @JsonProperty("solicitud")
    private Submission submission;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "rol_jurado_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonProperty("rolPanelist")
    private RolePanelist rolePanelist;

    @Column(name = "confirmado", nullable = false)
    @Builder.Default
    private boolean confirmado = false;

    @Column(name = "asignado_en", nullable = false, updatable = false)
    private LocalDateTime asignadoEn;

    @PrePersist
    protected void onCreate() {
        asignadoEn = LocalDateTime.now();
    }

    public String getRole() {
        return rolePanelist != null ? rolePanelist.getCodigo() : null;
    }
}
