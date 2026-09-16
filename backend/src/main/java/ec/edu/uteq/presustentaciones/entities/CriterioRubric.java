package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "criterios_rubrica", schema = "presus")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CriterioRubric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nombre del criterio: Propuesta, Documento, Exposición */
    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    /** Descripción del criterio */
    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    /** Ponderación del criterio sobre el total de la rúbrica (ej: 33.33) */
    @Column(name = "ponderacion", nullable = false)
    private Double ponderacion;

    /** Orden de presentación */
    @Column(name = "orden", nullable = false)
    @Builder.Default
    private Integer orden = 1;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "rubrica_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "criterios"})
    @JsonProperty("rubrica")
    private Rubric rubric;
}
