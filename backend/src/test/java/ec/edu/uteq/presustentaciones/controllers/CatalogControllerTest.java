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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * CatalogoController concentraba la mayor cantidad de ramas sin ejercitar de todo el
 * paquete de controladores (1 de 136 líneas cubiertas y 102 ramas en cero). Casi toda
 * esa complejidad son validaciones de entrada del CRUD de la estructura académica
 * (faculty, program, modality, período), que es exactamente el tipo de código donde
 * un fallo silencioso deja create catálogos inconsistentes.
 *
 * Se cubren las tres salidas de cada operación: éxito, rechazo por validación
 * (campos vacíos, duplicados, referencias inexistentes, rangos de fecha inválidos) y
 * el conflicto de integridad referencial al delete un catálogo que ya está en uso.
 */
@ExtendWith(MockitoExtension.class)
class CatalogControllerTest {

    @Mock private ModalityDegreeRepository modalityRepo;
    @Mock private AnnouncementDegreeRepository announcementRepo;
    @Mock private ResearchLineRepository researchLineRepo;
    @Mock private SubjectRepository subjectRepo;
    @Mock private ProgramRepository programRepo;
    @Mock private PeriodAcademicRepository periodAcademicRepo;
    @Mock private FacultyRepository facultyRepo;
    @Mock private AuditService auditService;
    @Mock private CatalogAdminService catalogAdminService;

    @InjectMocks
    private CatalogController controller;

    @SuppressWarnings("unchecked")
    private String errorOf(ResponseEntity<?> response) {
        return ((Map<String, String>) response.getBody()).get("error");
    }

    private SaveFacultyRequest facultyReq(String code, String nombre) {
        SaveFacultyRequest req = new SaveFacultyRequest();
        req.setCode(code);
        req.setNombre(nombre);
        return req;
    }

    private SaveProgramRequest programReq(String code, String nombre, Integer facultyId, String modality) {
        SaveProgramRequest req = new SaveProgramRequest();
        req.setCode(code);
        req.setNombre(nombre);
        req.setFacultyId(facultyId);
        req.setModalityEstudio(modality);
        return req;
    }

    private SaveModalityRequest modalityReq(String code, String nombre) {
        SaveModalityRequest req = new SaveModalityRequest();
        req.setCode(code);
        req.setNombre(nombre);
        return req;
    }

    private SavePeriodRequest periodReq(String code, String nombre, LocalDate start, LocalDate end, Boolean activo) {
        SavePeriodRequest req = new SavePeriodRequest();
        req.setCode(code);
        req.setNombre(nombre);
        req.setDateStart(start);
        req.setDateEnd(end);
        req.setActivo(activo);
        return req;
    }

    // ── Consultas de catálogo (abiertas a cualquier autenticado) ──────────────

    @Test
    void listModalitiesReturnsThatDeliveryRepository() {
        List<ModalityDegree> esperado = List.of(ModalityDegree.builder().id((short) 1).build());
        when(modalityRepo.findAll()).thenReturn(esperado);

        assertSame(esperado, controller.listModalities().getBody());
    }

    @Test
    void listLinesResearchReturnsThatDeliveryRepository() {
        List<ResearchLine> esperado = List.of(new ResearchLine());
        when(researchLineRepo.findAll()).thenReturn(esperado);

        assertSame(esperado, controller.listLinesResearch().getBody());
    }

    @Test
    void listSubjectsWithoutLineIdReturnsAll() {
        List<Subject> all = List.of(new Subject());
        when(subjectRepo.findAll()).thenReturn(all);

        assertSame(all, controller.listSubjects(null).getBody());
        verify(subjectRepo, never()).findByResearchLineId(any());
    }

    @Test
    void listSubjectsWithLineIdFiltersByThatLine() {
        List<Subject> filtradas = List.of(new Subject());
        when(subjectRepo.findByResearchLineId(7)).thenReturn(filtradas);

        assertSame(filtradas, controller.listSubjects(7).getBody());
        verify(subjectRepo, never()).findAll();
    }

    @Test
    void listAnnouncementsActiveReturnsOnlyActive() {
        List<AnnouncementDegree> active = List.of(AnnouncementDegree.builder().id(1).build());
        when(announcementRepo.findByActiveTrue()).thenReturn(active);

        assertSame(active, controller.listAnnouncementsActive().getBody());
    }

    @Test
    void announcementActiveReturnsAnnouncementWhenExists() {
        AnnouncementDegree active = AnnouncementDegree.builder().id(1).code("2026-1").build();
        when(announcementRepo.findFirstByActiveTrue()).thenReturn(Optional.of(active));

        assertSame(active, controller.announcementActive().getBody());
    }

    @Test
    void announcementActiveReturns200WithMessageWhenNoneExists() {
        when(announcementRepo.findFirstByActiveTrue()).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.announcementActive();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("No hay convocatoria activa", errorOf(response));
    }

    @Test
    void listProgramsAndPeriodsDelegateInRepositories() {
        when(programRepo.findAll()).thenReturn(List.of(Program.builder().id(1).build()));
        when(periodAcademicRepo.findAll()).thenReturn(List.of(PeriodAcademic.builder().id(1).build()));

        assertEquals(1, controller.listPrograms().getBody().size());
        assertEquals(1, controller.listPeriodsAcademic().getBody().size());
    }

    @Test
    void listFacultiesDelegatesInRepository() {
        when(facultyRepo.findAll()).thenReturn(List.of(Faculty.builder().id(1).build()));

        assertEquals(1, controller.listFaculties().getBody().size());
    }

    // ── Faculties ────────────────────────────────────────────────────────────

    @Test
    void createFacultyNormalizesCodeToUppercaseAndSaves() {
        when(facultyRepo.findByCode("FCI")).thenReturn(Optional.empty());
        when(facultyRepo.save(any(Faculty.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = controller.createFaculty(facultyReq("  fci  ", "  Ciencias de la Ingeniería  "));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Faculty guardada = (Faculty) response.getBody();
        assertEquals("FCI", guardada.getCode());
        assertEquals("Ciencias de la Ingeniería", guardada.getNombre());
        verify(auditService).markActorActual();
    }

    @Test
    void createFacultyRejectsCodeOrNameEmpty() {
        ResponseEntity<?> sinCode = controller.createFaculty(facultyReq("   ", "Ciencias"));
        ResponseEntity<?> sinNombre = controller.createFaculty(facultyReq("FCI", null));

        assertEquals(HttpStatus.BAD_REQUEST, sinCode.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, sinNombre.getStatusCode());
        assertEquals("Código y nombre son obligatorios.", errorOf(sinNombre));
        verify(facultyRepo, never()).save(any());
    }

    @Test
    void createFacultyRejectsCodeDuplicate() {
        when(facultyRepo.findByCode("FCI")).thenReturn(Optional.of(Faculty.builder().id(1).build()));

        ResponseEntity<?> response = controller.createFaculty(facultyReq("FCI", "Ciencias"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Ya existe una facultad con ese código.", errorOf(response));
        verify(facultyRepo, never()).save(any());
    }

    @Test
    void updateFacultyChangesName() {
        Faculty existing = Faculty.builder().id(1).code("FCI").nombre("Antiguo").build();
        when(facultyRepo.findById(1)).thenReturn(Optional.of(existing));
        when(facultyRepo.save(existing)).thenReturn(existing);

        ResponseEntity<?> response = controller.updateFaculty(1, facultyReq(null, " Nuevo nombre "));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Nuevo nombre", existing.getNombre());
    }

    @Test
    void updateFacultyNonexistentReturns404() {
        when(facultyRepo.findById(99)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND,
                controller.updateFaculty(99, facultyReq(null, "X")).getStatusCode());
    }

    @Test
    void updateFacultyRejectsNameEmpty() {
        when(facultyRepo.findById(1)).thenReturn(Optional.of(Faculty.builder().id(1).build()));

        ResponseEntity<?> response = controller.updateFaculty(1, facultyReq(null, "   "));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("El nombre no puede estar vacío.", errorOf(response));
        verify(facultyRepo, never()).save(any());
    }

    @Test
    void deleteFacultyReturns204WhenExists() {
        when(facultyRepo.existsById(1)).thenReturn(true);

        assertEquals(HttpStatus.NO_CONTENT, controller.deleteFaculty(1).getStatusCode());
        verify(catalogAdminService).deleteFaculty(1);
    }

    @Test
    void deleteFacultyNonexistentReturns404() {
        when(facultyRepo.existsById(99)).thenReturn(false);

        assertEquals(HttpStatus.NOT_FOUND, controller.deleteFaculty(99).getStatusCode());
        verify(catalogAdminService, never()).deleteFaculty(any());
    }

    @Test
    void deleteFacultyWithProgramsAssociatedReturnsErrorReadable() {
        when(facultyRepo.existsById(1)).thenReturn(true);
        doThrow(new DataIntegrityViolationException("FK")).when(catalogAdminService).deleteFaculty(1);

        ResponseEntity<?> response = controller.deleteFaculty(1);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(errorOf(response).contains("carreras u otros registros asociados"));
    }

    // ── Programs ──────────────────────────────────────────────────────────────

    @Test
    void createProgramSavesWithFacultyResolved() {
        Faculty faculty = Faculty.builder().id(2).build();
        when(programRepo.findByCode("SW")).thenReturn(Optional.empty());
        when(facultyRepo.findById(2)).thenReturn(Optional.of(faculty));
        when(programRepo.save(any(Program.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = controller.createProgram(programReq("sw", "Software", 2, "PRESENCIAL"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Program guardada = (Program) response.getBody();
        assertEquals("SW", guardada.getCode());
        assertSame(faculty, guardada.getFaculty());
        assertEquals("PRESENCIAL", guardada.getModalityEstudio());
    }

    @Test
    void createProgramRejectsFieldsRequiredMissing() {
        ResponseEntity<?> sinFaculty = controller.createProgram(programReq("SW", "Software", null, null));

        assertEquals(HttpStatus.BAD_REQUEST, sinFaculty.getStatusCode());
        assertEquals("Código, nombre y facultad son obligatorios.", errorOf(sinFaculty));
        verify(programRepo, never()).save(any());
    }

    @Test
    void createProgramRejectsCodeDuplicate() {
        when(programRepo.findByCode("SW")).thenReturn(Optional.of(Program.builder().id(1).build()));

        ResponseEntity<?> response = controller.createProgram(programReq("SW", "Software", 2, null));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Ya existe una carrera con ese código.", errorOf(response));
    }

    @Test
    void createProgramWithFacultyNonexistentIsRejected() {
        when(programRepo.findByCode("SW")).thenReturn(Optional.empty());
        when(facultyRepo.findById(99)).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.createProgram(programReq("SW", "Software", 99, null));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Facultad no encontrada.", errorOf(response));
        verify(programRepo, never()).save(any());
    }

    @Test
    void updateProgramChangesNameModalityAndFaculty() {
        Program existing = Program.builder().id(1).nombre("Antiguo").build();
        Faculty nuevaFaculty = Faculty.builder().id(3).build();
        when(programRepo.findById(1)).thenReturn(Optional.of(existing));
        when(facultyRepo.findById(3)).thenReturn(Optional.of(nuevaFaculty));
        when(programRepo.save(existing)).thenReturn(existing);

        ResponseEntity<?> response = controller.updateProgram(1, programReq(null, "Software", 3, "VIRTUAL"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Software", existing.getNombre());
        assertEquals("VIRTUAL", existing.getModalityEstudio());
        assertSame(nuevaFaculty, existing.getFaculty());
    }

    @Test
    void updateProgramWithoutModalityOrFacultyKeepsValuesPrevious() {
        Faculty facultyPrevia = Faculty.builder().id(1).build();
        Program existing = Program.builder().id(1).nombre("Antiguo")
                .modalityEstudio("PRESENCIAL").faculty(facultyPrevia).build();
        when(programRepo.findById(1)).thenReturn(Optional.of(existing));
        when(programRepo.save(existing)).thenReturn(existing);

        controller.updateProgram(1, programReq(null, "Software", null, null));

        assertEquals("PRESENCIAL", existing.getModalityEstudio());
        assertSame(facultyPrevia, existing.getFaculty());
        verify(facultyRepo, never()).findById(any());
    }

    @Test
    void updateProgramWithFacultyNonexistentIsRejected() {
        when(programRepo.findById(1)).thenReturn(Optional.of(Program.builder().id(1).build()));
        when(facultyRepo.findById(99)).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.updateProgram(1, programReq(null, "Software", 99, null));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Facultad no encontrada.", errorOf(response));
        verify(programRepo, never()).save(any());
    }

    @Test
    void updateProgramNonexistentReturns404AndNameEmptyIsRejected() {
        when(programRepo.findById(99)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND,
                controller.updateProgram(99, programReq(null, "X", null, null)).getStatusCode());

        when(programRepo.findById(1)).thenReturn(Optional.of(Program.builder().id(1).build()));
        ResponseEntity<?> empty = controller.updateProgram(1, programReq(null, null, null, null));
        assertEquals(HttpStatus.BAD_REQUEST, empty.getStatusCode());
        assertEquals("El nombre no puede estar vacío.", errorOf(empty));
    }

    @Test
    void deleteProgramCoversSuccessNotFoundAndIntegrity() {
        when(programRepo.existsById(1)).thenReturn(true);
        assertEquals(HttpStatus.NO_CONTENT, controller.deleteProgram(1).getStatusCode());

        when(programRepo.existsById(99)).thenReturn(false);
        assertEquals(HttpStatus.NOT_FOUND, controller.deleteProgram(99).getStatusCode());

        when(programRepo.existsById(2)).thenReturn(true);
        doThrow(new DataIntegrityViolationException("FK")).when(catalogAdminService).deleteProgram(2);
        ResponseEntity<?> conflicto = controller.deleteProgram(2);
        assertEquals(HttpStatus.BAD_REQUEST, conflicto.getStatusCode());
        assertTrue(errorOf(conflicto).contains("estudiantes u otros registros asociados"));
    }

    // ── Modalities ───────────────────────────────────────────────────────────

    @Test
    void createModalityReplacesSpacesByHyphenUnderInCode() {
        when(modalityRepo.findByCode("PROYECTO_DE_TITULACION")).thenReturn(Optional.empty());
        when(modalityRepo.save(any(ModalityDegree.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = controller.createModality(
                modalityReq(" proyecto de titulacion ", "Proyecto de Titulación"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("PROYECTO_DE_TITULACION", ((ModalityDegree) response.getBody()).getCode());
    }

    @Test
    void createModalityRejectsEmptyAndDuplicates() {
        ResponseEntity<?> vacia = controller.createModality(modalityReq(null, "Proyecto"));
        assertEquals(HttpStatus.BAD_REQUEST, vacia.getStatusCode());

        when(modalityRepo.findByCode("EXAMEN")).thenReturn(Optional.of(ModalityDegree.builder().id((short) 1).build()));
        ResponseEntity<?> duplicada = controller.createModality(modalityReq("examen", "Examen"));
        assertEquals(HttpStatus.BAD_REQUEST, duplicada.getStatusCode());
        assertEquals("Ya existe una modalidad con ese código.", errorOf(duplicada));
    }

    @Test
    void updateModalityCoversSuccessNotFoundAndNameEmpty() {
        ModalityDegree existing = ModalityDegree.builder().id((short) 1).nombre("Antiguo").build();
        when(modalityRepo.findById((short) 1)).thenReturn(Optional.of(existing));
        when(modalityRepo.save(existing)).thenReturn(existing);
        assertEquals(HttpStatus.OK, controller.updateModality((short) 1, modalityReq(null, "Nuevo")).getStatusCode());
        assertEquals("Nuevo", existing.getNombre());

        when(modalityRepo.findById((short) 99)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND,
                controller.updateModality((short) 99, modalityReq(null, "X")).getStatusCode());

        when(modalityRepo.findById((short) 2)).thenReturn(Optional.of(ModalityDegree.builder().id((short) 2).build()));
        assertEquals(HttpStatus.BAD_REQUEST,
                controller.updateModality((short) 2, modalityReq(null, "  ")).getStatusCode());
    }

    @Test
    void deleteModalityCoversSuccessNotFoundAndIntegrity() {
        when(modalityRepo.existsById((short) 1)).thenReturn(true);
        assertEquals(HttpStatus.NO_CONTENT, controller.deleteModality((short) 1).getStatusCode());

        when(modalityRepo.existsById((short) 99)).thenReturn(false);
        assertEquals(HttpStatus.NOT_FOUND, controller.deleteModality((short) 99).getStatusCode());

        when(modalityRepo.existsById((short) 2)).thenReturn(true);
        doThrow(new DataIntegrityViolationException("FK")).when(catalogAdminService).deleteModality((short) 2);
        ResponseEntity<?> conflicto = controller.deleteModality((short) 2);
        assertEquals(HttpStatus.BAD_REQUEST, conflicto.getStatusCode());
        assertTrue(errorOf(conflicto).contains("solicitudes u otros registros asociados"));
    }

    // ── Períodos académicos ───────────────────────────────────────────────────

    @Test
    void createPeriodSavesWithActiveExplicit() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 6, 30);
        when(periodAcademicRepo.findByCode("2026-1")).thenReturn(Optional.empty());
        when(periodAcademicRepo.save(any(PeriodAcademic.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = controller.createPeriod(
                periodReq("2026-1", "Primer semestre 2026", start, end, true));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        PeriodAcademic saved = (PeriodAcademic) response.getBody();
        assertEquals("2026-1", saved.getCode());
        assertTrue(saved.getActivo());
    }

    @Test
    void createPeriodWithActiveNullSavesAsInactive() {
        when(periodAcademicRepo.findByCode("2026-2")).thenReturn(Optional.empty());
        when(periodAcademicRepo.save(any(PeriodAcademic.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = controller.createPeriod(periodReq("2026-2", "Segundo",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31), null));

        assertFalse(((PeriodAcademic) response.getBody()).getActivo());
    }

    @Test
    void createPeriodRejectsFieldsMissingDatesInvalidAndDuplicates() {
        ResponseEntity<?> sinFechas = controller.createPeriod(periodReq("2026-1", "Primer", null, null, null));
        assertEquals(HttpStatus.BAD_REQUEST, sinFechas.getStatusCode());
        assertTrue(errorOf(sinFechas).contains("obligatorios"));

        LocalDate start = LocalDate.of(2026, 6, 30);
        LocalDate endAnterior = LocalDate.of(2026, 1, 1);
        ResponseEntity<?> fechasInvertidas = controller.createPeriod(
                periodReq("2026-1", "Primer", start, endAnterior, null));
        assertEquals(HttpStatus.BAD_REQUEST, fechasInvertidas.getStatusCode());
        assertEquals("La fecha de fin debe ser posterior a la fecha de inicio.", errorOf(fechasInvertidas));

        when(periodAcademicRepo.findByCode("2026-1")).thenReturn(Optional.of(PeriodAcademic.builder().id(1).build()));
        ResponseEntity<?> duplicado = controller.createPeriod(
                periodReq("2026-1", "Primer", endAnterior, start, null));
        assertEquals(HttpStatus.BAD_REQUEST, duplicado.getStatusCode());
        assertEquals("Ya existe un período académico con ese código.", errorOf(duplicado));
    }

    @Test
    void updatePeriodWithoutDatesNewKeepsExisting() {
        PeriodAcademic existing = PeriodAcademic.builder().id(1).nombre("Antiguo")
                .dateStart(LocalDate.of(2026, 1, 1)).dateEnd(LocalDate.of(2026, 6, 30))
                .activo(false).build();
        when(periodAcademicRepo.findById(1)).thenReturn(Optional.of(existing));
        when(periodAcademicRepo.save(existing)).thenReturn(existing);

        ResponseEntity<?> response = controller.updatePeriod(1,
                periodReq(null, "Nuevo nombre", null, null, true));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Nuevo nombre", existing.getNombre());
        assertEquals(LocalDate.of(2026, 1, 1), existing.getDateStart());
        assertEquals(LocalDate.of(2026, 6, 30), existing.getDateEnd());
        assertTrue(existing.getActivo());
    }

    @Test
    void updatePeriodWithRangeOfDatesInvalidIsRejected() {
        PeriodAcademic existing = PeriodAcademic.builder().id(1)
                .dateStart(LocalDate.of(2026, 1, 1)).dateEnd(LocalDate.of(2026, 6, 30)).build();
        when(periodAcademicRepo.findById(1)).thenReturn(Optional.of(existing));

        ResponseEntity<?> response = controller.updatePeriod(1,
                periodReq(null, "Nombre", LocalDate.of(2026, 12, 1), null, null));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("La fecha de fin debe ser posterior a la fecha de inicio.", errorOf(response));
        verify(periodAcademicRepo, never()).save(any());
    }

    @Test
    void updatePeriodNonexistentReturns404AndNameEmptyIsRejected() {
        when(periodAcademicRepo.findById(99)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND,
                controller.updatePeriod(99, periodReq(null, "X", null, null, null)).getStatusCode());

        when(periodAcademicRepo.findById(1)).thenReturn(Optional.of(PeriodAcademic.builder().id(1).build()));
        assertEquals(HttpStatus.BAD_REQUEST,
                controller.updatePeriod(1, periodReq(null, "   ", null, null, null)).getStatusCode());
    }

    @Test
    void deletePeriodCoversSuccessNotFoundAndIntegrity() {
        when(periodAcademicRepo.existsById(1)).thenReturn(true);
        assertEquals(HttpStatus.NO_CONTENT, controller.deletePeriod(1).getStatusCode());

        when(periodAcademicRepo.existsById(99)).thenReturn(false);
        assertEquals(HttpStatus.NOT_FOUND, controller.deletePeriod(99).getStatusCode());

        when(periodAcademicRepo.existsById(2)).thenReturn(true);
        doThrow(new DataIntegrityViolationException("FK")).when(catalogAdminService).deletePeriod(2);
        ResponseEntity<?> conflicto = controller.deletePeriod(2);
        assertEquals(HttpStatus.BAD_REQUEST, conflicto.getStatusCode());
        assertTrue(errorOf(conflicto).contains("estudiantes o convocatorias asociadas"));
    }
}
