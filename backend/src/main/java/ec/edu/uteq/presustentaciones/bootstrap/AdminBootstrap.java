package ec.edu.uteq.presustentaciones.bootstrap;

import ec.edu.uteq.presustentaciones.entities.RoleAppUser;
import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.repositories.RoleAppUserRepository;
import ec.edu.uteq.presustentaciones.repositories.AppUserRepository;
import ec.edu.uteq.presustentaciones.security.PasswordPolicyValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * RNF-15: cierra el hallazgo de las cuentas con contraseña literal (ver {@link DemoDataSeeder},
 * que ahora solo corre bajo el perfil {@code dev}). Fuera de ese perfil, un despliegue sin
 * ningún appUser ADMIN necesita de todos modos una primera cuenta para poder entrar -- este
 * componente la crea, pero SOLO a partir de {@code ADMIN_BOOTSTRAP_EMAIL}/
 * {@code ADMIN_BOOTSTRAP_PASSWORD} del entorno, nunca con una contraseña conocida de antemano.
 *
 * <p>Mismo patrón que {@code jwt.secret} (RNF-14, ver {@code application.properties}): sin el
 * dato requerido, el arranque falla explícitamente en vez de arrancar en silencio con un valor
 * por omisión inseguro. La diferencia con {@code JWT_SECRET} es que aquí la falta solo es fatal
 * si además no existe ya ningún ADMIN -- una vez sembrada la primera cuenta, reinicios
 * posteriores no vuelven a exigir la variable.
 */
@Component
@Profile("!dev")
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrap implements CommandLineRunner {

    private final AppUserRepository appUserRepository;
    private final RoleAppUserRepository roleAppUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;

    @Value("${ADMIN_BOOTSTRAP_EMAIL:}")
    private String bootstrapEmail;

    @Value("${ADMIN_BOOTSTRAP_PASSWORD:}")
    private String bootstrapPassword;

    @Override
    /**
     * Run.
     * @param args args
     */
    public void run(String... args) {
        if (!appUserRepository.findByRole("ADMIN").isEmpty()) {
            log.info("Ya existe al menos un usuario ADMIN; se omite el bootstrap de administración.");
            return;
        }

        if (!StringUtils.hasText(bootstrapEmail) || !StringUtils.hasText(bootstrapPassword)) {
            throw new IllegalStateException(
                    "No existe ningún usuario ADMIN y faltan ADMIN_BOOTSTRAP_EMAIL / "
                    + "ADMIN_BOOTSTRAP_PASSWORD en el entorno. El sistema no puede arrancar sin una "
                    + "vía de acceso administrativa, y no va a crear una con contraseña conocida de "
                    + "antemano. Define ambas variables (ver .env.example) y vuelve a intentar.");
        }

        // RNF-06: la contraseña de la primera cuenta administrativa tampoco puede ser débil
        // solo por venir del entorno -- se valida igual que cualquier otra.
        passwordPolicyValidator.validate(bootstrapPassword);

        RoleAppUser adminRole = roleAppUserRepository.findByCode("ADMIN").orElse(null);
        AppUser admin = AppUser.builder()
                .nombre("Administrador")
                .apellido("Inicial")
                .email(bootstrapEmail)
                .password(passwordEncoder.encode(bootstrapPassword))
                .role("ADMIN")
                .roleAppUser(adminRole)
                .activo(true)
                .build();
        appUserRepository.save(admin);
        log.info("Cuenta ADMIN inicial creada desde ADMIN_BOOTSTRAP_EMAIL ({}). "
                + "La contraseña nunca se registra en el log.", bootstrapEmail);
    }
}
