package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.entities.Anteproyecto;
import ec.edu.uteq.presustentaciones.services.AnteproyectoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/** AnteproyectoController no tenia ningun test (0% de ramas segun JaCoCo). */
@ExtendWith(MockitoExtension.class)
class AnteproyectoControllerTest {

    @TempDir
    Path tempDir;

    @Mock
    private AnteproyectoService anteproyectoService;

    private AnteproyectoController controller;

    @BeforeEach
    void setUp() {
        controller = new AnteproyectoController(anteproyectoService);
        ReflectionTestUtils.setField(controller, "uploadDir", tempDir.toString());
    }

    @Test
    void enviarDelegaEnElServicio() {
        Anteproyecto creado = Anteproyecto.builder().id(1L).build();
        MockMultipartFile archivo = new MockMultipartFile("archivo", "tesis.pdf", "application/pdf", "contenido".getBytes());
        when(anteproyectoService.enviarAnteproyecto(5L, archivo)).thenReturn(creado);

        ResponseEntity<Anteproyecto> resp = controller.enviar(5L, archivo);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertSame(creado, resp.getBody());
    }

    @Test
    void obtenerPorSolicitudDevuelve404SiNoExiste() {
        when(anteproyectoService.buscarPorSolicitud(5L)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, controller.obtenerPorSolicitud(5L).getStatusCode());
    }

    @Test
    void obtenerPorSolicitudDevuelveElAnteproyectoSiExiste() {
        Anteproyecto ap = Anteproyecto.builder().id(1L).build();
        when(anteproyectoService.buscarPorSolicitud(5L)).thenReturn(Optional.of(ap));

        assertEquals(HttpStatus.OK, controller.obtenerPorSolicitud(5L).getStatusCode());
    }

    @Test
    void verPdfLanzaExcepcionSiNoHayAnteproyecto() {
        when(anteproyectoService.buscarPorSolicitud(5L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> controller.verPdf(5L));
    }

    @Test
    void verPdfDevuelve404SiElArchivoNoExisteEnDisco() {
        Anteproyecto ap = Anteproyecto.builder().id(1L).archivoPdf("no-existe.pdf").build();
        when(anteproyectoService.buscarPorSolicitud(5L)).thenReturn(Optional.of(ap));

        assertEquals(HttpStatus.NOT_FOUND, controller.verPdf(5L).getStatusCode());
    }

    @Test
    void verPdfDevuelveElPdfRealCuandoExisteEnDisco() throws IOException {
        Files.writeString(tempDir.resolve("real.pdf"), "%PDF-1.4 contenido");
        Anteproyecto ap = Anteproyecto.builder().id(1L).archivoPdf("real.pdf").build();
        when(anteproyectoService.buscarPorSolicitud(5L)).thenReturn(Optional.of(ap));

        ResponseEntity<?> resp = controller.verPdf(5L);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    void verificarDevuelveIntegridadOkCuandoElHashCoincide() {
        Anteproyecto ap = Anteproyecto.builder().id(1L).sha256Hash("abc123").build();
        when(anteproyectoService.verificarIntegridad(5L)).thenReturn(true);
        when(anteproyectoService.buscarPorSolicitud(5L)).thenReturn(Optional.of(ap));

        ResponseEntity<?> resp = controller.verificar(5L);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    void verificarAvisaCuandoElHashNoCoincide() {
        Anteproyecto ap = Anteproyecto.builder().id(1L).sha256Hash(null).build();
        when(anteproyectoService.verificarIntegridad(5L)).thenReturn(false);
        when(anteproyectoService.buscarPorSolicitud(5L)).thenReturn(Optional.of(ap));

        ResponseEntity<?> resp = controller.verificar(5L);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    void verificarDevuelveBadRequestSiElServicioFalla() {
        when(anteproyectoService.verificarIntegridad(5L)).thenThrow(new RuntimeException("archivo no existe en disco"));

        assertEquals(HttpStatus.BAD_REQUEST, controller.verificar(5L).getStatusCode());
    }

    @Test
    void verificarPropagaAccessDeniedExceptionSinConvertirlaEnBadRequest() {
        when(anteproyectoService.verificarIntegridad(5L)).thenThrow(new AccessDeniedException("sin permiso"));

        assertThrows(AccessDeniedException.class, () -> controller.verificar(5L));
    }

    @Test
    void aprobarDelegaEnElServicio() {
        Anteproyecto aprobado = Anteproyecto.builder().id(1L).estado("APROBADO").build();
        when(anteproyectoService.aprobarAnteproyecto(1L, "ok")).thenReturn(aprobado);

        assertEquals("APROBADO", controller.aprobar(1L, "ok").getBody().getEstado());
    }

    @Test
    void rechazarDelegaEnElServicio() {
        Anteproyecto rechazado = Anteproyecto.builder().id(1L).estado("RECHAZADO").build();
        when(anteproyectoService.rechazarAnteproyecto(1L, "falta firma")).thenReturn(rechazado);

        assertEquals("RECHAZADO", controller.rechazar(1L, "falta firma").getBody().getEstado());
    }
}
