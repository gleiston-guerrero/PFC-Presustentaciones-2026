package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.EvaluationRubricRequest;
import ec.edu.uteq.presustentaciones.dto.EvaluationRubricResponse;
import ec.edu.uteq.presustentaciones.dto.ObservationsSubmissionDTO;

import java.util.List;

/**
 * Contrato. Servicio de rubric evaluation.
 */
public interface RubricEvaluationService {
    /** El panelist registra sus scales por criterio
     * @param request calificación por criterio de rúbrica emitida por un panelist
     * @return la evaluación registrada, con la nota calculada para ese panelist
     * @throws RuntimeException si la submission, la rúbrica o el panelist no existen
     */
    EvaluationRubricResponse registerEvaluation(EvaluationRubricRequest request);

    /** Estado de la evaluación de un panelist para una submission
     * @param submissionId identificador de la solicitud de pre-sustentación
     * @param panelistId    identificador del panelista (miembro del tribunal)
     * @return la evaluación de ese panelist para esa submission, si ya la registró
     */
    EvaluationRubricResponse obtainEvaluationPanelist(Long submissionId, Long panelistId);

    /** Resumen de todos los panelists para una submission
     * @param submissionId identificador de la solicitud de pre-sustentación
     * @return las evaluations registradas por cada panelist de esa submission
     */
    List<EvaluationRubricResponse> obtainEvaluationsSubmission(Long submissionId);

    /** Nota promedio del tribunal (40%) lista para usar en la evaluación final
     * @param submissionId identificador de la solicitud de pre-sustentación
     * @return el promedio, redondeado a 2 decimales, de las notas de los panelists que ya
     *         evaluaron; {@code 0.0} si ninguno ha evaluado todavía
     */
    Double calculateGradePanel(Long submissionId);

    /** Obtain todas las observaciones de una submission (tutor, panelists, coordinador)
     * @param submissionId identificador de la solicitud de pre-sustentación
     * @return observaciones consolidadas de todos los actores que han evaluado la submission
     */
    ObservationsSubmissionDTO obtainObservationsSubmission(Long submissionId);
}
