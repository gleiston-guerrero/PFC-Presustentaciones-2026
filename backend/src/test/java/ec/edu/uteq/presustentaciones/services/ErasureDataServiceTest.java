package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.SubmissionErasure;
import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.repositories.SubmissionErasureRepository;
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
class ErasureDataServiceTest {

    private AppUserRepository appUserRepository;
    private SubmissionErasureRepository submissionRepository;
    private ErasureDataService service;

    @BeforeEach
    void setUp() {
        appUserRepository = mock(AppUserRepository.class);
        submissionRepository = mock(SubmissionErasureRepository.class);
        service = new ErasureDataService(appUserRepository, submissionRepository);
        // save() devuelve lo que recibe, como un repositorio real
        when(submissionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void requestCreatesSubmissionInStatusPending() {
        when(appUserRepository.existsById(5L)).thenReturn(true);
        when(submissionRepository.existsByAppUserIdAndStatus(5L, "PENDIENTE")).thenReturn(false);

        SubmissionErasure submission = service.solicitar(5L);

        assertEquals(5L, submission.getAppUserId());
        assertEquals("PENDIENTE", submission.getStatus());
        assertNotNull(submission.getDateSubmission());
    }

    @Test
    void notCanRequestTwoTimesWhileHasPending() {
        when(submissionRepository.existsByAppUserIdAndStatus(5L, "PENDIENTE")).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.solicitar(5L));
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void resolveAcceptingPseudonymizesToHolderWithoutEraseRow() {
        AppUser holder = new AppUser();
        holder.setId(5L);
        holder.setNombre("Ana");
        holder.setApellido("Perez");
        holder.setEmail("ana.perez@uteq.edu.ec");
        holder.setPhone("0999999999");
        holder.setActivo(true);

        SubmissionErasure submission = SubmissionErasure.builder()
                .id(1L).appUserId(5L).status("PENDIENTE").build();
        when(submissionRepository.findById(1L)).thenReturn(Optional.of(submission));
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(holder));

        SubmissionErasure resuelta = service.resolve(1L, true, 99L, "Solicitud legítima");

        assertEquals("RESUELTA", resuelta.getStatus());
        assertEquals("SEUDONIMIZACION", resuelta.getKindResolucion());
        assertEquals(99L, resuelta.getResueltoBy());
        assertNotNull(resuelta.getDateResolucion());

        // El appUser NUNCA se borra -- se muta y se guarda, mismo id.
        verify(appUserRepository, never()).delete(any());
        verify(appUserRepository, never()).deleteById(any());
        verify(appUserRepository).save(holder);
        assertEquals(5L, holder.getId()); // el expediente enlazado a este id sigue siendo valido
        assertFalse(holder.getNombre().equals("Ana"));
        assertFalse(holder.getApellido().equals("Perez"));
        assertNotEquals("ana.perez@uteq.edu.ec", holder.getEmail());
        assertNull(holder.getPhone());
        assertEquals(Boolean.FALSE, holder.getActivo());
    }

    @Test
    void resolveRejectingNotTouchesToAppUser() {
        SubmissionErasure submission = SubmissionErasure.builder()
                .id(2L).appUserId(5L).status("PENDIENTE").build();
        when(submissionRepository.findById(2L)).thenReturn(Optional.of(submission));

        SubmissionErasure resuelta = service.resolve(2L, false, 99L, "Proceso de titulación en curso");

        assertEquals("RECHAZADA", resuelta.getStatus());
        assertEquals("RECHAZADA", resuelta.getKindResolucion());
        verify(appUserRepository, never()).findById(any());
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void notCanResolveTwoTimes() {
        SubmissionErasure yaResuelta = SubmissionErasure.builder()
                .id(3L).appUserId(5L).status("RESUELTA").build();
        when(submissionRepository.findById(3L)).thenReturn(Optional.of(yaResuelta));

        assertThrows(IllegalStateException.class, () -> service.resolve(3L, true, 99L, "x"));
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void recordOfSubmissionNeverContainsDataSuppressed() {
        // La entidad SubmissionSupresion (ver su clase) no tiene ningun campo de
        // nombre/correo/telefono -- solo appUserId. Esta prueba documenta esa garantia
        // estructural: intentar save el dato ahi no compila.
        SubmissionErasure s = SubmissionErasure.builder().appUserId(5L).status("PENDIENTE").build();
        assertNotNull(s.getAppUserId());
        // No existe s.getNombre()/getEmail()/getTelefono() -- la ausencia del getter es la prueba.
    }
}
