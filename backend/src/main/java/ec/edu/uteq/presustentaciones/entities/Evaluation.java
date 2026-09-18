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
                targetClass = ec.edu.uteq.presustentaciones.dto.AverageEvaluationResult.class,
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
    @JsonProperty("id")
    private Long id;

    // ── Notas desagregadas ───────────────────────────────────────────────────
    /** Nota asignada por el instructor del curso (ponderación default 60%) */
    @Column(name = "nota_instructor")
    @JsonProperty("notaInstructor")
    private Double gradeInstructor;

    /** Nota asignada por el tribunal/panelist (ponderación default 40%) */
    @Column(name = "nota_jurado")
    @JsonProperty("notaPanelist")
    private Double gradePanelist;

    /** Ponderación del instructor en %, default 60 */
    @Column(name = "peso_instructor", nullable = false)
    @Builder.Default
    @JsonProperty("pesoInstructor")
    private Double pesoInstructor= 60.0;

    /** Ponderación del panelist en %, default 40 */
    @Column(name = "peso_jurado", nullable = false)
    @Builder.Default
    @JsonProperty("pesoPanelist")
    private Double pesoPanelist= 40.0;

    /** Nota final calculada = (notaInstructor * pesoInstructor/100) + (notaPanelist * pesoPanelist/100) */
    @Column(name = "nota_final")
    @JsonProperty("notaFinal")
    private Double gradeFinal;

    /** Valores posibles: APROBADO, REPROBADO */
    @Column(name = "resultado", length = 20)
    @JsonProperty("resultado")
    private String result;

    @Column(name = "observaciones", columnDefinition = "TEXT")
    @JsonProperty("observaciones")
    private String observations;

    @Column(name = "comentario_preestablecido", columnDefinition = "TEXT")
    @JsonProperty("comentarioPreestablecido")
    private String commentPreestablecido;

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
    public void calculateGradeFinal() {
        if (gradeInstructor != null && gradePanelist != null) {
            this.gradeFinal = (gradeInstructor * pesoInstructor / 100.0)
                           + (gradePanelist * pesoPanelist / 100.0);
            // Scale sobre 10
            this.gradeFinal = Math.round(this.gradeFinal * 100.0) / 100.0;
            this.result = this.gradeFinal >= 7.0 ? "APROBADO" : "REPROBADO";
        }
    }
}
