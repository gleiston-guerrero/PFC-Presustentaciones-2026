package ec.edu.uteq.presustentaciones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Resumen general del process de pre-sustentaciones para el dashboard de
 * coordinador/administrador. Todo se calcula con COUNT/GROUP BY en la base.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportSummaryDTO {

    @JsonProperty("totalSolicitudes")
    private long totalSubmissions;
    @JsonProperty("solicitudesCompletadas")
    private long submissionsCompletadas;
    @JsonProperty("solicitudesEnProceso")
    private long submissionsEnProcess;
    @JsonProperty("solicitudesRechazadas")
    private long submissionsRechazadas;

    @JsonProperty("totalActas")
    private long totalMinutes;
    @JsonProperty("actasGeneradas")
    private long minutesGeneradas;
    @JsonProperty("actasRevisadas")
    private long minutesRevisadas;
    @JsonProperty("actasObservadas")
    private long minutesObservadas;
    @JsonProperty("actasFinalizadas")
    private long minutesFinalizadas;
    @JsonProperty("actasAnuladas")
    private long minutesAnuladas;
    @JsonProperty("actasPendientesFirma")
    private long minutesPendingSignature;

    @JsonProperty("solicitudesPorEstado")
    private List<ReportCountDTO> submissionsByStatus;
    @JsonProperty("sustentacionesPorPeriodo")
    private List<ReportCountDTO> defensesByPeriod;
}
