package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

/**
 * Guía o resource del Centro de Titulación (formatos, plantillas, reglamentos).
 * {@code program} nulo = resource general, visible para todas las programs.
 */
@Entity
@Table(name = "recursos_titulacion", schema = "presus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceTitulacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    @JsonProperty("id")
    private Integer id;

    @Column(name = "titulo", nullable = false, length = 255)
    @JsonProperty("titulo")
    private String titulo;

    @Column(name = "categoria", nullable = false, length = 100)
    @JsonProperty("categoria")
    private String categoria;

    @Column(name = "url_archivo", nullable = false, length = 500)
    @JsonProperty("urlArchivo")
    private String urlArchivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carrera_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonProperty("carrera")
    private Program program;
}
