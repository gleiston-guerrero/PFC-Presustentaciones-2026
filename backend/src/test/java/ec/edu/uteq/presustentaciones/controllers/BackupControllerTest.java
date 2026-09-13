package ec.edu.uteq.presustentaciones.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.edu.uteq.presustentaciones.config.SecurityConfig;
import ec.edu.uteq.presustentaciones.dto.BackupInfoDTO;
import ec.edu.uteq.presustentaciones.dto.EstadoRespaldosDTO;
import ec.edu.uteq.presustentaciones.dto.RegistrarPruebaRestauracionRequest;
import ec.edu.uteq.presustentaciones.dto.RespaldoConfigDTO;
import ec.edu.uteq.presustentaciones.entities.RespaldoConfig;
import ec.edu.uteq.presustentaciones.entities.RespaldoPruebaRestauracion;
import ec.edu.uteq.presustentaciones.security.RateLimiterService;
import ec.edu.uteq.presustentaciones.security.jwt.JwtTokenProvider;
import ec.edu.uteq.presustentaciones.services.BackupService;
import ec.edu.uteq.presustentaciones.services.PermisoService;
import ec.edu.uteq.presustentaciones.services.WalPitrService;
import ec.edu.uteq.presustentaciones.services.backup.OrigenRespaldo;
import ec.edu.uteq.presustentaciones.services.backup.TipoRespaldo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BackupController no tenia ningun test dedicado (auditoria de cobertura 2026-09-13, punto
 * "Cobertura de controladores" de la revision del docente-director) pese a exponer operaciones
 * destructivas (restaurar, eliminar respaldos, limpiar WAL). Todo el controlador exige
 * BACKUPS_GESTIONAR a nivel de clase; se cubre ese permiso una vez y luego el camino feliz de
 * cada endpoint.
 */
@WebMvcTest(controllers = BackupController.class)
@Import(SecurityConfig.class)
class BackupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BackupService backupService;

    @MockBean
    private WalPitrService walPitrService;

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

    @MockBean
    private ec.edu.uteq.presustentaciones.repositories.UsuarioRepository usuarioRepository;

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

    @Test
    void listarSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/backups")).andExpect(status().isUnauthorized());
    }

    @Test
    void listarRechazaSinBackupsGestionar() throws Exception {
        autenticarComo("docente@uteq.edu.ec", "DOCENTE", false);

        mockMvc.perform(get("/api/v1/backups").header("Authorization", bearer("docente@uteq.edu.ec")))
                .andExpect(status().isForbidden());

        verify(backupService, never()).listar();
    }

    @Test
    void listarPermiteAAdminConBackupsGestionar() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        when(backupService.listar()).thenReturn(List.of(BackupInfoDTO.builder().nombre("full-1.dump").build()));

        mockMvc.perform(get("/api/v1/backups").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void generarInvocaElServicioConOrigenManualPorDefecto() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        when(backupService.generar(TipoRespaldo.FULL, OrigenRespaldo.MANUAL))
                .thenReturn(BackupInfoDTO.builder().nombre("full-2.dump").build());

        mockMvc.perform(post("/api/v1/backups").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());

        verify(backupService).generar(TipoRespaldo.FULL, OrigenRespaldo.MANUAL);
    }

    @Test
    void generarDiferencialInvocaElServicio() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        when(backupService.generarDiferencial(OrigenRespaldo.MANUAL))
                .thenReturn(BackupInfoDTO.builder().nombre("diff-1.dump").build());

        mockMvc.perform(post("/api/v1/backups/diferencial").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void descargarDevuelveElContenidoComoAdjunto() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        when(backupService.leer("full-1.dump")).thenReturn(new byte[]{1, 2, 3});

        mockMvc.perform(get("/api/v1/backups/full-1.dump/descargar").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void restaurarInvocaElServicio() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);

        mockMvc.perform(post("/api/v1/backups/full-1.dump/restaurar").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());

        verify(backupService).restaurar("full-1.dump");
    }

    @Test
    void eliminarInvocaElServicio() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);

        mockMvc.perform(delete("/api/v1/backups/full-1.dump").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());

        verify(backupService).eliminar("full-1.dump");
    }

    @Test
    void estadoDevuelveElResumenDelPanel() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        when(backupService.estado()).thenReturn(new EstadoRespaldosDTO());

        mockMvc.perform(get("/api/v1/backups/estado").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void obtenerConfigDevuelveLaConfiguracionVigente() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        when(backupService.configDTO()).thenReturn(new RespaldoConfigDTO());

        mockMvc.perform(get("/api/v1/backups/config").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void actualizarConfigDelegaEnElServicio() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        RespaldoConfigDTO dto = new RespaldoConfigDTO();
        dto.setActivo(true);
        dto.setCron("0 0 23 * * SUN");
        dto.setRetenerDiarios(7);
        dto.setRetenerSemanales(4);
        dto.setRetenerMensuales(12);
        dto.setRetenerDiasWal(7);
        dto.setDiferencialActivo(false);
        dto.setCronDiferencial("0 0 3 * * *");
        when(backupService.actualizarConfig(any(RespaldoConfigDTO.class))).thenReturn(dto);

        mockMvc.perform(put("/api/v1/backups/config")
                        .header("Authorization", bearer("admin@uteq.edu.ec"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    void aplicarRetencionDevuelveLosNombresEliminados() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        when(backupService.aplicarRetencion()).thenReturn(List.of("full-viejo.dump"));

        mockMvc.perform(post("/api/v1/backups/retencion").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void listarPruebasDevuelveLaBitacora() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        when(backupService.pruebas()).thenReturn(List.of(RespaldoPruebaRestauracion.builder().build()));

        mockMvc.perform(get("/api/v1/backups/pruebas").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void registrarPruebaDelegaEnElServicioConLosCuatroCampos() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        RegistrarPruebaRestauracionRequest req = new RegistrarPruebaRestauracionRequest();
        req.setRespaldoNombre("full-1.dump");
        req.setResultado("EXITOSA");
        req.setResponsable("admin@uteq.edu.ec");
        req.setNotas("Restauracion de prueba en entorno aislado");
        when(backupService.registrarPrueba(eq("full-1.dump"), eq("EXITOSA"), eq("admin@uteq.edu.ec"), any()))
                .thenReturn(RespaldoPruebaRestauracion.builder().build());

        mockMvc.perform(post("/api/v1/backups/pruebas")
                        .header("Authorization", bearer("admin@uteq.edu.ec"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void estadoWalDevuelveElEstadoDelArchivado() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        when(walPitrService.estado()).thenReturn(new ec.edu.uteq.presustentaciones.dto.EstadoWalDTO());

        mockMvc.perform(get("/api/v1/backups/wal").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void switchWalCierraElSegmentoActual() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        when(walPitrService.forzarSwitchWal()).thenReturn("000000010000000000000005");

        mockMvc.perform(post("/api/v1/backups/wal/switch").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void limpiarWalUsaLaRetencionConfigurada() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        RespaldoConfig config = RespaldoConfig.builder().retenerDiasWal((short) 7).build();
        when(backupService.config()).thenReturn(config);
        when(walPitrService.limpiarWal(7)).thenReturn(2);

        mockMvc.perform(post("/api/v1/backups/wal/limpiar").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());

        verify(walPitrService).limpiarWal(7);
    }

    @Test
    void generarBaseFisicaInvocaElServicio() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        when(walPitrService.generarBaseFisica())
                .thenReturn(ec.edu.uteq.presustentaciones.dto.BaseFisicaDTO.builder().nombre("base-1").build());

        mockMvc.perform(post("/api/v1/backups/bases").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void eliminarBaseInvocaElServicio() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);

        mockMvc.perform(delete("/api/v1/backups/bases/base-1").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());

        verify(walPitrService).eliminarBase("base-1");
    }
}
