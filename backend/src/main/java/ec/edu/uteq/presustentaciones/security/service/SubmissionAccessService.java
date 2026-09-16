package ec.edu.uteq.presustentaciones.security.service;

import ec.edu.uteq.presustentaciones.entities.Panelist;
import ec.edu.uteq.presustentaciones.entities.Submission;
import ec.edu.uteq.presustentaciones.entities.Tutor;
import ec.edu.uteq.presustentaciones.repositories.PanelistRepository;
import ec.edu.uteq.presustentaciones.repositories.TutorRepository;
import ec.edu.uteq.presustentaciones.services.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Verifica que el appUser autenticado tenga relación real con una {@link Submission} antes de
 * exponer datos académicos asociados a ella (evaluations, rúbricas, proposal). Mismo
 * patrón que {@code MinutesServiceImpl.validateAcceso} / {@code NotificationServiceImpl.validateAcceso}
 * (evita IDOR: nunca basta con "estar autenticado", hay que ser el student dueño, un panelist
 * asignado, el tutor, o tener el permission administrativo indicado), consolidado aquí porque
 * Evaluación, EvaluaciónPanelist, RúbricaEvaluación y Proposal comparten exactamente la misma
 * relación con Submission.
 */
@Service
@RequiredArgsConstructor
public class SubmissionAccessService {

    private final PanelistRepository panelistRepository;
    private final TutorRepository tutorRepository;
    private final PermissionService permissionService;

    /**
     * @param submission             la submission cuyo dato asociado se quiere leer/escribir
     * @param codigosPermissionBypass  códigos de permission (p.ej. "EVALUACION_CALIFICAR",
     *                              "ANTEPROYECTO_REVISAR") cuya sola tenencia ya autoriza el
     *                              acceso, sin necesidad de participar directamente en la
     *                              submission -- refleja los mismos permissions que ya protegen las
     *                              acciones administrativas equivalentes en cada controlador.
     * @throws AccessDeniedException si el appUser no es ADMIN, no tiene ninguno de esos
     *                               permissions, y no participa en la submission como student
     *                               dueño, panelist asignado o tutor.
     */
    public void validateAcceso(Submission submission, String... codigosPermissionBypass) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Usuario no autenticado");
        }

        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) return;

        for (String codigo : codigosPermissionBypass) {
            if (permissionService.tienePermission(auth, codigo)) return;
        }

        String email = auth.getName();

        if (submission.getStudent() != null && submission.getStudent().getAppUser() != null
                && submission.getStudent().getAppUser().getEmail().equals(email)) {
            return;
        }

        List<Panelist> panelists = panelistRepository.findBySubmissionId(submission.getId());
        boolean esPanelist = panelists.stream().anyMatch(j ->
                j.getTeacher() != null && j.getTeacher().getAppUser() != null
                        && j.getTeacher().getAppUser().getEmail().equals(email));
        if (esPanelist) return;

        Optional<Tutor> tutorOpt = tutorRepository.findBySubmissionId(submission.getId());
        if (tutorOpt.isPresent() && tutorOpt.get().getTeacher() != null
                && tutorOpt.get().getTeacher().getAppUser() != null
                && tutorOpt.get().getTeacher().getAppUser().getEmail().equals(email)) {
            return;
        }

        throw new AccessDeniedException("No tienes permiso para acceder a la información de esta solicitud");
    }
}
