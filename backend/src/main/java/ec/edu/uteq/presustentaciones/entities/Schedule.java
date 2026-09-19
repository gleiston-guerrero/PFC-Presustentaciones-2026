package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Schedule.
 */
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
    @JsonProperty("id")
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
    private AnnouncementDegree announcement;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sala_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonProperty("sala")
    private Room room;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "bloque_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonProperty("bloque")
    private TimeBlock block;

    @Column(name = "numero_intento", nullable = false)
    @Builder.Default
    @JsonProperty("numeroIntento")
    private Short numeroIntento= 1;
    
    @Column(name = "fecha_inicio", nullable = false)
    @JsonProperty("fechaInicio")
    private LocalDateTime dateStart;
    
    @Column(name = "duracion_min", nullable = false)
    @JsonProperty("duracionMin")
    private Integer duracionMin= 45;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "estado_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private StatusSchedule status;

    /**
     * Columna "estado" (VARCHAR NOT NULL) heredada del esquema real, en paralelo a
     * la relación "estado" (FK a estado_id) de arriba. Caso inverso al de
     * Submission/Proposal/Tutor/TutoringFase: aquí la relación FK ya estaba bien
     * mapeada, pero faltaba synchronize la columna de texto redundante.
     */
    @Column(name = "estado", nullable = false, length = 30)
    @JsonProperty("estadoCodigo")
    private String statusCode;

    @Column(name = "creado_en", nullable = false, updatable = false)
    @JsonProperty("creadoEn")
    private LocalDateTime creadoEn;

    /**
     * On create.
     */
    @PrePersist
    protected void onCreate() {
        creadoEn = LocalDateTime.now();
        synchronizeStatusCode();
    }

    /**
     * On update.
     */
    @PreUpdate
    protected void onUpdate() {
        synchronizeStatusCode();
    }

    private void synchronizeStatusCode() {
        if (status != null) {
            statusCode = status.getCode();
        }
    }
    
    /**
     * Get fecha fin.
     * @return el LocalDateTime correspondiente
     */
    public LocalDateTime getDateEnd() {
        return dateStart.plusMinutes(duracionMin);
    }
}
