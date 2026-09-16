package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.dto.RoleDTO;
import ec.edu.uteq.presustentaciones.entities.RoleAppUser;
import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.repositories.PermissionRepository;
import ec.edu.uteq.presustentaciones.repositories.RoleAppUserRepository;
import ec.edu.uteq.presustentaciones.repositories.AppUserRepository;
import ec.edu.uteq.presustentaciones.services.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * RoleController tenía 2 de 47 líneas cubiertas y 16 ramas en cero. Concentra dos
 * salvaguardas que el resto del sistema da por hechas: los 4 roles base (ADMIN,
 * DOCENTE, COORDINADOR, ESTUDIANTE) no se pueden delete porque el frontend y
 * AppUser.role todavía distinguen casos por esos códigos exactos, y ningún role con
 * appUsers asignados puede erasese.
 */
@ExtendWith(MockitoExtension.class)
class RoleControllerTest {

    @Mock private RoleAppUserRepository roleAppUserRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private AuditService auditService;

    @InjectMocks
    private RoleController controller;

    @SuppressWarnings("unchecked")
    private String errorDe(ResponseEntity<?> response) {
        return ((Map<String, String>) response.getBody()).get("error");
    }

    private RoleAppUser role(short id, String codigo, String nombre) {
        return RoleAppUser.builder().id(id).codigo(codigo).nombre(nombre).build();
    }

    @Test
    void listArmaElDtoConAppUsersAsignadosYPermissionsDeCadaRole() {
        when(roleAppUserRepository.findAll()).thenReturn(List.of(role((short) 1, "ADMIN", "Administrador")));
        when(appUserRepository.findByRole("ADMIN")).thenReturn(List.of(
                AppUser.builder().id(1L).build(), AppUser.builder().id(2L).build()));
        when(permissionRepository.findCodigosPorRole((short) 1))
                .thenReturn(List.of("ROLES_PERMISOS_GESTIONAR", "SOLICITUDES_REVISAR"));

        List<RoleDTO> roles = controller.list();

        assertEquals(1, roles.size());
        RoleDTO dto = roles.get(0);
        assertEquals("ADMIN", dto.getCodigo());
        assertEquals("Administrador", dto.getNombre());
        assertEquals(2, dto.getAppUsersAsignados());
        assertEquals(2, dto.getPermissions().size());
    }

    // ── Creación ──────────────────────────────────────────────────────────────

    @Test
    void createNormalizaElCodigoYCalculaElSiguienteIdDisponible() {
        when(roleAppUserRepository.findByCodigo("SECRETARIA_ACADEMICA")).thenReturn(Optional.empty());
        when(roleAppUserRepository.findAll()).thenReturn(List.of(
                role((short) 1, "ADMIN", "Administrador"), role((short) 4, "ESTUDIANTE", "Estudiante")));
        when(roleAppUserRepository.save(any(RoleAppUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(appUserRepository.findByRole("SECRETARIA_ACADEMICA")).thenReturn(List.of());
        when(permissionRepository.findCodigosPorRole((short) 5)).thenReturn(List.of());

        ResponseEntity<?> response = controller.create(Map.of(
                "codigo", " secretaria academica ", "nombre", " Secretaría Académica "));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        RoleDTO dto = (RoleDTO) response.getBody();
        assertEquals("SECRETARIA_ACADEMICA", dto.getCodigo());
        assertEquals("Secretaría Académica", dto.getNombre());
        // El mayor id existente es 4, así que el nuevo role toma el 5
        assertEquals((short) 5, dto.getId());
        verify(auditService).marcarActorActual();
    }

    @Test
    void createRechazaCodigoONombreVacios() {
        ResponseEntity<?> sinCodigo = controller.create(Map.of("nombre", "Secretaría"));
        ResponseEntity<?> sinNombre = controller.create(Map.of("codigo", "SECRETARIA"));

        assertEquals(HttpStatus.BAD_REQUEST, sinCodigo.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, sinNombre.getStatusCode());
        assertEquals("Código y nombre son obligatorios.", errorDe(sinCodigo));
        verify(roleAppUserRepository, never()).save(any());
    }

    @Test
    void createRechazaCodigoDuplicado() {
        when(roleAppUserRepository.findByCodigo("ADMIN"))
                .thenReturn(Optional.of(role((short) 1, "ADMIN", "Administrador")));

        ResponseEntity<?> response = controller.create(Map.of("codigo", "admin", "nombre", "Otro admin"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Ya existe un rol con ese código.", errorDe(response));
        verify(roleAppUserRepository, never()).save(any());
    }

    // ── Renombrado ────────────────────────────────────────────────────────────

    @Test
    void renameCambiaSoloElNombreVisibleNoElCodigo() {
        RoleAppUser existente = role((short) 5, "SECRETARIA", "Secretaria");
        when(roleAppUserRepository.findById((short) 5)).thenReturn(Optional.of(existente));
        when(roleAppUserRepository.save(existente)).thenReturn(existente);
        when(appUserRepository.findByRole("SECRETARIA")).thenReturn(List.of());
        when(permissionRepository.findCodigosPorRole((short) 5)).thenReturn(List.of());

        ResponseEntity<?> response = controller.rename((short) 5,
                Map.of("nombre", " Secretaría Académica ", "codigo", "OTRO_CODIGO"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        RoleDTO dto = (RoleDTO) response.getBody();
        assertEquals("Secretaría Académica", dto.getNombre());
        // El código no se toca aunque venga en el body: lo usan @PreAuthorize y AppUser.role
        assertEquals("SECRETARIA", dto.getCodigo());
    }

    @Test
    void renameRoleInexistenteDevuelve404() {
        when(roleAppUserRepository.findById((short) 99)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND,
                controller.rename((short) 99, Map.of("nombre", "X")).getStatusCode());
    }

    @Test
    void renameRechazaNombreVacio() {
        when(roleAppUserRepository.findById((short) 5))
                .thenReturn(Optional.of(role((short) 5, "SECRETARIA", "Secretaria")));

        ResponseEntity<?> response = controller.rename((short) 5, Map.of("nombre", "   "));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("El nombre no puede estar vacío.", errorDe(response));
        verify(roleAppUserRepository, never()).save(any());
    }

    // ── Eliminación ───────────────────────────────────────────────────────────

    @Test
    void noSePuedeDeleteNingunoDeLosCuatroRolesBase() {
        for (String codigo : List.of("ADMIN", "DOCENTE", "COORDINADOR", "ESTUDIANTE")) {
            when(roleAppUserRepository.findById((short) 1))
                    .thenReturn(Optional.of(role((short) 1, codigo, codigo)));

            ResponseEntity<?> response = controller.delete((short) 1);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertTrue(errorDe(response).contains("roles base del sistema"));
        }
        verify(roleAppUserRepository, never()).delete(any());
    }

    @Test
    void noSePuedeDeleteUnRoleConAppUsersAsignados() {
        when(roleAppUserRepository.findById((short) 5))
                .thenReturn(Optional.of(role((short) 5, "SECRETARIA", "Secretaría")));
        when(appUserRepository.findByRole("SECRETARIA")).thenReturn(List.of(
                AppUser.builder().id(1L).build(), AppUser.builder().id(2L).build()));

        ResponseEntity<?> response = controller.delete((short) 5);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(errorDe(response).contains("hay 2 usuario(s) con este rol"));
        verify(roleAppUserRepository, never()).delete(any());
    }

    @Test
    void deleteRoleInexistenteDevuelve404() {
        when(roleAppUserRepository.findById((short) 99)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, controller.delete((short) 99).getStatusCode());
    }

    @Test
    void unRoleNuevoSinAppUsersSiSePuedeDelete() {
        RoleAppUser role = role((short) 5, "SECRETARIA", "Secretaría");
        when(roleAppUserRepository.findById((short) 5)).thenReturn(Optional.of(role));
        when(appUserRepository.findByRole("SECRETARIA")).thenReturn(List.of());

        ResponseEntity<?> response = controller.delete((short) 5);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(auditService).marcarActorActual();
        verify(roleAppUserRepository).delete(role);
    }

    @Test
    void siLaBaseRechazaElBorradoPorReferenciasSeDevuelveUnMensajeLegible() {
        RoleAppUser role = role((short) 5, "SECRETARIA", "Secretaría");
        when(roleAppUserRepository.findById((short) 5)).thenReturn(Optional.of(role));
        when(appUserRepository.findByRole("SECRETARIA")).thenReturn(List.of());
        doThrow(new org.springframework.dao.DataIntegrityViolationException("FK rol_permisos"))
                .when(roleAppUserRepository).delete(role);

        ResponseEntity<?> response = controller.delete((short) 5);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(errorDe(response).contains("referencias asociadas"));
    }
}
