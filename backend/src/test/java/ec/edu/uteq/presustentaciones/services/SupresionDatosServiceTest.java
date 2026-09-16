package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.SubmissionSupresion;
import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.repositories.SubmissionSupresionRepository;
import ec.edu.uteq.presustentaciones.repositories.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * RNF-19, tercer criterio: procedimiento de supresión. La resolución aceptada seudonimiza (no
 * borra) al titular -- el expediente académico enlazado al mismo id debe seguir existiendo.
 */
class SupresionDatosServiceTest {

    private AppUserRepository appUserRepository;
    private SubmissionSupresionRepository submissionRepository;
    private SupresionDatosService service;

    @BeforeEach
    void setUp() {
        appUserRepository = mock(AppUserRepository.class);
        submissionRepository = mock(SubmissionSupresionRepository.class);
        service = new SupresionDatosService(appUserRepository, submissionRepository);
        // save() devuelve lo que recibe, como un repositorio real
        when(submissionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void solicitarCreaLaSubmissionEnEstadoPendiente() {
        when(appUserRepository.existsById(5L)).thenReturn(true);
        when(submissionRepository.existsByAppUserIdAndEstado(5L, "PENDIENTE")).thenReturn(false);

        SubmissionSupresion submission = service.solicitar(5L);

        assertEquals(5L, submission.getAppUserId());
        assertEquals("PENDIENTE", submission.getEstado());
        assertNotNull(submission.getFechaSubmission());
    }

    @Test
    void noSePuedeSolicitarDosVecesMientrasHayaUnaPendiente() {
        when(submissionRepository.existsByAppUserIdAndEstado(5L, "PENDIENTE")).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.solicitar(5L));
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void resolveAceptandoSeudonimizaAlTitularSinEraseLaFila() {
        AppUser titular = new AppUser();
        titular.setId(5L);
        titular.setNombre("Ana");
        titular.setApellido("Perez");
        titular.setEmail("ana.perez@uteq.edu.ec");
        titular.setTelefono("0999999999");
        titular.setActivo(true);

        SubmissionSupresion submission = SubmissionSupresion.builder()
                .id(1L).appUserId(5L).estado("PENDIENTE").build();
        when(submissionRepository.findById(1L)).thenReturn(Optional.of(submission));
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(titular));

        SubmissionSupresion resuelta = service.resolve(1L, true, 99L, "Solicitud legítima");

        assertEquals("RESUELTA", resuelta.getEstado());
        assertEquals("SEUDONIMIZACION", resuelta.getTipoResolucion());
        assertEquals(99L, resuelta.getResueltoPor());
        assertNotNull(resuelta.getFechaResolucion());

        // El appUser NUNCA se borra -- se muta y se guarda, mismo id.
        verify(appUserRepository, never()).delete(any());
        verify(appUserRepository, never()).deleteById(any());
        verify(appUserRepository).save(titular);
        assertEquals(5L, titular.getId()); // el expediente enlazado a este id sigue siendo valido
        assertFalse(titular.getNombre().equals("Ana"));
        assertFalse(titular.getApellido().equals("Perez"));
        assertNotEquals("ana.perez@uteq.edu.ec", titular.getEmail());
        assertNull(titular.getTelefono());
        assertEquals(Boolean.FALSE, titular.getActivo());
    }

    @Test
    void resolveRechazandoNoTocaAlAppUser() {
        SubmissionSupresion submission = SubmissionSupresion.builder()
                .id(2L).appUserId(5L).estado("PENDIENTE").build();
        when(submissionRepository.findById(2L)).thenReturn(Optional.of(submission));

        SubmissionSupresion resuelta = service.resolve(2L, false, 99L, "Proceso de titulación en curso");

        assertEquals("RECHAZADA", resuelta.getEstado());
        assertEquals("RECHAZADA", resuelta.getTipoResolucion());
        verify(appUserRepository, never()).findById(any());
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void noSePuedeResolveDosVeces() {
        SubmissionSupresion yaResuelta = SubmissionSupresion.builder()
                .id(3L).appUserId(5L).estado("RESUELTA").build();
        when(submissionRepository.findById(3L)).thenReturn(Optional.of(yaResuelta));

        assertThrows(IllegalStateException.class, () -> service.resolve(3L, true, 99L, "x"));
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void elRegistroDeLaSubmissionNuncaContieneElDatoSuprimido() {
        // La entidad SubmissionSupresion (ver su clase) no tiene ningun campo de
        // nombre/correo/telefono -- solo appUserId. Esta prueba documenta esa garantia
        // estructural: intentar save el dato ahi no compila.
        SubmissionSupresion s = SubmissionSupresion.builder().appUserId(5L).estado("PENDIENTE").build();
        assertNotNull(s.getAppUserId());
        // No existe s.getNombre()/getEmail()/getTelefono() -- la ausencia del getter es la prueba.
    }
}
