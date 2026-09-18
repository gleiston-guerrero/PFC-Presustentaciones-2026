package ec.edu.uteq.presustentaciones.controllers;

import ec.edu.uteq.presustentaciones.entities.AppUser;
import ec.edu.uteq.presustentaciones.repositories.AppUserRepository;
import ec.edu.uteq.presustentaciones.security.dto.LoginRequest;
import ec.edu.uteq.presustentaciones.security.dto.LoginResponse;
import ec.edu.uteq.presustentaciones.security.dto.ChangePasswordRequest;
import ec.edu.uteq.presustentaciones.security.dto.RecoverPasswordRequest;
import ec.edu.uteq.presustentaciones.security.dto.RegisterRequest;
import ec.edu.uteq.presustentaciones.security.dto.ResetPasswordRequest;
import ec.edu.uteq.presustentaciones.security.PasswordPolicyValidator;
import ec.edu.uteq.presustentaciones.security.PasswordRecoveryService;
import ec.edu.uteq.presustentaciones.security.RateLimiterService;
import ec.edu.uteq.presustentaciones.security.RateLimiterUnavailableException;
import ec.edu.uteq.presustentaciones.security.jwt.JwtTokenProvider;
import ec.edu.uteq.presustentaciones.services.IAppUserService;
import ec.edu.uteq.presustentaciones.dto.ResponseWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200", allowCredentials = "true")
@Slf4j
@Tag(name = "Autenticación", description = "Endpoints para inicio de sesión, registro, actualización de tokens y cierre de sesión")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final IAppUserService appUserService;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final PasswordRecoveryService passwordRecoveryService;
    private final RateLimiterService rateLimiterService;

    /**
     * Autentica al appUser y emite el par de tokens. El access token viaja en el cuerpo y el
     * refresh token se deja además en una cookie HTTP-Only, de modo que JavaScript no pueda
     * leerlo. Este endpoint está sujeto al rate limiting de 6 intentos por minuto y por IP.
     *
     * @param loginRequest correo institucional y contraseña
     * @param response     respuesta HTTP donde se agrega la cookie del refresh token
     * @return 200 con el token de acceso y los datos de sesión, o 401 si las credenciales no
     *         son válidas; 429 si se superó el límite de intentos
     */
    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión y obtener tokens", description = "Genera el JWT de acceso y el refresh token. Configura cookies HttpOnly + Secure + SameSite=Strict.")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest, HttpServletResponse response) {

        // Hallazgo real de seguridad (2026-09-01): lanzar RuntimeException generica aqui hacia que
        // el GlobalExceptionHandler devolviera 400, mientras que una contrasena incorrecta (mas
        // abajo, via authenticationManager.authenticate) devuelve 401 -- un atacante podia
        // distinguir "email no existe" de "email existe, password incorrecta" por el codigo HTTP
        // (enumeracion de appUsers). UsernameNotFoundException es una AuthenticationException,
        // capturada por el mismo handler que ya usa authenticationManager.authenticate() -> 401
        // en ambos casos, igual que ya hace CustomUserDetailsService para este mismo escenario.
        AppUser appUser = appUserRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getEmail(),
                        loginRequest.getPassword()
                )
        );

        // Generamos Access Token (JWT de 7 claims) y Refresh Token (UUID en Redis)
        String token = jwtTokenProvider.generateToken(authentication);
        String refreshToken = jwtTokenProvider.generateRefreshToken(appUser.getEmail());

        // Cabeceras para Set-Cookie seguras (Requisito E13 - HttpOnly, Secure, SameSite=Strict)
        // Usamos addHeader en lugar de Cookie de Servlet para tener soporte de SameSite=Strict completo
        response.addHeader(HttpHeaders.SET_COOKIE, 
                String.format("jwtToken=%s; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=86400", token));
        response.addHeader(HttpHeaders.SET_COOKIE, 
                String.format("refreshToken=%s; Path=/api/auth/refresh; HttpOnly; Secure; SameSite=Strict; Max-Age=604800", refreshToken));

        LoginResponse loginResponse = LoginResponse.builder()
                .token(token)
                .type("Bearer")
                .id(appUser.getId())
                .email(appUser.getEmail())
                .nombre(appUser.getNombre() + " " + appUser.getApellido())
                .role(appUser.getRole())
                .emailNotifications(appUser.getEmailNotifications())
                .build();

        // Creamos una respuesta enriquecida
        Map<String, Object> data = new HashMap<>();
        data.put("auth", loginResponse);
        data.put("refreshToken", refreshToken);

        return ResponseEntity.ok(ResponseWrapper.success(data, "Sesión iniciada correctamente"));
    }

    /**
     * Renueva el access token a partir del refresh token de la cookie, aplicando rotación:
     * el refresh usado se invalida y se emite uno nuevo. Si llega un refresh ya utilizado se
     * trata como reutilización (posible robo de token) y se rechaza.
     *
     * @param request  petición de la que se lee la cookie del refresh token
     * @param response respuesta donde se deja el refresh token rotado
     * @return 200 con el nuevo access token, o 401 si el refresh falta, expiró o ya fue usado
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refrescar el token de acceso vencido", description = "Recibe el refresh token, lo valida en Redis y rota ambos tokens (Access y Refresh).")
    public ResponseEntity<?> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = null;

        // Extraer refresh token de las cookies
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("refreshToken".equals(cookie.getName())) {
                    refreshToken = cookie.getValue();
                    break;
                }
            }
        }

        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseWrapper.error("Refresh token no proporcionado"));
        }

        // 1. Verify si el token fue reutilizado (ataque de robo de sesión)
        String reusedBy = jwtTokenProvider.getUsernameFromUsedRefreshToken(refreshToken);
        if (reusedBy != null) {
            log.warn("¡ALERTA DE SEGURIDAD! Intento de reutilización de Refresh Token detectado para el usuario: {}", reusedBy);
            jwtTokenProvider.revokeAllUserTokens(reusedBy);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ResponseWrapper.error("Token de seguridad comprometido. Todas las sesiones han sido cerradas."));
        }

        // 2. Extraer appUser del token válido
        String email = jwtTokenProvider.getUsernameFromRefreshToken(refreshToken);
        if (email == null || !jwtTokenProvider.validateRefreshToken(refreshToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ResponseWrapper.error("Refresh token inválido o expirado"));
        }

        AppUser appUser = appUserRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        // 3. Rotación de tokens (Requisito Rotación): invalidate el usado y generate nuevos
        jwtTokenProvider.rotateRefreshToken(refreshToken, email);
        String newAccessToken = jwtTokenProvider.generateTokenFromUsername(email);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(email);

        // Update cookies seguras
        response.addHeader(HttpHeaders.SET_COOKIE, 
                String.format("jwtToken=%s; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=86400", newAccessToken));
        response.addHeader(HttpHeaders.SET_COOKIE, 
                String.format("refreshToken=%s; Path=/api/auth/refresh; HttpOnly; Secure; SameSite=Strict; Max-Age=604800", newRefreshToken));

        Map<String, Object> data = new HashMap<>();
        data.put("token", newAccessToken);
        data.put("refreshToken", newRefreshToken);

        return ResponseEntity.ok(ResponseWrapper.success(data, "Tokens actualizados correctamente"));
    }

    /**
     * Cierra la sesión: agrega el identificador del token a la lista negra en Redis y borra
     * la cookie del refresh, de modo que el access token deje de aceptarse aunque no haya
     * expirado todavía.
     *
     * @param request  petición de la que se leen el token y la cookie
     * @param response respuesta donde se limpia la cookie del refresh token
     * @return 200 confirmando el cierre de sesión
     */
    @PostMapping("/logout")
    @Operation(summary = "Cerrar sesión e invalidar tokens", description = "Agrega el token de acceso a la blacklist en Redis y elimina el refresh token.")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        String token = null;

        // Intentar extraer token del header Authorization
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            token = bearerToken.substring(7);
        }

        // Si no está en el header, intentar extraer de las cookies
        if (token == null && request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("jwtToken".equals(cookie.getName())) {
                    token = cookie.getValue();
                    break;
                }
            }
        }

        if (token != null) {
            // Blacklist de Access Token (Requisito Blacklist)
            jwtTokenProvider.blacklistToken(token);
            
            try {
                // Delete el refresh token específico de la sesión (usando las cookies)
                String refreshCookie = null;
                if (request.getCookies() != null) {
                    for (Cookie cookie : request.getCookies()) {
                        if ("refreshToken".equals(cookie.getName())) {
                            refreshCookie = cookie.getValue();
                            break;
                        }
                    }
                }
                if (refreshCookie != null) {
                    jwtTokenProvider.deleteRefreshToken(refreshCookie);
                }
            } catch (Exception e) {
                log.warn("No se pudo extraer o borrar refresh token: {}", e.getMessage());
            }
        }

        // Limpiar Cookies del cliente
        response.addHeader(HttpHeaders.SET_COOKIE, "jwtToken=; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=0");
        response.addHeader(HttpHeaders.SET_COOKIE, "refreshToken=; Path=/api/auth/refresh; HttpOnly; Secure; SameSite=Strict; Max-Age=0");

        return ResponseEntity.ok(ResponseWrapper.success(null, "Sesión cerrada correctamente"));
    }

    /**
     * Provisión de cuentas: solo un ADMIN puede create appUsers (incluye poder assign cualquier
     * role). Hallazgo real de auditoría (2026-09-04): antes recibía la entidad {@link AppUser}
     * cruda por @RequestBody, sin @Valid -- un body con "id", "activo":false, "rolUsuario":
     * {"id":N} o "creadoEn" podía pisar esos campos directamente (mass-assignment), y "rol" y
     * "rolUsuario" podían quedar desincronizados porque nunca pasaba por resolveRole(). Ahora se
     * usa {@link RegisterRequest} (ya existía, sin usar) con @Valid, y el AppUser se arma en el
     * controller solo con los 5 campos permitidos -- ningún otro campo del cliente llega a la
     * entidad. La creación en sí (verificación de email duplicado, encode de password,
     * resolución de role) reutiliza {@link IAppUserService#create} tal cual la usa
     * AppUserController, en vez de duplicar esa lógica aquí.
     *
     * @param request los 5 campos permitidos para create la cuenta
     * @return 200 con el appUser creado, o 400 si el email ya existe o el role no es válido
     */
    @PostMapping("/register")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'USUARIOS_GESTIONAR')")
    @Operation(summary = "Registrar nuevo usuario", description = "Permite a un administrador crear nuevos usuarios en el sistema.")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        // RNF-06: @Size en el DTO ya cubre la longitud minima; la lista de contrasenas
        // comunes no se puede expresar como anotacion de Bean Validation sin un
        // ConstraintValidator dedicado, asi que se aplica aqui explicitamente.
        passwordPolicyValidator.validate(request.getPassword());

        AppUser appUser = new AppUser();
        appUser.setNombre(request.getNombre());
        appUser.setApellido(request.getApellido());
        appUser.setEmail(request.getEmail());
        appUser.setPassword(request.getPassword());
        appUser.setRole(request.getRole());
        appUser.setActivo(true);

        appUserService.create(appUser);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseWrapper.success(null, "Usuario registrado exitosamente"));
    }

    /**
     * RF-06: cambio de contraseña propia. Antes de esta fase no existía ninguna vía, propia ni
     * administrativa, para change una contraseña una vez creada la cuenta.
     *
     * @param id      appUser cuya contraseña se cambia -- debe ser el mismo que el autenticado
     * @param request contraseña vigente y nueva contraseña
     * @param http    para leer la cookie {@code refreshToken} de la sesión actual y preservarla
     * @return 200 si el cambio se aplicó, 401 si la contraseña vigente no coincide (sin tocar
     *         nada), 403 si {@code id} no es el propio appUser autenticado
     */
    @PatchMapping("/usuarios/{id}/password")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cambiar la contraseña propia", description = "Solo el titular puede cambiar su propia contraseña, incluso si quien lo intenta es Administrador.")
    public ResponseEntity<?> changePassword(@PathVariable("id") Long id,
                                              @Valid @RequestBody ChangePasswordRequest request,
                                              HttpServletRequest http) {
        // Mismo patrón que AppUserController#updatePerfil (RF-10): la identidad se resuelve
        // por el email del JWT, nunca por el id de la ruta -- ni siquiera un Administrador puede
        // change la contraseña de otra cuenta por aquí.
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        AppUser holder = appUserRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
        if (!holder.getId().equals(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ResponseWrapper.error("No puedes cambiar la contraseña de otro usuario"));
        }

        if (!passwordEncoder.matches(request.getPasswordActual(), holder.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ResponseWrapper.error("La contraseña actual no es correcta"));
        }
        if (request.getPasswordActual().equals(request.getPasswordNueva())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseWrapper.error("La nueva contraseña no puede ser igual a la actual"));
        }
        // RNF-06, mismo validador que RF-04 (alta): longitud minima y lista de comunes.
        passwordPolicyValidator.validate(request.getPasswordNueva());

        holder.setPassword(passwordEncoder.encode(request.getPasswordNueva()));
        appUserRepository.save(holder);

        // Revoca todas las sesiones activas SALVO la actual -- revocar tambien esa dejaria al
        // appUser fuera justo despues de un cambio legitimo.
        String refreshActual = null;
        if (http.getCookies() != null) {
            for (Cookie cookie : http.getCookies()) {
                if ("refreshToken".equals(cookie.getName())) {
                    refreshActual = cookie.getValue();
                    break;
                }
            }
        }
        jwtTokenProvider.revokeAllUserTokensExcept(holder.getEmail(), refreshActual);

        return ResponseEntity.ok(ResponseWrapper.success(null, "Contraseña actualizada correctamente"));
    }

    /**
     * RF-05: submission de recuperación de contraseña, sin sesión activa. Endpoint público (ver
     * {@code SecurityConfig}/{@code JwtAuthenticationFilter.shouldNotFilter}, igual que
     * {@code /login} y {@code /refresh}).
     *
     * <p>Responde exactamente el mismo cuerpo, con el mismo código, exista o no una cuenta con
     * ese correo -- de lo contrario el propio endpoint sería una forma de enumerar cuentas
     * registradas. La diferencia de trabajo interno (send el correo o no) vive en
     * {@link PasswordRecoveryService#solicitarRecovery}, que iguala también el costo para
     * no filtrar la respuesta por el tiempo.
     *
     * @param request correo de la cuenta a recuperar
     * @return 200 siempre (salvo límite de tasa: 429, o 503 si el almacén de límite de tasa
     *         está caído -- RNF-04)
     */
    @PostMapping("/recuperar")
    @Operation(summary = "Solicitar recuperación de contraseña", description = "Responde igual exista o no la cuenta, para no permitir enumerarlas.")
    public ResponseEntity<?> recover(@Valid @RequestBody RecoverPasswordRequest request) {
        String correo = request.getEmail().trim().toLowerCase();
        try {
            if (!rateLimiterService.isAllowed("ratelimit:recuperar:" + correo, 3, 3600)) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(ResponseWrapper.error("Demasiadas solicitudes de recuperación para esta cuenta. Intenta de nuevo más tarde."));
            }
        } catch (RateLimiterUnavailableException e) {
            // RNF-04: mismo fail-closed que RateLimitingFilter -- 503, no acceso sin límite.
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ResponseWrapper.error("Servicio de recuperación no disponible temporalmente. Intenta de nuevo en un momento."));
        }

        passwordRecoveryService.solicitarRecovery(correo);

        return ResponseEntity.ok(ResponseWrapper.success(null,
                "Si existe una cuenta con ese correo, recibirás un enlace de recuperación en unos minutos."));
    }

    /**
     * RF-05: aplica el restablecimiento con el token de un solo uso recibido por correo.
     * Endpoint público, mismo motivo que {@link #recover}.
     *
     * @param request token y nueva contraseña
     * @return 200 si el restablecimiento se aplicó; 400 si el token es inválido/expirado/ya
     *         usado, o si la nueva contraseña incumple RNF-06
     */
    @PostMapping("/restablecer")
    @Operation(summary = "Restablecer contraseña con el token de recuperación")
    public ResponseEntity<?> reset(@Valid @RequestBody ResetPasswordRequest request) {
        passwordRecoveryService.reset(request.getToken(), request.getPasswordNueva());
        return ResponseEntity.ok(ResponseWrapper.success(null, "Contraseña restablecida correctamente"));
    }
}