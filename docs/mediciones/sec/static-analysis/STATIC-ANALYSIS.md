# Análisis Estático de Seguridad — SpotBugs + find-sec-bugs

**Fecha:** 2026-08-17, re-corrido 2026-08-29 (auditoría de reproducibilidad).
**Herramienta:** SpotBugs 4.8.6.4 + plugin find-sec-bugs 1.13.0, filtrado a la categoría `SECURITY`.
**Alcance:** todo el código fuente compilado del backend (`backend/src/main/java`).
**Evidencia cruda:** [`spotbugs-findsecbugs-report.xml`](spotbugs-findsecbugs-report.xml) (corrida 2026-08-17, conservada), [`spotbugs-findsecbugs-report-2026-08-29.xml`](spotbugs-findsecbugs-report-2026-08-29.xml) (corrida vigente).
**Config del filtro:** [`backend/spotbugs-security-include.xml`](../../../../backend/spotbugs-security-include.xml).
**Integración CI:** paso no bloqueante en [`.github/workflows/ci.yml`](../../../../.github/workflows/ci.yml) (job `backend`), se publica el XML como artefacto en cada push.

## Cómo se reprodujo

```bash
cd backend
./mvnw com.github.spotbugs:spotbugs-maven-plugin:4.8.6.4:spotbugs
# genera target/spotbugsXml.xml
```

## Resultado: regla obligatoria `SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE`

**0 hallazgos.** No existe ninguna instancia en el código donde se pase un `String` no constante a un método `execute`/`executeQuery`/`executeUpdate` de JDBC. Todo el acceso a datos pasa por Spring Data JPA (parámetros ligados) o por los procedimientos almacenados PL/pgSQL parametrizados en `backend/src/main/resources/db/migration/V2__stored_procedures.sql`. Esto confirma cuantitativamente lo que la revisión manual (ver [`../owasp/OWASP-AUDIT.md`](../owasp/OWASP-AUDIT.md), control A03) ya había determinado por inspección de código.

## Todos los hallazgos — corrida vigente 2026-08-29 (233 totales, ninguno de SQL dinámico)

El total subió de 189 (17-08) a 233 (29-08) porque se agregó código de producción real en ese periodo
(más controllers/endpoints) — no porque se introdujeran nuevas categorías de vulnerabilidad. La regla
`UNSAFE_HASH_EQUALS` (la única con severidad Media que se había corregido) **sigue en 0 ocurrencias**,
confirmando que el fix del 17-08 se mantiene.

| Regla | Cantidad (17-08 → 29-08) | Severidad | Estado |
|---|---|---|---|
| `SPRING_ENDPOINT` | 121 → 160 | Informativa | Sin acción — marca cada endpoint REST como tal, no es una vulnerabilidad |
| `CRLF_INJECTION_LOGS` | 44 → 52 | Baja/Media | Abierto — `Logger.info(fmt, objetoDeUsuario)` podría permitir forjar entradas de log si el objeto serializado contiene `\r\n` |
| `IMPROPER_UNICODE` | 13 → 11 | Baja | Abierto — comparaciones/transformaciones de `String` sin especificar `Locale` explícito |
| `PATH_TRAVERSAL_IN` | 9 → 9 | Media | Abierto — `Paths.get()` en `MinutesServiceImpl`, `ProposalServiceImpl`, `TutoringServiceImpl` construye rutas a partir de datos que en última instancia vienen de la BD (IDs `Long`), sin validación explícita de que la ruta resultante quede dentro del directorio esperado |
| `UNSAFE_HASH_EQUALS` | 1 → 0 | Media | **Corregido 2026-08-17, confirmado que se mantiene el 2026-08-29** — `ProposalServiceImpl.verificarIntegridad()` comparaba hashes SHA-256 con `String.equals()` (vulnerable a timing attack); se cambió a `MessageDigest.isEqual()` |
| `SPRING_CSRF_PROTECTION_DISABLED` | 1 → 1 | Informativa | Sin acción — CSRF deshabilitado deliberadamente por ser API JWT stateless |

## Próximos pasos recomendados (no corregidos en esta ronda)

1. Sanitizar/escapar saltos de línea antes de loguear objetos con datos de usuario (`CRLF_INJECTION_LOGS`), o migrar a logging estructurado (JSON) que no sea vulnerable a esto por diseño.
2. Añadir `Locale.ROOT` explícito en las comparaciones de `String` señaladas por `IMPROPER_UNICODE`.
3. Validar explícitamente que las rutas resueltas en `PATH_TRAVERSAL_IN` permanezcan dentro del directorio base esperado (p. ej. con `Path.normalize()` + verificación de prefijo), aunque el riesgo real es bajo porque los valores de entrada son IDs `Long`, no strings arbitrarios de usuario.
