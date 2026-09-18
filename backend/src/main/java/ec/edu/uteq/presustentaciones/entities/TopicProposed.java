package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "temas_propuestos", schema = "presus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicProposed {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    @JsonProperty("id")
    private Integer id;

    @Column(name = "titulo", nullable = false, length = 500)
    @JsonProperty("titulo")
    private String titulo;

    @Column(name = "problema", columnDefinition = "TEXT")
    @JsonProperty("problema")
    private String problema;

    @Column(name = "objetivo_general", columnDefinition = "TEXT")
    @JsonProperty("objetivoGeneral")
    private String objetivoGeneral;

    @Column(name = "objetivos_especificos", columnDefinition = "TEXT")
    @JsonProperty("objetivosEspecificos")
    private String objetivosEspecificos;

    @Column(name = "justificacion", columnDefinition = "TEXT")
    @JsonProperty("justificacion")
    private String justificacion;

    @Column(name = "beneficiarios", columnDefinition = "TEXT")
    @JsonProperty("beneficiarios")
    private String beneficiarios;

    @Column(name = "nivel_dificultad", length = 50)
    @JsonProperty("nivelDificultad")
    private String nivelDificultad;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carrera_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonProperty("carrera")
    private Program program;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linea_investigacion_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonProperty("lineaInvestigacion")
    private ResearchLine researchLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "area_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Subject area;
}
