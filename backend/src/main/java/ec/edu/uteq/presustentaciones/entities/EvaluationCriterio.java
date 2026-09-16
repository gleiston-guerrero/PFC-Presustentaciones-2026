package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Registra la nota que un JURADO asigna a UN criterio de la rúbrica
 * para una submission específica.
 * Scale: 1-100 (% del puntaje máximo del criterio)
 * Rangos de observación: 67-100 (Alto), 34-66 (Medio), 1-33 (Bajo)
 */
@Entity
@Table(name = "evaluaciones_criterio", schema = "presus",
       uniqueConstraints = @UniqueConstraint(
           columnNames = {"evaluador_id", "criterio_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EvaluationCriterio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Scale aplicada: 1-100 (% del puntaje máximo del criterio) */
    @Column(name = "escala", nullable = false)
    @JsonProperty("escala")
    private Integer scale;

    /** Nota calculada = criterio.ponderacion * scale / 100  (sobre la base del criterio) */
    @Column(name = "nota_obtenida", nullable = false)
    private Double notaObtenida;

    /** Observación automática según el rango de la scale */
    @Column(name = "observacion_auto", columnDefinition = "TEXT")
    private String observacionAuto;

    /** Observación manual ingresada por el panelist */
    @Column(name = "observacion_manual", columnDefinition = "TEXT")
    private String observacionManual;

    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;

    @Column(name = "registrado_en", nullable = false, updatable = false)
    private LocalDateTime registradoEn;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "solicitud_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "jurados", "tutor",
                           "evaluacion", "acta", "anteproyecto", "cronograma", "notificaciones"})
    @JsonProperty("solicitud")
    private Submission submission;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "evaluador_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "solicitud"})
    @JsonProperty("evaluador")
    private Evaluator evaluator;

    /**
     * FK NOT NULL a members_tribunal (columna real panelist_id), ausente por completo
     * en la entidad: nada la poblaba, causando un error al register la evaluación de
     * un panelist ("null value in column jurado_id"). A diferencia de los demás bugs de
     * columnas duplicadas encontrados en esta sesión, aquí no había ningún campo en
     * absoluto — se agrega la relación real.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "jurado_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "solicitud"})
    @JsonProperty("jurado")
    private Panelist panelist;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "criterio_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "rubrica"})
    private CriterioRubric criterio;

    @PrePersist
    protected void onCreate() {
        registradoEn = LocalDateTime.now();
    }

    public static String getObservacionPorRango(int scale) {
        if (scale >= 67) {
            return "Excelente. Cumple satisfactoriamente con los requisitos y objetivos del criterio establecido.";
        } else if (scale >= 34) {
            return "Aceptable. Presenta algunas deficiencias que requieren corrección o mejora.";
        } else {
            return "Deficiente. No cumple con los requisitos mínimos del criterio. Se evidencian falencias significativas.";
        }
    }

    public static String getRangoDescripcion(int scale) {
        if (scale >= 67) {
            return "ALTO";
        } else if (scale >= 34) {
            return "MEDIO";
        } else {
            return "BAJO";
        }
    }
}
