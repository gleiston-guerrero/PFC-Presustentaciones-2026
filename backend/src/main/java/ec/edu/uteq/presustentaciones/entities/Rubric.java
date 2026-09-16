package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "rubricas", schema = "presus")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Rubric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "puntaje_maximo", nullable = false)
    private Double puntajeMaximo;

    /** Criterios de evaluación que componen esta rúbrica */
    @OneToMany(mappedBy = "rubric", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @OrderBy("orden ASC")
    @Builder.Default
    private List<CriterioRubric> criterios = new ArrayList<>();
}
