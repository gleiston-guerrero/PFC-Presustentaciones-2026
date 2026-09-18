package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // IMPORTANTE: Importar esto
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * sp_generate_codigo_expediente (backend/src/main/resources/db/migration/
 * V3__stored_procedures_validacion_y_codigos.sql) es un PROCEDURE de Postgres (no FUNCTION)
 * con un parámetro INOUT para el valor de retorno scaler -- Hibernate invoca @Procedure vía
 * la sintaxis JDBC "{call proc(?, ?)}", que Postgres solo acepta para PROCEDURE (una FUNCTION
 * con {@code RETURNS <tipo>} rechaza CALL con "is not a procedure. Hint: To call a function, use
 * SELECT", sin importar cuántos parámetros se declaren) -- bugs reales encontrados probando
 * el endpoint en vivo contra Docker. Fase 3 / Criterio P1.
 */
@NamedStoredProcedureQuery(
        name = "Estudiante.generarCodigoExpediente",
        procedureName = "presus.sp_generar_codigo_expediente",
        parameters = {
                @StoredProcedureParameter(mode = ParameterMode.IN, name = "p_anio", type = Integer.class),
                @StoredProcedureParameter(mode = ParameterMode.INOUT, name = "p_codigo", type = String.class)
        }
)
@Entity
@Table(name = "estudiante", schema = "presus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
// Añadimos esto a nivel de clase para que Jackson ignore los proxies de Hibernate
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    @JsonProperty("id")
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonProperty("usuario")
    private AppUser appUser;

    @Column(name = "carrera", nullable = false, length = 180)
    @JsonProperty("carrera")
    private String program;

    @Column(name = "semestre", length = 30)
    @JsonProperty("semestre")
    private String semestre;

    @Column(name = "telefono", length = 30)
    @JsonProperty("telefono")
    private String phone;

    @Column(name = "expediente_codigo", unique = true, length = 60)
    @JsonProperty("expedienteCodigo")
    private String expedienteCode;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "carrera_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonProperty("carreraEntidad")
    private Program programEntidad;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "periodo_ingreso_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonProperty("periodoIngreso")
    private PeriodAcademic periodIngreso;

    @Column(name = "semestre_actual", nullable = false)
    @JsonProperty("semestreActual")
    private Short semestreActual;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "estado_academico_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private StatusAcademic statusAcademic;

    @Column(name = "creado_en", nullable = false, updatable = false)
    @JsonProperty("creadoEn")
    private LocalDateTime creadoEn;

    @PrePersist
    protected void onCreate() {
        creadoEn = LocalDateTime.now();
    }
}