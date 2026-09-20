package ec.edu.uteq.presustentaciones.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.repositories.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cubre RF-60 (consulta de auditoría) contra AuditController real, con Postgres real
 * (mismo criterio que {@code PreDefenseApplicationTests}/
 * {@code TopicProposedRepositoryIntegrationTest}: {@code @AutoConfigureTestDatabase(replace = NONE)}
 * para no sustituir el datasource por H2). Hace falta el contexto completo -- no un
 * {@code @WebMvcTest} con {@code permissionService} mockeado como en {@code MinutesControllerTest} --
 * porque el permission AUDITORIA_VER se resuelve con una consulta real (role_id -> role_permissions,
 * ver {@code PermissionRepository.appUserTienePermission}) y porque la garantía de "nunca se guarda
 * el password" la da el trigger real {@code fn_audit_generica} (V15__audit.sql), no
 * código Java que se pueda mockear: solo se puede comprobar contra la fila que ese trigger
 * escribió de verdad.
 *
 * <p>Usa los appUsers semilla (ver {@code PreDefenseApplication.initDemoData}):
 * admin@uteq.edu.ec (ADMIN, único role con AUDITORIA_VER -- V15 lo asigna solo a role_id=1) y
 * demo@uteq.edu.ec (COORDINADOR).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.test.database.replace=NONE")
@AutoConfigureTestDatabase(replace = Replace.NONE)
@AutoConfigureMockMvc
@Transactional
class AuditControllerTest {

    private static final String ADMIN = "admin@uteq.edu.ec";
    private static final String COORDINADOR = "demo@uteq.edu.ec";
    private static final String DOCENTE = "docente@uteq.edu.ec";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void withoutAuthenticateReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/auditoria/paginado"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = DOCENTE)
    void withoutPermissionAuditViewReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/auditoria/paginado"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = COORDINADOR)
    void coordinatorAlsoReceives403BecausePermissionIsExclusiveOfAdmin() throws Exception {
        // Asimetria deliberada (SRS §6.4): a diferencia de otros permissions administrativos que
        // ADMIN y COORDINADOR comparten, AUDITORIA_VER solo se asigna al role ADMIN en V15.
        mockMvc.perform(get("/api/v1/auditoria/paginado"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = ADMIN)
    void adminWithPermissionGetsPageFilteredByTable() throws Exception {
        // Garantiza al menos una fila real con tabla=appUsers antes de filtrar.
        modifyDemoAppUserPhone();

        MvcResult result = mockMvc.perform(get("/api/v1/auditoria/paginado")
                        .param("tabla", "usuarios")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();

        // Un ResponseBodyAdvice global envuelve toda respuesta en ResponseWrapper (success/data/...),
        // aunque el controller devuelva el Page directamente: el body real es data.content, no content.
        JsonNode contenido = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("content");
        assertNotNull(contenido);
        assertTrue(contenido.size() > 0, "Debe existir al menos un evento de auditoria para 'usuarios'");
        for (JsonNode row : contenido) {
            assertEquals("usuarios", row.get("tabla").asText());
        }
    }

    @Test
    @WithMockUser(username = ADMIN)
    void auditEntriesOfAppUsersNeverExposePassword() throws Exception {
        Long recordId = modifyDemoAppUserPhone();

        MvcResult result = mockMvc.perform(get("/api/v1/auditoria/paginado")
                        .param("tabla", "usuarios")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();

        String cuerpo = result.getResponse().getContentAsString();
        // Comprobacion sobre el resultado completo tal cual lo recibe el cliente: ni siquiera
        // como texto plano dentro del JSON (datos_anteriores/datos_nuevos viajan como string).
        assertFalse(cuerpo.contains("\"password\""),
                "La respuesta de auditoria no debe exponer el campo password en ninguna entrada: " + cuerpo);

        JsonNode contenido = objectMapper.readTree(cuerpo).get("data").get("content");
        JsonNode rowDelCambio = searchByRecordId(contenido, recordId);
        assertNotNull(rowDelCambio, "Debe aparecer el evento generado por el cambio de telefono de esta prueba");

        JsonNode dataNuevos = objectMapper.readTree(rowDelCambio.get("datosNuevos").asText());
        assertFalse(dataNuevos.has("password"), "fn_auditoria_generica debe haber quitado 'password' de datos_nuevos");
        // Prueba de que sí se guardó el resto de la fila (no es un objeto vacío por otra razón).
        assertTrue(dataNuevos.has("telefono"));

        if (rowDelCambio.hasNonNull("datosAnteriores")) {
            JsonNode dataAnteriores = objectMapper.readTree(rowDelCambio.get("datosAnteriores").asText());
            assertFalse(dataAnteriores.has("password"), "fn_auditoria_generica debe haber quitado 'password' de datos_anteriores");
        }
    }

    @Test
    @WithMockUser(username = ADMIN)
    void tablesAuditedReturnsCatalogFixedOfTables() throws Exception {
        mockMvc.perform(get("/api/v1/auditoria/tablas"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("usuarios")));
    }

    private JsonNode searchByRecordId(JsonNode contenido, Long recordId) {
        for (JsonNode row : contenido) {
            if (row.get("registroId").asLong() == recordId) {
                return row;
            }
        }
        return null;
    }

    /**
     * Dispara trg_audit_appUsers con un UPDATE real e inofensivo (cambia el teléfono del
     * appUser teacher demo). Corre dentro de la transacción de la prueba (@Transactional hace
     * rollback al terminar), así que no deja rastro en los datos semilla.
     */
    private Long modifyDemoAppUserPhone() {
        AppUser appUser = appUserRepository.findByEmail(DOCENTE).orElseThrow();
        appUser.setPhone("099" + (System.nanoTime() % 10_000_000L));
        appUserRepository.saveAndFlush(appUser);
        return appUser.getId();
    }
}
