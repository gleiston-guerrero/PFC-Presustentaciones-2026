package ec.edu.uteq.presustentaciones.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "actas", schema = "presus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Minutes {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    @JsonProperty("id")
    private Long id;

    @Column(name = "fecha_generacion", nullable = false)
    @JsonProperty("fechaGeneracion")
    private LocalDate fechaGeneracion;

    @Column(name = "archivo_pdf")
    @JsonProperty("archivoPdf")
    private String archivoPdf;

    // ── Firma multi-actor (RF-08) ─────────────────────────────────────────────

    /** ¿Ha firmado el presidente del panelist? */
    @Column(name = "firmada_presidente", nullable = false)
    @Builder.Default
    @JsonProperty("firmadaPresidente")
    private boolean firmadaPresidente= false;

    @Column(name = "fecha_firma_presidente")
    @JsonProperty("fechaFirmaPresidente")
    private LocalDateTime fechaFirmaPresidente;

    /** ¿Ha firmado el vocal 1? */
    @Column(name = "firmada_vocal1", nullable = false)
    @Builder.Default
    @JsonProperty("firmadaVocal1")
    private boolean firmadaVocal1= false;

    @Column(name = "fecha_firma_vocal1")
    @JsonProperty("fechaFirmaVocal1")
    private LocalDateTime fechaFirmaVocal1;

    /** ¿Ha firmado el vocal 2? */
    @Column(name = "firmada_vocal2", nullable = false)
    @Builder.Default
    @JsonProperty("firmadaVocal2")
    private boolean firmadaVocal2= false;

    @Column(name = "fecha_firma_vocal2")
    @JsonProperty("fechaFirmaVocal2")
    private LocalDateTime fechaFirmaVocal2;

    /** ¿Ha firmado el tutor? */
    @Column(name = "firmada_tutor", nullable = false)
    @Builder.Default
    @JsonProperty("firmadaTutor")
    private boolean firmadaTutor= false;

    @Column(name = "fecha_firma_tutor")
    @JsonProperty("fechaFirmaTutor")
    private LocalDateTime fechaFirmaTutor;

    /** true solo cuando TODOS los actores requeridos han firmado */
    @Column(name = "firmada", nullable = false)
    @Builder.Default
    @JsonProperty("firmada")
    private boolean firmada= false;

    @Column(name = "observaciones_acta", columnDefinition = "TEXT")
    @JsonProperty("observacionesMinutes")
    private String observacionesMinutes;

    /**
     * Estado del minutes en el flujo GENERADA -> REVISADA -> FINALIZADA (catálogo
     * presus.estados_minutes, V19). Complementa las banderas firmada_* : una firma
     * completa lleva el minutes a FINALIZADA; coordinador/administrador pueden moverla
     * a REVISADA/OBSERVADA/ANULADA vía MinutesService.changeEstado. Cada transición
     * queda en presus.history_estados_minutes.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "estado_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private EstadoMinutes estado;

    @OneToOne
    @JoinColumn(name = "solicitud_id", nullable = false)
    @JsonProperty("solicitud")
    private Submission submission;

    // ── Helper ───────────────────────────────────────────────────────────────
    /** El minutes queda totalmente firmada cuando presidente + ambos vocales + tutor firmaron */
    public void updateEstadoFirma() {
        this.firmada = firmadaPresidente && firmadaVocal1 && firmadaVocal2 && firmadaTutor;
    }

    /** @return los firmantes pendientes como texto, o cadena vacía si ya firmaron todos */
    public String getFirmantesPendientes() {
        StringBuilder sb = new StringBuilder();
        if (!firmadaPresidente) sb.append("Presidente, ");
        if (!firmadaVocal1)     sb.append("Vocal 1, ");
        if (!firmadaVocal2)     sb.append("Vocal 2, ");
        if (!firmadaTutor)      sb.append("Tutor, ");
        String result = sb.toString();
        return result.isEmpty() ? "Todos firmaron" : result.substring(0, result.length() - 2);
    }
}
