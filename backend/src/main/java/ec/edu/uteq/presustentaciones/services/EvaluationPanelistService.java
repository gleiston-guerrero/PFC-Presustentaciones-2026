package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.EvaluationPanelistDTO;
import ec.edu.uteq.presustentaciones.entities.EvaluationPanelist;
import ec.edu.uteq.presustentaciones.entities.Panelist;
import ec.edu.uteq.presustentaciones.entities.Submission;
import ec.edu.uteq.presustentaciones.repositories.EvaluationPanelistRepository;
import ec.edu.uteq.presustentaciones.repositories.PanelistRepository;
import ec.edu.uteq.presustentaciones.repositories.SubmissionRepository;
import ec.edu.uteq.presustentaciones.security.service.SubmissionAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EvaluationPanelistService {

    private final EvaluationPanelistRepository evaluationPanelistRepo;
    private final SubmissionRepository submissionRepo;
    private final PanelistRepository panelistRepo;
    private final SubmissionAccessService submissionAccessService;
    private final PermissionService permissionService;

    /** Solo el propio panelist (teacher asignado a esa fila de members_tribunal) puede
     * register su nota; ADMIN/COORDINADOR (EVALUACION_CALIFICAR) pueden hacerlo en su
     * representación -- evita que un panelist registre una nota a nombre de otro (IDOR de
     * escritura). */
    private void validatePuedeRegister(Panelist panelist) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Usuario no autenticado");
        }
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin || permissionService.tienePermission(auth, "EVALUACION_CALIFICAR")) {
            return;
        }
        if (!permissionService.esPropioTeacher(auth, panelist.getTeacher().getId())) {
            throw new AccessDeniedException("Solo puedes registrar tu propia evaluación como jurado");
        }
    }

    /**
     * Registra o actualiza la nota que un panelist le asigna a una submission, calculando el
     * resultado ("APROBADO" si {@code notaPanelist >= 7}) y un comentario preestablecido según
     * el rango de la nota.
     *
     * @param submissionId  id de la submission evaluada
     * @param panelistId     id del panelist que evalúa
     * @param notaPanelist   nota asignada, debe estar entre 1 y 10
     * @param observaciones observaciones opcionales del panelist
     * @return la evaluación registrada, con resultado y comentario calculados
     * @throws RuntimeException si la submission o el panelist no existen, el panelist no
     *                          pertenece a esa submission, o la nota está fuera de 1-10
     */
    @Transactional
    public EvaluationPanelistDTO saveEvaluation(Long submissionId, Long panelistId, Double notaPanelist, String observaciones) {
        Submission submission = submissionRepo.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada: " + submissionId));

        Panelist panelist = panelistRepo.findById(panelistId)
                .orElseThrow(() -> new RuntimeException("Jurado no encontrado: " + panelistId));

        if (!panelist.getSubmission().getId().equals(submissionId)) {
            throw new RuntimeException("El jurado no pertenece a esta solicitud.");
        }

        validatePuedeRegister(panelist);

        if (notaPanelist < 1 || notaPanelist > 10) {
            throw new RuntimeException("La nota debe estar entre 1 y 10.");
        }

        String resultado = notaPanelist >= 7 ? "APROBADO" : "REPROBADO";
        String comentarioPreestablecido = generateComentarioPorRango(notaPanelist);

        Optional<EvaluationPanelist> existente = evaluationPanelistRepo.findBySubmissionIdAndPanelistId(submissionId, panelistId);
        
        EvaluationPanelist evaluation;
        if (existente.isPresent()) {
            evaluation = existente.get();
            evaluation.setNotaPanelist(notaPanelist);
            evaluation.setObservaciones(observaciones);
            evaluation.setResultado(resultado);
            evaluation.setComentarioPreestablecido(comentarioPreestablecido);
        } else {
            evaluation = EvaluationPanelist.builder()
                    .submission(submission)
                    .panelist(panelist)
                    .notaPanelist(notaPanelist)
                    .observaciones(observaciones)
                    .resultado(resultado)
                    .comentarioPreestablecido(comentarioPreestablecido)
                    .build();
        }

        evaluation = evaluationPanelistRepo.save(evaluation);
        return toDTO(evaluation);
    }

    @Transactional(readOnly = true)
    /**
     * @param submissionId id de la submission
     * @param panelistId    id del panelist
     * @return la evaluación de ese panelist para esa submission, o {@code null} si aún no evaluó
     */
    public EvaluationPanelistDTO obtainEvaluation(Long submissionId, Long panelistId) {
        Submission submission = submissionRepo.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada: " + submissionId));
        submissionAccessService.validateAcceso(submission, "EVALUACION_CALIFICAR");
        return evaluationPanelistRepo.findBySubmissionIdAndPanelistId(submissionId, panelistId)
                .map(this::toDTO)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    /**
     * @param submissionId id de la submission
     * @return las evaluations registradas por todos los panelists de esa submission
     */
    public List<EvaluationPanelistDTO> obtainTribunal(Long submissionId) {
        Submission submission = submissionRepo.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada: " + submissionId));
        submissionAccessService.validateAcceso(submission, "EVALUACION_CALIFICAR");
        return evaluationPanelistRepo.findBySubmissionId(submissionId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    private EvaluationPanelistDTO toDTO(EvaluationPanelist eval) {
        String nombrePanelist = "";
        if (eval.getPanelist() != null && eval.getPanelist().getTeacher() != null 
            && eval.getPanelist().getTeacher().getAppUser() != null) {
            nombrePanelist = eval.getPanelist().getTeacher().getAppUser().getNombre() + " "
                    + eval.getPanelist().getTeacher().getAppUser().getApellido();
        }

        return EvaluationPanelistDTO.builder()
                .id(eval.getId())
                .submissionId(eval.getSubmission().getId())
                .panelistId(eval.getPanelist().getId())
                .notaPanelist(eval.getNotaPanelist())
                .observaciones(eval.getObservaciones())
                .resultado(eval.getResultado())
                .comentarioPreestablecido(eval.getComentarioPreestablecido())
                .nombrePanelist(nombrePanelist)
                .rolePanelist(eval.getPanelist().getRole())
                .build();
    }

    private String generateComentarioPorRango(Double nota) {
        if (nota <= 3) {
            return "El trabajo no cumple con los requisitos mínimos esperados. Se evidencian falencias significativas que requieren correcciones sustanciales.";
        } else if (nota <= 6) {
            return "El trabajo presenta un nivel aceptable pero con aspectos que requieren mejoras o correcciones para alcanzar los estándares esperados.";
        } else {
            return "El trabajo cumple satisfactoriamente con los objetivos y requisitos establecidos, demostrando un desempeño adecuado.";
        }
    }
}
