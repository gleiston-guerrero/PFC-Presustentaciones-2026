# Cobertura de pruebas (JaCoCo) — datos reales

> ⚠️ **Documento superado.** Este es el primer punto de datos (2026-08-12, 3 archivos de test) de una
> serie histórica. La cifra vigente y el historial completo están en
> [`docs/mediciones/jacoco/COVERAGE.md`](jacoco/COVERAGE.md) — ese es el archivo que enlaza el badge de
> `README.md`. Se conserva este archivo sin borrar como evidencia del punto de partida real.

**Cómo se generó:** `cd backend && ./mvnw test`, luego `backend/target/site/jacoco/jacoco.csv` (reporte generado localmente, no versionado — se regenera en cada corrida).

Una versión anterior de este documento (y el badge de `README.md`) afirmaba `>60%` de cobertura sin que existiera ni una sola clase de prueba en el repositorio. Esa cifra era falsa. Estos son los números reales tras ejecutar la suite de pruebas:

| Métrica | Cobertura real |
|---|---|
| Instrucciones | 1.65% (421 / 25,503) |
| Líneas | 2.83% (86 / 3,036) |

## Clases con cobertura real

| Clase | Líneas cubiertas |
|---|---|
| `security.jwt.JwtTokenProvider` | 27/27 (100%) |
| `services.AppUserServiceImpl` | 34/50 (68%) |
| `entities.Usuario` | 12/18 |
| `services.RubricEvaluationServiceImpl` (`calcularNotaTribunal`) | 9/199 |

## Suite actual (15 pruebas, todas en verde)

- `PreSustentacionesApplicationTests` — smoke test de arranque del contexto Spring.
- `JwtTokenProviderTest` — generación/validación de JWT.
- `AppUserServiceImplTest` — CRUD de usuarios, encriptado de contraseña con BCrypt, asignación de `rolUsuario`, rechazo de email duplicado.
- `RubricEvaluationServiceImplTest` — cálculo real del promedio de notas del tribunal (`calcularNotaTribunal`), incluyendo redondeo a 2 decimales y caso sin evaluaciones.

## Por qué es baja y qué falta

El backend tiene 21 controladores y ~20 servicios; solo 2 servicios tienen pruebas unitarias reales. La cobertura baja es honesta, no un fallo de ejecución: refleja que la mayoría de la lógica (controladores REST, generación de PDF, procedimientos almacenados, flujo de tutorías/actas) todavía no tiene pruebas automatizadas. Ampliar esta suite (especialmente `SubmissionServiceImpl`, con las reglas de transición de estados) es el trabajo pendiente más valioso para la siguiente entrega.
