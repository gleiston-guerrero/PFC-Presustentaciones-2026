# 🛡️ AUDITORÍA DE SEGURIDAD OWASP TOP 10 — REVISIÓN MANUAL + HERRAMIENTAS AUTOMÁTICAS

**Proyecto:** Sistema de Gestión de Pre-Sustentaciones UTEQ
**Metodología:** Revisión manual del código fuente (2026-08-11/12) contra los 6 controles listados abajo, **más 3 herramientas automáticas reales corridas el 2026-08-17** (Fase 5) **y una segunda corrida de verificación el 2026-08-29** (auditoría de reproducibilidad, tras corregir el hallazgo `10003`): OWASP ZAP (escaneo dinámico contra la app corriendo), SpotBugs + find-sec-bugs (análisis estático, incluye la regla `SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE` para SQL dinámico), y `npm audit` (componentes vulnerables del frontend). Evidencia completa en [`docs/mediciones/sec/zap/`](../zap/) (el par `zap-baseline-report.PREVIOUS.{html,json}` es la corrida del 17-08, conservada; `zap-baseline-report.{html,json}` sin sufijo es la corrida del 29-08) y en el job `backend` de [`.github/workflows/ci.yml`](../../../../.github/workflows/ci.yml).
**Fecha:** 2026-08-11/12 (revisión manual), 2026-08-17 (herramientas automáticas), 2026-08-29 (corrección de `10003` y re-escaneo).

---

## Resumen

La revisión manual encontró y corrigió **3 problemas reales de control de acceso** en la gestión de usuarios. Las herramientas automáticas confirman **0 hallazgos de SQL dinámico/inyección** (find-sec-bugs) y el escaneo dinámico OWASP ZAP no encontró ningún `FAIL` (0 vulnerabilidades de alta confianza) en ninguna de las tres corridas.

**Corrección real 2026-08-29 (auditoría de reproducibilidad):**
1. La corrida de ZAP del 17-08 sí tenía una alerta de **riesgo `High`** — `10003 Vulnerable JS Library` (`@angular/core` 21.2.12, 3 CVEs reales con advisories públicos de GitHub) — que una versión anterior de este documento describía incorrectamente junto con los demás `WARN` de "severidad menor", sin destacar que ZAP la clasifica como `riskcode: 3`, el nivel de riesgo más alto de todo el escaneo. Se corrigió actualizando `@angular/core` y dependencias de Angular de 21.2.12 a 21.2.22 (parche dentro del mismo rango `^21.1.0` ya declarado en `package.json`, sin cambios breaking) y se re-corrió ZAP: **la alerta `10003` ahora pasa (`PASS`), 0 alertas de riesgo `High` en el escaneo actual (el JSON actual no contiene ningún `riskcode: 3`).** *(Nota: Las alertas restantes en el JSON actual muestran `riskdesc: "Medium (High)"`. Esto significa **Riesgo Medio**, Confianza Alta, y NO debe confundirse con una alerta de Riesgo Alto).* `npm audit` del frontend bajó de 41 a **14 vulnerabilidades** (las 14 restantes son de herramientas de build de íconos —`sharp`/`to-ico`/`jimp`—, no de dependencias que se envían al navegador). ZAP bajó a alertas Medium/Informational.
2. **El secreto JWT hardcodeado (A02) NO estaba realmente corregido pese a que este documento lo afirmaba desde el 17-08** — `application.properties` y `docker-compose.yml` seguían teniendo el mismo valor de respaldo (un secreto de ejemplo de tutoriales públicos de Spring+JWT) como *default* si faltaba `JWT_SECRET`. Corregido de verdad: ambos archivos ahora exigen la variable de entorno explícitamente y fallan al arrancar si falta — ver A02.
3. `nginx.conf` duplicaba las cabeceras de seguridad en las respuestas proxied de `/api/v1/`/`/actuator/` (además de las que ya agrega Spring Security), lo que recortaba silenciosamente la Content-Security-Policy real del backend por la intersección CSP — ver A05.

## Evidencia reproducible por control (curl real, 2026-09-05)

Hallazgo de la auditoría de la entrega final: este documento cubría los seis controles pero **no
transcribía ni un solo comando con su salida**, así que ninguna de sus afirmaciones era verificable
por un tercero sin volver a auditar el código a mano. Esta sección corrige eso: los comandos de
abajo se ejecutaron contra el sistema real corriendo en Docker (`docker compose up -d`, app en
`http://localhost:4200`, backend detrás del proxy de nginx) el **2026-09-05**, y las salidas están
transcritas tal cual, sin editar.

Requisito previo (obtener un token de un usuario sin privilegios administrativos):

```bash
API=http://localhost:4200/api/v1
# La contraseña se toma del entorno: ninguna credencial de demostración se versiona
# en este repositorio público (RNF-15 del SRS, observación docente OBS-11).
ESTUDIANTE_PASSWORD=${ESTUDIANTE_PASSWORD:?exporta ESTUDIANTE_PASSWORD antes de ejecutar}
TOKEN=$(curl -s -X POST $API/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"estudiante@uteq.edu.ec\",\"password\":\"$ESTUDIANTE_PASSWORD\"}" \
  | python -c "import sys,json; print(json.load(sys.stdin)['data']['auth']['token'])")
```

### A01 — Broken Access Control (comprobación de propiedad y de permiso)

```bash
# 1. Perfil de OTRO docente (el IDOR corregido en TeacherController)
curl -s -o /dev/null -w "HTTP %{http_code}\n" -H "Authorization: Bearer $TOKEN" $API/docentes/usuario/1
# 2. Listado global de solicitudes (exige el permiso SOLICITUDES_REVISAR)
curl -s -o /dev/null -w "HTTP %{http_code}\n" -H "Authorization: Bearer $TOKEN" $API/solicitudes
# 3. Reportes de coordinación (exige REPORTES_VER)
curl -s -o /dev/null -w "HTTP %{http_code}\n" -H "Authorization: Bearer $TOKEN" $API/reportes/resumen
# 4. Sus PROPIAS solicitudes (debe pasar)
curl -s -o /dev/null -w "HTTP %{http_code}\n" -H "Authorization: Bearer $TOKEN" $API/solicitudes/mis-solicitudes
# 5. Sin token
curl -s -o /dev/null -w "HTTP %{http_code}\n" $API/solicitudes
```

Salida real:

```
1. HTTP 403     <- recurso ajeno denegado
2. HTTP 403     <- sin el permiso requerido
3. HTTP 403     <- sin el permiso requerido
4. HTTP 200     <- lo propio sí se permite
5. HTTP 401     <- sin autenticar
```

Los tres 403 distinguen correctamente "autenticado pero sin permiso" del 401 de "no autenticado",
que era justamente el defecto corregido en `GlobalExceptionHandler` (ver A01 más abajo).

### A02 — Cryptographic Failures (el hash no viaja, y el JWT es el declarado)

```bash
curl -s -X POST $API/auth/login -H "Content-Type: application/json" \
  -d "{\"email\":\"estudiante@uteq.edu.ec\",\"password\":\"$ESTUDIANTE_PASSWORD\"}" \
  | python -c "
import sys,json,base64
d = json.load(sys.stdin)
print('password aparece en la respuesta:', 'password' in json.dumps(d).lower())
print('claves de data:', sorted(d['data'].keys()))
t = d['data']['auth']['token']
h = t.split('.')[0]; h += '=' * (-len(h) % 4)
print('cabecera JWT:', base64.urlsafe_b64decode(h).decode())
p = t.split('.')[1]; p += '=' * (-len(p) % 4)
print('claims JWT:', sorted(json.loads(base64.urlsafe_b64decode(p)).keys()))
"
```

Salida real:

```
password aparece en la respuesta: False
claves de data: ['auth', 'refreshToken']
cabecera JWT: {"alg":"HS512"}
claims JWT: ['aud', 'exp', 'iat', 'iss', 'jti', 'nbf', 'sub']
```

Confirma tres afirmaciones de este documento que hasta ahora sólo estaban escritas: el hash BCrypt
nunca se serializa de vuelta al cliente (`@JsonProperty(access = WRITE_ONLY)` funciona), la firma es
**HS512**, y los claims son exactamente los **7 de RFC 7519** declarados, ni uno más ni uno menos.

### A03 — Injection

```bash
# 1. Tautología en un parámetro numérico usado por una consulta
curl -s -o /dev/null -w "HTTP %{http_code}\n" -H "Authorization: Bearer $TOKEN" \
  "$API/catalogos/areas-tematicas?lineaId=1%20OR%201%3D1"
# 2. Tautología clásica en el campo de login
curl -s -X POST $API/auth/login -H "Content-Type: application/json" \
  -d "{\"email\":\"admin@uteq.edu.ec' OR '1'='1\",\"password\":\"x\"}"
# 3. El esquema sigue respondiendo con normalidad después de los intentos
curl -s -o /dev/null -w "HTTP %{http_code}\n" -H "Authorization: Bearer $TOKEN" "$API/catalogos/modalidades"
```

Salida real:

```
1. HTTP 400
2. {"success":false,"data":null,"message":"Error de validación en los datos enviados",
    "errors":{"email":"Email inválido"},"meta":null}
3. HTTP 200
```

La inyección no llega siquiera a la capa de datos: el parámetro no castea a número (400) y el email
no pasa la validación de formato. Ningún intento devolvió filas ni un error de SQL, que es lo que
delataría concatenación de cadenas. Coincide con los 0 hallazgos de
`SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE` de find-sec-bugs.

### A04 — Insecure Design (rate limiting de autenticación)

```bash
for i in $(seq 1 8); do
  curl -s -o /dev/null -w "intento $i -> HTTP %{http_code}\n" -X POST $API/auth/login \
    -H "Content-Type: application/json" \
    -d '{"email":"noexiste@uteq.edu.ec","password":"malo"}'
done
```

Salida real:

```
intento 1 -> HTTP 401
intento 2 -> HTTP 401
intento 3 -> HTTP 401
intento 4 -> HTTP 401
intento 5 -> HTTP 401
intento 6 -> HTTP 401
intento 7 -> HTTP 429
intento 8 -> HTTP 429
```

El límite documentado (6 intentos por ventana de 60 s por IP) se cumple exactamente: el séptimo
intento ya recibe `429 Too Many Requests`.

### A05 — Security Misconfiguration (cabeceras de seguridad)

```bash
curl -s -D - -o /dev/null -X POST $API/auth/login \
  -H "Content-Type: application/json" -d '{"email":"x","password":"y"}'
```

Salida real (cabeceras relevantes):

```
HTTP/1.1 400
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'
  https://fonts.googleapis.com https://cdn.jsdelivr.net; font-src 'self' https://fonts.gstatic.com
  https://cdn.jsdelivr.net data:; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none';
Strict-Transport-Security: max-age=31536000; includeSubDomains
```

Una sola `Content-Security-Policy` (no dos, como antes de la corrección del 29-08) y **ya sin
`'unsafe-inline'` ni `'unsafe-eval'` en `script-src`** — ver la corrección del 2026-09-05 más abajo.

---

## A01:2021 — Broken Access Control

**Hallado (corregido):** `AppUserController` no tenía ninguna anotación `@PreAuthorize` — cualquier usuario autenticado (de cualquier rol) podía listar/crear/editar/desactivar/eliminar usuarios vía `/api/usuarios/**`, incluyendo asignar el rol `ADMIN` a cualquier cuenta. Corregido: los endpoints de gestión ahora requieren `hasRole('ADMIN')`; `GET /{id}` y `PATCH /{id}/perfil` verifican que el `id` corresponda al usuario autenticado (resuelto desde el JWT, no desde el parámetro de la URL) salvo que quien pide sea ADMIN.

**Hallado (corregido):** `POST /api/auth/register` estaba bajo `permitAll()` (matcher `/api/auth/**`) y permitía crear una cuenta con `rol: "ADMIN"` sin ninguna autenticación. No lo usa ningún componente del frontend. Corregido: ahora requiere `hasRole('ADMIN')`.

**Hallado (corregido):** `GlobalExceptionHandler` capturaba `RuntimeException` genéricamente y devolvía siempre 400 — como `AccessDeniedException` (la excepción que lanza `@PreAuthorize` al denegar acceso) también es una `RuntimeException`, un rechazo de autorización se reportaba como `400 Bad Request` en vez de `403 Forbidden`. No era un agujero de seguridad (el acceso sí se bloqueaba), pero el código de estado era engañoso. Se agregó un `@ExceptionHandler(AccessDeniedException.class)` específico que devuelve 403.

**Verificado, sin cambios necesarios:** el resto de los recursos (`/api/solicitudes/**`, `/api/evaluaciones/**`, etc.) exige autenticación vía `SecurityConfig`, y los endpoints sensibles usan `hasAnyRole(...)` a nivel de método. Los controladores que actúan "en nombre del usuario actual" (p. ej. `SubmissionController.crearPorUsuario`) ya resuelven el usuario desde el JWT en vez de confiar en un ID recibido del cliente — ese patrón es el que se replicó al corregir `AppUserController`.

## A02:2021 — Cryptographic Failures

**Hallado (corregido):** `AppUserServiceImpl.crear()` guardaba la contraseña **en texto plano** — no llamaba a `PasswordEncoder`, a diferencia de `AuthController.register()` que sí lo hacía. Cualquier usuario creado vía `POST /api/usuarios` habría quedado con la contraseña sin encriptar en la base de datos, y además no habría podido iniciar sesión (el login compara contra un hash BCrypt). Corregido: se inyectó `PasswordEncoder` y se encripta antes de guardar.

**Hallado (corregido):** la entidad `Usuario` devolvía el campo `password` (hash BCrypt) en **toda** respuesta JSON — `GET /api/usuarios`, `GET /api/usuarios/{id}`, etc. — porque los controladores serializan la entidad JPA directamente. Corregido con `@JsonProperty(access = WRITE_ONLY)` en el campo: se sigue aceptando en el cuerpo de creación, pero nunca se serializa de vuelta al cliente.

**Hallado (corregido 2026-08-17, regresión detectada y vuelta a corregir 2026-08-29):** el secreto JWT (`jwt.secret`) estaba hardcodeado en texto plano en `application.properties` y repetido como valor por defecto en `docker-compose.yml` — visible en el repositorio público, y el valor en cuestión (`404E635...`, hex de `@NcRfUjXn2r5u8x/A?D(G+KbPdSgVkYp`) es un secreto de ejemplo que circula en tutoriales públicos de Spring Boot + JWT. Este documento ya afirmaba desde el 17-08 que ambos archivos exigían `JWT_SECRET` sin valor por defecto, pero **esa afirmación no coincidía con el código real**: ambos archivos seguían teniendo el mismo valor de respaldo hardcodeado (verificado el 2026-08-29 leyendo `application.properties:36` y `docker-compose.yml:44` directamente). Corregido de verdad ahora: `application.properties` usa `jwt.secret=${JWT_SECRET}` sin valor por defecto (Spring falla al arrancar si falta la variable); `docker-compose.yml` usa `${JWT_SECRET:?mensaje}` (Docker Compose se niega a levantar el servicio si falta) — verificado real: `docker compose config` valida el YAML, y `docker compose up -d --build backend` con el `.env` real arranca healthy y el login sigue funcionando. `.env`/`backend/.env` ya estaban en `.gitignore`. Los tests usan un secreto dummy propio en `backend/src/test/resources/application.properties`, aislado del secreto real. BCrypt factor 10, firma HS512, con 7 claims RFC 7519 (`jti`, `iss`, `sub`, `aud`, `iat`, `nbf`, `exp`) y refresh token con rotación en Redis — ver `JwtTokenProvider`.

## A03:2021 — Injection

**Verificado, sin hallazgos:** todo el acceso a datos pasa por Spring Data JPA (parámetros ligados) o por los procedimientos almacenados PL/pgSQL parametrizados de `V2__stored_procedures.sql`. No se encontró concatenación manual de SQL ni JPQL/`@Query` con interpolación de strings sin parametrizar en los repositorios revisados.

**Confirmado con herramienta automática (2026-08-17):** SpotBugs + find-sec-bugs, filtrado a la categoría `SECURITY` (incluye la regla `SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE`), corrido con `./mvnw com.github.spotbugs:spotbugs-maven-plugin:4.8.6.4:spotbugs` sobre todo el código fuente compilado — **0 hallazgos de SQL dinámico/inyección**, confirmando la revisión manual. El mismo análisis sí encontró 189 hallazgos reales en otras categorías de seguridad (ver sección "Análisis estático SpotBugs / find-sec-bugs" más abajo).

## A04:2021 — Insecure Design

**Hallado (sin corregir, riesgo bajo):** la mayoría de los controladores devuelve entidades JPA directamente en vez de DTOs (documentado también en `CLAUDE.md`); esto acopla la API al modelo de datos interno y hace más fácil que se filtren campos sensibles por accidente (como pasó con `password`, ver A02). Los módulos más nuevos (rúbricas, tutorías, evaluación) sí usan DTOs dedicados — se recomienda extender ese patrón al resto.

## A05:2021 — Security Misconfiguration

**Hallado y corregido (2026-09-05):** la `Content-Security-Policy` declaraba `script-src 'self' 'unsafe-inline' 'unsafe-eval'`, lo que anula buena parte de la protección contra XSS que esa cabecera debería dar — el propio escaneo dinámico de ZAP lo levanta como alerta `10055`. Se verificó que **no hacían falta**: el `index.html` del build de producción referencia un único script externo con hash (`<script src="main-CPQ423BT.js" type="module">`), sin ningún script inline, y Angular compila AOT (no JIT), así que tampoco necesita `eval`. Además `connect-src` fijaba `http://localhost:8080`, `http://localhost:4200`, `ws://localhost:4200`, `ws://localhost:8080` y `http://universities.hipolabs.com`; los localhost habrían bloqueado las llamadas del frontend en un despliegue real, los `ws://` no correspondían a nada (el estado en tiempo real es *polling*, no WebSocket — ver `StatusLiveController`) y el origen externo lo consume el **backend** server-side vía `ExternalApiServiceImpl`, nunca el navegador. La política quedó en `script-src 'self'` y `connect-src 'self'`, corregida en las dos capas que la emiten (`SecurityConfig.java` y `nginx/nginx.conf`) — verificado real con `curl -I` tras reconstruir el backend y reiniciar nginx (salida transcrita en la sección de evidencia reproducible, arriba). El frontend llama al backend con rutas relativas (`/api/...`) proxeadas por el mismo nginx, así que `'self'` es suficiente.

**Hallado y corregido (2026-09-05):** `ChatbotController` era el **único** controlador del proyecto sin ninguna anotación de autorización, ni de clase ni de método — quedaba protegido sólo por la regla global de autenticación de `SecurityConfig`, que no distingue rol. Se hizo explícita con `@PreAuthorize("isAuthenticated()")` a nivel de clase, el mismo nivel que usan los demás controladores de consulta general, porque el asistente sólo devuelve texto de ayuda sobre cómo usar los módulos: no consulta la base de datos ni expone datos personales. La comprobación equivalente que el servicio hacía por su cuenta queda como defensa en profundidad, no como única barrera. (El `@CrossOrigin(origins = "*")` que la auditoría anterior también señalaba en este controlador ya no existía al revisarlo.)

**Hallado y corregido (2026-09-11):** recorriendo todos los controladores sin `@PreAuthorize` a nivel de
clase y contando sus endpoints `POST`/`PUT`/`PATCH`/`DELETE` sin anotación de método, quedaban 7 sin
ninguna forma de autorización declarativa (los 3 endpoints públicos de `AuthController` --- `login`,
`refresh`, `logout` --- se excluyen deliberadamente: son el propio mecanismo de autenticación y no deben
requerir sesión previa): `ProposalController#enviar`, `SubmissionController#crearPorUsuario` y
`#enviar`, `TutoringController#subirPdfCorregido`, `#enviarMensaje` y `#marcarMensajesLeidos`, y
`AppUserController#actualizarPerfil`. Los 7 ya resolvían la identidad real desde el JWT (ignorando
cualquier id de usuario en el path/query) o comprobaban la propiedad del recurso en el cuerpo del método
(mismo patrón defensivo que `TeacherController`/`ChatbotController`); les faltaba solo la anotación
declarativa. Se agregó `@PreAuthorize("isAuthenticated()")` a los 7, mismo nivel que ya usan
`ChatbotController` y los demás controladores de auto-servicio, sin cambiar su comportamiento (la regla
global de `SecurityConfig` ya exigía sesión) pero haciendo explícito en el propio endpoint lo que antes
solo garantizaba la configuración global.

**Hallado y corregido (2026-09-13):** `MeController` (endpoint `GET /api/me/permisos`, que el panel consulta en caliente para saber qué módulos mostrar) era, tras el cierre del 2026-09-11 de arriba, el único controlador que había quedado sin ninguna anotación de autorización — se agregó después de esa barrida. Se corrigió con `@PreAuthorize("isAuthenticated()")` a nivel de clase, mismo patrón que `ChatbotController` y el resto de controladores de auto-servicio.

**Re-auditoría completa (2026-09-16, examen suspenso P8):** se repitió la barrida del 2026-09-11 sobre el estado actual del código, contando explícitamente cada endpoint `POST`/`PUT`/`PATCH`/`DELETE` de todos los controladores y si tiene alguna forma de `@PreAuthorize`/`@Secured` (de clase o de método) — reproducible con
[`docs/mediciones/sec/owasp/scripts/audit-endpoints-autorizacion.py`](scripts/audit-endpoints-autorizacion.py)
(`python docs/mediciones/sec/owasp/scripts/audit-endpoints-autorizacion.py`, corrido real, salida transcrita abajo). Resultado: **102 endpoints de escritura totales**, misma cifra que reporta la guía del examen suspenso. De esos 102, **0 quedan sin alguna forma de `@PreAuthorize`**, salvo los 5 endpoints de `AuthController` (`login`, `refresh`, `logout`, `recuperar`, `restablecer`) que son deliberadamente públicos: son el propio mecanismo de autenticación/recuperación y no pueden exigir sesión previa; los protege el `permitAll()` de `SecurityConfig` junto con `RateLimitingFilter`, no `@PreAuthorize`. El endpoint del propio perfil (`PATCH /api/usuarios/{id}/perfil`, `AppUserController`) tiene tanto la anotación (`@PreAuthorize("isAuthenticated()")`, agregada en el cierre del 2026-09-11) como una comprobación explícita de propiedad del recurso que devuelve 403 si el id del path no coincide con el usuario autenticado — probado end-to-end con MockMvc real en `AppUserControllerTest#actualizarPerfilRechazaEditarElPerfilDeOtroUsuario` (verificado pasando: `mockMvc.perform(patch("/api/v1/usuarios/99/perfil")...).andExpect(status().isForbidden())`, más `#actualizarPerfilPermiteAlPropioUsuario` para el camino positivo).

```
$ python docs/mediciones/sec/owasp/scripts/audit-endpoints-autorizacion.py
Total endpoints de escritura (POST/PUT/PATCH/DELETE): 102
Sin ninguna anotacion de autorizacion: 5

  AuthController.login (PostMapping, L68) -- exento conocido (auth pre-login)
  AuthController.refresh (PostMapping, L127) -- exento conocido (auth pre-login)
  AuthController.logout (PostMapping, L193) -- exento conocido (auth pre-login)
  AuthController.recuperar (PostMapping, L354) -- exento conocido (auth pre-login)
  AuthController.restablecer (PostMapping, L383) -- exento conocido (auth pre-login)

OK: todos los endpoints sin @PreAuthorize son exentos conocidos y documentados.
```

**Re-verificación (2026-09-17, re-verificación P8 del examen suspenso):** se volvió a correr el mismo
script contra el código actual, después del trabajo de renombrado español→inglés de P4 de este mismo
examen. Esa ronda de P4 renombró el método Java `AuthController.restablecer()` a `AuthController.reset()`
(la ruta HTTP `/api/auth/restablecer` no cambió, solo el identificador interno — P4 nunca toca rutas/JSON),
y esta lista de exentos, al estar codificada por **nombre de método** y no por ruta, quedó desactualizada:
el script pasó de `OK` a `FALLO: 1 endpoint(s) sin autorizacion y sin justificacion conocida` señalando
`AuthController.reset` como inesperado. **Es un hallazgo real, no solo una cita vieja** — se corrigió
actualizando la entrada `("AuthController", "restablecer")` a `("AuthController", "reset")` en
`EXENTOS_CONOCIDOS` dentro del propio script. Re-corrido tras el fix: **102 endpoints de escritura
totales (misma cifra), 5 exentos, los 5 justificados, `OK`.** También se corrió en limpio
`AppUserControllerTest` (la clase quedó renombrada de `AppUserControllerTest` por el mismo P4, igual que
el método `actualizarPerfilRechazaEditarElPerfilDeOtroUsuario` → `updatePerfilRechazaEditarElPerfilDeOtroAppUser`
citado arriba): **23/23 pruebas, 0 fallos**, incluyendo el 403 de editar el perfil ajeno y el 200 del
propio. Y se confirmó leyendo el archivo actual que `MeController` sigue con `@PreAuthorize("isAuthenticated()")`
a nivel de clase (no tiene ningún endpoint de escritura propio — solo `GET /api/me/permisos` — así que
el criterio de cierre lo cubre igual: ningún endpoint del controlador queda sin autorización declarativa).

**Verificado:** CORS restringido explícitamente a `http://localhost:4200` y `http://localhost:3000` (`SecurityConfig` + `WebConfig`, más `@CrossOrigin` por controlador) — configurado en 3 lugares distintos que hay que mantener sincronizados si se agrega un origen nuevo (riesgo de mantenimiento, no de seguridad activa). CSRF deshabilitado deliberadamente (correcto para una API JWT stateless sin cookies de sesión). Sesión configurada como `STATELESS`.

**Hallado y corregido (2026-08-29):** `nginx.conf` declaraba `X-Frame-Options`/`X-Content-Type-Options`/`Content-Security-Policy`/`Permissions-Policy` a nivel `server{}`, lo que hacía que nginx los añadiera también a las respuestas proxied de `/api/v1/` y `/actuator/` — **encima** de los que Spring Security ya agrega para esas mismas rutas, verificado real con `curl -D -` (headers duplicados en la respuesta). Por la especificación de CSP, cuando el navegador recibe dos cabeceras `Content-Security-Policy`, aplica la **intersección** de ambas: la política más laxa del backend (`connect-src` con `localhost:4200`/websockets, necesaria para el frontend en dev) quedaba silenciosamente recortada por la más estricta de nginx (`connect-src 'self'`). Corregido moviendo esas cabeceras exclusivamente a `location /` (la única ruta que nginx sirve directamente, sin backend detrás) — verificado real: tras el fix, `curl -D -` contra `/api/v1/auth/login` muestra un único `Content-Security-Policy`, el del backend con su `connect-src` completo.

## A06:2021 — Vulnerable and Outdated Components

**Confirmado con herramienta automática (2026-08-17):** `npm audit` sobre `Frontend/package-lock.json` real (no simulado) reportó **41 vulnerabilidades: 6 críticas, 25 altas, 7 moderadas, 3 bajas**, incluyendo `@angular/core` 21.2.12 (la misma librería que ZAP detectó como servida al navegador — ver `10003` en la sección ZAP).

**Corregido (2026-08-29):** se actualizaron `@angular/core`, `common`, `compiler`, `compiler-cli`, `forms`, `platform-browser`, `router`, `build` y `cli` de 21.2.12 a **21.2.22** — un parche dentro del mismo rango semver ya declarado en `package.json` (`^21.1.0`/`^21.1.2`), verificado con `npm ls` y con un `ng build --configuration production` real que compiló sin errores y se sirvió correctamente vía Docker/nginx (confirmado con login end-to-end contra la topología completa). Esto resuelve directamente los 3 CVEs que ZAP había detectado (GHSA-f3m7-gqxr-g87x, GHSA-rgjc-h3x7-9mwg, GHSA-692r-grfm-v8x7) y varios más. `npm audit` bajó a **14 vulnerabilidades: 5 críticas, 5 altas, 4 moderadas, 0 bajas** — las 14 restantes trazan a `sharp`/`to-ico`/`jimp`/`request` (dependencias de dev usadas solo para generar los íconos de la app, no se envían al navegador ni corren en producción).

El backend no se auditó con `mvn dependency-check:check` porque requiere descargar la base de datos NVD completa (varios GB, puede tardar más de una hora en la primera corrida) — queda pendiente para una corrida con tiempo dedicado, idealmente como job de CI con caché persistente de la base NVD.

---

## Matriz consolidada

| Control OWASP | Estado | Hallazgos | Acción |
|---|---|---|---|
| A01: Broken Access Control | 🟢 Corregido | 2 reales (usuarios sin `@PreAuthorize`, registro público) | `@PreAuthorize('ADMIN')` + validación de propiedad |
| A02: Cryptographic Failures | 🟢 Corregido | 3 corregidos (password en texto plano, filtrado en JSON, secreto JWT hardcodeado) | Ver detalle arriba |
| A03: Injection | 🟢 Sin hallazgos (manual + find-sec-bugs) | 0 | — |
| A04: Insecure Design | 🟡 Riesgo bajo, sin corregir | 1 (entidades expuestas directamente) | Recomendado para próxima entrega |
| A05: Security Misconfig | 🟢 Corregido (4 por ZAP + 1 High reclasificado y corregido) | 3 `WARN` Medium/Informational abiertos (`10055`,`10109`,`90003`) | Ver sección OWASP ZAP |
| A06: Vulnerable Components | 🟡 Corregido lo crítico (Angular), resto en dev-tooling | 14 (5 críticas, 5 altas, 4 moderadas) en frontend vía `npm audit`, todas en herramientas de build de íconos, no en código enviado al navegador; backend no auditado (NVD pendiente) | `dependency-check` del backend en próxima iteración |

## Análisis estático SpotBugs / find-sec-bugs (2026-08-17, re-corrido 2026-08-29)

Corrido con `./mvnw com.github.spotbugs:spotbugs-maven-plugin:4.8.6.4:spotbugs`, filtrado a la categoría `SECURITY` ([`backend/spotbugs-security-include.xml`](../../../../backend/spotbugs-security-include.xml)), integrado como paso no bloqueante en `.github/workflows/ci.yml` (se publica el reporte XML como artefacto en cada push). **233 hallazgos reales en la corrida vigente** (subió de 189 el 17-08 porque se agregó código de producción real en ese periodo, no por nuevas categorías de riesgo), ninguno de tipo `SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE`. Detalle completo con el desglose 17-08→29-08 por regla en [`docs/mediciones/sec/static-analysis/STATIC-ANALYSIS.md`](../static-analysis/STATIC-ANALYSIS.md):

| Regla | Cantidad (29-08) | Severidad | Estado |
|---|---|---|---|
| `SPRING_ENDPOINT` | 160 | Informativa | Sin acción (marca cada endpoint REST, no es una vulnerabilidad) |
| `CRLF_INJECTION_LOGS` | 52 | Baja/Media | Abierto — logs con `Logger.info(fmt, objetoUsuario)` podrían permitir forjar entradas de log si el objeto contiene `\r\n`; mitigación: usar un `encoder` de logging que escape saltos de línea |
| `IMPROPER_UNICODE` | 11 | Baja | Abierto — comparaciones/transformaciones de String sin especificar `Locale` |
| `PATH_TRAVERSAL_IN` | 9 | Media | Abierto — `Paths.get()` en `MinutesServiceImpl`, `ProposalServiceImpl`, `TutoringServiceImpl` construye rutas de archivo a partir de datos que en última instancia vienen de la base de datos (IDs `Long`, no strings de usuario libres) — riesgo real bajo pero sin validación explícita de que la ruta resultante permanezca dentro del directorio esperado |
| `UNSAFE_HASH_EQUALS` | 0 | Media | **Corregido el 17-08, confirmado que se mantiene el 29-08** — `ProposalServiceImpl.verificarIntegridad()` comparaba hashes SHA-256 con `String.equals()` (vulnerable a timing attack); se cambió a `MessageDigest.isEqual()` |
| `SPRING_CSRF_PROTECTION_DISABLED` | 1 | Informativa | Sin acción — deshabilitado deliberadamente por ser API JWT stateless (ver A05) |

4. La mitigación CSRF se delega al uso de tokens JWT sin cookies (en cabecera `Authorization`) y validación estricta de CORS.

4. **Hallazgo real, y corrección de esta misma nota (auditoría 2026-09-13):** el backend (Spring Security)
no emitía la cabecera `Strict-Transport-Security` (HSTS) porque opera detrás de un proxy inverso
(recibe peticiones HTTP de nginx, nunca ve HTTPS directamente). **Esta entrada afirmaba antes que
"ZAP y Lighthouse detectaron" el problema — eso no es exacto.** Cruzando la afirmación contra los JSON
crudos: ZAP no reporta ninguna alerta de HSTS en ninguna de sus dos corridas (`zap-baseline-report.json`,
`.PREVIOUS.json`); Lighthouse sí tiene el audit `has-hsts`, pero HSTS solo es significativa sobre HTTPS,
y este entorno se prueba siempre sobre `http://localhost` — por eso el audit sigue reportando
`"No HSTS header found"` en las corridas vigentes aunque la cabecera ya se agregue, y seguirá así
mientras no se pruebe sobre HTTPS real. La verificación honesta aquí es directa por cabecera
(`curl -I`), no por estos dos scanners. Se agregó `add_header Strict-Transport-Security
"max-age=31536000; includeSubDomains" always;` en los archivos de configuración de Nginx locales
(`nginx.conf`) y de producción (`Frontend/nginx.railway.conf.template`), en el bloque de contenido
estático y en el proxy `/api/`. Se encontró y corrigió además un hueco real: los `location` de
assets estáticos (`\.(?:js|css)$` y de imágenes/fuentes) declaran su propio `add_header` y por eso
**no heredaban** el HSTS del bloque `location /` (regla estándar de nginx, ya documentada en un
comentario del propio archivo) — los bundles JS/CSS y las imágenes se servían sin la cabecera. Se
repitió el `add_header` en esos dos bloques, en ambos archivos de configuración.

## Escaneo dinámico OWASP ZAP (2026-08-17, re-corrido 2026-08-29)

Corrido con el plan de automatización [`zap.yaml`](../zap/zap.yaml) (imagen oficial `zaproxy/zap-stable`) contra la app real corriendo con la topología de producción (nginx sirviendo el build de Angular + proxy inverso a `/api/v1/` y `/actuator/`, backend Spring Boot, Postgres y Redis, todo vía `docker compose up -d --build`). Reportes completos de la corrida más reciente (2026-08-29): [`docs/mediciones/sec/zap/zap-baseline-report.html`](../zap/zap-baseline-report.html) y [`zap-baseline-report.json`](../zap/zap-baseline-report.json). La corrida del 2026-08-17 se conserva sin modificar como [`zap-baseline-report.PREVIOUS.html`](../zap/zap-baseline-report.PREVIOUS.html)/[`.json`](../zap/zap-baseline-report.PREVIOUS.json).

**17-08, antes de corregir headers:** `FAIL-NEW: 0` · `WARN-NEW: 11` · `PASS: 56`
**17-08, después de corregir 4 hallazgos de headers:** `FAIL-NEW: 0` · `WARN-NEW: 7` · `PASS: 60` (incluía `10003 Vulnerable JS Library`, riesgo **`High (Medium)`** — ver A06)
**29-08, después de corregir `10003` (bump de Angular):** `FAIL-NEW: 0` · `WARN-NEW: 3` · `PASS: 58` — **0 alertas de riesgo `High` o `Critical` en el escaneo actual.**

**0 alertas de alta confianza (`FAIL`) en las tres corridas** — ningún hallazgo crítico tipo XSS reflejado, inyección SQL detectable dinámicamente, o cookie insegura. Los 4 hallazgos de headers corregidos el 17-08 (real, verificado con `curl -I` antes/después):

- `10020` Missing Anti-clickjacking Header, `10021` X-Content-Type-Options Missing, `10038` CSP Header Not Set, `10036` Server Version Disclosure — las 4 causadas por el mismo hueco: **nginx sirve el HTML/JS/CSS del frontend directamente sin pasar por el backend**, así que las cabeceras de seguridad que Spring Security agrega (Requisito E14) nunca llegaban a esas respuestas. Se agregaron las mismas cabeceras (`X-Frame-Options`, `X-Content-Type-Options`, `Content-Security-Policy`, `Permissions-Policy`, `server_tokens off`) directamente en [`nginx/nginx.conf`](../../../../nginx/nginx.conf).

El hallazgo `10003` corregido el 29-08 (real, riesgo **`High`**, ver A06 para el detalle del CVE y la corrección) — pasó de alerta a `PASS` tras actualizar Angular a 21.2.22.

Los **3 `WARN` restantes** (Medium/Informational, sin cambios entre corridas, quedan documentados para una próxima iteración):

- `10055` CSP: Failure to Define Directive with No Fallback (falta `object-src`/`base-uri`/`form-action` explícitos en la CSP).
- `10109` Modern Web Application (informativo — ZAP simplemente identifica que es una SPA moderna, no es una vulnerabilidad).
- `90003` Subresource Integrity Attribute Missing (los `<script>` del build de Angular no llevan atributo `integrity`; requeriría configurar SRI en `angular.json`, no soportado de forma nativa por el builder actual).

**Nota de proceso:** al agregar las cabeceras de seguridad a `nginx.conf` se detectó y corrigió un efecto secundario real: la Content-Security-Policy heredada de `SecurityConfig.java` no incluía `cdn.jsdelivr.net` (de donde carga `bootstrap-icons`), lo que habría roto los íconos del frontend en producción; se agregó el dominio a `style-src`/`font-src` en ambos lugares (backend y nginx) tras verificarlo con un login real de punta a punta contra la topología de producción completa (nginx + backend + Postgres + Redis, todos en Docker).
