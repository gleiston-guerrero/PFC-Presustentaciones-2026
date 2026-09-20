package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.entities.Permission;
import ec.edu.uteq.presustentaciones.entities.RoleAppUser;
import ec.edu.uteq.presustentaciones.repositories.PermissionRepository;
import ec.edu.uteq.presustentaciones.repositories.RoleAppUserRepository;
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
 * PermissionController tenía 1 de 20 líneas cubiertas y 12 ramas en cero, pese a contener
 * la salvaguarda que impide dejar al sistema sin ningún role capaz de gestionar permissions.
 *
 * Ese es el caso crítico que se cubre aquí: si se acepta remove ROLES_PERMISOS_GESTIONAR
 * del último role que lo tiene, nadie puede volver a asignarlo desde la interfaz y el
 * sistema queda blockado sin más salida que tocar la base de datos a mano.
 */
@ExtendWith(MockitoExtension.class)
class PermissionControllerTest {

    private static final String GESTION = "ROLES_PERMISOS_GESTIONAR";

    @Mock private PermissionRepository permissionRepository;
    @Mock private RoleAppUserRepository roleAppUserRepository;
    @Mock private AuditService auditService;

    @InjectMocks
    private PermissionController controller;

    @SuppressWarnings("unchecked")
    private String failureOf(ResponseEntity<?> response) {
        return ((Map<String, String>) response.getBody()).get("error");
    }

    private Permission permission(short id, String code) {
        return Permission.builder().id(id).code(code).build();
    }

    @Test
    void listReturnsCatalogSortedByCategoryAndName() {
        List<Permission> catalog = List.of(permission((short) 1, "SOLICITUDES_REVISAR"));
        when(permissionRepository.findAllByOrderByCategoriaAscNombreAsc()).thenReturn(catalog);

        assertSame(catalog, controller.list());
    }

    @Test
    void updatePermissionsOfRoleNonexistentReturns404() {
        when(roleAppUserRepository.findById((short) 99)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND,
                controller.updatePermissionsOfRole((short) 99, List.of("SOLICITUDES_REVISAR")).getStatusCode());
        verify(permissionRepository, never()).deletePermissionsDeRole(any());
    }

    @Test
    void updatePermissionsRejectsCodesThatNotExist() {
        when(roleAppUserRepository.findById((short) 1))
                .thenReturn(Optional.of(RoleAppUser.builder().id((short) 1).build()));
        // Se piden 2 códigos pero el repositorio solo resuelve 1: hay uno inventado
        when(permissionRepository.findByCodeIn(List.of("SOLICITUDES_REVISAR", "INVENTADO")))
                .thenReturn(List.of(permission((short) 1, "SOLICITUDES_REVISAR")));

        ResponseEntity<?> response = controller.updatePermissionsOfRole(
                (short) 1, List.of("SOLICITUDES_REVISAR", "INVENTADO"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Uno o más códigos de permiso no existen.", failureOf(response));
        verify(permissionRepository, never()).deletePermissionsDeRole(any());
    }

    @Test
    void updatePermissionsAcceptsCodesDuplicatesInRequest() {
        // El frontend puede mandar el mismo código repetido; el count se hace sobre
        // los distintos, así que no debe tratarse como "código inexistente".
        when(roleAppUserRepository.findById((short) 1))
                .thenReturn(Optional.of(RoleAppUser.builder().id((short) 1).build()));
        when(permissionRepository.findByCodeIn(List.of(GESTION, GESTION)))
                .thenReturn(List.of(permission((short) 1, GESTION)));
        when(permissionRepository.findCodigosByRole((short) 1)).thenReturn(List.of(GESTION));

        ResponseEntity<?> response = controller.updatePermissionsOfRole((short) 1, List.of(GESTION, GESTION));

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void notCanRemoveManagementOfPermissionsToLastRoleThatHas() {
        when(roleAppUserRepository.findById((short) 1))
                .thenReturn(Optional.of(RoleAppUser.builder().id((short) 1).build()));
        when(permissionRepository.findByCodeIn(List.of("SOLICITUDES_REVISAR")))
                .thenReturn(List.of(permission((short) 1, "SOLICITUDES_REVISAR")));
        // El único role que hoy tiene el permission es el que se está editando
        when(permissionRepository.findRoleIdsWithPermission(GESTION)).thenReturn(List.of((short) 1));

        ResponseEntity<?> response = controller.updatePermissionsOfRole(
                (short) 1, List.of("SOLICITUDES_REVISAR"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(failureOf(response).contains("ningún otro rol lo tendría"));
        verify(permissionRepository, never()).deletePermissionsDeRole(any());
        verify(auditService, never()).markActorActual();
    }

    @Test
    void ifOtherRoleKeepsManagementOfPermissionsIfCanRemoveOfThis() {
        when(roleAppUserRepository.findById((short) 2))
                .thenReturn(Optional.of(RoleAppUser.builder().id((short) 2).build()));
        when(permissionRepository.findByCodeIn(List.of("SOLICITUDES_REVISAR")))
                .thenReturn(List.of(permission((short) 5, "SOLICITUDES_REVISAR")));
        // El role 1 (ADMIN) también lo tiene, así que quitárselo al 2 no blocka el sistema
        when(permissionRepository.findRoleIdsWithPermission(GESTION)).thenReturn(List.of((short) 1, (short) 2));
        when(permissionRepository.findCodigosByRole((short) 2)).thenReturn(List.of("SOLICITUDES_REVISAR"));

        ResponseEntity<?> response = controller.updatePermissionsOfRole(
                (short) 2, List.of("SOLICITUDES_REVISAR"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(auditService).markActorActual();
        verify(permissionRepository).deletePermissionsDeRole((short) 2);
        verify(permissionRepository).assignPermission((short) 2, (short) 5);
    }

    @Test
    void updatePermissionsReplacesSetCompleteOfRole() {
        when(roleAppUserRepository.findById((short) 1))
                .thenReturn(Optional.of(RoleAppUser.builder().id((short) 1).build()));
        List<String> codigos = List.of(GESTION, "SOLICITUDES_REVISAR", "ACTAS_GESTIONAR");
        when(permissionRepository.findByCodeIn(codigos)).thenReturn(List.of(
                permission((short) 1, GESTION),
                permission((short) 2, "SOLICITUDES_REVISAR"),
                permission((short) 3, "ACTAS_GESTIONAR")));
        when(permissionRepository.findCodigosByRole((short) 1)).thenReturn(codigos);

        ResponseEntity<?> response = controller.updatePermissionsOfRole((short) 1, codigos);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(codigos, response.getBody());
        // Primero borra todo y luego reinserta: es un reemplazo, no un delta
        var orden = inOrder(permissionRepository);
        orden.verify(permissionRepository).deletePermissionsDeRole((short) 1);
        orden.verify(permissionRepository).assignPermission((short) 1, (short) 1);
        orden.verify(permissionRepository).assignPermission((short) 1, (short) 2);
        orden.verify(permissionRepository).assignPermission((short) 1, (short) 3);
        // Incluye el permission de gestión, así que no hace falta consultar los otros roles
        verify(permissionRepository, never()).findRoleIdsWithPermission(any());
    }
}
