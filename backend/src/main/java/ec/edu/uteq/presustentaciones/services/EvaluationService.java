package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.AverageEvaluationResult;
import ec.edu.uteq.presustentaciones.entities.EvaluationFinal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

/**
 * Contrato. Servicio de evaluation.
 */
public interface EvaluationService {

    /**
     * Agrega las notas por criterio del tribunal (evaluations_criterio) con la nota del
     * instructor y persiste nota_final/estado_resultado vía sp_calculate_promedio_evaluation
     * (Fase 3 / Criterio P1, categoría "cálculos agregados").
     *
     * @param submissionId id de la submission a calculate
     * @return submissionId, nota final ponderada y estado del resultado ("APROBADO"/"REPROBADO")
     * @throws RuntimeException si la submission no existe o el procedimiento no devuelve fila
     */
    AverageEvaluationResult calculateAverageSp(Long submissionId);

    /** Registra evaluación con notas separadas de instructor y panelist (RF-09)
     * @param submissionId    id de la submission a evaluar
     * @param rubricId      id de la rúbrica aplicada
     * @param gradeInstructor nota del instructor del curso, entre 0 y 10
     * @param gradePanelist     nota promedio del tribunal, entre 0 y 10
     * @param observations  observaciones opcionales de la evaluación
     * @param pesoInstructor peso del instructor en la ponderación (0-100); {@code null} usa 60
     * @param pesoPanelist     peso del panelist en la ponderación (0-100); {@code null} usa 40
     * @return la evaluación final persistida, con nota final calculada y resultado asignado
     * @throws RuntimeException si la submission o la rúbrica no existen, los pesos no suman
     *                          100, o alguna nota está fuera de 0-10
     */
    EvaluationFinal evaluateSubmission(Long submissionId, Long rubricId,
                                 Double gradeInstructor, Double gradePanelist,
                                 String observations,
                                 Double pesoInstructor, Double pesoPanelist);

    /** Compatibilidad: evalúa pasando nota final directa (para uso legacy)
     * @param submissionId   id de la submission a evaluar
     * @param rubricId     id de la rúbrica aplicada
     * @param gradeFinal     nota final ya calculada externamente
     * @param observations observaciones opcionales
     * @return la evaluación final persistida
     * @throws RuntimeException si la submission o la rúbrica no existen
     */
    EvaluationFinal evaluateSubmission(Long submissionId, Long rubricId,
                                 Double gradeFinal, String observations);

    /**
     * List evaluations.
     * @param pageable configuración de paginación
     * @return página de todas las evaluations finales del sistema
     */
    Page<EvaluationFinal> listEvaluations(Pageable pageable);

    /**
     * List by student.
     * @param studentId id del student
     * @return las evaluations finales de las submissions de ese student
     */
    List<EvaluationFinal> listByStudent(Long studentId);

    /**
     * List by app user.
     * @param appUserId id del appUser autenticado
     * @return las evaluations finales visibles para ese appUser
     */
    List<EvaluationFinal> listByAppUser(Long appUserId);

    /**
     * Search by submission.
     * @param submissionId id de la submission
     * @return la evaluación final de esa submission, si ya fue calificada
     */
    Optional<EvaluationFinal> searchBySubmission(Long submissionId);

    /**
     * Variante de {@link #calculateAverageSp} que devuelve el resultado como un mapa
     * genérico en vez de un DTO tipado, para consumo directo desde el controlador.
     *
     * @param submissionId id de la submission a calculate
     * @return mapa con las claves {@code submissionId}, {@code gradeFinal} y
     *         {@code statusResult}; vacío si el procedimiento no devolvió filas
     */
    java.util.Map<String, Object> calculateAverageSP(Long submissionId);
}
