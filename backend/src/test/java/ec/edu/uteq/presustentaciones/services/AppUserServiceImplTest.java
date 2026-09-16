package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.entities.RoleAppUser;
import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.repositories.RoleAppUserRepository;
import ec.edu.uteq.presustentaciones.repositories.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppUserServiceImplTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private RoleAppUserRepository roleAppUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private AppUserServiceImpl appUserService;

    private AppUser appUser;

    @BeforeEach
    void setUp() {
        appUser = new AppUser();
        appUser.setId(1L);
        appUser.setNombre("Juan");
        appUser.setApellido("Pérez");
        appUser.setEmail("jperez@uteq.edu.ec");
        appUser.setRole("ESTUDIANTE");
        appUser.setActivo(true);

        lenient().when(roleAppUserRepository.findByCodigo(anyString()))
                .thenAnswer(inv -> Optional.of(RoleAppUser.builder().codigo(inv.getArgument(0)).build()));
    }

    @Test
    void testListTodos() {
        when(appUserRepository.findAll()).thenReturn(Arrays.asList(appUser));
        List<AppUser> resultado = appUserService.listTodos();
        assertEquals(1, resultado.size());
        assertEquals("Juan", resultado.get(0).getNombre());
    }

    @Test
    void testObtainPorIdExitoso() {
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(appUser));
        Optional<AppUser> resultado = appUserService.obtainPorId(1L);
        assertTrue(resultado.isPresent());
        assertEquals("jperez@uteq.edu.ec", resultado.get().getEmail());
    }

    @Test
    void testCreateAppUser() {
        when(passwordEncoder.encode(any())).thenReturn("hash-encriptado");
        when(appUserRepository.save(any(AppUser.class))).thenReturn(appUser);
        AppUser guardado = appUserService.create(appUser);
        assertNotNull(guardado);
        assertEquals("jperez@uteq.edu.ec", guardado.getEmail());
    }

    @Test
    void testCreateAppUserIgnoraIdDelClienteParaEvitarSobrescribirAppUserExistente() {
        // Hallazgo real: save el "usuario" recibido tal cual, con save(appUser), es solo
        // seguro si id=null. Si el id llega no-nulo (por ejemplo, 1L = el appUser ADMIN real),
        // Spring Data JPA hace merge() en vez de persist() y SOBRESCRIBE esa fila existente en
        // lugar de create una nueva. create() debe forzar id=null sin importar lo que traiga el
        // objeto de entrada.
        appUser.setId(1L); // simula un id de un appUser ya existente llegando en el body
        when(passwordEncoder.encode(any())).thenReturn("hash");

        org.mockito.ArgumentCaptor<AppUser> captor = org.mockito.ArgumentCaptor.forClass(AppUser.class);
        when(appUserRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        appUserService.create(appUser);

        assertNull(captor.getValue().getId(), "crear() debe forzar id=null antes de guardar, sin importar el id recibido");
    }

    @Test
    void testCreateAppUserEncriptaLaContrasena() {
        appUser.setPassword("claveEnTextoPlano");
        when(passwordEncoder.encode("claveEnTextoPlano")).thenReturn("hash-bcrypt-simulado");
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        AppUser guardado = appUserService.create(appUser);

        assertEquals("hash-bcrypt-simulado", guardado.getPassword());
        verify(passwordEncoder).encode("claveEnTextoPlano");
    }

    @Test
    void testCreateAppUserAsignaRoleAppUser() {
        // Regresión: create() guardaba la columna 'role' (string) pero dejaba 'role_id' (FK) nulo,
        // lo que violaba la restricción NOT NULL de la base de datos real.
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        AppUser guardado = appUserService.create(appUser);

        assertNotNull(guardado.getRoleAppUser());
        assertEquals("ESTUDIANTE", guardado.getRoleAppUser().getCodigo());
    }

    @Test
    void testUpdateSincronizaRoleAppUserAlChangeRole() {
        AppUser existente = new AppUser();
        existente.setId(1L);
        existente.setRole("ESTUDIANTE");
        existente.setRoleAppUser(RoleAppUser.builder().codigo("ESTUDIANTE").build());

        when(appUserRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        AppUser cambios = new AppUser();
        cambios.setNombre("Juan");
        cambios.setApellido("Pérez");
        cambios.setEmail("jperez@uteq.edu.ec");
        cambios.setRole("COORDINADOR");

        AppUser actualizado = appUserService.update(1L, cambios);

        assertEquals("COORDINADOR", actualizado.getRole());
        assertEquals("COORDINADOR", actualizado.getRoleAppUser().getCodigo());
    }

    @Test
    void testCreateAppUserRechazaEmailDuplicado() {
        when(appUserRepository.existsByEmail(appUser.getEmail())).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> appUserService.create(appUser));
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void testChangeEstadoActivo() {
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(appUser));
        when(appUserRepository.save(any(AppUser.class))).thenReturn(appUser);
        
        appUserService.deactivate(1L);
        assertFalse(appUser.getActivo());

        appUserService.activate(1L);
        assertTrue(appUser.getActivo());
    }
}
