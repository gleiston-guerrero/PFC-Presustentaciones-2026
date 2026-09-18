package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Ruta de titulación / checklist de cada student. {@code pasosJson} guarda el
 * estado de los pasos como un objeto JSON {"clave_paso": true/false} — el catálogo
 * de pasos lo define el servicio, aquí solo se persiste qué marcó el student.
 * Se mapea como String (mismo criterio que Audit) y el servicio lo convierte.
 */
@Entity
@Table(name = "progreso_estudiante", schema = "presus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProgressStudent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    @JsonProperty("id")
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estudiante_id", nullable = false, unique = true)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonProperty("estudiante")
    private Student student;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pasos_json", columnDefinition = "jsonb", nullable = false)
    @JsonProperty("pasosJson")
    private String pasosJson;
}
