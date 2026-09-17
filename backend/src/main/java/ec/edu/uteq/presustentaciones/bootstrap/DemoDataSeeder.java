package ec.edu.uteq.presustentaciones.bootstrap;

import ec.edu.uteq.presustentaciones.entities.RoleAppUser;
import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.repositories.RoleAppUserRepository;
import ec.edu.uteq.presustentaciones.repositories.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * RNF-15: siembra las cuentas de demostración (admin@/demo@/teacher@/student@uteq.edu.ec)
 * con contraseñas literales en el código. Antes de esta fase corría en TODO arranque, sin
 * importar el entorno -- este componente ahora solo existe bajo el perfil {@code dev}
 * (activate con {@code SPRING_PROFILES_ACTIVE=dev}, como ya hace {@code docker-compose.override.yml}
 * para desarrollo local). Un despliegue sin ese perfil activo nunca instancia esta clase: cero
 * cuentas con contraseña conocida en el código. El bootstrap de un despliegue real vive en
 * {@link AdminBootstrap}, que toma la contraseña del entorno y valida contra
 * {@code PasswordPolicyValidator} en vez de sembrar una constante.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DemoDataSeeder implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleAppUserRepository roleAppUserRepository;

    @Override
    /**
     * Run.
     * @param args args
     */
    public void run(String... args) {
        try {
            // Insertar faculty inicial si no existe
            try {
                jdbcTemplate.update(
                    "INSERT INTO presus.facultades (id, codigo, nombre) OVERRIDING SYSTEM VALUE VALUES (1, 'FCI', 'Facultad de Ciencias de la Ingeniería') ON CONFLICT (id) DO NOTHING"
                );
            } catch (Exception e) {
                log.warn("Verificación de facultad inicial: {}", e.getMessage());
            }

            // Insertar program inicial si no existe
            try {
                jdbcTemplate.update(
                    "INSERT INTO presus.carreras (id, facultad_id, codigo, nombre) OVERRIDING SYSTEM VALUE VALUES (1, 1, 'ISW', 'Ingeniería en Software') ON CONFLICT (id) DO NOTHING"
                );
            } catch (Exception e) {
                log.warn("Verificación de carrera inicial: {}", e.getMessage());
            }

            // Sembrar catalogo de roles si no existe (ninguna migracion los inserta:
            // roles_appUser.id no es autogenerado, requiere valores explicitos)
            try {
                jdbcTemplate.update(
                    "INSERT INTO presus.roles_usuario (id, codigo, nombre) VALUES " +
                    "(1, 'ADMIN', 'Administrador'), (2, 'DOCENTE', 'Docente'), " +
                    "(3, 'COORDINADOR', 'Coordinador'), (4, 'ESTUDIANTE', 'Estudiante') " +
                    "ON CONFLICT (id) DO NOTHING"
                );
            } catch (Exception e) {
                log.warn("Verificación de catálogo de roles: {}", e.getMessage());
            }

            RoleAppUser adminRole = roleAppUserRepository.findByCodigo("ADMIN").orElse(null);
            RoleAppUser coordinadorRole = roleAppUserRepository.findByCodigo("COORDINADOR").orElse(null);

            // AppUser administrador del sistema
            if (!appUserRepository.existsByEmail("admin@uteq.edu.ec")) {
                AppUser admin = AppUser.builder()
                    .nombre("Admin")
                    .apellido("Sistema")
                    .email("admin@uteq.edu.ec")
                    .password(passwordEncoder.encode("Admin2026!"))
                    .role("ADMIN")
                    .roleAppUser(adminRole)
                    .activo(true)
                    .build();
                appUserRepository.save(admin);
                log.info("Usuario administrador inicial verificado.");
            }

            // AppUser de demostración (Fase 8, criterio P5): credenciales publicadas en
            // README.md para que el tribunal pueda entrar sin registerse. Role COORDINADOR
            // porque expone el flujo académico completo (assign panelists, programar
            // schedule, ver reportes) sin ser una cuenta de administración del sistema.
            if (!appUserRepository.existsByEmail("demo@uteq.edu.ec")) {
                AppUser demo = AppUser.builder()
                    .nombre("Usuario")
                    .apellido("Demostración")
                    .email("demo@uteq.edu.ec")
                    .password(passwordEncoder.encode("Demo2026!"))
                    .role("COORDINADOR")
                    .roleAppUser(coordinadorRole)
                    .activo(true)
                    .build();
                appUserRepository.save(demo);
                log.info("Usuario de demostración inicial verificado.");
            }

            RoleAppUser teacherRole = roleAppUserRepository.findByCodigo("DOCENTE").orElse(null);
            RoleAppUser studentRole = roleAppUserRepository.findByCodigo("ESTUDIANTE").orElse(null);

            // AppUser Teacher / Tutor / Panelist
            if (!appUserRepository.existsByEmail("docente@uteq.edu.ec")) {
                AppUser teacherUser = AppUser.builder()
                    .nombre("Docente")
                    .apellido("Tutor")
                    .email("docente@uteq.edu.ec")
                    .password(passwordEncoder.encode("Docente2026!"))
                    .role("DOCENTE")
                    .roleAppUser(teacherRole)
                    .activo(true)
                    .build();
                AppUser savedTeacher = appUserRepository.save(teacherUser);
                jdbcTemplate.update(
                    "INSERT INTO presus.docente (usuario_id, facultad_id, area_especialidad, carga_horaria_semanal, disponible, creado_en) " +
                    "VALUES (?, 1, 'Ingeniería de Software', 20, true, now()) ON CONFLICT (usuario_id) DO NOTHING",
                    savedTeacher.getId()
                );
                log.info("Usuario docente inicial verificado.");
            }

            // AppUser Student
            if (!appUserRepository.existsByEmail("estudiante@uteq.edu.ec")) {
                AppUser estUser = AppUser.builder()
                    .nombre("Estudiante")
                    .apellido("Pregrado")
                    .email("estudiante@uteq.edu.ec")
                    .password(passwordEncoder.encode("Estudiante2026!"))
                    .role("ESTUDIANTE")
                    .roleAppUser(studentRole)
                    .activo(true)
                    .build();
                AppUser savedEst = appUserRepository.save(estUser);
                jdbcTemplate.update(
                    "INSERT INTO presus.estudiante (usuario_id, carrera_id, carrera, semestre, semestre_actual, expediente_codigo, telefono, creado_en) " +
                    "VALUES (?, 1, 'Ingeniería en Software', '8vo', 8, 'EXP-2026-001', '0999999999', now()) ON CONFLICT (usuario_id) DO NOTHING",
                    savedEst.getId()
                );
                log.info("Usuario estudiante inicial verificado.");
            }

        } catch (Exception e) {
            log.error("Error al inicializar datos de demostración: {}", e.getMessage());
        }
    }
}
