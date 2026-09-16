package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.GenerateTopicRequest;
import ec.edu.uteq.presustentaciones.dto.SaveTopicPropuestoRequest;
import ec.edu.uteq.presustentaciones.dto.TopicPropuestoDTO;
import ec.edu.uteq.presustentaciones.entities.AreaTematica;
import ec.edu.uteq.presustentaciones.entities.Program;
import ec.edu.uteq.presustentaciones.entities.Student;
import ec.edu.uteq.presustentaciones.entities.LineInvestigacion;
import ec.edu.uteq.presustentaciones.entities.TopicGuardadoStudent;
import ec.edu.uteq.presustentaciones.entities.TopicPropuesto;
import ec.edu.uteq.presustentaciones.repositories.AreaTematicaRepository;
import ec.edu.uteq.presustentaciones.repositories.ProgramRepository;
import ec.edu.uteq.presustentaciones.repositories.StudentRepository;
import ec.edu.uteq.presustentaciones.repositories.LineInvestigacionRepository;
import ec.edu.uteq.presustentaciones.repositories.TopicGuardadoStudentRepository;
import ec.edu.uteq.presustentaciones.repositories.TopicPropuestoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TopicServiceImplTest {

    @Mock private TopicPropuestoRepository topicPropuestoRepository;
    @Mock private TopicGuardadoStudentRepository topicGuardadoStudentRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private ProgramRepository programRepository;
    @Mock private LineInvestigacionRepository lineInvestigacionRepository;
    @Mock private AreaTematicaRepository areaTematicaRepository;

    @InjectMocks private TopicServiceImpl topicService;

    private TopicPropuesto topicMock;
    private Student studentMock;

    @BeforeEach
    void setUp() {
        Program program = new Program();
        program.setId(1);
        program.setNombre("Ingeniería en Software");

        LineInvestigacion line = new LineInvestigacion();
        line.setId(1);
        line.setNombre("Ingeniería de Software y Calidad");

        topicMock = TopicPropuesto.builder()
                .id(1).titulo("Tema Prueba").nivelDificultad("BASICO")
                .program(program).lineInvestigacion(line)
                .build();

        studentMock = new Student();
        studentMock.setId(1L);
    }

    @Test
    void generateIdeasPorProgramYLine() {
        GenerateTopicRequest request = new GenerateTopicRequest();
        request.setProgramId(1);
        request.setLineInvestigacionId(1);
        when(topicPropuestoRepository.findByProgramIdAndLineInvestigacionId(1, 1))
                .thenReturn(Collections.singletonList(topicMock));

        List<TopicPropuestoDTO> resultados = topicService.generateIdeas(request);

        assertFalse(resultados.isEmpty());
        assertEquals("Tema Prueba", resultados.get(0).getTitulo());
        assertEquals("Ingeniería en Software", resultados.get(0).getProgramNombre());
        verify(topicPropuestoRepository).findByProgramIdAndLineInvestigacionId(1, 1);
    }

    @Test
    void generateIdeasSoloPorProgramCuandoNoHayLine() {
        GenerateTopicRequest request = new GenerateTopicRequest();
        request.setProgramId(1);
        when(topicPropuestoRepository.findByProgramId(1)).thenReturn(Collections.singletonList(topicMock));

        List<TopicPropuestoDTO> resultados = topicService.generateIdeas(request);

        assertEquals(1, resultados.size());
        verify(topicPropuestoRepository).findByProgramId(1);
        verify(topicPropuestoRepository, never()).findByProgramIdAndLineInvestigacionId(anyInt(), anyInt());
    }

    @Test
    void explorarMarcaLosTopicsYaGuardadosDelStudent() {
        when(topicPropuestoRepository.searchConFiltros(1, null, null, null))
                .thenReturn(Collections.singletonList(topicMock));
        when(topicGuardadoStudentRepository.findTopicIdsByStudentId(1L))
                .thenReturn(List.of(1));

        List<TopicPropuestoDTO> resultados = topicService.explorar(1, null, null, null, 1L);

        assertEquals(1, resultados.size());
        assertTrue(resultados.get(0).getGuardado());
    }

    @Test
    void explorarSinStudentNoConsultaGuardados() {
        when(topicPropuestoRepository.searchConFiltros(null, null, null, null))
                .thenReturn(Collections.singletonList(topicMock));

        List<TopicPropuestoDTO> resultados = topicService.explorar(null, null, null, null, null);

        assertNull(resultados.get(0).getGuardado());
        verify(topicGuardadoStudentRepository, never()).findTopicIdsByStudentId(any());
    }

    @Test
    void obtainDetalleLanzaSiNoExiste() {
        when(topicPropuestoRepository.findByIdConCatalogos(99)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> topicService.obtainDetalle(99));
    }

    @Test
    void saveTopicStudentExitoso() {
        when(topicGuardadoStudentRepository.existsByStudentIdAndTopicPropuestoId(1L, 1)).thenReturn(false);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(studentMock));
        when(topicPropuestoRepository.findById(1)).thenReturn(Optional.of(topicMock));

        topicService.saveTopicStudent(1L, 1);

        verify(topicGuardadoStudentRepository).save(any(TopicGuardadoStudent.class));
    }

    @Test
    void saveTopicYaGuardadoLanzaIllegalState() {
        when(topicGuardadoStudentRepository.existsByStudentIdAndTopicPropuestoId(1L, 1)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> topicService.saveTopicStudent(1L, 1));
        verify(topicGuardadoStudentRepository, never()).save(any());
    }

    @Test
    void removeTopicGuardadoExitoso() {
        when(topicGuardadoStudentRepository.deleteByStudentIdAndTopicPropuestoId(1L, 1)).thenReturn(1);

        assertDoesNotThrow(() -> topicService.removeTopicGuardado(1L, 1));
    }

    @Test
    void removeTopicGuardadoInexistenteLanza() {
        when(topicGuardadoStudentRepository.deleteByStudentIdAndTopicPropuestoId(1L, 1)).thenReturn(0);

        assertThrows(IllegalArgumentException.class, () -> topicService.removeTopicGuardado(1L, 1));
    }

    @Test
    void obtainTopicsGuardadosMapeaYMarcaGuardado() {
        TopicGuardadoStudent guardado = TopicGuardadoStudent.builder()
                .id(1).student(studentMock).topicPropuesto(topicMock).build();
        when(topicGuardadoStudentRepository.findByStudentIdOrderByFechaGuardadoDesc(1L))
                .thenReturn(List.of(guardado));

        List<TopicPropuestoDTO> resultados = topicService.obtainTopicsGuardados(1L);

        assertEquals(1, resultados.size());
        assertTrue(resultados.get(0).getGuardado());
    }

    // ── CRUD del catálogo ────────────────────────────────────────────────

    private SaveTopicPropuestoRequest reqCreate() {
        SaveTopicPropuestoRequest r = new SaveTopicPropuestoRequest();
        r.setTitulo("  Nuevo tema  ");
        r.setProblema("  ");
        return r;
    }

    @Test
    void createTopicSinCatalogosGuardaYRecortaCampos() {
        when(topicPropuestoRepository.save(any(TopicPropuesto.class))).thenAnswer(i -> i.getArgument(0));

        TopicPropuestoDTO dto = topicService.create(reqCreate());

        assertEquals("Nuevo tema", dto.getTitulo());
        assertNull(dto.getProblema()); // "  " -> null
        assertNull(dto.getProgramId());
        verify(programRepository, never()).findById(any());
    }

    @Test
    void createTopicConProgramInexistenteLanza() {
        SaveTopicPropuestoRequest r = reqCreate();
        r.setProgramId(9);
        when(programRepository.findById(9)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> topicService.create(r));
        verify(topicPropuestoRepository, never()).save(any());
    }

    @Test
    void createTopicConAreaQueNoPerteneceALaLineLanza() {
        SaveTopicPropuestoRequest r = reqCreate();
        r.setLineInvestigacionId(1);
        r.setAreaId(2);

        LineInvestigacion line = new LineInvestigacion();
        line.setId(1);
        LineInvestigacion otraLine = new LineInvestigacion();
        otraLine.setId(99);
        AreaTematica area = new AreaTematica();
        area.setId(2);
        area.setLineInvestigacion(otraLine);

        when(lineInvestigacionRepository.findById(1)).thenReturn(Optional.of(line));
        when(areaTematicaRepository.findById(2)).thenReturn(Optional.of(area));

        assertThrows(IllegalArgumentException.class, () -> topicService.create(r));
    }

    @Test
    void updateTopicInexistenteLanza() {
        when(topicPropuestoRepository.findById(7)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> topicService.update(7, reqCreate()));
    }

    @Test
    void deleteTopicInexistenteLanza() {
        when(topicPropuestoRepository.existsById(7)).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> topicService.delete(7));
        verify(topicPropuestoRepository, never()).deleteById(any());
    }

    @Test
    void deleteTopicExistenteBorra() {
        when(topicPropuestoRepository.existsById(1)).thenReturn(true);
        topicService.delete(1);
        verify(topicPropuestoRepository).deleteById(1);
    }
}
