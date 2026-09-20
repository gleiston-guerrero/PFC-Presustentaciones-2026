package ec.edu.uteq.presustentaciones.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.edu.uteq.presustentaciones.config.SecurityConfig;
import ec.edu.uteq.presustentaciones.dto.ProfileRequest;
import ec.edu.uteq.presustentaciones.dto.ResolveErasureRequest;
import ec.edu.uteq.presustentaciones.entities.SubmissionErasure;
import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.repositories.AppUserRepository;
import ec.edu.uteq.presustentaciones.security.RateLimiterService;
import ec.edu.uteq.presustentaciones.security.dto.RegisterRequest;
import ec.edu.uteq.presustentaciones.security.jwt.JwtTokenProvider;
import ec.edu.uteq.presustentaciones.services.IAppUserService;
import ec.edu.uteq.presustentaciones.services.PermissionService;
import ec.edu.uteq.presustentaciones.services.ErasureDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AppUserController no tenia ningun test dedicado (audit de cobertura 2026-09-13, punto
 * "Cobertura de controladores" de la revision del teacher-director) pese a exponer la gestion
 * completa de cuentas (CRUD, activate/deactivate, supresion RNF-19) y el hallazgo de audit
 * documentado en la clase (mass-assignment via RegisterRequest en vez de la entidad cruda).
 * Cubre el control de propiedad real (esAppUserActual/esAppUserActualOAdmin) ademas del
 * permission USUARIOS_GESTIONAR.
 */
@WebMvcTest(controllers = AppUserController.class)
@Import(SecurityConfig.class)
class AppUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IAppUserService appUserService;

    @MockBean
    private ErasureDataService erasureDataService;

    @MockBean
    private AppUserRepository appUserRepository;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private RateLimiterService rateLimiterService;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @MockBean
    private AuthenticationManager authenticationManager;

    @MockBean(name = "permissionService")
    private PermissionService permissionService;

    @MockBean
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @MockBean
    private ec.edu.uteq.presustentaciones.repositories.RoleAppUserRepository roleAppUserRepository;

    private AppUser currentAppUser;

    @BeforeEach
    void setUp() {
        currentAppUser = new AppUser();
        currentAppUser.setId(50L);
        currentAppUser.setNombre("Ana");
        currentAppUser.setApellido("Torres");
        currentAppUser.setEmail("estudiante@uteq.edu.ec");
        currentAppUser.setActivo(true);

        when(appUserRepository.findByEmail("estudiante@uteq.edu.ec")).thenReturn(Optional.of(currentAppUser));
    }

    private void authenticateAs(String email, String role, boolean hasPermission) {
        String token = "token-" + email;
        UserDetails userDetails = new User(email, "x",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)));
        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken(token)).thenReturn(email);
        when(userDetailsService.loadUserByUsername(email)).thenReturn(userDetails);
        when(permissionService.hasPermission(any(), any())).thenReturn(hasPermission);
    }

    private String bearer(String email) {
        return "Bearer token-" + email;
    }

    // ── listTodos / listPaginado / listActivos ─────────────────────────────

    @Test
    void listAllWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listAllRejectsWithoutPermissionAppUsersManage() throws Exception {
        authenticateAs("docente@uteq.edu.ec", "DOCENTE", false);

        mockMvc.perform(get("/api/v1/usuarios").header("Authorization", bearer("docente@uteq.edu.ec")))
                .andExpect(status().isForbidden());
    }

    @Test
    void listAllAllowsToAdminWithPermission() throws Exception {
        authenticateAs("admin@uteq.edu.ec", "ADMIN", true);
        when(appUserService.listAll()).thenReturn(List.of(currentAppUser));

        mockMvc.perform(get("/api/v1/usuarios").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void listPagedDelegatesInServiceWithParameters() throws Exception {
        authenticateAs("admin@uteq.edu.ec", "ADMIN", true);
        Page<AppUser> pagina = new PageImpl<>(List.of(currentAppUser));
        when(appUserService.listPaged(0, 20, "torres")).thenReturn(pagina);

        mockMvc.perform(get("/api/v1/usuarios/paginado")
                        .param("page", "0").param("size", "20").param("q", "torres")
                        .header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void listActiveAllowsToAdmin() throws Exception {
        authenticateAs("admin@uteq.edu.ec", "ADMIN", true);
        when(appUserService.listActive()).thenReturn(List.of(currentAppUser));

        mockMvc.perform(get("/api/v1/usuarios/activos").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    // ── obtainPorId (control de propiedad real, no solo permission) ────────────────

    @Test
    void obtainByIdAllowsToOwnAppUserWithoutPermissionAdmin() throws Exception {
        authenticateAs("estudiante@uteq.edu.ec", "ESTUDIANTE", false);
        when(appUserService.obtainById(50L)).thenReturn(Optional.of(currentAppUser));

        mockMvc.perform(get("/api/v1/usuarios/50").header("Authorization", bearer("estudiante@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void obtainByIdRejectsViewProfileOfOtherAppUser() throws Exception {
        // Caso IDOR: student 50 intenta ver la ficha del appUser 99 cambiando el id de la URL.
        authenticateAs("estudiante@uteq.edu.ec", "ESTUDIANTE", false);

        mockMvc.perform(get("/api/v1/usuarios/99").header("Authorization", bearer("estudiante@uteq.edu.ec")))
                .andExpect(status().isForbidden());

        verify(appUserService, never()).obtainById(99L);
    }

    @Test
    void obtainByIdAllowsToAdminViewAnyAppUser() throws Exception {
        authenticateAs("admin@uteq.edu.ec", "ADMIN", true);
        when(appUserService.obtainById(99L)).thenReturn(Optional.of(currentAppUser));

        mockMvc.perform(get("/api/v1/usuarios/99").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void obtainByIdReturns404IfNotExists() throws Exception {
        authenticateAs("estudiante@uteq.edu.ec", "ESTUDIANTE", false);
        when(appUserService.obtainById(50L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/usuarios/50").header("Authorization", bearer("estudiante@uteq.edu.ec")))
                .andExpect(status().isNotFound());
    }

    // ── searchPorEmail ────────────────────────────────────────────────────────────

    @Test
    void searchByEmailReturnsAppUserFound() throws Exception {
        authenticateAs("admin@uteq.edu.ec", "ADMIN", true);
        when(appUserService.obtainByEmail("ana@uteq.edu.ec")).thenReturn(Optional.of(currentAppUser));

        mockMvc.perform(get("/api/v1/usuarios/email/ana@uteq.edu.ec").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void searchByEmailReturns404IfNotExists() throws Exception {
        authenticateAs("admin@uteq.edu.ec", "ADMIN", true);
        when(appUserService.obtainByEmail("nadie@uteq.edu.ec")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/usuarios/email/nadie@uteq.edu.ec").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isNotFound());
    }

    // ── create / update / activate / deactivate / delete ─────────────────────

    @Test
    void createReturns201WithAppUserCreated() throws Exception {
        authenticateAs("admin@uteq.edu.ec", "ADMIN", true);
        RegisterRequest req = new RegisterRequest();
        req.setNombre("Mario");
        req.setApellido("Rojas");
        req.setEmail("mario@uteq.edu.ec");
        req.setPassword("claveSegura123");
        req.setRole("ESTUDIANTE");
        when(appUserService.create(any(AppUser.class))).thenReturn(currentAppUser);

        mockMvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", bearer("admin@uteq.edu.ec"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    void createReturnsBadRequestIfServiceRejects() throws Exception {
        authenticateAs("admin@uteq.edu.ec", "ADMIN", true);
        RegisterRequest req = new RegisterRequest();
        req.setNombre("Mario");
        req.setApellido("Rojas");
        req.setEmail("mario@uteq.edu.ec");
        req.setPassword("claveSegura123");
        req.setRole("ESTUDIANTE");
        when(appUserService.create(any(AppUser.class))).thenThrow(new RuntimeException("Email ya registrado"));

        mockMvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", bearer("admin@uteq.edu.ec"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateReturnsAppUserUpdated() throws Exception {
        authenticateAs("admin@uteq.edu.ec", "ADMIN", true);
        when(appUserService.update(eq(50L), any(AppUser.class))).thenReturn(currentAppUser);

        mockMvc.perform(put("/api/v1/usuarios/50")
                        .header("Authorization", bearer("admin@uteq.edu.ec"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(currentAppUser)))
                .andExpect(status().isOk());
    }

    @Test
    void activateInvokesServiceAndReturns200() throws Exception {
        authenticateAs("admin@uteq.edu.ec", "ADMIN", true);

        mockMvc.perform(patch("/api/v1/usuarios/50/activar").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());

        verify(appUserService).activate(50L);
    }

    @Test
    void deactivateInvokesServiceAndReturns200() throws Exception {
        authenticateAs("admin@uteq.edu.ec", "ADMIN", true);

        mockMvc.perform(patch("/api/v1/usuarios/50/desactivar").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());

        verify(appUserService).deactivate(50L);
    }

    @Test
    void deleteReturnsBadRequestIfServiceRejectsByReferences() throws Exception {
        authenticateAs("admin@uteq.edu.ec", "ADMIN", true);
        org.mockito.Mockito.doThrow(new RuntimeException("El usuario tiene solicitudes asociadas"))
                .when(appUserService).delete(50L);

        mockMvc.perform(delete("/api/v1/usuarios/50").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isBadRequest());
    }

    // ── updatePerfil (auto-servicio, sin permission de admin) ───────────────────

    @Test
    void updateProfileAllowsToOwnAppUser() throws Exception {
        authenticateAs("estudiante@uteq.edu.ec", "ESTUDIANTE", false);
        ProfileRequest req = new ProfileRequest();
        req.setEmailNotifications("alterno@gmail.com");
        req.setPhone("0999999999");
        when(appUserService.updateProfile(50L, "alterno@gmail.com", "0999999999")).thenReturn(currentAppUser);

        mockMvc.perform(patch("/api/v1/usuarios/50/perfil")
                        .header("Authorization", bearer("estudiante@uteq.edu.ec"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void updateProfileRejectsEditProfileOfOtherAppUser() throws Exception {
        authenticateAs("estudiante@uteq.edu.ec", "ESTUDIANTE", false);
        ProfileRequest req = new ProfileRequest();
        req.setPhone("0999999999");

        mockMvc.perform(patch("/api/v1/usuarios/99/perfil")
                        .header("Authorization", bearer("estudiante@uteq.edu.ec"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        verify(appUserService, never()).updateProfile(eq(99L), anyString(), anyString());
    }

    // ── supresion de datos personales (RNF-19) ───────────────────────────────────

    @Test
    void requestErasureAllowsToOwnHolder() throws Exception {
        authenticateAs("estudiante@uteq.edu.ec", "ESTUDIANTE", false);
        SubmissionErasure submission = new SubmissionErasure();
        when(erasureDataService.solicitar(50L)).thenReturn(submission);

        mockMvc.perform(post("/api/v1/usuarios/50/solicitar-supresion")
                        .header("Authorization", bearer("estudiante@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void requestErasureRejectsInNameOfOtherAppUser() throws Exception {
        authenticateAs("estudiante@uteq.edu.ec", "ESTUDIANTE", false);

        mockMvc.perform(post("/api/v1/usuarios/99/solicitar-supresion")
                        .header("Authorization", bearer("estudiante@uteq.edu.ec")))
                .andExpect(status().isForbidden());

        verify(erasureDataService, never()).solicitar(99L);
    }

    @Test
    void listSubmissionsErasureAllowsToAdmin() throws Exception {
        authenticateAs("admin@uteq.edu.ec", "ADMIN", true);
        when(erasureDataService.list()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/usuarios/solicitudes-supresion").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void resolveErasureResolvesSubmissionAsAdmin() throws Exception {
        authenticateAs("admin@uteq.edu.ec", "ADMIN", true);
        when(appUserRepository.findByEmail("admin@uteq.edu.ec")).thenReturn(Optional.of(currentAppUser));
        ResolveErasureRequest req = new ResolveErasureRequest();
        req.setAceptar(true);
        req.setNotas("Procede");
        when(erasureDataService.resolve(eq(3L), eq(true), any(), eq("Procede")))
                .thenReturn(new SubmissionErasure());

        mockMvc.perform(post("/api/v1/usuarios/solicitudes-supresion/3/resolver")
                        .header("Authorization", bearer("admin@uteq.edu.ec"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }
}
