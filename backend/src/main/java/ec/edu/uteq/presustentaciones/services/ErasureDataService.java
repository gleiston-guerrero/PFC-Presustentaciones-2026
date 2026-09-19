package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.SubmissionErasure;
import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.repositories.SubmissionErasureRepository;
import ec.edu.uteq.presustentaciones.repositories.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RNF-19, tercer criterio: procedimiento de supresión de datos personales a submission del
 * titular. La resolución nunca borra el registro del appUser -- lo <b>seudonimiza</b>: el
 * expediente académico (submissions, minutes, calificaciones) sigue enlazado al mismo id, porque
 * la normativa de titulación obliga a conservarlo (ver {@code docs/etica/RETENCION-DATOS.md}).
 * El registro de la propia submission tampoco guarda el dato suprimido -- solo referencia el id,
 * que sigue siendo válido después de la seudonimización.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ErasureDataService {

    private final AppUserRepository appUserRepository;
    private final SubmissionErasureRepository submissionRepository;

    /**
     * Solicitar.
     * @param appUserId id del appUser titular que solicita la supresión de sus datos
     * @return la submission de supresión creada, en estado "PENDIENTE"
     * @throws IllegalStateException    si ya existe una submission pendiente para esa cuenta
     * @throws IllegalArgumentException si el appUser no existe
     */
    public SubmissionErasure solicitar(Long appUserId) {
        if (submissionRepository.existsByAppUserIdAndStatus(appUserId, "PENDIENTE")) {
            throw new IllegalStateException("Ya existe una solicitud de supresión pendiente para esta cuenta.");
        }
        if (!appUserRepository.existsById(appUserId)) {
            throw new IllegalArgumentException("Usuario no encontrado.");
        }
        SubmissionErasure submission = SubmissionErasure.builder()
                .appUserId(appUserId)
                .dateSubmission(LocalDateTime.now())
                .status("PENDIENTE")
                .build();
        return submissionRepository.save(submission);
    }

    /**
     * List.
     * @return todas las submissions de supresión, de la más reciente a la más antigua
     */
    public List<SubmissionErasure> list() {
        return submissionRepository.findAllByOrderByDateSubmissionDesc();
    }

    /**
     * Resolve.
     * @param submissionId   id de la submission de supresión a resolve
     * @param aceptar       true para seudonimizar la cuenta, false para reject la submission
     *                      (p. ej. porque el titular tiene un process de titulación en curso
     *                      que la normativa obliga a poder identificar)
     * @param resueltoById id del appUser (ADMIN) que resuelve la submission
     * @param notas         notas opcionales de la resolución
     * @return la submission actualizada, con su resolución aplicada
     * @throws IllegalArgumentException si la submission no existe
     * @throws IllegalStateException    si la submission ya había sido resuelta
     */
    @Transactional
    public SubmissionErasure resolve(Long submissionId, boolean aceptar, Long resueltoById, String notas) {
        SubmissionErasure submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Solicitud de supresión no encontrada."));
        if (!"PENDIENTE".equals(submission.getStatus())) {
            throw new IllegalStateException("Esta solicitud ya fue resuelta.");
        }

        if (aceptar) {
            pseudonymize(submission.getAppUserId());
            submission.setKindResolucion("SEUDONIMIZACION");
            submission.setStatus("RESUELTA");
        } else {
            submission.setKindResolucion("RECHAZADA");
            submission.setStatus("RECHAZADA");
        }
        submission.setResueltoBy(resueltoById);
        submission.setDateResolucion(LocalDateTime.now());
        submission.setNotas(notas);
        return submissionRepository.save(submission);
    }

    /**
     * Reemplaza los campos identificables por marcadores no identificables y desactiva la
     * cuenta. No borra la fila: el expediente académico enlazado al mismo id (submissions,
     * minutes, evaluations) debe seguir existiendo para cumplir la obligación legal de
     * conservación -- ver RETENCION-DATOS.md.
     */
    private void pseudonymize(Long appUserId) {
        AppUser appUser = appUserRepository.findById(appUserId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        appUser.setNombre("Usuario suprimido");
        appUser.setApellido("#" + appUser.getId());
        appUser.setEmail("suprimido-" + appUser.getId() + "@presustentaciones.invalid");
        appUser.setPhone(null);
        appUser.setActivo(false);
        appUserRepository.save(appUser);
        log.warn("RNF-19: usuario {} seudonimizado por resolución de solicitud de supresión.", appUserId);
    }
}
