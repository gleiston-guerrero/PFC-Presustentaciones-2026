package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.dto.SaveResourceRequest;
import ec.edu.uteq.presustentaciones.dto.ResourceDegreeDTO;
import ec.edu.uteq.presustentaciones.entities.Program;
import ec.edu.uteq.presustentaciones.entities.Student;
import ec.edu.uteq.presustentaciones.repositories.StudentRepository;
import ec.edu.uteq.presustentaciones.security.service.CurrentAppUserService;
import ec.edu.uteq.presustentaciones.services.ResourceDegreeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ResourceTitulacionController no tenía ningún test dedicado (0% de cobertura pese a que su
 * servicio ya está probado al 76%) -- brecha identificada en la auditoría de cobertura de
 * 2026-09-04. Cubre la resolución de programId por defecto (comportamiento real y no trivial
 * del endpoint /list) y que el resto de métodos delega correctamente.
 */
@ExtendWith(MockitoExtension.class)
class ResourceDegreeControllerTest {

    @Mock private ResourceDegreeService resourceService;
    @Mock private CurrentAppUserService currentAppUser;
    @Mock private StudentRepository studentRepository;

    @InjectMocks
    private ResourceDegreeController controller;

    private ResourceDegreeDTO resourceMock;

    @BeforeEach
    void setUp() {
        resourceMock = ResourceDegreeDTO.builder().id(1).titulo("Guía de formato APA").build();
    }

    @Test
    void listWithProgramIdExplicitNotQueryToStudentCurrent() {
        when(resourceService.list(5)).thenReturn(List.of(resourceMock));

        ResponseEntity<List<ResourceDegreeDTO>> response = controller.list(5);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        verify(currentAppUser, never()).studentIdOrNull();
        verify(resourceService).list(5);
    }

    @Test
    void listWithoutProgramIdResolvesProgramOfStudentAuthenticated() {
        Program program = Program.builder().id(3).build();
        Student student = Student.builder().id(7L).programEntidad(program).build();
        when(currentAppUser.studentIdOrNull()).thenReturn(7L);
        when(studentRepository.findById(7L)).thenReturn(Optional.of(student));
        when(resourceService.list(3)).thenReturn(List.of(resourceMock));

        ResponseEntity<List<ResourceDegreeDTO>> response = controller.list(null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(resourceService).list(3);
    }

    @Test
    void listWithoutProgramIdAndWithoutProfileOfStudentListAllWithoutFilter() {
        // Caller autenticado que no es student (ej. ADMIN/DOCENTE): sin id de student,
        // el filtro efectivo debe quedar en null (resources generales de todas las programs).
        when(currentAppUser.studentIdOrNull()).thenReturn(null);
        when(resourceService.list(null)).thenReturn(Collections.emptyList());

        ResponseEntity<List<ResourceDegreeDTO>> response = controller.list(null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(studentRepository, never()).findById(anyLong());
        verify(resourceService).list(null);
    }

    @Test
    void createDelegatesAndReturns201() {
        SaveResourceRequest req = new SaveResourceRequest();
        req.setTitulo("Guía de formato APA");
        req.setCategoria("GUIA");
        req.setUrlFile("https://example.com/guia.pdf");
        when(resourceService.create(req)).thenReturn(resourceMock);

        ResponseEntity<ResourceDegreeDTO> response = controller.create(req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("Guía de formato APA", response.getBody().getTitulo());
        verify(resourceService).create(req);
    }

    @Test
    void updateDelegatesAndReturns200() {
        SaveResourceRequest req = new SaveResourceRequest();
        req.setTitulo("Guía actualizada");
        req.setCategoria("GUIA");
        req.setUrlFile("https://example.com/guia-v2.pdf");
        when(resourceService.update(eq(1), eq(req))).thenReturn(resourceMock);

        ResponseEntity<ResourceDegreeDTO> response = controller.update(1, req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(resourceService).update(1, req);
    }

    @Test
    void deleteDelegatesAndReturns204() {
        ResponseEntity<Void> response = controller.delete(1);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(resourceService).delete(1);
    }
}
