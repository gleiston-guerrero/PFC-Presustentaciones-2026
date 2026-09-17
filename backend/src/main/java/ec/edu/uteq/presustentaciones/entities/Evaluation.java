package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

/**
 * sp_calculate_promedio_evaluation (backend/src/main/resources/db/migration/V2__stored_procedures.sql)
 * agrega las notas por criterio de evaluations_criterio junto con nota_instructor de esta
 * misma tabla, y persiste nota_final + resultado -- invocado vía EvaluationRepository
 * (JPA 2.1 @NamedStoredProcedureQuery, Fase 3 / Criterio P1). Es un PROCEDURE con un
 * parámetro INOUT tipo refcursor (ParameterMode.REF_CURSOR) en vez de una FUNCTION con
 * RETURNS TABLE -- ver la nota completa en V2__stored_procedures.sql sobre por qué
 * (Postgres rechaza la sintaxis CALL que Hibernate genera contra una FUNCTION).
 */
@NamedStoredProcedureQuery(
        name = "Evaluacion.calcularPromedioEvaluacion",
        procedureName = "presus.sp_calcular_promedio_evaluacion",
        resultSetMappings = "PromedioEvaluacionMapping",
        parameters = {
                @StoredProcedureParameter(mode = ParameterMode.IN, name = "p_solicitud_id", type = Long.class),
                @StoredProcedureParameter(mode = ParameterMode.REF_CURSOR, name = "p_resultado", type = void.class)
        }
)
@SqlResultSetMapping(
        name = "PromedioEvaluacionMapping",
        classes = @ConstructorResult(
                targetClass = ec.edu.uteq.presustentaciones.dto.PromedioEvaluationResult.class,
                columns = {
                        @ColumnResult(name = "solicitud_id", type = Long.class),
                        @ColumnResult(name = "nota_final", type = Double.class),
                        @ColumnResult(name = "estado_resultado", type = String.class)
                }
        )
)
@Entity
@Table(name = "evaluaciones", schema = "presus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Evaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    // ── Notas desagregadas ───────────────────────────────────────────────────
    /** Nota asignada por el instructor del curso (ponderación default 60%) */
    @Column(name = "nota_instructor")
    private Double notaInstructor;

    /** Nota asignada por el tribunal/panelist (ponderación default 40%) */
    @Column(name = "nota_jurado")
    private Double notaPanelist;

    /** Ponderación del instructor en %, default 60 */
    @Column(name = "peso_instructor", nullable = false)
    @Builder.Default
    private Double pesoInstructor = 60.0;

    /** Ponderación del panelist en %, default 40 */
    @Column(name = "peso_jurado", nullable = false)
    @Builder.Default
    private Double pesoPanelist = 40.0;

    /** Nota final calculada = (notaInstructor * pesoInstructor/100) + (notaPanelist * pesoPanelist/100) */
    @Column(name = "nota_final")
    private Double notaFinal;

    /** Valores posibles: APROBADO, REPROBADO */
    @Column(name = "resultado", length = 20)
    private String resultado;

    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;

    @Column(name = "comentario_preestablecido", columnDefinition = "TEXT")
    private String comentarioPreestablecido;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "solicitud_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "creadoPor", "actualizadoPor"})
    @JsonProperty("solicitud")
    private Submission submission;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "rubrica_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonProperty("rubrica")
    private Rubric rubric;

    // ── Método helper para calculate nota final ───────────────────────────────
    /**
     * Calculate nota final.
     */
    public void calculateNotaFinal() {
        if (notaInstructor != null && notaPanelist != null) {
            this.notaFinal = (notaInstructor * pesoInstructor / 100.0)
                           + (notaPanelist * pesoPanelist / 100.0);
            // Scale sobre 10
            this.notaFinal = Math.round(this.notaFinal * 100.0) / 100.0;
            this.resultado = this.notaFinal >= 7.0 ? "APROBADO" : "REPROBADO";
        }
    }
}
