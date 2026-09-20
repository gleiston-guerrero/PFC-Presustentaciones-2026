package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.SaveResourceRequest;
import ec.edu.uteq.presustentaciones.dto.ResourceDegreeDTO;
import ec.edu.uteq.presustentaciones.entities.Program;
import ec.edu.uteq.presustentaciones.entities.ResourceDegree;
import ec.edu.uteq.presustentaciones.repositories.ProgramRepository;
import ec.edu.uteq.presustentaciones.repositories.ResourceDegreeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResourceDegreeServiceImplTest {

    @Mock private ResourceDegreeRepository resourceRepository;
    @Mock private ProgramRepository programRepository;

    @InjectMocks private ResourceDegreeServiceImpl service;

    private ResourceDegree resource() {
        Program c = new Program();
        c.setId(1);
        c.setNombre("Ingeniería en Software");
        return ResourceDegree.builder()
                .id(5).titulo("Plantilla").categoria("Plantillas")
                .urlFile("http://x/y.docx").program(c).build();
    }

    @Test
    void listWithoutProgramUsesListAll() {
        when(resourceRepository.listAll()).thenReturn(List.of(resource()));

        List<ResourceDegreeDTO> r = service.list(null);

        assertEquals(1, r.size());
        assertEquals("Ingeniería en Software", r.get(0).getProgramNombre());
        verify(resourceRepository).listAll();
        verify(resourceRepository, never()).listVisiblesForProgram(any());
    }

    @Test
    void listWithProgramFilters() {
        when(resourceRepository.listVisiblesForProgram(1)).thenReturn(List.of(resource()));

        assertEquals(1, service.list(1).size());
        verify(resourceRepository).listVisiblesForProgram(1);
    }

    @Test
    void createResourceGeneralWithoutProgram() {
        SaveResourceRequest req = new SaveResourceRequest();
        req.setTitulo("  Guía  "); req.setCategoria("Guías"); req.setUrlFile("http://x");
        when(resourceRepository.save(any(ResourceDegree.class))).thenAnswer(i -> i.getArgument(0));

        ResourceDegreeDTO dto = service.create(req);

        assertEquals("Guía", dto.getTitulo());
        assertNull(dto.getProgramId());
        verify(programRepository, never()).findById(any());
    }

    @Test
    void createWithProgramNonexistentThrows() {
        SaveResourceRequest req = new SaveResourceRequest();
        req.setTitulo("X"); req.setCategoria("Y"); req.setUrlFile("Z"); req.setProgramId(99);
        when(programRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.create(req));
    }

    @Test
    void updateNonexistentThrows() {
        when(resourceRepository.findById(7)).thenReturn(Optional.empty());
        SaveResourceRequest req = new SaveResourceRequest();
        req.setTitulo("X"); req.setCategoria("Y"); req.setUrlFile("Z");

        assertThrows(IllegalArgumentException.class, () -> service.update(7, req));
    }

    @Test
    void deleteNonexistentThrows() {
        when(resourceRepository.existsById(7)).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> service.delete(7));
        verify(resourceRepository, never()).deleteById(any());
    }

    @Test
    void deleteExistingDeletes() {
        when(resourceRepository.existsById(5)).thenReturn(true);
        service.delete(5);
        verify(resourceRepository).deleteById(5);
    }
}
