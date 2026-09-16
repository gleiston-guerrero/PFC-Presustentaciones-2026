package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.repositories.ProgramRepository;
import ec.edu.uteq.presustentaciones.repositories.FacultyRepository;
import ec.edu.uteq.presustentaciones.repositories.ModalityTitulacionRepository;
import ec.edu.uteq.presustentaciones.repositories.PeriodAcademicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Solo las eliminaciones de Gestión de Programs viven aquí, en un bean aparte del
 * controlador. Si @Transactional envuelve el método del controlador y el catch de
 * DataIntegrityViolationException vive DENTRO de ese mismo método, Hibernate ya marcó
 * la transacción como rollback-only en cuanto el flush() falla -- el catch atrapa la
 * excepción en código Java, pero al volver "normalmente" el intento de commit del AOP
 * de Spring lanza UnexpectedRollbackException igual (bug real: probado borrando una
 * faculty con programs asociadas, el mensaje amigable nunca llegaba a devolverse).
 * Separando el delete+flush en su propio bean transaccional, la excepción cruza la
 * frontera del proxy @Transactional (así Spring hace el rollback correctamente) antes
 * de llegar al controlador, que la atrapa ya con la transacción cerrada.
 */
@Service
@RequiredArgsConstructor
public class CatalogoAdminService {

    private final FacultyRepository facultyRepo;
    private final ProgramRepository programRepo;
    private final ModalityTitulacionRepository modalityRepo;
    private final PeriodAcademicoRepository periodAcademicoRepo;
    private final AuditService auditService;

    /**
     * @param id id de la faculty a delete
     * @throws org.springframework.dao.DataIntegrityViolationException si hay programs
     *         asociadas a la faculty
     */
    @Transactional
    public void deleteFaculty(Integer id) {
        auditService.marcarActorActual();
        facultyRepo.deleteById(id);
        facultyRepo.flush();
    }

    /**
     * @param id id de la program a delete
     * @throws org.springframework.dao.DataIntegrityViolationException si hay registros
     *         dependientes de la program
     */
    @Transactional
    public void deleteProgram(Integer id) {
        auditService.marcarActorActual();
        programRepo.deleteById(id);
        programRepo.flush();
    }

    /**
     * @param id id de la modality de titulación a delete
     * @throws org.springframework.dao.DataIntegrityViolationException si hay registros
     *         dependientes de la modality
     */
    @Transactional
    public void deleteModality(Short id) {
        auditService.marcarActorActual();
        modalityRepo.deleteById(id);
        modalityRepo.flush();
    }

    /**
     * @param id id del period académico a delete
     * @throws org.springframework.dao.DataIntegrityViolationException si hay registros
     *         dependientes del period
     */
    @Transactional
    public void deletePeriod(Integer id) {
        auditService.marcarActorActual();
        periodAcademicoRepo.deleteById(id);
        periodAcademicoRepo.flush();
    }
}
