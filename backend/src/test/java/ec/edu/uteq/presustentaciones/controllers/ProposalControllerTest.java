package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.entities.Proposal;
import ec.edu.uteq.presustentaciones.services.ProposalService;
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

/** ProposalController no tenia ningun test (0% de ramas segun JaCoCo). */
@ExtendWith(MockitoExtension.class)
class ProposalControllerTest {

    @TempDir
    Path tempDir;

    @Mock
    private ProposalService proposalService;

    private ProposalController controller;

    @BeforeEach
    void setUp() {
        controller = new ProposalController(proposalService);
        ReflectionTestUtils.setField(controller, "uploadDir", tempDir.toString());
    }

    @Test
    void sendDelegaEnElServicio() {
        Proposal creado = Proposal.builder().id(1L).build();
        MockMultipartFile file = new MockMultipartFile("archivo", "tesis.pdf", "application/pdf", "contenido".getBytes());
        when(proposalService.sendProposal(5L, file)).thenReturn(creado);

        ResponseEntity<Proposal> resp = controller.send(5L, file);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertSame(creado, resp.getBody());
    }

    @Test
    void obtainBySubmissionDevuelve404SiNoExists() {
        when(proposalService.searchBySubmission(5L)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, controller.obtainBySubmission(5L).getStatusCode());
    }

    @Test
    void obtainBySubmissionDevuelveElProposalSiExists() {
        Proposal ap = Proposal.builder().id(1L).build();
        when(proposalService.searchBySubmission(5L)).thenReturn(Optional.of(ap));

        assertEquals(HttpStatus.OK, controller.obtainBySubmission(5L).getStatusCode());
    }

    @Test
    void viewPdfLanzaExcepcionSiNoHayProposal() {
        when(proposalService.searchBySubmission(5L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> controller.viewPdf(5L));
    }

    @Test
    void viewPdfDevuelve404SiElFileNoExistsEnDisco() {
        Proposal ap = Proposal.builder().id(1L).filePdf("no-existe.pdf").build();
        when(proposalService.searchBySubmission(5L)).thenReturn(Optional.of(ap));

        assertEquals(HttpStatus.NOT_FOUND, controller.viewPdf(5L).getStatusCode());
    }

    @Test
    void viewPdfDevuelveElPdfRealCuandoExistsEnDisco() throws IOException {
        Files.writeString(tempDir.resolve("real.pdf"), "%PDF-1.4 contenido");
        Proposal ap = Proposal.builder().id(1L).filePdf("real.pdf").build();
        when(proposalService.searchBySubmission(5L)).thenReturn(Optional.of(ap));

        ResponseEntity<?> resp = controller.viewPdf(5L);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    void verifyDevuelveIntegrityOkCuandoElHashCoincide() {
        Proposal ap = Proposal.builder().id(1L).sha256Hash("abc123").build();
        when(proposalService.verifyIntegrity(5L)).thenReturn(true);
        when(proposalService.searchBySubmission(5L)).thenReturn(Optional.of(ap));

        ResponseEntity<?> resp = controller.verify(5L);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    void verifyAvisaCuandoElHashNoCoincide() {
        Proposal ap = Proposal.builder().id(1L).sha256Hash(null).build();
        when(proposalService.verifyIntegrity(5L)).thenReturn(false);
        when(proposalService.searchBySubmission(5L)).thenReturn(Optional.of(ap));

        ResponseEntity<?> resp = controller.verify(5L);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    void verifyDevuelveBadRequestSiElServicioFalla() {
        when(proposalService.verifyIntegrity(5L)).thenThrow(new RuntimeException("archivo no existe en disco"));

        assertEquals(HttpStatus.BAD_REQUEST, controller.verify(5L).getStatusCode());
    }

    @Test
    void verifyPropagaAccessDeniedExceptionWithoutConvertirlaEnBadRequest() {
        when(proposalService.verifyIntegrity(5L)).thenThrow(new AccessDeniedException("sin permiso"));

        assertThrows(AccessDeniedException.class, () -> controller.verify(5L));
    }

    @Test
    void approveDelegaEnElServicio() {
        Proposal aprobado = Proposal.builder().id(1L).status("APROBADO").build();
        when(proposalService.approveProposal(1L, "ok")).thenReturn(aprobado);

        assertEquals("APROBADO", controller.approve(1L, "ok").getBody().getStatus());
    }

    @Test
    void rejectDelegaEnElServicio() {
        Proposal rechazado = Proposal.builder().id(1L).status("RECHAZADO").build();
        when(proposalService.rejectProposal(1L, "falta firma")).thenReturn(rechazado);

        assertEquals("RECHAZADO", controller.reject(1L, "falta firma").getBody().getStatus());
    }
}
