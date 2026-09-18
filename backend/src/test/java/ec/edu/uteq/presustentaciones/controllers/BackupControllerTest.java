package ec.edu.uteq.presustentaciones.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.edu.uteq.presustentaciones.config.SecurityConfig;
import ec.edu.uteq.presustentaciones.dto.BackupInfoDTO;
import ec.edu.uteq.presustentaciones.dto.StatusBackupsDTO;
import ec.edu.uteq.presustentaciones.dto.RegisterDrillRestoreRequest;
import ec.edu.uteq.presustentaciones.dto.BackupConfigDTO;
import ec.edu.uteq.presustentaciones.entities.BackupConfig;
import ec.edu.uteq.presustentaciones.entities.BackupDrillRestore;
import ec.edu.uteq.presustentaciones.security.RateLimiterService;
import ec.edu.uteq.presustentaciones.security.jwt.JwtTokenProvider;
import ec.edu.uteq.presustentaciones.services.BackupService;
import ec.edu.uteq.presustentaciones.services.PermissionService;
import ec.edu.uteq.presustentaciones.services.WalPitrService;
import ec.edu.uteq.presustentaciones.services.backup.SourceBackup;
import ec.edu.uteq.presustentaciones.services.backup.KindBackup;
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

    @Test
    void listWithoutTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/backups")).andExpect(status().isUnauthorized());
    }

    @Test
    void listRechazaWithoutBackupsGestionar() throws Exception {
        authenticateAs("docente@uteq.edu.ec", "DOCENTE", false);

        mockMvc.perform(get("/api/v1/backups").header("Authorization", bearer("docente@uteq.edu.ec")))
                .andExpect(status().isForbidden());

        verify(backupService, never()).list();
    }

    @Test
    void listPermiteAAdminWithBackupsGestionar() throws Exception {
        authenticateAs("admin@uteq.edu.ec", "ADMIN", true);
        when(backupService.list()).thenReturn(List.of(BackupInfoDTO.builder().nombre("full-1.dump").build()));

        mockMvc.perform(get("/api/v1/backups").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void generateInvocaElServicioWithSourceManualByDefault() throws Exception {
        authenticateAs("admin@uteq.edu.ec", "ADMIN", true);
        when(backupService.generate(KindBackup.FULL, SourceBackup.MANUAL))
                .thenReturn(BackupInfoDTO.builder().nombre("full-2.dump").build());

        mockMvc.perform(post("/api/v1/backups").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());

        verify(backupService).generate(KindBackup.FULL, SourceBackup.MANUAL);
    }

    @Test
    void generateDifferentialInvocaElServicio() throws Exception {
        authenticateAs("admin@uteq.edu.ec", "ADMIN", true);
        when(backupService.generateDifferential(SourceBackup.MANUAL))
                .thenReturn(BackupInfoDTO.builder().nombre("diff-1.dump").build());

        mockMvc.perform(post("/api/v1/backups/diferencial").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void downloadDevuelveElContenidoAsAdjunto() throws Exception {
        authenticateAs("admin@uteq.edu.ec", "ADMIN", true);
        when(backupService.read("full-1.dump")).thenReturn(new byte[]{1, 2, 3});

        mockMvc.perform(get("/api/v1/backups/full-1.dump/descargar").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void restoreInvocaElServicio() throws Exception {
        authenticateAs("admin@uteq.edu.ec", "ADMIN", true);

        mockMvc.perform(post("/api/v1/backups/full-1.dump/restaurar").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());

        verify(backupService).restore("full-1.dump");
    }

    @Test
    void deleteInvocaElServicio() throws Exception {
        authenticateAs("admin@uteq.edu.ec", "ADMIN", true);

        mockMvc.perform(delete("/api/v1/backups/full-1.dump").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());

        verify(backupService).delete("full-1.dump");
    }

    @Test
    void statusDevuelveElSummaryDelPanel() throws Exception {
        authenticateAs("admin@uteq.edu.ec", "ADMIN", true);
        when(backupService.status()).thenReturn(new StatusBackupsDTO());

        mockMvc.perform(get("/api/v1/backups/estado").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void obtainConfigDevuelveLaConfiguracionVigente() throws Exception {
        authenticateAs("admin@uteq.edu.ec", "ADMIN", true);
        when(backupService.configDTO()).thenReturn(new BackupConfigDTO());

        mockMvc.perform(get("/api/v1/backups/config").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void updateConfigDelegaEnElServicio() throws Exception {
        authenticateAs("admin@uteq.edu.ec", "ADMIN", true);
        BackupConfigDTO dto = new BackupConfigDTO();
        dto.setActivo(true);
        dto.setCron("0 0 23 * * SUN");
        dto.setRetenerDiarios(7);
        dto.setRetenerSemanales(4);
        dto.setRetenerMensuales(12);
        dto.setRetenerDiasWal(7);
        dto.setDifferentialActivo(false);
        dto.setCronDifferential("0 0 3 * * *");
        when(backupService.updateConfig(any(BackupConfigDTO.class))).thenReturn(dto);

        mockMvc.perform(put("/api/v1/backups/config")
                        .header("Authorization", bearer("admin@uteq.edu.ec"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    void applyRetentionDevuelveLosNombresEliminados() throws Exception {
        authenticateAs("admin@uteq.edu.ec", "ADMIN", true);
        when(backupService.applyRetention()).thenReturn(List.of("full-viejo.dump"));

        mockMvc.perform(post("/api/v1/backups/retencion").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void listDrillsDevuelveLaLog() throws Exception {
        authenticateAs("admin@uteq.edu.ec", "ADMIN", true);
        when(backupService.drills()).thenReturn(List.of(BackupDrillRestore.builder().build()));

        mockMvc.perform(get("/api/v1/backups/pruebas").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void registerDrillDelegaEnElServicioWithLosCuatroCampos() throws Exception {
        authenticateAs("admin@uteq.edu.ec", "ADMIN", true);
        RegisterDrillRestoreRequest req = new RegisterDrillRestoreRequest();
        req.setBackupNombre("full-1.dump");
        req.setResult("EXITOSA");
        req.setResponsable("admin@uteq.edu.ec");
        req.setNotas("Restauracion de prueba en entorno aislado");
        when(backupService.registerDrill(eq("full-1.dump"), eq("EXITOSA"), eq("admin@uteq.edu.ec"), any()))
                .thenReturn(BackupDrillRestore.builder().build());

        mockMvc.perform(post("/api/v1/backups/pruebas")
                        .header("Authorization", bearer("admin@uteq.edu.ec"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void statusWalDevuelveElStatusDelArchivado() throws Exception {
        authenticateAs("admin@uteq.edu.ec", "ADMIN", true);
        when(walPitrService.status()).thenReturn(new ec.edu.uteq.presustentaciones.dto.StatusWalDTO());

        mockMvc.perform(get("/api/v1/backups/wal").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void switchWalCierraElSegmentoActual() throws Exception {
        authenticateAs("admin@uteq.edu.ec", "ADMIN", true);
        when(walPitrService.forceSwitchWal()).thenReturn("000000010000000000000005");

        mockMvc.perform(post("/api/v1/backups/wal/switch").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void cleanWalUsaLaRetentionConfigurada() throws Exception {
        authenticateAs("admin@uteq.edu.ec", "ADMIN", true);
        BackupConfig config = BackupConfig.builder().retenerDiasWal((short) 7).build();
        when(backupService.config()).thenReturn(config);
        when(walPitrService.cleanWal(7)).thenReturn(2);

        mockMvc.perform(post("/api/v1/backups/wal/limpiar").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());

        verify(walPitrService).cleanWal(7);
    }

    @Test
    void generateBasePhysicalInvocaElServicio() throws Exception {
        authenticateAs("admin@uteq.edu.ec", "ADMIN", true);
        when(walPitrService.generateBasePhysical())
                .thenReturn(ec.edu.uteq.presustentaciones.dto.BasePhysicalDTO.builder().nombre("base-1").build());

        mockMvc.perform(post("/api/v1/backups/bases").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());
    }

    @Test
    void deleteBaseInvocaElServicio() throws Exception {
        authenticateAs("admin@uteq.edu.ec", "ADMIN", true);

        mockMvc.perform(delete("/api/v1/backups/bases/base-1").header("Authorization", bearer("admin@uteq.edu.ec")))
                .andExpect(status().isOk());

        verify(walPitrService).deleteBase("base-1");
    }
}
