package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.GenerateTopicRequest;
import ec.edu.uteq.presustentaciones.dto.SaveTopicProposedRequest;
import ec.edu.uteq.presustentaciones.dto.TopicProposedDTO;
import ec.edu.uteq.presustentaciones.entities.Subject;
import ec.edu.uteq.presustentaciones.entities.Program;
import ec.edu.uteq.presustentaciones.entities.Student;
import ec.edu.uteq.presustentaciones.entities.ResearchLine;
import ec.edu.uteq.presustentaciones.entities.TopicSavedStudent;
import ec.edu.uteq.presustentaciones.entities.TopicProposed;
import ec.edu.uteq.presustentaciones.repositories.SubjectRepository;
import ec.edu.uteq.presustentaciones.repositories.ProgramRepository;
import ec.edu.uteq.presustentaciones.repositories.StudentRepository;
import ec.edu.uteq.presustentaciones.repositories.ResearchLineRepository;
import ec.edu.uteq.presustentaciones.repositories.TopicSavedStudentRepository;
import ec.edu.uteq.presustentaciones.repositories.TopicProposedRepository;
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

    @Mock private TopicProposedRepository topicProposedRepository;
    @Mock private TopicSavedStudentRepository topicSavedStudentRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private ProgramRepository programRepository;
    @Mock private ResearchLineRepository researchLineRepository;
    @Mock private SubjectRepository subjectRepository;

    @InjectMocks private TopicServiceImpl topicService;

    private TopicProposed topicMock;
    private Student studentMock;

    @BeforeEach
    void setUp() {
        Program program = new Program();
        program.setId(1);
        program.setNombre("Ingeniería en Software");

        ResearchLine line = new ResearchLine();
        line.setId(1);
        line.setNombre("Ingeniería de Software y Calidad");

        topicMock = TopicProposed.builder()
                .id(1).titulo("Tema Prueba").nivelDificultad("BASICO")
                .program(program).researchLine(line)
                .build();

        studentMock = new Student();
        studentMock.setId(1L);
    }

    @Test
    void generateSuggestionsByProgramAndLine() {
        GenerateTopicRequest request = new GenerateTopicRequest();
        request.setProgramId(1);
        request.setResearchLineId(1);
        when(topicProposedRepository.findByProgramIdAndResearchLineId(1, 1))
                .thenReturn(Collections.singletonList(topicMock));

        List<TopicProposedDTO> results = topicService.generateSuggestions(request);

        assertFalse(results.isEmpty());
        assertEquals("Tema Prueba", results.get(0).getTitulo());
        assertEquals("Ingeniería en Software", results.get(0).getProgramNombre());
        verify(topicProposedRepository).findByProgramIdAndResearchLineId(1, 1);
    }

    @Test
    void generateSuggestionsOnlyByProgramWhenNotHasLine() {
        GenerateTopicRequest request = new GenerateTopicRequest();
        request.setProgramId(1);
        when(topicProposedRepository.findByProgramId(1)).thenReturn(Collections.singletonList(topicMock));

        List<TopicProposedDTO> results = topicService.generateSuggestions(request);

        assertEquals(1, results.size());
        verify(topicProposedRepository).findByProgramId(1);
        verify(topicProposedRepository, never()).findByProgramIdAndResearchLineId(anyInt(), anyInt());
    }

    @Test
    void exploreMarksTopicsAlreadySavedOfStudent() {
        when(topicProposedRepository.searchWithFiltros(1, null, null, null))
                .thenReturn(Collections.singletonList(topicMock));
        when(topicSavedStudentRepository.findTopicIdsByStudentId(1L))
                .thenReturn(List.of(1));

        List<TopicProposedDTO> results = topicService.explore(1, null, null, null, 1L);

        assertEquals(1, results.size());
        assertTrue(results.get(0).getSaved());
    }

    @Test
    void exploreWithoutStudentNotQuerySaved() {
        when(topicProposedRepository.searchWithFiltros(null, null, null, null))
                .thenReturn(Collections.singletonList(topicMock));

        List<TopicProposedDTO> results = topicService.explore(null, null, null, null, null);

        assertNull(results.get(0).getSaved());
        verify(topicSavedStudentRepository, never()).findTopicIdsByStudentId(any());
    }

    @Test
    void obtainDetailThrowsIfNotExists() {
        when(topicProposedRepository.findByIdWithCatalogs(99)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> topicService.obtainDetail(99));
    }

    @Test
    void saveTopicStudentSuccessful() {
        when(topicSavedStudentRepository.existsByStudentIdAndTopicProposedId(1L, 1)).thenReturn(false);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(studentMock));
        when(topicProposedRepository.findById(1)).thenReturn(Optional.of(topicMock));

        topicService.saveTopicStudent(1L, 1);

        verify(topicSavedStudentRepository).save(any(TopicSavedStudent.class));
    }

    @Test
    void saveTopicAlreadySavedThrowsIllegalState() {
        when(topicSavedStudentRepository.existsByStudentIdAndTopicProposedId(1L, 1)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> topicService.saveTopicStudent(1L, 1));
        verify(topicSavedStudentRepository, never()).save(any());
    }

    @Test
    void removeTopicSavedSuccessful() {
        when(topicSavedStudentRepository.deleteByStudentIdAndTopicProposedId(1L, 1)).thenReturn(1);

        assertDoesNotThrow(() -> topicService.removeTopicSaved(1L, 1));
    }

    @Test
    void removeTopicSavedNonexistentThrows() {
        when(topicSavedStudentRepository.deleteByStudentIdAndTopicProposedId(1L, 1)).thenReturn(0);

        assertThrows(IllegalArgumentException.class, () -> topicService.removeTopicSaved(1L, 1));
    }

    @Test
    void obtainTopicsSavedMapsAndMarksSaved() {
        TopicSavedStudent saved = TopicSavedStudent.builder()
                .id(1).student(studentMock).topicProposed(topicMock).build();
        when(topicSavedStudentRepository.findByStudentIdOrderByDateSavedDesc(1L))
                .thenReturn(List.of(saved));

        List<TopicProposedDTO> results = topicService.obtainTopicsSaved(1L);

        assertEquals(1, results.size());
        assertTrue(results.get(0).getSaved());
    }

    // ── CRUD del catálogo ────────────────────────────────────────────────

    private SaveTopicProposedRequest reqCreate() {
        SaveTopicProposedRequest r = new SaveTopicProposedRequest();
        r.setTitulo("  Nuevo tema  ");
        r.setProblema("  ");
        return r;
    }

    @Test
    void createTopicWithoutCatalogsSavesAndTrimsFields() {
        when(topicProposedRepository.save(any(TopicProposed.class))).thenAnswer(i -> i.getArgument(0));

        TopicProposedDTO dto = topicService.create(reqCreate());

        assertEquals("Nuevo tema", dto.getTitulo());
        assertNull(dto.getProblema()); // "  " -> null
        assertNull(dto.getProgramId());
        verify(programRepository, never()).findById(any());
    }

    @Test
    void createTopicWithProgramNonexistentThrows() {
        SaveTopicProposedRequest r = reqCreate();
        r.setProgramId(9);
        when(programRepository.findById(9)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> topicService.create(r));
        verify(topicProposedRepository, never()).save(any());
    }

    @Test
    void createTopicWithAreaThatNotBelongsToLineThrows() {
        SaveTopicProposedRequest r = reqCreate();
        r.setResearchLineId(1);
        r.setAreaId(2);

        ResearchLine line = new ResearchLine();
        line.setId(1);
        ResearchLine otraLine = new ResearchLine();
        otraLine.setId(99);
        Subject area = new Subject();
        area.setId(2);
        area.setResearchLine(otraLine);

        when(researchLineRepository.findById(1)).thenReturn(Optional.of(line));
        when(subjectRepository.findById(2)).thenReturn(Optional.of(area));

        assertThrows(IllegalArgumentException.class, () -> topicService.create(r));
    }

    @Test
    void updateTopicNonexistentThrows() {
        when(topicProposedRepository.findById(7)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> topicService.update(7, reqCreate()));
    }

    @Test
    void deleteTopicNonexistentThrows() {
        when(topicProposedRepository.existsById(7)).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> topicService.delete(7));
        verify(topicProposedRepository, never()).deleteById(any());
    }

    @Test
    void deleteTopicExistingDeletes() {
        when(topicProposedRepository.existsById(1)).thenReturn(true);
        topicService.delete(1);
        verify(topicProposedRepository).deleteById(1);
    }
}
