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
    private String failureOf(ResponseEntity<?> response) {
        return ((Map<String, String>) response.getBody()).get("error");
    }

    private RoleAppUser role(short id, String code, String nombre) {
        return RoleAppUser.builder().id(id).code(code).nombre(nombre).build();
    }

    @Test
    void listBuildsDtoWithAppUsersAssignedAndPermissionsOfEachRole() {
        when(roleAppUserRepository.findAll()).thenReturn(List.of(role((short) 1, "ADMIN", "Administrador")));
        when(appUserRepository.findByRole("ADMIN")).thenReturn(List.of(
                AppUser.builder().id(1L).build(), AppUser.builder().id(2L).build()));
        when(permissionRepository.findCodigosByRole((short) 1))
                .thenReturn(List.of("ROLES_PERMISOS_GESTIONAR", "SOLICITUDES_REVISAR"));

        List<RoleDTO> roles = controller.list();

        assertEquals(1, roles.size());
        RoleDTO dto = roles.get(0);
        assertEquals("ADMIN", dto.getCode());
        assertEquals("Administrador", dto.getNombre());
        assertEquals(2, dto.getAppUsersAsignados());
        assertEquals(2, dto.getPermissions().size());
    }

    // ── Creación ──────────────────────────────────────────────────────────────

    @Test
    void createNormalizesCodeAndCalculatesNextIdAvailable() {
        when(roleAppUserRepository.findByCode("SECRETARIA_ACADEMICA")).thenReturn(Optional.empty());
        when(roleAppUserRepository.findAll()).thenReturn(List.of(
                role((short) 1, "ADMIN", "Administrador"), role((short) 4, "ESTUDIANTE", "Estudiante")));
        when(roleAppUserRepository.save(any(RoleAppUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(appUserRepository.findByRole("SECRETARIA_ACADEMICA")).thenReturn(List.of());
        when(permissionRepository.findCodigosByRole((short) 5)).thenReturn(List.of());

        ResponseEntity<?> response = controller.create(Map.of(
                "codigo", " secretaria academica ", "nombre", " Secretaría Académica "));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        RoleDTO dto = (RoleDTO) response.getBody();
        assertEquals("SECRETARIA_ACADEMICA", dto.getCode());
        assertEquals("Secretaría Académica", dto.getNombre());
        // El mayor id existente es 4, así que el nuevo role toma el 5
        assertEquals((short) 5, dto.getId());
        verify(auditService).markActorActual();
    }

    @Test
    void createRejectsCodeOrNameEmpty() {
        ResponseEntity<?> sinCode = controller.create(Map.of("nombre", "Secretaría"));
        ResponseEntity<?> sinNombre = controller.create(Map.of("codigo", "SECRETARIA"));

        assertEquals(HttpStatus.BAD_REQUEST, sinCode.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, sinNombre.getStatusCode());
        assertEquals("Código y nombre son obligatorios.", failureOf(sinCode));
        verify(roleAppUserRepository, never()).save(any());
    }

    @Test
    void createRejectsCodeDuplicate() {
        when(roleAppUserRepository.findByCode("ADMIN"))
                .thenReturn(Optional.of(role((short) 1, "ADMIN", "Administrador")));

        ResponseEntity<?> response = controller.create(Map.of("codigo", "admin", "nombre", "Otro admin"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Ya existe un rol con ese código.", failureOf(response));
        verify(roleAppUserRepository, never()).save(any());
    }

    // ── Renombrado ────────────────────────────────────────────────────────────

    @Test
    void renameChangesOnlyNameVisibleNotCode() {
        RoleAppUser existing = role((short) 5, "SECRETARIA", "Secretaria");
        when(roleAppUserRepository.findById((short) 5)).thenReturn(Optional.of(existing));
        when(roleAppUserRepository.save(existing)).thenReturn(existing);
        when(appUserRepository.findByRole("SECRETARIA")).thenReturn(List.of());
        when(permissionRepository.findCodigosByRole((short) 5)).thenReturn(List.of());

        ResponseEntity<?> response = controller.rename((short) 5,
                Map.of("nombre", " Secretaría Académica ", "codigo", "OTRO_CODIGO"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        RoleDTO dto = (RoleDTO) response.getBody();
        assertEquals("Secretaría Académica", dto.getNombre());
        // El código no se toca aunque venga en el body: lo usan @PreAuthorize y AppUser.role
        assertEquals("SECRETARIA", dto.getCode());
    }

    @Test
    void renameRoleNonexistentReturns404() {
        when(roleAppUserRepository.findById((short) 99)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND,
                controller.rename((short) 99, Map.of("nombre", "X")).getStatusCode());
    }

    @Test
    void renameRejectsNameEmpty() {
        when(roleAppUserRepository.findById((short) 5))
                .thenReturn(Optional.of(role((short) 5, "SECRETARIA", "Secretaria")));

        ResponseEntity<?> response = controller.rename((short) 5, Map.of("nombre", "   "));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("El nombre no puede estar vacío.", failureOf(response));
        verify(roleAppUserRepository, never()).save(any());
    }

    // ── Eliminación ───────────────────────────────────────────────────────────

    @Test
    void notCanDeleteNoneOfFourRolesBase() {
        for (String code : List.of("ADMIN", "DOCENTE", "COORDINADOR", "ESTUDIANTE")) {
            when(roleAppUserRepository.findById((short) 1))
                    .thenReturn(Optional.of(role((short) 1, code, code)));

            ResponseEntity<?> response = controller.delete((short) 1);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertTrue(failureOf(response).contains("roles base del sistema"));
        }
        verify(roleAppUserRepository, never()).delete(any());
    }

    @Test
    void notCanDeleteRoleWithAppUsersAssigned() {
        when(roleAppUserRepository.findById((short) 5))
                .thenReturn(Optional.of(role((short) 5, "SECRETARIA", "Secretaría")));
        when(appUserRepository.findByRole("SECRETARIA")).thenReturn(List.of(
                AppUser.builder().id(1L).build(), AppUser.builder().id(2L).build()));

        ResponseEntity<?> response = controller.delete((short) 5);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(failureOf(response).contains("hay 2 usuario(s) con este rol"));
        verify(roleAppUserRepository, never()).delete(any());
    }

    @Test
    void deleteRoleNonexistentReturns404() {
        when(roleAppUserRepository.findById((short) 99)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, controller.delete((short) 99).getStatusCode());
    }

    @Test
    void roleNewWithoutAppUsersIfCanDelete() {
        RoleAppUser role = role((short) 5, "SECRETARIA", "Secretaría");
        when(roleAppUserRepository.findById((short) 5)).thenReturn(Optional.of(role));
        when(appUserRepository.findByRole("SECRETARIA")).thenReturn(List.of());

        ResponseEntity<?> response = controller.delete((short) 5);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(auditService).markActorActual();
        verify(roleAppUserRepository).delete(role);
    }

    @Test
    void ifBaseRejectsDeletedByReferencesReturnsMessageReadable() {
        RoleAppUser role = role((short) 5, "SECRETARIA", "Secretaría");
        when(roleAppUserRepository.findById((short) 5)).thenReturn(Optional.of(role));
        when(appUserRepository.findByRole("SECRETARIA")).thenReturn(List.of());
        doThrow(new org.springframework.dao.DataIntegrityViolationException("FK rol_permisos"))
                .when(roleAppUserRepository).delete(role);

        ResponseEntity<?> response = controller.delete((short) 5);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(failureOf(response).contains("referencias asociadas"));
    }
}
