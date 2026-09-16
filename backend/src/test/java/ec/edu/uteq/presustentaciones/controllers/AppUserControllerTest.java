package ec.edu.uteq.presustentaciones.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.edu.uteq.presustentaciones.config.SecurityConfig;
import ec.edu.uteq.presustentaciones.dto.PerfilRequest;
import ec.edu.uteq.presustentaciones.dto.ResolveSupresionRequest;
import ec.edu.uteq.presustentaciones.entities.SubmissionSupresion;
import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.repositories.AppUserRepository;
import ec.edu.uteq.presustentaciones.security.RateLimiterService;
import ec.edu.uteq.presustentaciones.security.dto.RegisterRequest;
import ec.edu.uteq.presustentaciones.security.jwt.JwtTokenProvider;
import ec.edu.uteq.presustentaciones.services.IAppUserService;
import ec.edu.uteq.presustentaciones.services.PermissionService;
import ec.edu.uteq.presustentaciones.services.SupresionDatosService;
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
    private SupresionDatosService supresionDatosService;

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

    private AppUser appUserActual;

    @BeforeEach
    void setUp() {
        appUserActual = new AppUser();
        appUserActual.setId(50L);
        appUserActual.setNombre("Ana");
        appUserActual.setApellido("Torres");
        appUserActual.setEmail("estudiante@uteq.edu.ec");
        appUserActual.setActivo(true);

        when(appUserRepository.findByEmail("estudiante@uteq.edu.ec")).thenReturn(Optional.of(appUserActual));
    }

    private void autenticarComo(String email, String role, boolean tienePermission) {
        String token = "token-" + email;
        UserDetails userDetails = new User(email, "x",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)));
        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken(token)).thenReturn(email);
        when(userDetailsService.loadUserByUsername(email)).thenReturn(userDetails);
        when(permissionService.tienePermission(any(), any())).thenReturn(tienePermission);
    }

    private String bearer(String email) {
        return "Bearer token-" + email;
    }

    // ── listTodos / listPaginado / listActivos ─────────────────────────────

    @Test
    void listTodosSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listTodosRechazaSinPermissionAppUsersGestionar() throws Exception {
        autenticarComo("docente@uteq.edu.ec", "DOCENTE", false);

        mockMvc.perform(get("/api/v1/usuarios").header("Authorization", bearer("docente@uteq.edu.ec")))
                .andExpect(status().isForbidden());
    }

    @Test
    void listTodosPermiteAAdminConPermission() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        when(appUserService.listTodos()).thenReturn(List.of(appUserActual));

        mockMvc.perform(get("/api/v1/usuarios").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void listPaginadoDelegaEnElServicioConLosParametros() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        Page<AppUser> pagina = new PageImpl<>(List.of(appUserActual));
        when(appUserService.listPaginado(0, 20, "torres")).thenReturn(pagina);

        mockMvc.perform(get("/api/v1/usuarios/paginado")
                        .param("page", "0").param("size", "20").param("q", "torres")
                        .header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void listActivosPermiteAAdmin() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        when(appUserService.listActivos()).thenReturn(List.of(appUserActual));

        mockMvc.perform(get("/api/v1/usuarios/activos").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    // ── obtainPorId (control de propiedad real, no solo permission) ────────────────

    @Test
    void obtainPorIdPermiteAlPropioAppUserSinPermissionAdmin() throws Exception {
        autenticarComo("estudiante@uteq.edu.ec", "ESTUDIANTE", false);
        when(appUserService.obtainPorId(50L)).thenReturn(Optional.of(appUserActual));

        mockMvc.perform(get("/api/v1/usuarios/50").header("Authorization", bearer("estudiante@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void obtainPorIdRechazaConsultarElPerfilDeOtroAppUser() throws Exception {
        // Caso IDOR: student 50 intenta ver la ficha del appUser 99 cambiando el id de la URL.
        autenticarComo("estudiante@uteq.edu.ec", "ESTUDIANTE", false);

        mockMvc.perform(get("/api/v1/usuarios/99").header("Authorization", bearer("estudiante@uteq.edu.ec")))
                .andExpect(status().isForbidden());

        verify(appUserService, never()).obtainPorId(99L);
    }

    @Test
    void obtainPorIdPermiteAAdminConsultarCualquierAppUser() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        when(appUserService.obtainPorId(99L)).thenReturn(Optional.of(appUserActual));

        mockMvc.perform(get("/api/v1/usuarios/99").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void obtainPorIdDevuelve404SiNoExiste() throws Exception {
        autenticarComo("estudiante@uteq.edu.ec", "ESTUDIANTE", false);
        when(appUserService.obtainPorId(50L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/usuarios/50").header("Authorization", bearer("estudiante@uteq.edu.ec")))
                .andExpect(status().isNotFound());
    }

    // ── searchPorEmail ────────────────────────────────────────────────────────────

    @Test
    void searchPorEmailDevuelveElAppUserEncontrado() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        when(appUserService.obtainPorEmail("ana@uteq.edu.ec")).thenReturn(Optional.of(appUserActual));

        mockMvc.perform(get("/api/v1/usuarios/email/ana@uteq.edu.ec").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void searchPorEmailDevuelve404SiNoExiste() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        when(appUserService.obtainPorEmail("nadie@uteq.edu.ec")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/usuarios/email/nadie@uteq.edu.ec").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isNotFound());
    }

    // ── create / update / activate / deactivate / delete ─────────────────────

    @Test
    void createDevuelve201ConElAppUserCreado() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        RegisterRequest req = new RegisterRequest();
        req.setNombre("Mario");
        req.setApellido("Rojas");
        req.setEmail("mario@uteq.edu.ec");
        req.setPassword("claveSegura123");
        req.setRole("ESTUDIANTE");
        when(appUserService.create(any(AppUser.class))).thenReturn(appUserActual);

        mockMvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", bearer("admin@uteq.edu.ec"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    void createDevuelveBadRequestSiElServicioRechaza() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
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
    void updateDevuelveElAppUserActualizado() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        when(appUserService.update(eq(50L), any(AppUser.class))).thenReturn(appUserActual);

        mockMvc.perform(put("/api/v1/usuarios/50")
                        .header("Authorization", bearer("admin@uteq.edu.ec"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(appUserActual)))
                .andExpect(status().isOk());
    }

    @Test
    void activateInvocaElServicioYDevuelve200() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);

        mockMvc.perform(patch("/api/v1/usuarios/50/activar").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());

        verify(appUserService).activate(50L);
    }

    @Test
    void deactivateInvocaElServicioYDevuelve200() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);

        mockMvc.perform(patch("/api/v1/usuarios/50/desactivar").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());

        verify(appUserService).deactivate(50L);
    }

    @Test
    void deleteDevuelveBadRequestSiElServicioRechazaPorReferencias() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        org.mockito.Mockito.doThrow(new RuntimeException("El usuario tiene solicitudes asociadas"))
                .when(appUserService).delete(50L);

        mockMvc.perform(delete("/api/v1/usuarios/50").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isBadRequest());
    }

    // ── updatePerfil (auto-servicio, sin permission de admin) ───────────────────

    @Test
    void updatePerfilPermiteAlPropioAppUser() throws Exception {
        autenticarComo("estudiante@uteq.edu.ec", "ESTUDIANTE", false);
        PerfilRequest req = new PerfilRequest();
        req.setEmailNotifications("alterno@gmail.com");
        req.setTelefono("0999999999");
        when(appUserService.updatePerfil(50L, "alterno@gmail.com", "0999999999")).thenReturn(appUserActual);

        mockMvc.perform(patch("/api/v1/usuarios/50/perfil")
                        .header("Authorization", bearer("estudiante@uteq.edu.ec"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void updatePerfilRechazaEditarElPerfilDeOtroAppUser() throws Exception {
        autenticarComo("estudiante@uteq.edu.ec", "ESTUDIANTE", false);
        PerfilRequest req = new PerfilRequest();
        req.setTelefono("0999999999");

        mockMvc.perform(patch("/api/v1/usuarios/99/perfil")
                        .header("Authorization", bearer("estudiante@uteq.edu.ec"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        verify(appUserService, never()).updatePerfil(eq(99L), anyString(), anyString());
    }

    // ── supresion de datos personales (RNF-19) ───────────────────────────────────

    @Test
    void solicitarSupresionPermiteAlPropioTitular() throws Exception {
        autenticarComo("estudiante@uteq.edu.ec", "ESTUDIANTE", false);
        SubmissionSupresion submission = new SubmissionSupresion();
        when(supresionDatosService.solicitar(50L)).thenReturn(submission);

        mockMvc.perform(post("/api/v1/usuarios/50/solicitar-supresion")
                        .header("Authorization", bearer("estudiante@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void solicitarSupresionRechazaEnNombreDeOtroAppUser() throws Exception {
        autenticarComo("estudiante@uteq.edu.ec", "ESTUDIANTE", false);

        mockMvc.perform(post("/api/v1/usuarios/99/solicitar-supresion")
                        .header("Authorization", bearer("estudiante@uteq.edu.ec")))
                .andExpect(status().isForbidden());

        verify(supresionDatosService, never()).solicitar(99L);
    }

    @Test
    void listSubmissionsSupresionPermiteAAdmin() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        when(supresionDatosService.list()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/usuarios/solicitudes-supresion").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void resolveSupresionResuelveLaSubmissionComoAdmin() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        when(appUserRepository.findByEmail("admin@uteq.edu.ec")).thenReturn(Optional.of(appUserActual));
        ResolveSupresionRequest req = new ResolveSupresionRequest();
        req.setAceptar(true);
        req.setNotas("Procede");
        when(supresionDatosService.resolve(eq(3L), eq(true), any(), eq("Procede")))
                .thenReturn(new SubmissionSupresion());

        mockMvc.perform(post("/api/v1/usuarios/solicitudes-supresion/3/resolver")
                        .header("Authorization", bearer("admin@uteq.edu.ec"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }
}
