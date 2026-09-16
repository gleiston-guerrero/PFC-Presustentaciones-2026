package ec.edu.uteq.presustentaciones.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.edu.uteq.presustentaciones.config.SecurityConfig;
import ec.edu.uteq.presustentaciones.dto.BackupInfoDTO;
import ec.edu.uteq.presustentaciones.dto.EstadoBackupsDTO;
import ec.edu.uteq.presustentaciones.dto.RegisterPruebaRestauracionRequest;
import ec.edu.uteq.presustentaciones.dto.BackupConfigDTO;
import ec.edu.uteq.presustentaciones.entities.BackupConfig;
import ec.edu.uteq.presustentaciones.entities.BackupPruebaRestauracion;
import ec.edu.uteq.presustentaciones.security.RateLimiterService;
import ec.edu.uteq.presustentaciones.security.jwt.JwtTokenProvider;
import ec.edu.uteq.presustentaciones.services.BackupService;
import ec.edu.uteq.presustentaciones.services.PermissionService;
import ec.edu.uteq.presustentaciones.services.WalPitrService;
import ec.edu.uteq.presustentaciones.services.backup.OrigenBackup;
import ec.edu.uteq.presustentaciones.services.backup.TipoBackup;
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
 * BackupController no tenia ningun test dedicado (audit de cobertura 2026-09-13, punto
 * "Cobertura de controladores" de la revision del teacher-director) pese a exponer operaciones
 * destructivas (restore, delete backups, limpiar WAL). Todo el controlador exige
 * BACKUPS_GESTIONAR a nivel de clase; se cubre ese permission una vez y luego el camino feliz de
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

    @MockBean(name = "permissionService")
    private PermissionService permissionService;

    @MockBean
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @MockBean
    private ec.edu.uteq.presustentaciones.repositories.RoleAppUserRepository roleAppUserRepository;

    @MockBean
    private ec.edu.uteq.presustentaciones.repositories.AppUserRepository appUserRepository;

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

    @Test
    void listSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/backups")).andExpect(status().isUnauthorized());
    }

    @Test
    void listRechazaSinBackupsGestionar() throws Exception {
        autenticarComo("docente@uteq.edu.ec", "DOCENTE", false);

        mockMvc.perform(get("/api/v1/backups").header("Authorization", bearer("docente@uteq.edu.ec")))
                .andExpect(status().isForbidden());

        verify(backupService, never()).list();
    }

    @Test
    void listPermiteAAdminConBackupsGestionar() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        when(backupService.list()).thenReturn(List.of(BackupInfoDTO.builder().nombre("full-1.dump").build()));

        mockMvc.perform(get("/api/v1/backups").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void generateInvocaElServicioConOrigenManualPorDefecto() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        when(backupService.generate(TipoBackup.FULL, OrigenBackup.MANUAL))
                .thenReturn(BackupInfoDTO.builder().nombre("full-2.dump").build());

        mockMvc.perform(post("/api/v1/backups").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());

        verify(backupService).generate(TipoBackup.FULL, OrigenBackup.MANUAL);
    }

    @Test
    void generateDiferencialInvocaElServicio() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        when(backupService.generateDiferencial(OrigenBackup.MANUAL))
                .thenReturn(BackupInfoDTO.builder().nombre("diff-1.dump").build());

        mockMvc.perform(post("/api/v1/backups/diferencial").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void downloadDevuelveElContenidoComoAdjunto() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        when(backupService.leer("full-1.dump")).thenReturn(new byte[]{1, 2, 3});

        mockMvc.perform(get("/api/v1/backups/full-1.dump/descargar").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void restoreInvocaElServicio() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);

        mockMvc.perform(post("/api/v1/backups/full-1.dump/restaurar").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());

        verify(backupService).restore("full-1.dump");
    }

    @Test
    void deleteInvocaElServicio() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);

        mockMvc.perform(delete("/api/v1/backups/full-1.dump").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());

        verify(backupService).delete("full-1.dump");
    }

    @Test
    void estadoDevuelveElResumenDelPanel() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        when(backupService.estado()).thenReturn(new EstadoBackupsDTO());

        mockMvc.perform(get("/api/v1/backups/estado").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void obtainConfigDevuelveLaConfiguracionVigente() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        when(backupService.configDTO()).thenReturn(new BackupConfigDTO());

        mockMvc.perform(get("/api/v1/backups/config").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void updateConfigDelegaEnElServicio() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        BackupConfigDTO dto = new BackupConfigDTO();
        dto.setActivo(true);
        dto.setCron("0 0 23 * * SUN");
        dto.setRetenerDiarios(7);
        dto.setRetenerSemanales(4);
        dto.setRetenerMensuales(12);
        dto.setRetenerDiasWal(7);
        dto.setDiferencialActivo(false);
        dto.setCronDiferencial("0 0 3 * * *");
        when(backupService.updateConfig(any(BackupConfigDTO.class))).thenReturn(dto);

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
    void listPruebasDevuelveLaBitacora() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        when(backupService.pruebas()).thenReturn(List.of(BackupPruebaRestauracion.builder().build()));

        mockMvc.perform(get("/api/v1/backups/pruebas").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void registerPruebaDelegaEnElServicioConLosCuatroCampos() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        RegisterPruebaRestauracionRequest req = new RegisterPruebaRestauracionRequest();
        req.setBackupNombre("full-1.dump");
        req.setResultado("EXITOSA");
        req.setResponsable("admin@uteq.edu.ec");
        req.setNotas("Restauracion de prueba en entorno aislado");
        when(backupService.registerPrueba(eq("full-1.dump"), eq("EXITOSA"), eq("admin@uteq.edu.ec"), any()))
                .thenReturn(BackupPruebaRestauracion.builder().build());

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
        BackupConfig config = BackupConfig.builder().retenerDiasWal((short) 7).build();
        when(backupService.config()).thenReturn(config);
        when(walPitrService.limpiarWal(7)).thenReturn(2);

        mockMvc.perform(post("/api/v1/backups/wal/limpiar").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());

        verify(walPitrService).limpiarWal(7);
    }

    @Test
    void generateBaseFisicaInvocaElServicio() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);
        when(walPitrService.generateBaseFisica())
                .thenReturn(ec.edu.uteq.presustentaciones.dto.BaseFisicaDTO.builder().nombre("base-1").build());

        mockMvc.perform(post("/api/v1/backups/bases").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void deleteBaseInvocaElServicio() throws Exception {
        autenticarComo("admin@uteq.edu.ec", "ADMIN", true);

        mockMvc.perform(delete("/api/v1/backups/bases/base-1").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());

        verify(walPitrService).deleteBase("base-1");
    }
}
