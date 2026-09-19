package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.dto.SaveProgramRequest;
import ec.edu.uteq.presustentaciones.dto.SaveFacultyRequest;
import ec.edu.uteq.presustentaciones.dto.SaveModalityRequest;
import ec.edu.uteq.presustentaciones.dto.SavePeriodRequest;
import ec.edu.uteq.presustentaciones.entities.Subject;
import ec.edu.uteq.presustentaciones.entities.Program;
import ec.edu.uteq.presustentaciones.entities.AnnouncementDegree;
import ec.edu.uteq.presustentaciones.entities.Faculty;
import ec.edu.uteq.presustentaciones.entities.ResearchLine;
import ec.edu.uteq.presustentaciones.entities.ModalityDegree;
import ec.edu.uteq.presustentaciones.entities.PeriodAcademic;
import ec.edu.uteq.presustentaciones.repositories.SubjectRepository;
import ec.edu.uteq.presustentaciones.repositories.ProgramRepository;
import ec.edu.uteq.presustentaciones.repositories.AnnouncementDegreeRepository;
import ec.edu.uteq.presustentaciones.repositories.FacultyRepository;
import ec.edu.uteq.presustentaciones.repositories.ResearchLineRepository;
import ec.edu.uteq.presustentaciones.repositories.ModalityDegreeRepository;
import ec.edu.uteq.presustentaciones.repositories.PeriodAcademicRepository;
import ec.edu.uteq.presustentaciones.services.AuditService;
import ec.edu.uteq.presustentaciones.services.CatalogAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Controlador REST de catalog.
 */
@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/catalogos")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class CatalogController {

    private static final String PERMISO_GESTIONAR = "@permissionService.hasPermission(authentication, 'CARRERAS_GESTIONAR')";

    private final ModalityDegreeRepository modalityRepo;
    private final AnnouncementDegreeRepository announcementRepo;
    private final ResearchLineRepository researchLineRepo;
    private final SubjectRepository subjectRepo;
    private final ProgramRepository programRepo;
    private final PeriodAcademicRepository periodAcademicRepo;
    private final FacultyRepository facultyRepo;
    private final AuditService auditService;
    private final CatalogAdminService catalogAdminService;

    /**
     * List modalities.
     * @return 200 con todas las modalities de titulación disponibles para elegir al create
     *         una submission
     */
    @GetMapping("/modalidades")
    public ResponseEntity<List<ModalityDegree>> listModalities() {
        return ResponseEntity.ok(modalityRepo.findAll());
    }

    /**
     * List lines research.
     * @return 200 con las líneas de investigación institucionales
     */
    @GetMapping("/lineas-investigacion")
    public ResponseEntity<List<ResearchLine>> listLinesResearch() {
        return ResponseEntity.ok(researchLineRepo.findAll());
    }

    /**
     * Lista las áreas temáticas. Si se pasa lineId, filtra solo las de esa línea
     * (uso típico: poblar el segundo dropdown dependiente del formulario de registro de topic).
     *
     * @param lineId línea de investigación por la que filtrar; si es null devuelve todas
     * @return 200 con las áreas temáticas correspondientes
     */
    @GetMapping("/areas-tematicas")
    public ResponseEntity<List<Subject>> listSubjects(
            @RequestParam(name = "lineaId", required = false) Integer lineId) {
        if (lineId != null) {
            return ResponseEntity.ok(subjectRepo.findByResearchLineId(lineId));
        }
        return ResponseEntity.ok(subjectRepo.findAll());
    }

    /**
     * List announcements active.
     * @return 200 con las announcements de titulación marcadas como activas
     */
    @GetMapping("/convocatorias")
    public ResponseEntity<List<AnnouncementDegree>> listAnnouncementsActive() {
        return ResponseEntity.ok(announcementRepo.findByActiveTrue());
    }

    /**
     * Announcement vigente, para autocompletar el formulario de submission.
     *
     * @return 200 con la announcement activa; si no hay ninguna devuelve igualmente 200 con
     *         un mensaje de error en el cuerpo, no un 404, para que el formulario pueda
     *         mostrar el aviso sin tratarlo como fallo de red
     */
    @GetMapping("/convocatoria-activa")
    public ResponseEntity<?> announcementActive() {
        return announcementRepo.findFirstByActiveTrue()
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.ok(Map.of("error", "No hay convocatoria activa")));
    }

    /**
     * List programs.
     * @return 200 con todas las programs; lo usa Gestión de Students para elegir la
     *         program al register o editar un student
     */
    @GetMapping("/carreras")
    public ResponseEntity<List<Program>> listPrograms() {
        return ResponseEntity.ok(programRepo.findAll());
    }

    /**
     * List periods academic.
     * @return 200 con todos los períodos académicos; lo usa Gestión de Students para
     *         assign el período de ingreso
     */
    @GetMapping("/periodos-academicos")
    public ResponseEntity<List<PeriodAcademic>> listPeriodsAcademic() {
        return ResponseEntity.ok(periodAcademicRepo.findAll());
    }

    // ── Gestión de Programs (CARRERAS_GESTIONAR, solo ADMIN) ──────────────────────
    // CRUD de la estructura académica base: faculties, programs, modalities de
    // titulación y períodos académicos. Los GET de arriba quedan abiertos a cualquier
    // autenticado (@PreAuthorize de clase); estos métodos lo sobrescriben con el
    // permission dedicado porque en Spring Security el @PreAuthorize de método reemplaza
    // -- no combina con -- el de clase.

    /**
     * List faculties.
     * @return 200 con todas las faculties
     */
    @GetMapping("/facultades")
    public ResponseEntity<List<Faculty>> listFaculties() {
        return ResponseEntity.ok(facultyRepo.findAll());
    }

    /**
     * Crea una faculty. El código se normaliza a mayúsculas sin espacios sobrantes.
     *
     * @param req código y nombre de la faculty; ambos obligatorios
     * @return 200 con la faculty creada, o 400 si falta algún campo o el código ya existe
     */
    @PostMapping("/facultades")
    @PreAuthorize(PERMISO_GESTIONAR)
    @Transactional
    public ResponseEntity<?> createFaculty(@RequestBody SaveFacultyRequest req) {
        String code = req.getCode() == null ? "" : req.getCode().trim().toUpperCase();
        String nombre = req.getNombre() == null ? "" : req.getNombre().trim();
        if (code.isEmpty() || nombre.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Código y nombre son obligatorios."));
        }
        if (facultyRepo.findByCode(code).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Ya existe una facultad con ese código."));
        }
        auditService.markActorActual();
        Faculty faculty = facultyRepo.save(Faculty.builder().code(code).nombre(nombre).build());
        return ResponseEntity.ok(faculty);
    }

    /**
     * Renombra una faculty. El código no se modifica para no dejar huérfanas las
     * referencias existentes.
     *
     * @param id  faculty a update
     * @param req nuevo nombre
     * @return 200 con la faculty actualizada, 404 si no existe, o 400 si el nombre viene vacío
     */
    @PutMapping("/facultades/{id}")
    @PreAuthorize(PERMISO_GESTIONAR)
    @Transactional
    public ResponseEntity<?> updateFaculty(@PathVariable("id") Integer id, @RequestBody SaveFacultyRequest req) {
        auditService.markActorActual();
        Faculty faculty = facultyRepo.findById(id).orElse(null);
        if (faculty == null) {
            return ResponseEntity.notFound().build();
        }
        String nombre = req.getNombre() == null ? "" : req.getNombre().trim();
        if (nombre.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El nombre no puede estar vacío."));
        }
        faculty.setNombre(nombre);
        return ResponseEntity.ok(facultyRepo.save(faculty));
    }

    /**
     * Elimina una faculty.
     *
     * @param id faculty a delete
     * @return 204 si se eliminó, 404 si no existe, o 400 si tiene programs u otros registros
     *         asociados que impiden el borrado
     */
    @DeleteMapping("/facultades/{id}")
    @PreAuthorize(PERMISO_GESTIONAR)
    public ResponseEntity<?> deleteFaculty(@PathVariable("id") Integer id) {
        if (!facultyRepo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        try {
            catalogAdminService.deleteFaculty(id);
            return ResponseEntity.noContent().build();
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "No se pudo eliminar: hay carreras u otros registros asociados a esta facultad."));
        }
    }

    /**
     * Crea una program dentro de una faculty existente.
     *
     * @param req código, nombre, facultyId y modality de estudio; los tres primeros obligatorios
     * @return 200 con la program creada, o 400 si falta un campo, el código ya existe o la
     *         faculty indicada no existe
     */
    @PostMapping("/carreras")
    @PreAuthorize(PERMISO_GESTIONAR)
    @Transactional
    public ResponseEntity<?> createProgram(@RequestBody SaveProgramRequest req) {
        String code = req.getCode() == null ? "" : req.getCode().trim().toUpperCase();
        String nombre = req.getNombre() == null ? "" : req.getNombre().trim();
        if (code.isEmpty() || nombre.isEmpty() || req.getFacultyId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Código, nombre y facultad son obligatorios."));
        }
        if (programRepo.findByCode(code).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Ya existe una carrera con ese código."));
        }
        Faculty faculty = facultyRepo.findById(req.getFacultyId()).orElse(null);
        if (faculty == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Facultad no encontrada."));
        }
        auditService.markActorActual();
        Program program = programRepo.save(Program.builder()
                .code(code).nombre(nombre).faculty(faculty)
                .modalityEstudio(req.getModalityEstudio())
                .build());
        return ResponseEntity.ok(program);
    }

    /**
     * Actualiza una program. La modality y la faculty sólo se tocan si vienen en el cuerpo;
     * el código nunca se modifica.
     *
     * @param id  program a update
     * @param req nombre (obligatorio) y, opcionalmente, modality de estudio y facultyId
     * @return 200 con la program actualizada, 404 si no existe, o 400 si el nombre viene
     *         vacío o la faculty indicada no existe
     */
    @PutMapping("/carreras/{id}")
    @PreAuthorize(PERMISO_GESTIONAR)
    @Transactional
    public ResponseEntity<?> updateProgram(@PathVariable("id") Integer id, @RequestBody SaveProgramRequest req) {
        auditService.markActorActual();
        Program program = programRepo.findById(id).orElse(null);
        if (program == null) {
            return ResponseEntity.notFound().build();
        }
        String nombre = req.getNombre() == null ? "" : req.getNombre().trim();
        if (nombre.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El nombre no puede estar vacío."));
        }
        program.setNombre(nombre);
        if (req.getModalityEstudio() != null) {
            program.setModalityEstudio(req.getModalityEstudio());
        }
        if (req.getFacultyId() != null) {
            Faculty faculty = facultyRepo.findById(req.getFacultyId()).orElse(null);
            if (faculty == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Facultad no encontrada."));
            }
            program.setFaculty(faculty);
        }
        return ResponseEntity.ok(programRepo.save(program));
    }

    /**
     * Elimina una program.
     *
     * @param id program a delete
     * @return 204 si se eliminó, 404 si no existe, o 400 si tiene students u otros
     *         registros asociados
     */
    @DeleteMapping("/carreras/{id}")
    @PreAuthorize(PERMISO_GESTIONAR)
    public ResponseEntity<?> deleteProgram(@PathVariable("id") Integer id) {
        if (!programRepo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        try {
            catalogAdminService.deleteProgram(id);
            return ResponseEntity.noContent().build();
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "No se pudo eliminar: hay estudiantes u otros registros asociados a esta carrera."));
        }
    }

    /**
     * Crea una modality de titulación. El código se normaliza a mayúsculas y los espacios
     * internos se sustituyen por guiones bajos.
     *
     * @param req código y nombre; ambos obligatorios
     * @return 200 con la modality creada, o 400 si falta un campo o el código ya existe
     */
    @PostMapping("/modalidades")
    @PreAuthorize(PERMISO_GESTIONAR)
    @Transactional
    public ResponseEntity<?> createModality(@RequestBody SaveModalityRequest req) {
        String code = req.getCode() == null ? "" : req.getCode().trim().toUpperCase().replaceAll("\\s+", "_");
        String nombre = req.getNombre() == null ? "" : req.getNombre().trim();
        if (code.isEmpty() || nombre.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Código y nombre son obligatorios."));
        }
        if (modalityRepo.findByCode(code).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Ya existe una modalidad con ese código."));
        }
        auditService.markActorActual();
        ModalityDegree modality = modalityRepo.save(ModalityDegree.builder().code(code).nombre(nombre).build());
        return ResponseEntity.ok(modality);
    }

    /**
     * Renombra una modality, sin tocar su código.
     *
     * @param id  modality a update
     * @param req nuevo nombre
     * @return 200 con la modality actualizada, 404 si no existe, o 400 si el nombre viene vacío
     */
    @PutMapping("/modalidades/{id}")
    @PreAuthorize(PERMISO_GESTIONAR)
    @Transactional
    public ResponseEntity<?> updateModality(@PathVariable("id") Short id, @RequestBody SaveModalityRequest req) {
        auditService.markActorActual();
        ModalityDegree modality = modalityRepo.findById(id).orElse(null);
        if (modality == null) {
            return ResponseEntity.notFound().build();
        }
        String nombre = req.getNombre() == null ? "" : req.getNombre().trim();
        if (nombre.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El nombre no puede estar vacío."));
        }
        modality.setNombre(nombre);
        return ResponseEntity.ok(modalityRepo.save(modality));
    }

    /**
     * Elimina una modality de titulación.
     *
     * @param id modality a delete
     * @return 204 si se eliminó, 404 si no existe, o 400 si hay submissions asociadas
     */
    @DeleteMapping("/modalidades/{id}")
    @PreAuthorize(PERMISO_GESTIONAR)
    public ResponseEntity<?> deleteModality(@PathVariable("id") Short id) {
        if (!modalityRepo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        try {
            catalogAdminService.deleteModality(id);
            return ResponseEntity.noContent().build();
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "No se pudo eliminar: hay solicitudes u otros registros asociados a esta modalidad."));
        }
    }

    /**
     * Crea un período académico validando que el rango de fechas tenga sentido.
     *
     * @param req código, nombre, fecha de inicio y fecha de fin (obligatorios) más el
     *            indicador de activo (si no viene, el período se crea inactivo)
     * @return 200 con el período creado, o 400 si falta un campo, si la fecha de fin no es
     *         posterior a la de inicio, o si el código ya existe
     */
    @PostMapping("/periodos-academicos")
    @PreAuthorize(PERMISO_GESTIONAR)
    @Transactional
    public ResponseEntity<?> createPeriod(@RequestBody SavePeriodRequest req) {
        String code = req.getCode() == null ? "" : req.getCode().trim().toUpperCase();
        String nombre = req.getNombre() == null ? "" : req.getNombre().trim();
        if (code.isEmpty() || nombre.isEmpty() || req.getDateStart() == null || req.getDateEnd() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Código, nombre, fecha de inicio y fecha de fin son obligatorios."));
        }
        if (!req.getDateEnd().isAfter(req.getDateStart())) {
            return ResponseEntity.badRequest().body(Map.of("error", "La fecha de fin debe ser posterior a la fecha de inicio."));
        }
        if (periodAcademicRepo.findByCode(code).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Ya existe un período académico con ese código."));
        }
        auditService.markActorActual();
        PeriodAcademic period = periodAcademicRepo.save(PeriodAcademic.builder()
                .code(code).nombre(nombre)
                .dateStart(req.getDateStart()).dateEnd(req.getDateEnd())
                .activo(req.getActivo() != null && req.getActivo())
                .build());
        return ResponseEntity.ok(period);
    }

    /**
     * Actualiza un período académico. Las fechas que no vengan en el cuerpo conservan su
     * valor actual, y el rango resultante se vuelve a validate.
     *
     * @param id  período a update
     * @param req nombre (obligatorio) y, opcionalmente, fechas y estado activo
     * @return 200 con el período actualizado, 404 si no existe, o 400 si el nombre viene
     *         vacío o el rango de fechas resultante es inválido
     */
    @PutMapping("/periodos-academicos/{id}")
    @PreAuthorize(PERMISO_GESTIONAR)
    @Transactional
    public ResponseEntity<?> updatePeriod(@PathVariable("id") Integer id, @RequestBody SavePeriodRequest req) {
        auditService.markActorActual();
        PeriodAcademic period = periodAcademicRepo.findById(id).orElse(null);
        if (period == null) {
            return ResponseEntity.notFound().build();
        }
        String nombre = req.getNombre() == null ? "" : req.getNombre().trim();
        if (nombre.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El nombre no puede estar vacío."));
        }
        LocalDate start = req.getDateStart() != null ? req.getDateStart() : period.getDateStart();
        LocalDate end = req.getDateEnd() != null ? req.getDateEnd() : period.getDateEnd();
        if (!end.isAfter(start)) {
            return ResponseEntity.badRequest().body(Map.of("error", "La fecha de fin debe ser posterior a la fecha de inicio."));
        }
        period.setNombre(nombre);
        period.setDateStart(start);
        period.setDateEnd(end);
        if (req.getActivo() != null) {
            period.setActivo(req.getActivo());
        }
        return ResponseEntity.ok(periodAcademicRepo.save(period));
    }

    /**
     * Elimina un período académico.
     *
     * @param id período a delete
     * @return 204 si se eliminó, 404 si no existe, o 400 si hay students o announcements
     *         asociadas
     */
    @DeleteMapping("/periodos-academicos/{id}")
    @PreAuthorize(PERMISO_GESTIONAR)
    public ResponseEntity<?> deletePeriod(@PathVariable("id") Integer id) {
        if (!periodAcademicRepo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        try {
            catalogAdminService.deletePeriod(id);
            return ResponseEntity.noContent().build();
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "No se pudo eliminar: hay estudiantes o convocatorias asociadas a este período."));
        }
    }
}
