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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P7 (examen suspenso): ChatbotController no tenía ninguna prueba que ejercitara el endpoint
 * real ({@code POST /api/v1/chatbot/ask}) -- {@link ChatbotControllerTest} solo invoca el
 * método Java del controlador con mocks, sin pasar por el dispatcher de Spring MVC ni por la
 * cadena de filtros de seguridad. Mismo patrón {@code @WebMvcTest + SecurityConfig} real que
 * {@code MeControllerTest}, para probar la ruta HTTP completa (serialización JSON, filtro JWT,
 * {@code @PreAuthorize("isAuthenticated()")}) end-to-end.
 */
@WebMvcTest(controllers = ChatbotController.class)
@Import(SecurityConfig.class)
class ChatbotControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ChatbotService chatbotService;

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

        ChatResponse respuestaServicio = ChatResponse.builder()
                .response("Ve a 'Cargar Anteproyecto' desde tu panel de estudiante.")
                .options(List.of("Ver mis trámites", "Ver mi horario"))
                .route("/estudiante/anteproyecto")
                .build();
        when(chatbotService.processMessage(any(ChatRequest.class))).thenReturn(respuestaServicio);

        ChatRequest request = new ChatRequest();
        request.setMessage("¿Cómo subo mi anteproyecto?");

        mockMvc.perform(post("/api/v1/chatbot/ask")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Cargar Anteproyecto")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Consulta procesada correctamente")));
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
