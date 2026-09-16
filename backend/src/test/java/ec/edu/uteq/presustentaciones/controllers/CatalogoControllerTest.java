package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.dto.SaveProgramRequest;
import ec.edu.uteq.presustentaciones.dto.SaveFacultyRequest;
import ec.edu.uteq.presustentaciones.dto.SaveModalityRequest;
import ec.edu.uteq.presustentaciones.dto.SavePeriodRequest;
import ec.edu.uteq.presustentaciones.entities.AreaTematica;
import ec.edu.uteq.presustentaciones.entities.Program;
import ec.edu.uteq.presustentaciones.entities.AnnouncementTitulacion;
import ec.edu.uteq.presustentaciones.entities.Faculty;
import ec.edu.uteq.presustentaciones.entities.LineInvestigacion;
import ec.edu.uteq.presustentaciones.entities.ModalityTitulacion;
import ec.edu.uteq.presustentaciones.entities.PeriodAcademico;
import ec.edu.uteq.presustentaciones.repositories.AreaTematicaRepository;
import ec.edu.uteq.presustentaciones.repositories.ProgramRepository;
import ec.edu.uteq.presustentaciones.repositories.AnnouncementTitulacionRepository;
import ec.edu.uteq.presustentaciones.repositories.FacultyRepository;
import ec.edu.uteq.presustentaciones.repositories.LineInvestigacionRepository;
import ec.edu.uteq.presustentaciones.repositories.ModalityTitulacionRepository;
import ec.edu.uteq.presustentaciones.repositories.PeriodAcademicoRepository;
import ec.edu.uteq.presustentaciones.services.AuditService;
import ec.edu.uteq.presustentaciones.services.CatalogoAdminService;
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
class CatalogoControllerTest {

    @Mock private ModalityTitulacionRepository modalityRepo;
    @Mock private AnnouncementTitulacionRepository announcementRepo;
    @Mock private LineInvestigacionRepository lineInvestigacionRepo;
    @Mock private AreaTematicaRepository areaTematicaRepo;
    @Mock private ProgramRepository programRepo;
    @Mock private PeriodAcademicoRepository periodAcademicoRepo;
    @Mock private FacultyRepository facultyRepo;
    @Mock private AuditService auditService;
    @Mock private CatalogoAdminService catalogoAdminService;

    @InjectMocks
    private CatalogoController controller;

    @SuppressWarnings("unchecked")
    private String errorDe(ResponseEntity<?> response) {
        return ((Map<String, String>) response.getBody()).get("error");
    }

    private SaveFacultyRequest facultyReq(String codigo, String nombre) {
        SaveFacultyRequest req = new SaveFacultyRequest();
        req.setCodigo(codigo);
        req.setNombre(nombre);
        return req;
    }

    private SaveProgramRequest programReq(String codigo, String nombre, Integer facultyId, String modality) {
        SaveProgramRequest req = new SaveProgramRequest();
        req.setCodigo(codigo);
        req.setNombre(nombre);
        req.setFacultyId(facultyId);
        req.setModalityEstudio(modality);
        return req;
    }

    private SaveModalityRequest modalityReq(String codigo, String nombre) {
        SaveModalityRequest req = new SaveModalityRequest();
        req.setCodigo(codigo);
        req.setNombre(nombre);
        return req;
    }

    private SavePeriodRequest periodReq(String codigo, String nombre, LocalDate inicio, LocalDate fin, Boolean activo) {
        SavePeriodRequest req = new SavePeriodRequest();
        req.setCodigo(codigo);
        req.setNombre(nombre);
        req.setFechaInicio(inicio);
        req.setFechaFin(fin);
        req.setActivo(activo);
        return req;
    }

    // ── Consultas de catálogo (abiertas a cualquier autenticado) ──────────────

    @Test
    void listModalitiesDevuelveLoQueEntregaElRepositorio() {
        List<ModalityTitulacion> esperado = List.of(ModalityTitulacion.builder().id((short) 1).build());
        when(modalityRepo.findAll()).thenReturn(esperado);

        assertSame(esperado, controller.listModalities().getBody());
    }

    @Test
    void listLinesInvestigacionDevuelveLoQueEntregaElRepositorio() {
        List<LineInvestigacion> esperado = List.of(new LineInvestigacion());
        when(lineInvestigacionRepo.findAll()).thenReturn(esperado);

        assertSame(esperado, controller.listLinesInvestigacion().getBody());
    }

    @Test
    void listAreasTematicasSinLineIdDevuelveTodas() {
        List<AreaTematica> todas = List.of(new AreaTematica());
        when(areaTematicaRepo.findAll()).thenReturn(todas);

        assertSame(todas, controller.listAreasTematicas(null).getBody());
        verify(areaTematicaRepo, never()).findByLineInvestigacionId(any());
    }

    @Test
    void listAreasTematicasConLineIdFiltraPorEsaLine() {
        List<AreaTematica> filtradas = List.of(new AreaTematica());
        when(areaTematicaRepo.findByLineInvestigacionId(7)).thenReturn(filtradas);

        assertSame(filtradas, controller.listAreasTematicas(7).getBody());
        verify(areaTematicaRepo, never()).findAll();
    }

    @Test
    void listAnnouncementsActivasDevuelveSoloLasActivas() {
        List<AnnouncementTitulacion> activas = List.of(AnnouncementTitulacion.builder().id(1).build());
        when(announcementRepo.findByActivaTrue()).thenReturn(activas);

        assertSame(activas, controller.listAnnouncementsActivas().getBody());
    }

    @Test
    void announcementActivaDevuelveLaAnnouncementCuandoExiste() {
        AnnouncementTitulacion activa = AnnouncementTitulacion.builder().id(1).codigo("2026-1").build();
        when(announcementRepo.findFirstByActivaTrue()).thenReturn(Optional.of(activa));

        assertSame(activa, controller.announcementActiva().getBody());
    }

    @Test
    void announcementActivaDevuelve200ConMensajeCuandoNoHayNinguna() {
        when(announcementRepo.findFirstByActivaTrue()).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.announcementActiva();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("No hay convocatoria activa", errorDe(response));
    }

    @Test
    void listProgramsYPeriodsDeleganEnSusRepositorios() {
        when(programRepo.findAll()).thenReturn(List.of(Program.builder().id(1).build()));
        when(periodAcademicoRepo.findAll()).thenReturn(List.of(PeriodAcademico.builder().id(1).build()));

        assertEquals(1, controller.listPrograms().getBody().size());
        assertEquals(1, controller.listPeriodsAcademicos().getBody().size());
    }

    @Test
    void listFacultiesDelegaEnElRepositorio() {
        when(facultyRepo.findAll()).thenReturn(List.of(Faculty.builder().id(1).build()));

        assertEquals(1, controller.listFaculties().getBody().size());
    }

    // ── Faculties ────────────────────────────────────────────────────────────

    @Test
    void createFacultyNormalizaElCodigoAMayusculasYLoGuarda() {
        when(facultyRepo.findByCodigo("FCI")).thenReturn(Optional.empty());
        when(facultyRepo.save(any(Faculty.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = controller.createFaculty(facultyReq("  fci  ", "  Ciencias de la Ingeniería  "));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Faculty guardada = (Faculty) response.getBody();
        assertEquals("FCI", guardada.getCodigo());
        assertEquals("Ciencias de la Ingeniería", guardada.getNombre());
        verify(auditService).marcarActorActual();
    }

    @Test
    void createFacultyRechazaCodigoONombreVacios() {
        ResponseEntity<?> sinCodigo = controller.createFaculty(facultyReq("   ", "Ciencias"));
        ResponseEntity<?> sinNombre = controller.createFaculty(facultyReq("FCI", null));

        assertEquals(HttpStatus.BAD_REQUEST, sinCodigo.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, sinNombre.getStatusCode());
        assertEquals("Código y nombre son obligatorios.", errorDe(sinNombre));
        verify(facultyRepo, never()).save(any());
    }

    @Test
    void createFacultyRechazaCodigoDuplicado() {
        when(facultyRepo.findByCodigo("FCI")).thenReturn(Optional.of(Faculty.builder().id(1).build()));

        ResponseEntity<?> response = controller.createFaculty(facultyReq("FCI", "Ciencias"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Ya existe una facultad con ese código.", errorDe(response));
        verify(facultyRepo, never()).save(any());
    }

    @Test
    void updateFacultyCambiaElNombre() {
        Faculty existente = Faculty.builder().id(1).codigo("FCI").nombre("Antiguo").build();
        when(facultyRepo.findById(1)).thenReturn(Optional.of(existente));
        when(facultyRepo.save(existente)).thenReturn(existente);

        ResponseEntity<?> response = controller.updateFaculty(1, facultyReq(null, " Nuevo nombre "));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Nuevo nombre", existente.getNombre());
    }

    @Test
    void updateFacultyInexistenteDevuelve404() {
        when(facultyRepo.findById(99)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND,
                controller.updateFaculty(99, facultyReq(null, "X")).getStatusCode());
    }

    @Test
    void updateFacultyRechazaNombreVacio() {
        when(facultyRepo.findById(1)).thenReturn(Optional.of(Faculty.builder().id(1).build()));

        ResponseEntity<?> response = controller.updateFaculty(1, facultyReq(null, "   "));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("El nombre no puede estar vacío.", errorDe(response));
        verify(facultyRepo, never()).save(any());
    }

    @Test
    void deleteFacultyDevuelve204CuandoExiste() {
        when(facultyRepo.existsById(1)).thenReturn(true);

        assertEquals(HttpStatus.NO_CONTENT, controller.deleteFaculty(1).getStatusCode());
        verify(catalogoAdminService).deleteFaculty(1);
    }

    @Test
    void deleteFacultyInexistenteDevuelve404() {
        when(facultyRepo.existsById(99)).thenReturn(false);

        assertEquals(HttpStatus.NOT_FOUND, controller.deleteFaculty(99).getStatusCode());
        verify(catalogoAdminService, never()).deleteFaculty(any());
    }

    @Test
    void deleteFacultyConProgramsAsociadasDevuelveErrorLegible() {
        when(facultyRepo.existsById(1)).thenReturn(true);
        doThrow(new DataIntegrityViolationException("FK")).when(catalogoAdminService).deleteFaculty(1);

        ResponseEntity<?> response = controller.deleteFaculty(1);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(errorDe(response).contains("carreras u otros registros asociados"));
    }

    // ── Programs ──────────────────────────────────────────────────────────────

    @Test
    void createProgramGuardaConLaFacultyResuelta() {
        Faculty faculty = Faculty.builder().id(2).build();
        when(programRepo.findByCodigo("SW")).thenReturn(Optional.empty());
        when(facultyRepo.findById(2)).thenReturn(Optional.of(faculty));
        when(programRepo.save(any(Program.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = controller.createProgram(programReq("sw", "Software", 2, "PRESENCIAL"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Program guardada = (Program) response.getBody();
        assertEquals("SW", guardada.getCodigo());
        assertSame(faculty, guardada.getFaculty());
        assertEquals("PRESENCIAL", guardada.getModalityEstudio());
    }

    @Test
    void createProgramRechazaCamposObligatoriosFaltantes() {
        ResponseEntity<?> sinFaculty = controller.createProgram(programReq("SW", "Software", null, null));

        assertEquals(HttpStatus.BAD_REQUEST, sinFaculty.getStatusCode());
        assertEquals("Código, nombre y facultad son obligatorios.", errorDe(sinFaculty));
        verify(programRepo, never()).save(any());
    }

    @Test
    void createProgramRechazaCodigoDuplicado() {
        when(programRepo.findByCodigo("SW")).thenReturn(Optional.of(Program.builder().id(1).build()));

        ResponseEntity<?> response = controller.createProgram(programReq("SW", "Software", 2, null));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Ya existe una carrera con ese código.", errorDe(response));
    }

    @Test
    void createProgramConFacultyInexistenteEsRechazada() {
        when(programRepo.findByCodigo("SW")).thenReturn(Optional.empty());
        when(facultyRepo.findById(99)).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.createProgram(programReq("SW", "Software", 99, null));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Facultad no encontrada.", errorDe(response));
        verify(programRepo, never()).save(any());
    }

    @Test
    void updateProgramCambiaNombreModalityYFaculty() {
        Program existente = Program.builder().id(1).nombre("Antiguo").build();
        Faculty nuevaFaculty = Faculty.builder().id(3).build();
        when(programRepo.findById(1)).thenReturn(Optional.of(existente));
        when(facultyRepo.findById(3)).thenReturn(Optional.of(nuevaFaculty));
        when(programRepo.save(existente)).thenReturn(existente);

        ResponseEntity<?> response = controller.updateProgram(1, programReq(null, "Software", 3, "VIRTUAL"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Software", existente.getNombre());
        assertEquals("VIRTUAL", existente.getModalityEstudio());
        assertSame(nuevaFaculty, existente.getFaculty());
    }

    @Test
    void updateProgramSinModalityNiFacultyConservaLosValoresPrevios() {
        Faculty facultyPrevia = Faculty.builder().id(1).build();
        Program existente = Program.builder().id(1).nombre("Antiguo")
                .modalityEstudio("PRESENCIAL").faculty(facultyPrevia).build();
        when(programRepo.findById(1)).thenReturn(Optional.of(existente));
        when(programRepo.save(existente)).thenReturn(existente);

        controller.updateProgram(1, programReq(null, "Software", null, null));

        assertEquals("PRESENCIAL", existente.getModalityEstudio());
        assertSame(facultyPrevia, existente.getFaculty());
        verify(facultyRepo, never()).findById(any());
    }

    @Test
    void updateProgramConFacultyInexistenteEsRechazada() {
        when(programRepo.findById(1)).thenReturn(Optional.of(Program.builder().id(1).build()));
        when(facultyRepo.findById(99)).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.updateProgram(1, programReq(null, "Software", 99, null));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Facultad no encontrada.", errorDe(response));
        verify(programRepo, never()).save(any());
    }

    @Test
    void updateProgramInexistenteDevuelve404YNombreVacioEsRechazado() {
        when(programRepo.findById(99)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND,
                controller.updateProgram(99, programReq(null, "X", null, null)).getStatusCode());

        when(programRepo.findById(1)).thenReturn(Optional.of(Program.builder().id(1).build()));
        ResponseEntity<?> vacio = controller.updateProgram(1, programReq(null, null, null, null));
        assertEquals(HttpStatus.BAD_REQUEST, vacio.getStatusCode());
        assertEquals("El nombre no puede estar vacío.", errorDe(vacio));
    }

    @Test
    void deleteProgramCubreExitoNoEncontradaEIntegridad() {
        when(programRepo.existsById(1)).thenReturn(true);
        assertEquals(HttpStatus.NO_CONTENT, controller.deleteProgram(1).getStatusCode());

        when(programRepo.existsById(99)).thenReturn(false);
        assertEquals(HttpStatus.NOT_FOUND, controller.deleteProgram(99).getStatusCode());

        when(programRepo.existsById(2)).thenReturn(true);
        doThrow(new DataIntegrityViolationException("FK")).when(catalogoAdminService).deleteProgram(2);
        ResponseEntity<?> conflicto = controller.deleteProgram(2);
        assertEquals(HttpStatus.BAD_REQUEST, conflicto.getStatusCode());
        assertTrue(errorDe(conflicto).contains("estudiantes u otros registros asociados"));
    }

    // ── Modalities ───────────────────────────────────────────────────────────

    @Test
    void createModalityReemplazaEspaciosPorGuionBajoEnElCodigo() {
        when(modalityRepo.findByCodigo("PROYECTO_DE_TITULACION")).thenReturn(Optional.empty());
        when(modalityRepo.save(any(ModalityTitulacion.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = controller.createModality(
                modalityReq(" proyecto de titulacion ", "Proyecto de Titulación"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("PROYECTO_DE_TITULACION", ((ModalityTitulacion) response.getBody()).getCodigo());
    }

    @Test
    void createModalityRechazaVaciosYDuplicados() {
        ResponseEntity<?> vacia = controller.createModality(modalityReq(null, "Proyecto"));
        assertEquals(HttpStatus.BAD_REQUEST, vacia.getStatusCode());

        when(modalityRepo.findByCodigo("EXAMEN")).thenReturn(Optional.of(ModalityTitulacion.builder().id((short) 1).build()));
        ResponseEntity<?> duplicada = controller.createModality(modalityReq("examen", "Examen"));
        assertEquals(HttpStatus.BAD_REQUEST, duplicada.getStatusCode());
        assertEquals("Ya existe una modalidad con ese código.", errorDe(duplicada));
    }

    @Test
    void updateModalityCubreExitoNoEncontradaYNombreVacio() {
        ModalityTitulacion existente = ModalityTitulacion.builder().id((short) 1).nombre("Antiguo").build();
        when(modalityRepo.findById((short) 1)).thenReturn(Optional.of(existente));
        when(modalityRepo.save(existente)).thenReturn(existente);
        assertEquals(HttpStatus.OK, controller.updateModality((short) 1, modalityReq(null, "Nuevo")).getStatusCode());
        assertEquals("Nuevo", existente.getNombre());

        when(modalityRepo.findById((short) 99)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND,
                controller.updateModality((short) 99, modalityReq(null, "X")).getStatusCode());

        when(modalityRepo.findById((short) 2)).thenReturn(Optional.of(ModalityTitulacion.builder().id((short) 2).build()));
        assertEquals(HttpStatus.BAD_REQUEST,
                controller.updateModality((short) 2, modalityReq(null, "  ")).getStatusCode());
    }

    @Test
    void deleteModalityCubreExitoNoEncontradaEIntegridad() {
        when(modalityRepo.existsById((short) 1)).thenReturn(true);
        assertEquals(HttpStatus.NO_CONTENT, controller.deleteModality((short) 1).getStatusCode());

        when(modalityRepo.existsById((short) 99)).thenReturn(false);
        assertEquals(HttpStatus.NOT_FOUND, controller.deleteModality((short) 99).getStatusCode());

        when(modalityRepo.existsById((short) 2)).thenReturn(true);
        doThrow(new DataIntegrityViolationException("FK")).when(catalogoAdminService).deleteModality((short) 2);
        ResponseEntity<?> conflicto = controller.deleteModality((short) 2);
        assertEquals(HttpStatus.BAD_REQUEST, conflicto.getStatusCode());
        assertTrue(errorDe(conflicto).contains("solicitudes u otros registros asociados"));
    }

    // ── Períodos académicos ───────────────────────────────────────────────────

    @Test
    void createPeriodGuardaConActivoExplicito() {
        LocalDate inicio = LocalDate.of(2026, 1, 1);
        LocalDate fin = LocalDate.of(2026, 6, 30);
        when(periodAcademicoRepo.findByCodigo("2026-1")).thenReturn(Optional.empty());
        when(periodAcademicoRepo.save(any(PeriodAcademico.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = controller.createPeriod(
                periodReq("2026-1", "Primer semestre 2026", inicio, fin, true));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        PeriodAcademico guardado = (PeriodAcademico) response.getBody();
        assertEquals("2026-1", guardado.getCodigo());
        assertTrue(guardado.getActivo());
    }

    @Test
    void createPeriodConActivoNuloLoGuardaComoInactivo() {
        when(periodAcademicoRepo.findByCodigo("2026-2")).thenReturn(Optional.empty());
        when(periodAcademicoRepo.save(any(PeriodAcademico.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = controller.createPeriod(periodReq("2026-2", "Segundo",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31), null));

        assertFalse(((PeriodAcademico) response.getBody()).getActivo());
    }

    @Test
    void createPeriodRechazaCamposFaltantesFechasInvalidasYDuplicados() {
        ResponseEntity<?> sinFechas = controller.createPeriod(periodReq("2026-1", "Primer", null, null, null));
        assertEquals(HttpStatus.BAD_REQUEST, sinFechas.getStatusCode());
        assertTrue(errorDe(sinFechas).contains("obligatorios"));

        LocalDate inicio = LocalDate.of(2026, 6, 30);
        LocalDate finAnterior = LocalDate.of(2026, 1, 1);
        ResponseEntity<?> fechasInvertidas = controller.createPeriod(
                periodReq("2026-1", "Primer", inicio, finAnterior, null));
        assertEquals(HttpStatus.BAD_REQUEST, fechasInvertidas.getStatusCode());
        assertEquals("La fecha de fin debe ser posterior a la fecha de inicio.", errorDe(fechasInvertidas));

        when(periodAcademicoRepo.findByCodigo("2026-1")).thenReturn(Optional.of(PeriodAcademico.builder().id(1).build()));
        ResponseEntity<?> duplicado = controller.createPeriod(
                periodReq("2026-1", "Primer", finAnterior, inicio, null));
        assertEquals(HttpStatus.BAD_REQUEST, duplicado.getStatusCode());
        assertEquals("Ya existe un período académico con ese código.", errorDe(duplicado));
    }

    @Test
    void updatePeriodSinFechasNuevasConservaLasExistentes() {
        PeriodAcademico existente = PeriodAcademico.builder().id(1).nombre("Antiguo")
                .fechaInicio(LocalDate.of(2026, 1, 1)).fechaFin(LocalDate.of(2026, 6, 30))
                .activo(false).build();
        when(periodAcademicoRepo.findById(1)).thenReturn(Optional.of(existente));
        when(periodAcademicoRepo.save(existente)).thenReturn(existente);

        ResponseEntity<?> response = controller.updatePeriod(1,
                periodReq(null, "Nuevo nombre", null, null, true));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Nuevo nombre", existente.getNombre());
        assertEquals(LocalDate.of(2026, 1, 1), existente.getFechaInicio());
        assertEquals(LocalDate.of(2026, 6, 30), existente.getFechaFin());
        assertTrue(existente.getActivo());
    }

    @Test
    void updatePeriodConRangoDeFechasInvalidoEsRechazado() {
        PeriodAcademico existente = PeriodAcademico.builder().id(1)
                .fechaInicio(LocalDate.of(2026, 1, 1)).fechaFin(LocalDate.of(2026, 6, 30)).build();
        when(periodAcademicoRepo.findById(1)).thenReturn(Optional.of(existente));

        ResponseEntity<?> response = controller.updatePeriod(1,
                periodReq(null, "Nombre", LocalDate.of(2026, 12, 1), null, null));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("La fecha de fin debe ser posterior a la fecha de inicio.", errorDe(response));
        verify(periodAcademicoRepo, never()).save(any());
    }

    @Test
    void updatePeriodInexistenteDevuelve404YNombreVacioEsRechazado() {
        when(periodAcademicoRepo.findById(99)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND,
                controller.updatePeriod(99, periodReq(null, "X", null, null, null)).getStatusCode());

        when(periodAcademicoRepo.findById(1)).thenReturn(Optional.of(PeriodAcademico.builder().id(1).build()));
        assertEquals(HttpStatus.BAD_REQUEST,
                controller.updatePeriod(1, periodReq(null, "   ", null, null, null)).getStatusCode());
    }

    @Test
    void deletePeriodCubreExitoNoEncontradoEIntegridad() {
        when(periodAcademicoRepo.existsById(1)).thenReturn(true);
        assertEquals(HttpStatus.NO_CONTENT, controller.deletePeriod(1).getStatusCode());

        when(periodAcademicoRepo.existsById(99)).thenReturn(false);
        assertEquals(HttpStatus.NOT_FOUND, controller.deletePeriod(99).getStatusCode());

        when(periodAcademicoRepo.existsById(2)).thenReturn(true);
        doThrow(new DataIntegrityViolationException("FK")).when(catalogoAdminService).deletePeriod(2);
        ResponseEntity<?> conflicto = controller.deletePeriod(2);
        assertEquals(HttpStatus.BAD_REQUEST, conflicto.getStatusCode());
        assertTrue(errorDe(conflicto).contains("estudiantes o convocatorias asociadas"));
    }
}
