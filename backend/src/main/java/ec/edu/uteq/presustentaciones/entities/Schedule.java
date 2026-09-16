package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "cronograma", schema = "presus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Schedule {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "solicitud_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "creadoPor", "actualizadoPor"})
    @JsonProperty("solicitud")
    private Submission submission;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "convocatoria_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonProperty("convocatoria")
    private AnnouncementTitulacion announcement;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sala_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonProperty("sala")
    private Room room;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "bloque_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonProperty("bloque")
    private BlockHorario block;

    @Column(name = "numero_intento", nullable = false)
    @Builder.Default
    private Short numeroIntento = 1;
    
    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio;
    
    @Column(name = "duracion_min", nullable = false)
    private Integer duracionMin = 45;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "estado_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private EstadoSchedule estado;

    /**
     * Columna "estado" (VARCHAR NOT NULL) heredada del esquema real, en paralelo a
     * la relación "estado" (FK a estado_id) de arriba. Caso inverso al de
     * Submission/Proposal/Tutor/TutoringFase: aquí la relación FK ya estaba bien
     * mapeada, pero faltaba synchronize la columna de texto redundante.
     */
    @Column(name = "estado", nullable = false, length = 30)
    private String estadoCodigo;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    protected void onCreate() {
        creadoEn = LocalDateTime.now();
        synchronizeEstadoCodigo();
    }

    @PreUpdate
    protected void onUpdate() {
        synchronizeEstadoCodigo();
    }

    private void synchronizeEstadoCodigo() {
        if (estado != null) {
            estadoCodigo = estado.getCodigo();
        }
    }
    
    public LocalDateTime getFechaFin() {
        return fechaInicio.plusMinutes(duracionMin);
    }
}
