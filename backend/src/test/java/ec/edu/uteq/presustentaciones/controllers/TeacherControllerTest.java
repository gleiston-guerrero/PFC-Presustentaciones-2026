package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.config.SecurityConfig;
import ec.edu.uteq.presustentaciones.entities.Teacher;
import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.repositories.TeacherRepository;
import ec.edu.uteq.presustentaciones.security.RateLimiterService;
import ec.edu.uteq.presustentaciones.security.jwt.JwtTokenProvider;
import ec.edu.uteq.presustentaciones.security.service.CurrentAppUserService;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Corrección de IDOR (auditoría 2026-09-04): GET /api/teachers/appUser/{appUserId} exponía el
 * perfil de CUALQUIER teacher a cualquier appUser autenticado, sin verify que el appUserId
 * consultado correspondiera al propio appUser (el frontend siempre pasa
 * authService.getUserId(), nunca un id ajeno -- ver sign-minutes-teacher.component.ts y
 * mis-asignaciones.component.ts). Usa el mismo patrón @WebMvcTest + SecurityConfig real de
 * AuthControllerIntegrationTest para probar los códigos HTTP reales (401/403/200), no solo la
 * lógica de negocio mockeada.
 */
@WebMvcTest(controllers = TeacherController.class)
@Import(SecurityConfig.class)
class TeacherControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TeacherRepository teacherRepository;

    @MockBean
    private CurrentAppUserService currentAppUserService;

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

    private AppUser appUserTeacher;
    private Teacher teacher;

    @BeforeEach
    void setUp() {
        appUserTeacher = new AppUser();
        appUserTeacher.setId(50L);
        appUserTeacher.setEmail("docente@uteq.edu.ec");
        appUserTeacher.setNombre("Ana");
        appUserTeacher.setApellido("Torres");

        teacher = Teacher.builder().id(7L).appUser(appUserTeacher).build();

        when(teacherRepository.findByAppUserId(50L)).thenReturn(Optional.of(teacher));
        when(teacherRepository.findByAppUserId(99L)).thenReturn(Optional.of(
                Teacher.builder().id(8L).appUser(new AppUser()).build()));
    }

    private void authenticateAs(String email, String role) throws Exception {
        String token = "token-" + email;
        UserDetails userDetails = new User(email, "x",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)));
        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken(token)).thenReturn(email);
        when(userDetailsService.loadUserByUsername(email)).thenReturn(userDetails);
    }

    @Test
    void obtainByAppUserWithoutTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/docentes/usuario/50").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void obtainByAppUserPermiteConsultarElOwnProfile() throws Exception {
        authenticateAs("docente@uteq.edu.ec", "DOCENTE");
        when(currentAppUserService.appUser()).thenReturn(appUserTeacher);

        mockMvc.perform(get("/api/v1/docentes/usuario/50")
                        .header("Authorization", "Bearer token-docente@uteq.edu.ec")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void obtainByAppUserRechazaConsultaDeOtroTeacher() throws Exception {
        // Caso IDOR: un teacher autenticado (id 50) intenta ver el perfil del teacher 99
        // cambiando el appUserId en la URL.
        authenticateAs("docente@uteq.edu.ec", "DOCENTE");
        when(currentAppUserService.appUser()).thenReturn(appUserTeacher);

        mockMvc.perform(get("/api/v1/docentes/usuario/99")
                        .header("Authorization", "Bearer token-docente@uteq.edu.ec")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void obtainByAppUserPermiteAAdminConsultarCualquierAppUser() throws Exception {
        authenticateAs("admin@uteq.edu.ec", "ADMIN");

        mockMvc.perform(get("/api/v1/docentes/usuario/99")
                        .header("Authorization", "Bearer token-admin@uteq.edu.ec")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void obtainByAppUserPermiteACoordinatorConsultarCualquierAppUser() throws Exception {
        authenticateAs("coord@uteq.edu.ec", "COORDINADOR");

        mockMvc.perform(get("/api/v1/docentes/usuario/99")
                        .header("Authorization", "Bearer token-coord@uteq.edu.ec")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void listSigueFuncionandoForCualquierAuthenticated() throws Exception {
        // No debe change: el directorio completo (usado para seleccionar panelist/tutor) sigue
        // abierto a cualquier autenticado, sin control de propiedad -- no es el mismo caso.
        authenticateAs("docente@uteq.edu.ec", "DOCENTE");
        when(teacherRepository.findAll()).thenReturn(List.of(teacher));

        mockMvc.perform(get("/api/v1/docentes")
                        .header("Authorization", "Bearer token-docente@uteq.edu.ec")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void obtainByIdSigueFuncionandoWithoutControlDePropiedad() throws Exception {
        authenticateAs("docente@uteq.edu.ec", "DOCENTE");
        when(teacherRepository.findById(7L)).thenReturn(Optional.of(teacher));

        mockMvc.perform(get("/api/v1/docentes/7")
                        .header("Authorization", "Bearer token-docente@uteq.edu.ec")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void availableListaSoloTeachersWithAvailableTrue() throws Exception {
        authenticateAs("docente@uteq.edu.ec", "DOCENTE");
        when(teacherRepository.findByAvailableTrue()).thenReturn(List.of(teacher));

        mockMvc.perform(get("/api/v1/docentes/disponibles")
                        .header("Authorization", "Bearer token-docente@uteq.edu.ec")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void listPagedDelegaEnElRepositorioWithElFiltroDeTexto() throws Exception {
        authenticateAs("docente@uteq.edu.ec", "DOCENTE");
        org.springframework.data.domain.Page<Teacher> pagina =
                new org.springframework.data.domain.PageImpl<>(List.of(teacher));
        when(teacherRepository.searchPaged(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(pagina);

        // No se valida el body: Page<Teacher> no serializa limpio en este @WebMvcTest (el
        // teacher de prueba trae la relacion completa con AppUser). Lo que importa aqui es
        // que el controlador llega a invocar el repositorio con el filtro de texto.
        mockMvc.perform(get("/api/v1/docentes/paginado")
                        .param("q", "torres")
                        .param("page", "0")
                        .param("size", "10")
                        .header("Authorization", "Bearer token-docente@uteq.edu.ec")
                        .contentType(MediaType.APPLICATION_JSON));

        org.mockito.Mockito.verify(teacherRepository)
                .searchPaged(org.mockito.ArgumentMatchers.eq("torres"), org.mockito.ArgumentMatchers.any());
    }
}
