package ec.edu.uteq.presustentaciones.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.edu.uteq.presustentaciones.config.SecurityConfig;
import ec.edu.uteq.presustentaciones.dto.ChatRequest;
import ec.edu.uteq.presustentaciones.dto.ChatResponse;
import ec.edu.uteq.presustentaciones.security.RateLimiterService;
import ec.edu.uteq.presustentaciones.security.jwt.JwtTokenProvider;
import ec.edu.uteq.presustentaciones.services.ChatbotService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P7 (examen suspenso): ChatbotController no tenía ninguna prueba que ejercitara el endpoint
 * real ({@code POST /api/v1/chatbot/ask}) -- {@link ChatbotControllerTest} solo invoca el
 * método Java del controlador con mocks, sin pasar por el dispatcher de Spring MVC ni por la
 * cadena de filtros de seguridad. Mismo patrón {@code @WebMvcTest + SecurityConfig} real que
 * {@code MeControllerTest}, para probar la ruta HTTP completa (serialización JSON, filtro JWT,
 * {@code @PreAuthorize("isAuthenticated()")}) end-to-end.
 *
 * <p><b>Corregido el 2026-09-19.</b> La revisión del 18-sep observó que «en la prueba MockMvc el
 * servicio sigue simulado»: esta clase declaraba {@code @MockBean ChatbotService} y luego afirmaba
 * probar el chatbot. Lo que probaba era el transporte --ruta, filtro JWT, serialización-- con la
 * lógica del asistente sustituida por un {@code when(...).thenReturn(...)}, de modo que la prueba
 * habría pasado igual con el servicio roto.</p>
 *
 * <p>Simularlo además no hacía falta: {@link ChatbotService} no tiene dependencias externas --sin
 * base de datos, sin HTTP, sin estado--, solo lee {@code SecurityContextHolder} y hace coincidencia
 * de palabras. Ahora se importa el servicio <b>real</b> y las aserciones son sobre su salida
 * verdadera, no sobre un valor preparado por la propia prueba.</p>
 */
@WebMvcTest(controllers = ChatbotController.class)
@Import({SecurityConfig.class, ChatbotService.class})
class ChatbotControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    @MockBean
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @MockBean
    private ec.edu.uteq.presustentaciones.repositories.RoleAppUserRepository roleAppUserRepository;

    @MockBean
    private ec.edu.uteq.presustentaciones.repositories.AppUserRepository appUserRepository;

    @Test
    void sinTokenDevuelve401() throws Exception {
        ChatRequest request = new ChatRequest();
        request.setMessage("hola");

        mockMvc.perform(post("/api/v1/chatbot/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withTokenValidDevuelveLaRespuestaDelChatbot() throws Exception {
        String email = "estudiante@uteq.edu.ec";
        String token = "token-" + email;
        UserDetails userDetails = new User(email, "x",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ESTUDIANTE")));
        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken(token)).thenReturn(email);
        when(userDetailsService.loadUserByUsername(email)).thenReturn(userDetails);

        ChatRequest request = new ChatRequest();
        request.setMessage("¿Cómo subo mi anteproyecto?");

        // La respuesta la produce el ChatbotService REAL: no hay when(...).thenReturn(...)
        // que la prepare. Si su lógica de coincidencia se rompe, esta prueba falla.
        mockMvc.perform(post("/api/v1/chatbot/ask")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.response",
                        org.hamcrest.Matchers.containsString("anteproyecto es parte del proceso")))
                .andExpect(jsonPath("$.data.options",
                        org.hamcrest.Matchers.hasItem("Sustentación")))
                .andExpect(jsonPath("$.message").value("Consulta procesada correctamente"));
    }

    @Test
    void mensajeSinPalabraConocidaDevuelveLaRespuestaPorDefectoReal() throws Exception {
        String email = "estudiante@uteq.edu.ec";
        String token = "token-" + email;
        UserDetails userDetails = new User(email, "x",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ESTUDIANTE")));
        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken(token)).thenReturn(email);
        when(userDetailsService.loadUserByUsername(email)).thenReturn(userDetails);

        ChatRequest request = new ChatRequest();
        request.setMessage("cuanto cuesta un pasaje a Guayaquil");

        mockMvc.perform(post("/api/v1/chatbot/ask")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.response",
                        org.hamcrest.Matchers.containsString("No estoy seguro de cómo responder")));
    }

    @Test
    void cadaPalabraClaveDelServicioRealDevuelveSuPropiaRespuesta() throws Exception {
        String email = "estudiante@uteq.edu.ec";
        String token = "token-" + email;
        UserDetails userDetails = new User(email, "x",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ESTUDIANTE")));
        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken(token)).thenReturn(email);
        when(userDetailsService.loadUserByUsername(email)).thenReturn(userDetails);

        // Cada par es (lo que escribe el usuario, un fragmento que SOLO aparece en
        // la rama correspondiente del servicio real). Si dos ramas se cruzan por un
        // cambio en el orden de los `if`, esto lo detecta.
        String[][] casos = {
                {"quiero ver mi solicitud", "módulo de Solicitudes"},
                {"no me llegan las notificaciones", "centro de notificaciones"},
                {"como edito mi perfil", "Mi Perfil desde el menú de usuario"},
                {"cuando es mi sustentacion", "información disponible de tu sustentación"},
                {"olvide mi contraseña", "opciones de seguridad"},
                {"ayuda", "Puedo ayudarte con Solicitudes"},
        };

        for (String[] caso : casos) {
            ChatRequest request = new ChatRequest();
            request.setMessage(caso[0]);

            mockMvc.perform(post("/api/v1/chatbot/ask")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.response",
                            org.hamcrest.Matchers.containsString(caso[1])));
        }
    }

    @Test
    void messageMalFormadoDevuelve400() throws Exception {
        String email = "estudiante@uteq.edu.ec";
        String token = "token-" + email;
        UserDetails userDetails = new User(email, "x",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ESTUDIANTE")));
        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken(token)).thenReturn(email);
        when(userDetailsService.loadUserByUsername(email)).thenReturn(userDetails);

        mockMvc.perform(post("/api/v1/chatbot/ask")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ esto no es json valido"))
                .andExpect(status().isBadRequest());
    }
}
