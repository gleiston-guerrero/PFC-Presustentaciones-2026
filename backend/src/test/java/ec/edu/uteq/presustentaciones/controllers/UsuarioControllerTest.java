package ec.edu.uteq.presustentaciones.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.edu.uteq.presustentaciones.config.SecurityConfig;
import ec.edu.uteq.presustentaciones.dto.PerfilRequest;
import ec.edu.uteq.presustentaciones.dto.ResolverSupresionRequest;
import ec.edu.uteq.presustentaciones.entities.SolicitudSupresion;
import ec.edu.uteq.presustentaciones.entities.Usuario;
import ec.edu.uteq.presustentaciones.repositories.UsuarioRepository;
import ec.edu.uteq.presustentaciones.security.RateLimiterService;
import ec.edu.uteq.presustentaciones.security.dto.RegisterRequest;
import ec.edu.uteq.presustentaciones.security.jwt.JwtTokenProvider;
import ec.edu.uteq.presustentaciones.services.IUsuarioService;
import ec.edu.uteq.presustentaciones.services.PermisoService;
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
 * UsuarioController no tenia ningun test dedicado (auditoria de cobertura 2026-09-13, punto
 * "Cobertura de controladores" de la revision del docente-director) pese a exponer la gestion
 * completa de cuentas (CRUD, activar/desactivar, supresion RNF-19) y el hallazgo de auditoria
 * documentado en la clase (mass-assignment via RegisterRequest en vez de la entidad cruda).
 * Cubre el control de propiedad real (esUsuarioActual/esUsuarioActualOAdmin) ademas del
 * permiso USUARIOS_GESTIONAR.
 */
@WebMvcTest(controllers = UsuarioController.class)
@Import(SecurityConfig.class)
class UsuarioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IUsuarioService usuarioService;

    @MockBean
    private SupresionDatosService supresionDatosService;

    @MockBean
    private UsuarioRepository usuarioRepository;

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

    @MockBean(name = "permisoService")
    private PermisoService permisoService;

    @MockBean
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @MockBean
    private ec.edu.uteq.presustentaciones.repositories.RolUsuarioRepository rolUsuarioRepository;

    private Usuario usuarioActual;

    @BeforeEach
    void setUp() {
        usuarioActual = new Usuario();
        usuarioActual.setId(50L);
        usuarioActual.setNombre("Ana");
        usuarioActual.setApellido("Torres");
        usuarioActual.setEmail("estudiante@uteq.edu.ec");
        usuarioActual.setActivo(true);

        when(usuarioRepository.findByEmail("estudiante@uteq.edu.ec")).thenReturn(Optional.of(usuarioActual));
    }

    private void autenticarComo(String email, String rol, boolean tienePermiso) {
        String token = "token-" + email;
        UserDetails userDetails = new User(email, "x",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + rol)));
        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken(token)).thenReturn(email);
        when(userDetailsService.loadUserByUsername(email)).thenReturn(userDetails);
        when(permisoService.tienePermiso(any(), any())).thenReturn(tienePermiso);
    }

    private String bearer(String email) {
        return "Bearer token-" + email;
    }

    // ── listarTodos / listarPaginado / listarActivos ─────────────────────────────

    @Test
    void listarTodosSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listarTodosRechazaSinPermisoUsuariosGestionar() throws Exception {
        autenticarComo("docente@uteq.edu.ec", "DOCENTE", false);

        mockMvc.perform(get("/api/v1/usuarios").header("Authorization", bearer("docente@uteq.edu.ec")))
                .andExpect(status().isForbidden());
    }

    @Test
    void listarTodosPermiteAAdminConPermiso() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        when(usuarioService.listarTodos()).thenReturn(List.of(usuarioActual));

        mockMvc.perform(get("/api/v1/usuarios").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void listarPaginadoDelegaEnElServicioConLosParametros() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        Page<Usuario> pagina = new PageImpl<>(List.of(usuarioActual));
        when(usuarioService.listarPaginado(0, 20, "torres")).thenReturn(pagina);

        mockMvc.perform(get("/api/v1/usuarios/paginado")
                        .param("page", "0").param("size", "20").param("q", "torres")
                        .header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void listarActivosPermiteAAdmin() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        when(usuarioService.listarActivos()).thenReturn(List.of(usuarioActual));

        mockMvc.perform(get("/api/v1/usuarios/activos").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    // ── obtenerPorId (control de propiedad real, no solo permiso) ────────────────

    @Test
    void obtenerPorIdPermiteAlPropioUsuarioSinPermisoAdmin() throws Exception {
        autenticarComo("estudiante@uteq.edu.ec", "ESTUDIANTE", false);
        when(usuarioService.obtenerPorId(50L)).thenReturn(Optional.of(usuarioActual));

        mockMvc.perform(get("/api/v1/usuarios/50").header("Authorization", bearer("estudiante@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void obtenerPorIdRechazaConsultarElPerfilDeOtroUsuario() throws Exception {
        // Caso IDOR: estudiante 50 intenta ver la ficha del usuario 99 cambiando el id de la URL.
        autenticarComo("estudiante@uteq.edu.ec", "ESTUDIANTE", false);

        mockMvc.perform(get("/api/v1/usuarios/99").header("Authorization", bearer("estudiante@uteq.edu.ec")))
                .andExpect(status().isForbidden());

        verify(usuarioService, never()).obtenerPorId(99L);
    }

    @Test
    void obtenerPorIdPermiteAAdminConsultarCualquierUsuario() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        when(usuarioService.obtenerPorId(99L)).thenReturn(Optional.of(usuarioActual));

        mockMvc.perform(get("/api/v1/usuarios/99").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void obtenerPorIdDevuelve404SiNoExiste() throws Exception {
        autenticarComo("estudiante@uteq.edu.ec", "ESTUDIANTE", false);
        when(usuarioService.obtenerPorId(50L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/usuarios/50").header("Authorization", bearer("estudiante@uteq.edu.ec")))
                .andExpect(status().isNotFound());
    }

    // ── buscarPorEmail ────────────────────────────────────────────────────────────

    @Test
    void buscarPorEmailDevuelveElUsuarioEncontrado() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        when(usuarioService.obtenerPorEmail("ana@uteq.edu.ec")).thenReturn(Optional.of(usuarioActual));

        mockMvc.perform(get("/api/v1/usuarios/email/ana@uteq.edu.ec").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void buscarPorEmailDevuelve404SiNoExiste() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        when(usuarioService.obtenerPorEmail("nadie@uteq.edu.ec")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/usuarios/email/nadie@uteq.edu.ec").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isNotFound());
    }

    // ── crear / actualizar / activar / desactivar / eliminar ─────────────────────

    @Test
    void crearDevuelve201ConElUsuarioCreado() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        RegisterRequest req = new RegisterRequest();
        req.setNombre("Mario");
        req.setApellido("Rojas");
        req.setEmail("mario@uteq.edu.ec");
        req.setPassword("claveSegura123");
        req.setRol("ESTUDIANTE");
        when(usuarioService.crear(any(Usuario.class))).thenReturn(usuarioActual);

        mockMvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", bearer("admin@uteq.edu.ec"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    void crearDevuelveBadRequestSiElServicioRechaza() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        RegisterRequest req = new RegisterRequest();
        req.setNombre("Mario");
        req.setApellido("Rojas");
        req.setEmail("mario@uteq.edu.ec");
        req.setPassword("claveSegura123");
        req.setRol("ESTUDIANTE");
        when(usuarioService.crear(any(Usuario.class))).thenThrow(new RuntimeException("Email ya registrado"));

        mockMvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", bearer("admin@uteq.edu.ec"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void actualizarDevuelveElUsuarioActualizado() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        when(usuarioService.actualizar(eq(50L), any(Usuario.class))).thenReturn(usuarioActual);

        mockMvc.perform(put("/api/v1/usuarios/50")
                        .header("Authorization", bearer("admin@uteq.edu.ec"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(usuarioActual)))
                .andExpect(status().isOk());
    }

    @Test
    void activarInvocaElServicioYDevuelve200() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);

        mockMvc.perform(patch("/api/v1/usuarios/50/activar").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());

        verify(usuarioService).activar(50L);
    }

    @Test
    void desactivarInvocaElServicioYDevuelve200() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);

        mockMvc.perform(patch("/api/v1/usuarios/50/desactivar").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());

        verify(usuarioService).desactivar(50L);
    }

    @Test
    void eliminarDevuelveBadRequestSiElServicioRechazaPorReferencias() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        org.mockito.Mockito.doThrow(new RuntimeException("El usuario tiene solicitudes asociadas"))
                .when(usuarioService).eliminar(50L);

        mockMvc.perform(delete("/api/v1/usuarios/50").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isBadRequest());
    }

    // ── actualizarPerfil (auto-servicio, sin permiso de admin) ───────────────────

    @Test
    void actualizarPerfilPermiteAlPropioUsuario() throws Exception {
        autenticarComo("estudiante@uteq.edu.ec", "ESTUDIANTE", false);
        PerfilRequest req = new PerfilRequest();
        req.setEmailNotificaciones("alterno@gmail.com");
        req.setTelefono("0999999999");
        when(usuarioService.actualizarPerfil(50L, "alterno@gmail.com", "0999999999")).thenReturn(usuarioActual);

        mockMvc.perform(patch("/api/v1/usuarios/50/perfil")
                        .header("Authorization", bearer("estudiante@uteq.edu.ec"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void actualizarPerfilRechazaEditarElPerfilDeOtroUsuario() throws Exception {
        autenticarComo("estudiante@uteq.edu.ec", "ESTUDIANTE", false);
        PerfilRequest req = new PerfilRequest();
        req.setTelefono("0999999999");

        mockMvc.perform(patch("/api/v1/usuarios/99/perfil")
                        .header("Authorization", bearer("estudiante@uteq.edu.ec"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        verify(usuarioService, never()).actualizarPerfil(eq(99L), anyString(), anyString());
    }

    // ── supresion de datos personales (RNF-19) ───────────────────────────────────

    @Test
    void solicitarSupresionPermiteAlPropioTitular() throws Exception {
        autenticarComo("estudiante@uteq.edu.ec", "ESTUDIANTE", false);
        SolicitudSupresion solicitud = new SolicitudSupresion();
        when(supresionDatosService.solicitar(50L)).thenReturn(solicitud);

        mockMvc.perform(post("/api/v1/usuarios/50/solicitar-supresion")
                        .header("Authorization", bearer("estudiante@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void solicitarSupresionRechazaEnNombreDeOtroUsuario() throws Exception {
        autenticarComo("estudiante@uteq.edu.ec", "ESTUDIANTE", false);

        mockMvc.perform(post("/api/v1/usuarios/99/solicitar-supresion")
                        .header("Authorization", bearer("estudiante@uteq.edu.ec")))
                .andExpect(status().isForbidden());

        verify(supresionDatosService, never()).solicitar(99L);
    }

    @Test
    void listarSolicitudesSupresionPermiteAAdmin() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        when(supresionDatosService.listar()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/usuarios/solicitudes-supresion").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void resolverSupresionResuelveLaSolicitudComoAdmin() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        when(usuarioRepository.findByEmail("admin@uteq.edu.ec")).thenReturn(Optional.of(usuarioActual));
        ResolverSupresionRequest req = new ResolverSupresionRequest();
        req.setAceptar(true);
        req.setNotas("Procede");
        when(supresionDatosService.resolver(eq(3L), eq(true), any(), eq("Procede")))
                .thenReturn(new SolicitudSupresion());

        mockMvc.perform(post("/api/v1/usuarios/solicitudes-supresion/3/resolver")
                        .header("Authorization", bearer("admin@uteq.edu.ec"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }
}
