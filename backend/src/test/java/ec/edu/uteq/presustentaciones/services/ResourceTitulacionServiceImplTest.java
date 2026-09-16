package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.SaveResourceRequest;
import ec.edu.uteq.presustentaciones.dto.ResourceTitulacionDTO;
import ec.edu.uteq.presustentaciones.entities.Program;
import ec.edu.uteq.presustentaciones.entities.ResourceTitulacion;
import ec.edu.uteq.presustentaciones.repositories.ProgramRepository;
import ec.edu.uteq.presustentaciones.repositories.ResourceTitulacionRepository;
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
class ResourceTitulacionServiceImplTest {

    @Mock private ResourceTitulacionRepository resourceRepository;
    @Mock private ProgramRepository programRepository;

    @InjectMocks private ResourceTitulacionServiceImpl service;

    private ResourceTitulacion resource() {
        Program c = new Program();
        c.setId(1);
        c.setNombre("Ingeniería en Software");
        return ResourceTitulacion.builder()
                .id(5).titulo("Plantilla").categoria("Plantillas")
                .urlArchivo("http://x/y.docx").program(c).build();
    }

    @Test
    void listSinProgramUsaListTodos() {
        when(resourceRepository.listTodos()).thenReturn(List.of(resource()));

        List<ResourceTitulacionDTO> r = service.list(null);

        assertEquals(1, r.size());
        assertEquals("Ingeniería en Software", r.get(0).getProgramNombre());
        verify(resourceRepository).listTodos();
        verify(resourceRepository, never()).listVisiblesParaProgram(any());
    }

    @Test
    void listConProgramFiltra() {
        when(resourceRepository.listVisiblesParaProgram(1)).thenReturn(List.of(resource()));

        assertEquals(1, service.list(1).size());
        verify(resourceRepository).listVisiblesParaProgram(1);
    }

    @Test
    void createResourceGeneralSinProgram() {
        SaveResourceRequest req = new SaveResourceRequest();
        req.setTitulo("  Guía  "); req.setCategoria("Guías"); req.setUrlArchivo("http://x");
        when(resourceRepository.save(any(ResourceTitulacion.class))).thenAnswer(i -> i.getArgument(0));

        ResourceTitulacionDTO dto = service.create(req);

        assertEquals("Guía", dto.getTitulo());
        assertNull(dto.getProgramId());
        verify(programRepository, never()).findById(any());
    }

    @Test
    void createConProgramInexistenteLanza() {
        SaveResourceRequest req = new SaveResourceRequest();
        req.setTitulo("X"); req.setCategoria("Y"); req.setUrlArchivo("Z"); req.setProgramId(99);
        when(programRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.create(req));
    }

    @Test
    void updateInexistenteLanza() {
        when(resourceRepository.findById(7)).thenReturn(Optional.empty());
        SaveResourceRequest req = new SaveResourceRequest();
        req.setTitulo("X"); req.setCategoria("Y"); req.setUrlArchivo("Z");

        assertThrows(IllegalArgumentException.class, () -> service.update(7, req));
    }

    @Test
    void deleteInexistenteLanza() {
        when(resourceRepository.existsById(7)).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> service.delete(7));
        verify(resourceRepository, never()).deleteById(any());
    }

    @Test
    void deleteExistenteBorra() {
        when(resourceRepository.existsById(5)).thenReturn(true);
        service.delete(5);
        verify(resourceRepository).deleteById(5);
    }
}
