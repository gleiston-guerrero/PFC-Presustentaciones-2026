package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.repositories.CarreraRepository;
import ec.edu.uteq.presustentaciones.repositories.FacultadRepository;
import ec.edu.uteq.presustentaciones.repositories.ModalidadTitulacionRepository;
import ec.edu.uteq.presustentaciones.repositories.PeriodoAcademicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Solo las eliminaciones de Gestión de Carreras viven aquí, en un bean aparte del
 * controlador. Si @Transactional envuelve el método del controlador y el catch de
 * DataIntegrityViolationException vive DENTRO de ese mismo método, Hibernate ya marcó
 * la transacción como rollback-only en cuanto el flush() falla -- el catch atrapa la
 * excepción en código Java, pero al volver "normalmente" el intento de commit del AOP
 * de Spring lanza UnexpectedRollbackException igual (bug real: probado borrando una
 * facultad con carreras asociadas, el mensaje amigable nunca llegaba a devolverse).
 * Separando el delete+flush en su propio bean transaccional, la excepción cruza la
 * frontera del proxy @Transactional (así Spring hace el rollback correctamente) antes
 * de llegar al controlador, que la atrapa ya con la transacción cerrada.
 */
@Service
@RequiredArgsConstructor
public class CatalogoAdminService {

    private final FacultadRepository facultadRepo;
    private final CarreraRepository carreraRepo;
    private final ModalidadTitulacionRepository modalidadRepo;
    private final PeriodoAcademicoRepository periodoAcademicoRepo;
    private final AuditoriaService auditoriaService;

    /**
     * @param id id de la facultad a eliminar
     * @throws org.springframework.dao.DataIntegrityViolationException si hay carreras
     *         asociadas a la facultad
     */
    @Transactional
    public void eliminarFacultad(Integer id) {
        auditoriaService.marcarActorActual();
        facultadRepo.deleteById(id);
        facultadRepo.flush();
    }

    /**
     * @param id id de la carrera a eliminar
     * @throws org.springframework.dao.DataIntegrityViolationException si hay registros
     *         dependientes de la carrera
     */
    @Transactional
    public void eliminarCarrera(Integer id) {
        auditoriaService.marcarActorActual();
        carreraRepo.deleteById(id);
        carreraRepo.flush();
    }

    /**
     * @param id id de la modalidad de titulación a eliminar
     * @throws org.springframework.dao.DataIntegrityViolationException si hay registros
     *         dependientes de la modalidad
     */
    @Transactional
    public void eliminarModalidad(Short id) {
        auditoriaService.marcarActorActual();
        modalidadRepo.deleteById(id);
        modalidadRepo.flush();
    }

    /**
     * @param id id del periodo académico a eliminar
     * @throws org.springframework.dao.DataIntegrityViolationException si hay registros
     *         dependientes del periodo
     */
    @Transactional
    public void eliminarPeriodo(Integer id) {
        auditoriaService.marcarActorActual();
        periodoAcademicoRepo.deleteById(id);
        periodoAcademicoRepo.flush();
    }
}
