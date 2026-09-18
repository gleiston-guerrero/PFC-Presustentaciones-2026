package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.ProgressDegreeDTO;
import ec.edu.uteq.presustentaciones.entities.Student;
import ec.edu.uteq.presustentaciones.entities.ProgressStudent;
import ec.edu.uteq.presustentaciones.repositories.StudentRepository;
import ec.edu.uteq.presustentaciones.repositories.ProgressStudentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProgressDegreeServiceImplTest {

    @Mock private ProgressStudentRepository progressRepository;
    @Mock private StudentRepository studentRepository;

    @InjectMocks private ProgressDegreeServiceImpl service;

    @Test
    void obtainWithoutRecordDevuelveAllEnCero() {
        when(progressRepository.findByStudentId(1L)).thenReturn(Optional.empty());

        ProgressDegreeDTO dto = service.obtain(1L);

        assertEquals(8, dto.getTotal());
        assertEquals(0, dto.getCompletados());
        assertEquals(0, dto.getPorcentaje());
        assertTrue(dto.getPasos().stream().noneMatch(ProgressDegreeDTO.StepDTO::isCompletado));
    }

    @Test
    void obtainWithStatusSavedCalculaPorcentaje() {
        ProgressStudent pe = ProgressStudent.builder()
                .pasosJson("{\"tema_definido\":true,\"tutor_asignado\":true}")
                .build();
        when(progressRepository.findByStudentId(1L)).thenReturn(Optional.of(pe));

        ProgressDegreeDTO dto = service.obtain(1L);

        assertEquals(2, dto.getCompletados());
        assertEquals(25, dto.getPorcentaje()); // 2 / 8
    }

    @Test
    void updateFusionaWithLoSavedYPersiste() {
        Student est = new Student();
        est.setId(1L);
        ProgressStudent pe = ProgressStudent.builder()
                .student(est).pasosJson("{\"tema_definido\":true}").build();
        when(progressRepository.findByStudentId(1L)).thenReturn(Optional.of(pe));

        ProgressDegreeDTO dto = service.update(1L, Map.of("tutor_asignado", true));

        assertEquals(2, dto.getCompletados());
        verify(progressRepository).save(pe);
        assertTrue(pe.getPasosJson().contains("tutor_asignado"));
        assertTrue(pe.getPasosJson().contains("tema_definido"));
    }

    @Test
    void updateIgnoraClavesFueraDelCatalog() {
        Student est = new Student();
        est.setId(1L);
        when(progressRepository.findByStudentId(1L)).thenReturn(Optional.empty());
        when(studentRepository.findById(1L)).thenReturn(Optional.of(est));
        when(progressRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ProgressDegreeDTO dto = service.update(1L, Map.of("paso_inventado", true, "tema_definido", true));

        assertEquals(1, dto.getCompletados());
        assertTrue(dto.getPasos().stream()
                .noneMatch(p -> "paso_inventado".equals(p.getClave())));
    }

    @Test
    void updateCreaRecordSiNoExistsYFallaSiStudentNoExists() {
        when(progressRepository.findByStudentId(1L)).thenReturn(Optional.empty());
        when(studentRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.update(1L, Map.of("tema_definido", true)));
    }
}
