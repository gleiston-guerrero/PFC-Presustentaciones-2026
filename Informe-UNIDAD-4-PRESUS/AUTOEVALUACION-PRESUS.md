# Autoevaluación — PFC Pre-Sustentaciones UTEQ (Evaluación cruzada · Equipo E)

> **Actividad:** Práctica Experimental Unidad IV — Aplicaciones Web (UTEQ, 5to Nivel A).
> **Proyecto evaluado:** Sistema de Gestión de Pre-Sustentaciones UTEQ — automatización, gestión y evaluación de pre-sustentaciones de trabajos de titulación.
> **Evaluadores:** Tejada Bajaña Luis Alejandro · Alava Alvarado Jean Pierre.
> **Propósito:** análisis crítico de qué falta y qué debería mejorar en el proyecto, para guiar la
> retroalimentación entre los integrantes de la actividad combinada PFC + práctica experimental.

> **Nota de estado (2026-08-17):** este documento es una fotografía de la evaluación cruzada en el momento en que
> se escribió. Desde entonces se cerraron varios de los hallazgos E1–E8 (API externa, caché Redis, versionado
> `/api/v1`, JWT de 7 claims + refresh token, Dockerfile/nginx, cabeceras de seguridad, rate limiting) — ver el
> historial de commits del backend. Las cifras de SUS y Lighthouse citadas más abajo se corrigieron para reflejar
> mediciones reales; los hallazgos y el plan de mejora que siguen no se reescribieron y pueden estar desactualizados
> frente al estado actual del repositorio.

---

## 1. Resumen ejecutivo

El Sistema de Gestión de Pre-Sustentaciones UTEQ es un proyecto completo que cubre el ciclo de vida de las
pre-sustentaciones de trabajos de titulación: solicitudes, anteproyectos, cronogramas, evaluaciones, jurados,
tutorías, rúbricas, actas y reportes. Está construido con **Spring Boot 3.2.1** (Java 17), **Angular 21.1** en el
frontend, **PostgreSQL 15** como base de datos y **Redis 7** como caché distribuida. El proyecto incluye
autenticación JWT stateless, documentación OpenAPI 3.0 (Springdoc 2.3.0), 31 controladores REST, generación de
PDF con iText, y despliegue con Docker Compose. La documentación es exhaustiva: SRS (ISO/IEC/IEEE 29148:2018),
6 ADR, colección Postman con 22 peticiones, auditoría OWASP, pruebas de carga k6 y métricas Lighthouse reales
contra build de producción (Performance 64/61 desktop/mobile, Accesibilidad 89, Buenas Prácticas 100, SEO 91;
ver [`docs/mediciones/perf/lighthouse/LIGHTHOUSE-REPORT.md`](../docs/mediciones/perf/lighthouse/LIGHTHOUSE-REPORT.md)).
La encuesta SUS de una versión anterior de este informe **era fabricada y fue retirada**;
el instrumento real está listo pero pendiente de aplicarse a usuarios reales
(ver [`docs/mediciones/sus/SUS-RESULTS.md`](../docs/mediciones/sus/SUS-RESULTS.md)). Sin embargo, el análisis contra los criterios exigidos por la guía
de la Unidad IV identifica **brechas concretas** que deben cerrarse: la **integración de una API REST externa**,
el **versionado de la API** (`/api/v1/`), el **proxy de borde (nginx)**, la **ausencia de refresh token** y la
**caché Redis en el backend**.

La siguiente tabla clasifica los hallazgos por severidad y prioridad de atención.

## 2. Hallazgos: qué falta y qué mejorar

| # | Área | Hallazgo | Estado actual | Impacto en la guía PE-U4 | Prioridad |
|---|------|----------|---------------|--------------------------|-----------|
| E1 | API externa | No existe consumo de ninguna API REST externa en el backend. No hay `WebClient`/`RestClient`/`RestTemplate` ni reintentos con backoff. | **Falta** | Incumple el "Paso 3 — Consumo de API externa con gestión de errores y caché" (OE3). | 🔴 Alta |
| E2 | Caché externa | El `docker-compose.yml` includes Redis pero el backend **no tiene** `spring-boot-starter-data-redis` en el `pom.xml` ni anotaciones `@Cacheable`. No hay caché implementada. | **Falta** | Criterio de verificación: `redis-cli KEYS "*api_externa*"` falla. | 🔴 Alta |
| E3 | API REST | La ruta base es `/api` sin versión (`/api/v1/`). La guía pide versionado explícito. No se usa el formato de respuesta `{success, data, message, errors, meta}`. | **Parcial** | Incumple parcialmente OE2 (respuestas JSON estructuradas y versionado). | 🔴 Alta |
| E4 | JWT/Seguridad | El JWT implementa solo 3 claims (`sub`, `iat`, `exp`). No hay `jti`, `iss`, `aud`, `nbf`. No existe refresh token ni blacklist en Redis. La cookie usa `Secure=false` y `SameSite=Lax`. | **Parcial** | Incumple parcialmente la gestión avanzada de JWT (RFC 7519 con 7 claims, refresh token, revocación). | 🔴 Alta |
| E5 | Infraestructura | Docker Compose tiene `postgres` y `redis`, pero **no hay backend como servicio Docker** ni nginx ni balanceador. Falta Dockerfile. | **Parcial** | Criterio "Docker Compose de producción" se cumple a medias. | 🟠 Media |
| E6 | Seguridad | Buena base con Spring Security, BCrypt, JWT y `SessionCreationPolicy.STATELESS`. Faltan: cabeceras HSTS/CSP/X-Frame-Options, rate limiting en login, y documentación formal de auditoría OWASP A01–A07 en el código. | **Parcial** | La guía pide auditoría A01–A07 y XSS con tabla de evidencia; varias contramedidas faltan en la config. | 🟠 Media |
| E7 | Frontend | El `CONTRIBUTORS.md` dice "Angular 17" pero `package.json` declara **Angular 21.1**. Los componentes son funcionales pero no hay pruebas unitarias de frontend. | **Desactualizado** | Documentación inconsistente; la defensa en vivo exponería la discrepancia. | 🟠 Media |
| E8 | Pruebas backend | Los tests existen (`PreSustentacionesApplicationTests`, `JwtTokenProviderTest`, `UsuarioServiceImplTest`) pero la cantidad es limitada (3 archivos). No se reporta cobertura con JaCoCo. | **Parcial** | Se espera mayor cobertura y umbral JaCoCo configurado (≥ 60%). | 🟠 Media |
| E9 | Código-On-Demand | No aplicado (es opcional en REST). Se documenta como "no aplicado" — correcto que se declare. | N/A | Sin impacto. | ⚪ Informativo |
| E10 | Monitoreo | `/actuator/**` está como `permitAll` pero no se verifica que Spring Boot Actuator esté como dependencia en el `pom.xml`. No hay health checks expuestos ni métricas Prometheus. | **Mejora** | Útil para el despliegue y la defensa. | 🟡 Baja |

## 3. Plan de mejora propuesto

| Hallazgo | Acción propuesta | Evidencia esperada |
|----------|------------------|--------------------|
| E1 | Implementar `WebClient` (o `RestClient`) con *connect/read timeout*, `onErrorResume` para 4xx/5xx y `retryWhen` con backoff exponencial; consumir una API apropiada al dominio (p. ej. API de universidades, calendario académico o datos abiertos del CES). | Fragmento de código + captura en la UI + mensaje amigable ante error de red. |
| E2 | Agregar `spring-boot-starter-data-redis` al `pom.xml`; anotar los servicios con `@Cacheable("solicitudes")`, `@Cacheable("api_externa")` y `@CacheEvict`; configurar TTL por dominio. | `redis-cli KEYS "*api_externa*"` muestra la clave con TTL. |
| E3 | Mover la ruta base a `/api/v1/` (o declarar versión por cabecera `Accept-Version`); envolver respuestas en el formato `{success, data, message, errors, meta}` con un `ResponseWrapper`. | Swagger UI muestra `/api/v1/*` y respuestas estructuradas. |
| E4 | Ampliar el JWT a 7 claims RFC 7519 (`jti`, `iss`, `sub`, `aud`, `iat`, `nbf`, `exp`); implementar refresh token con rotación en Redis; añadir blacklist para revocación; cambiar cookie a `Secure=true`, `SameSite=Strict`, `HttpOnly=true`. | Token decodificado con 7 claims + endpoint `/api/auth/refresh` + `redis-cli KEYS "refresh:*"`. |
| E5 | Crear Dockerfile multi-etapa (build con `eclipse-temurin:17-jdk`, runtime con `eclipse-temurin:17-jre`); añadir servicio `backend` y `nginx` al Compose con `depends_on`, proxy inverso y serving del frontend build. | `docker compose ps` con `nginx`, `backend`, `redis`, `postgres` en `Up`. |
| E6 | Configurar cabeceras de seguridad en `SecurityConfig` (HSTS, X-Frame-Options, CSP, nosniff); implementar rate limiter en `/api/auth/login` (6 intentos/60 s → HTTP 429); documentar evidencias en `docs/mediciones/sec/`. | Evidencias A01–A07 en `docs/mediciones/sec/` + respuesta 429 capturada. |
| E7 | Actualizar `CONTRIBUTORS.md` a Angular 21.1; agregar pruebas unitarias de frontend con Vitest. | README y CONTRIBUTORS coherentes con `package.json`; test suite de frontend en verde. |
| E8 | Configurar plugin JaCoCo con umbral mínimo 60%; agregar tests de integración para servicios críticos (SolicitudService, CronogramaService, EvaluacionService). | `./mvnw test jacoco:report` con cobertura ≥ 60%. |
| E10 | Agregar `spring-boot-starter-actuator` al `pom.xml`; exponer `/actuator/health` y opcionalmente `/actuator/prometheus`; conectar a healthcheck en Docker Compose. | `curl localhost:8080/actuator/health` devuelve `UP`. |

## 4. Puntos fuertes a conservar

- **Dominio complejo bien modelado:** 31 controladores REST cubriendo el ciclo completo de pre-sustentaciones
  (solicitudes, anteproyectos, cronogramas, evaluaciones, jurados, tutorías, rúbricas, actas, reportes, salas,
  observaciones, notificaciones, estado en tiempo real).
- **Generación de PDF integrada:** uso de iText para generación de actas y reportes directamente desde el backend,
  con endpoints dedicados (`/api/reportes/**`, `/api/tutorias/fases/*/pdf`).
- **Documentación exhaustiva:** SRS v1.0.0 (ISO/IEC/IEEE 29148:2018) con 12 HU y 12 CU (corregido en la
  Fase 7 de "15 HUs y 15 CUs", que era una cifra que nunca existió — ver
  [`../docs/requisitos/CHANGELOG-REQ.md`](../docs/requisitos/CHANGELOG-REQ.md)), 6 ADR, colección Postman con
  22 peticiones, arquitectura C4 en 3 niveles, matriz de trazabilidad, diccionario de datos, OWASP audit y
  documento ético con consentimientos informados.
- **Calidad web medida contra build de producción:** Lighthouse real con 6 corridas (3 desktop + 3 mobile) da
  Accesibilidad 89 y Buenas Prácticas 100 (ya cumplen); Rendimiento 64/61 sigue bajo el umbral de 80
  (ver nota metodológica en el reporte). La encuesta SUS citada en una versión anterior de este
  documento era fabricada y fue retirada (ver `docs/mediciones/sus/SUS-RESULTS.md`); el instrumento real existe pero está pendiente de aplicarse.
- **Seguridad base funcional:** JWT stateless con JJWT 0.12.5, BCrypt, `SessionCreationPolicy.STATELESS`,
  `@PreAuthorize` por roles (ADMIN, DOCENTE, COORDINADOR), CORS configurado y validación con Jakarta Validation.
- **Despliegue parcialmente reproducible:** Docker Compose con PostgreSQL 15 y Redis 7, health checks con
  `pg_isready` y `redis-cli ping`.
- **OpenAPI documentada:** Springdoc 2.3.0 con Swagger UI accesible, esquema Bearer Auth, descripción completa
  de la API y licencia MIT.

## 5. Conclusión de la autoevaluación

El proyecto cumple de manera notable los criterios de funcionalidad, documentación y usabilidad exigidos en las
prácticas experimentales. Con 31 controladores REST y un frontend Angular 21 completo, demuestra un dominio
sólido del patrón MVC y la arquitectura cliente-servidor. Las brechas principales
—por ser los criterios específicos del Paso 3 de la Unidad IV— son la **ausencia de consumo de una API REST
externa**, la **falta de caché Redis activa en el backend** (Redis existe en Docker Compose pero no se usa desde
el código), el **versionado de la API** y el **proxy nginx** en el despliegue de producción. La seguridad, aunque
funcional con JWT y BCrypt, necesita reforzarse con cabeceras HTTP de seguridad, rate limiting, refresh token y
7 claims RFC 7519. Con las acciones del plan de mejora y la retroalimentación del Equipo E, el Sistema de Gestión
de Pre-Sustentaciones UTEQ alcanza la totalidad de los criterios de verificación de la Práctica Experimental de
la Unidad IV.
