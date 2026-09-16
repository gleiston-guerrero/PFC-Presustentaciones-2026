package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * sp_generate_reporte_defensas (backend/src/main/resources/db/migration/V2__stored_procedures.sql)
 * arma el reporte consolidado de defensas por program cruzando submission/student/appUsers/
 * schedule/room/evaluations -- invocado vía SubmissionRepository (JPA 2.1
 * @NamedStoredProcedureQuery, Fase 3 / Criterio P1).
 */
@NamedStoredProcedureQuery(
        name = "Solicitud.generarReporteDefensas",
        procedureName = "presus.sp_generar_reporte_defensas",
        resultSetMappings = "ReporteDefensaMapping",
        parameters = {
                @StoredProcedureParameter(mode = ParameterMode.IN, name = "p_carrera", type = String.class),
                @StoredProcedureParameter(mode = ParameterMode.REF_CURSOR, name = "p_resultado", type = void.class)
        }
)
@SqlResultSetMapping(
        name = "ReporteDefensaMapping",
        classes = @ConstructorResult(
                targetClass = ec.edu.uteq.presustentaciones.dto.ReporteDefensaResult.class,
                columns = {
                        @ColumnResult(name = "solicitud_id", type = Long.class),
                        @ColumnResult(name = "estudiante_nombre", type = String.class),
                        @ColumnResult(name = "expediente", type = String.class),
                        @ColumnResult(name = "titulo_tema", type = String.class),
                        @ColumnResult(name = "estado_solicitud", type = String.class),
                        @ColumnResult(name = "fecha_defensa", type = LocalDateTime.class),
                        @ColumnResult(name = "sala_nombre", type = String.class),
                        @ColumnResult(name = "nota_final", type = Double.class)
                }
        )
)
@Entity
@Table(name = "solicitud", schema = "presus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Submission {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "estudiante_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonProperty("estudiante")
    private Student student;
    
    @Column(name = "titulo_tema", nullable = false, length = 300)
    private String tituloTopic;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "convocatoria_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonProperty("convocatoria")
    private AnnouncementTitulacion announcement;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "modalidad_titulacion_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonProperty("modalidadTitulacion")
    private ModalityTitulacion modalityTitulacion;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "linea_investigacion_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonProperty("lineaInvestigacion")
    private LineInvestigacion lineInvestigacion;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "area_tematica_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private AreaTematica areaTematica;

    @Column(name = "fecha_registro", nullable = false, updatable = false)
    private LocalDateTime fechaRegistro;
    
    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;
    
    // Trazabilidad
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por")
    @JsonIgnore
    private AppUser creadoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actualizado_por")
    @JsonIgnore
    private AppUser actualizadoPor;
    
    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "estado_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private EstadoSubmission estado;

    /**
     * Columna "estado" (VARCHAR) heredada del esquema real de la base de datos,
     * en paralelo a la relación "estado" (FK a estado_id) de arriba. Es NOT NULL
     * en la tabla real y ninguna clase la poblaba, causando un error al create o
     * update una submission ("null value in column estado"). Se mantiene
     * sincronizada automáticamente en @PrePersist/@PreUpdate a partir del código
     * de la relación FK, sin tocar los puntos del servicio que la usan.
     */
    @Column(name = "estado", nullable = false, length = 30)
    private String estadoCodigo;

    @Column(name = "motivo_suspension", columnDefinition = "TEXT")
    private String motivoSuspension;

    @Column(name = "suspendido_en")
    private LocalDateTime suspendidoEn;

    @PrePersist
    protected void onCreate() {
        fechaRegistro = LocalDateTime.now();
        actualizadoEn = LocalDateTime.now();
        synchronizeEstadoCodigo();
    }

    @PreUpdate
    protected void onUpdate() {
        actualizadoEn = LocalDateTime.now();
        synchronizeEstadoCodigo();
    }

    private void synchronizeEstadoCodigo() {
        if (estado != null) {
            estadoCodigo = estado.getCodigo();
        }
    }
}
