package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.dto.PerfilRequest;
import ec.edu.uteq.presustentaciones.dto.ResolveSupresionRequest;
import ec.edu.uteq.presustentaciones.dto.ResponseWrapper;
import ec.edu.uteq.presustentaciones.entities.SubmissionSupresion;
import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.repositories.AppUserRepository;
import ec.edu.uteq.presustentaciones.security.dto.RegisterRequest;
import ec.edu.uteq.presustentaciones.services.IAppUserService;
import ec.edu.uteq.presustentaciones.services.SupresionDatosService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * La gestión de appUsers (list/create/editar role/activate/delete) es exclusiva de ADMIN.
 * Cada appUser autenticado solo puede ver/editar su propio perfil vía {@code /{id}} y {@code /{id}/perfil}.
 */
@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:3000"})
@Slf4j
@Tag(name = "Usuarios", description = "API para gestión de usuarios del sistema")
public class AppUserController {

    private final IAppUserService appUserService;
    private final AppUserRepository appUserRepository;
    private final SupresionDatosService supresionDatosService;

    /**
     * Listado completo de appUsers, sin paginar. Se conserva para usos puntuales; el panel de
     * administración usa siempre {@code /paginado}, porque esta tabla puede tener decenas de
     * miles de filas tras la carga de datos de las pruebas k6.
     *
     * @return 200 con todos los appUsers
     */
    @GetMapping
    @PreAuthorize("@permissionService.tienePermission(authentication, 'USUARIOS_GESTIONAR')")
    @Operation(summary = "Listar todos los usuarios (solo ADMIN) — sin paginar, uso interno/pequeñas instalaciones")
    public ResponseEntity<?> listTodos() {
        log.info("GET /api/usuarios - Listando todos los usuarios");
        try {
            return ResponseEntity.ok(ResponseWrapper.success(appUserService.listTodos()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * Listado paginado con búsqueda de texto libre. La tabla puede tener decenas de miles de
     * filas (datos de carga k6), así que el panel de administración usa siempre este endpoint
     * y nunca el listado completo.
     *
     * @param page número de página (0 por defecto)
     * @param size filas por página (20 por defecto)
     * @param q    búsqueda de texto libre sobre nombre/apellido/email, opcional
     * @return 200 con la página de appUsers
     */
    @GetMapping("/paginado")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'USUARIOS_GESTIONAR')")
    @Operation(summary = "Listar usuarios paginado, con búsqueda opcional (solo ADMIN)")
    public ResponseEntity<?> listPaginado(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "q", required = false) String q
    ) {
        try {
            var resultado = appUserService.listPaginado(page, size, q);
            return ResponseEntity.ok(ResponseWrapper.success(java.util.Map.of(
                    "content", resultado.getContent(),
                    "totalElements", resultado.getTotalElements(),
                    "totalPages", resultado.getTotalPages(),
                    "page", resultado.getNumber(),
                    "size", resultado.getSize()
            )));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * Ficha de un appUser. Un appUser no administrador sólo puede consultar la suya: la
     * identidad se resuelve por el email del JWT, no por el id recibido en la URL.
     *
     * @param id appUser consultado
     * @return 200 con el appUser, o 403 si se pide el de otra persona sin ser ADMIN
     */
    @GetMapping("/{id}")
    @Operation(summary = "Obtener usuario por ID (propio usuario o ADMIN)")
    public ResponseEntity<?> obtainPorId(@PathVariable("id") Long id) {
        if (!esAppUserActualOAdmin(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ResponseWrapper.error("No tienes permiso para ver este usuario"));
        }
        log.info("GET /api/usuarios/{} - Obteniendo usuario", id);
        return appUserService.obtainPorId(id)
                .<ResponseEntity<?>>map(appUser -> ResponseEntity.ok(ResponseWrapper.success(appUser)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(ResponseWrapper.error("Usuario no encontrado")));
    }

    /**
     * Búsqueda de un appUser por su correo institucional.
     *
     * @param email correo exacto a search
     * @return 200 con el appUser encontrado, o el error correspondiente si no existe
     */
    @GetMapping("/email/{email}")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'USUARIOS_GESTIONAR')")
    @Operation(summary = "Buscar usuario por email (solo ADMIN)")
    public ResponseEntity<?> searchPorEmail(@PathVariable("email") String email) {
        log.info("GET /api/usuarios/email/{} - Buscando usuario", email);
        return appUserService.obtainPorEmail(email)
                .<ResponseEntity<?>>map(appUser -> ResponseEntity.ok(ResponseWrapper.success(appUser)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(ResponseWrapper.error("Usuario no encontrado")));
    }

    /**
     * AppUsers con la cuenta activa, para los selectores del frontend que no deben ofrecer
     * cuentas desactivadas.
     *
     * @return 200 con los appUsers activos
     */
    @GetMapping("/activos")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'USUARIOS_GESTIONAR')")
    @Operation(summary = "Listar usuarios activos (solo ADMIN)")
    public ResponseEntity<?> listActivos() {
        log.info("GET /api/usuarios/activos - Listando usuarios activos");
        try {
            return ResponseEntity.ok(ResponseWrapper.success(appUserService.listActivos()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * Hallazgo real de auditoría (2026-09-04), mismo criterio que AuthController.register():
     * recibir la entidad AppUser cruda permitía mass-assignment de "activo"/"rolUsuario"/"id" —
     * este último es el más grave, porque un "id" de un appUser ya existente hacía que
     * AppUserServiceImpl.create() sobrescribiera esa fila en vez de create una nueva (ver su
     * comentario). Se usa RegisterRequest (mismo DTO, mismos 5 campos que ya envía
     * gestion-appUsers.component.ts) en vez de create un DTO nuevo.
     *
     * @param request datos del nuevo appUser, validados con Bean Validation; el DTO acota los
     *                campos aceptados para que el cliente no pueda fijar id, activo ni roleAppUser
     * @return 200 con el appUser creado, o el error de validación/duplicado correspondiente
     */
    @PostMapping
    @PreAuthorize("@permissionService.tienePermission(authentication, 'USUARIOS_GESTIONAR')")
    @Operation(summary = "Crear nuevo usuario (solo ADMIN)")
    public ResponseEntity<?> create(@Valid @RequestBody RegisterRequest request) {
        log.info("POST /api/usuarios - Creando usuario: {}", request.getEmail());
        AppUser appUser = new AppUser();
        appUser.setNombre(request.getNombre());
        appUser.setApellido(request.getApellido());
        appUser.setEmail(request.getEmail());
        appUser.setPassword(request.getPassword());
        appUser.setRole(request.getRole());
        appUser.setActivo(true);
        try {
            AppUser creado = appUserService.create(appUser);
            return ResponseEntity.status(HttpStatus.CREATED).body(ResponseWrapper.success(creado, "Usuario creado exitosamente"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * Actualización administrativa de un appUser (incluye su role).
     *
     * @param id      appUser a update
     * @param appUser campos a modificar
     * @return 200 con el appUser actualizado, o el error correspondiente si no existe
     */
    @PutMapping("/{id}")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'USUARIOS_GESTIONAR')")
    @Operation(summary = "Actualizar usuario, incluido su rol (solo ADMIN)")
    public ResponseEntity<?> update(
            @PathVariable("id") Long id,
            @RequestBody AppUser appUser
    ) {
        log.info("PUT /api/usuarios/{} - Actualizando usuario", id);
        try {
            AppUser actualizado = appUserService.update(id, appUser);
            return ResponseEntity.ok(ResponseWrapper.success(actualizado, "Usuario actualizado exitosamente"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * Reactiva una cuenta previamente desactivada.
     *
     * @param id appUser a activate
     * @return 200 con el appUser ya activo
     */
    @PatchMapping("/{id}/activar")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'USUARIOS_GESTIONAR')")
    @Operation(summary = "Activar usuario (solo ADMIN)")
    public ResponseEntity<?> activate(@PathVariable("id") Long id) {
        log.info("PATCH /api/usuarios/{}/activar", id);
        try {
            appUserService.activate(id);
            return ResponseEntity.ok(ResponseWrapper.success(null, "Usuario activado exitosamente"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * Desactiva una cuenta sin erasela, conservando su history y sus referencias.
     *
     * @param id appUser a deactivate
     * @return 200 con el appUser ya inactivo
     */
    @PatchMapping("/{id}/desactivar")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'USUARIOS_GESTIONAR')")
    @Operation(summary = "Desactivar usuario (solo ADMIN)")
    public ResponseEntity<?> deactivate(@PathVariable("id") Long id) {
        log.info("PATCH /api/usuarios/{}/desactivar", id);
        try {
            appUserService.deactivate(id);
            return ResponseEntity.ok(ResponseWrapper.success(null, "Usuario desactivado exitosamente"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * Edición del propio perfil (datos de contacto). Comprueba explícitamente la propiedad
     * del resource antes de tocar nada.
     *
     * @param id  appUser cuyo perfil se edita
     * @param req campos editables del perfil
     * @return 200 con el perfil actualizado, o 403 si se intenta editar el de otra persona
     */
    @PatchMapping("/{id}/perfil")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Actualizar correo de notificaciones y teléfono del perfil propio")
    public ResponseEntity<?> updatePerfil(
            @PathVariable("id") Long id,
            @RequestBody PerfilRequest req
    ) {
        if (!esAppUserActual(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ResponseWrapper.error("No puedes editar el perfil de otro usuario"));
        }
        try {
            AppUser actualizado = appUserService.updatePerfil(id, req.getEmailNotifications(), req.getTelefono());
            return ResponseEntity.ok(ResponseWrapper.success(actualizado, "Perfil actualizado exitosamente"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * Eliminación permanente de un appUser. Preferir deactivate cuando el appUser ya tenga
     * history académico asociado.
     *
     * @param id appUser a delete
     * @return 200 al confirmar el borrado, o el error si la base lo rechaza por referencias
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'USUARIOS_GESTIONAR')")
    @Operation(summary = "Eliminar usuario (solo ADMIN)")
    public ResponseEntity<?> delete(@PathVariable("id") Long id) {
        log.info("DELETE /api/usuarios/{}", id);
        try {
            appUserService.delete(id);
            return ResponseEntity.ok(ResponseWrapper.success(null, "Usuario eliminado exitosamente"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    // ── RNF-19: supresión de datos personales a submission del titular ──────────────────────

    /**
     * El titular solicita la supresión de sus propios datos. Mismo patrón de auto-comprobación
     * que {@link #updatePerfil}: no se puede solicitar en nombre de otra cuenta.
     *
     * @param id appUser que solicita -- debe ser el autenticado
     * @return 200 con la submission creada (estado PENDIENTE), o 403 si no es el propio appUser
     */
    @PostMapping("/{id}/solicitar-supresion")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Solicitar la supresión de los propios datos personales (RNF-19)")
    public ResponseEntity<?> solicitarSupresion(@PathVariable("id") Long id) {
        if (!esAppUserActual(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ResponseWrapper.error("No puedes solicitar la supresión de datos de otro usuario"));
        }
        try {
            SubmissionSupresion submission = supresionDatosService.solicitar(id);
            return ResponseEntity.ok(ResponseWrapper.success(submission, "Solicitud de supresión registrada"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /** @return 200 con todas las submissions de supresión, más recientes primero (solo ADMIN) */
    @GetMapping("/solicitudes-supresion")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'USUARIOS_GESTIONAR')")
    @Operation(summary = "Listar solicitudes de supresión de datos personales (RNF-19, solo ADMIN)")
    public ResponseEntity<?> listSubmissionsSupresion() {
        return ResponseEntity.ok(ResponseWrapper.success(supresionDatosService.list()));
    }

    /**
     * Resuelve una submission de supresión: la acepta (seudonimiza la cuenta, conservando el
     * expediente académico) o la rechaza con motivo. Solo ADMIN -- es una decisión que pesa el
     * derecho del titular contra la obligación legal de conservar el expediente.
     *
     * @param submissionId submission a resolve
     * @param request     {@code aceptar} + notas de la resolución
     * @return 200 con la submission resuelta, o 400 si ya estaba resuelta o no existe
     */
    @PostMapping("/solicitudes-supresion/{submissionId}/resolver")
    @PreAuthorize("@permissionService.tienePermission(authentication, 'USUARIOS_GESTIONAR')")
    @Operation(summary = "Resolver una solicitud de supresión (RNF-19, solo ADMIN)")
    public ResponseEntity<?> resolveSupresion(@PathVariable("submissionId") Long submissionId,
                                                @RequestBody ResolveSupresionRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long resueltoPorId = appUserRepository.findByEmail(auth.getName()).map(AppUser::getId).orElse(null);
        try {
            SubmissionSupresion resuelta = supresionDatosService.resolve(
                    submissionId, request.isAceptar(), resueltoPorId, request.getNotas());
            return ResponseEntity.ok(ResponseWrapper.success(resuelta, "Solicitud de supresión resuelta"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.error(e.getMessage()));
        }
    }

    /**
     * Comprueba que el id del path corresponda al appUser autenticado. La identidad se
     * resuelve por el email del JWT y nunca por el id recibido, que es justamente lo que
     * hace que este control no se pueda saltar cambiando el número de la URL.
     *
     * @param id identificador que llega en la ruta
     * @return true si ese id es el del appUser autenticado
     */
    private boolean esAppUserActual(Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return appUserRepository.findByEmail(auth.getName())
                .map(u -> u.getId().equals(id))
                .orElse(false);
    }

    private boolean esAppUserActualOAdmin(Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean esAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return esAdmin || esAppUserActual(id);
    }
}