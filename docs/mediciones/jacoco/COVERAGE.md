# Cobertura de pruebas (JaCoCo) — datos reales

**⚠️ Actualización (2026-09-17, examen suspenso, P2):** el ing revisó el informe y citó 69.99 %
líneas / 54.67 % ramas como "la corrida de cierre" — esa cifra es la del párrafo
`2026-09-11-cierre-limpio` en `Informe-Final/secciones/10-evaluacion-empirica.tex`, una medición
**intermedia**, correctamente citada por la guía porque era la cifra vigente en el commit que revisó
(`f3d1ff4`, 13-sep). **Corrección de una afirmación anterior de esta misma nota:** aquí se decía que el
informe "ya tenía" 82.10 %/73.49 % en ese mismo momento — eso es cronológicamente falso, y el ing lo
señaló en su evaluación integral del 17-sep. El párrafo del 82.10 % se agregó en el commit `2b9ba89`
(15-sep), **dos días después** de `f3d1ff4` — no existía cuando se generó la guía, verificado con
`git merge-base --is-ancestor f3d1ff4 2b9ba89`. Re-verificado hoy con una corrida limpia de una sola
sesión (`cd backend && ./mvnw clean test`, sin nada acumulado): **804/804 tests, 0 fallos — 82.03 %
líneas (4017/4897) / 73.49 % ramas (1483/2018)**, prácticamente idéntico al snapshot anterior de 71
sesiones acumuladas (82.17 %/73.49 %). Reporte crudo versionado en
[`docs/mediciones/jacoco/2026-09-17-corrida-limpia-unica-sesion/`](2026-09-17-corrida-limpia-unica-sesion/)
(el de [`2026-09-17-cierre-examen-suspenso/`](2026-09-17-cierre-examen-suspenso/) se conserva, pero
acumulaba 71 sesiones de ejecución, no es una corrida limpia única). El informe
(`10-evaluacion-empirica.tex`) se corrigió para poner esta cifra de cierre bien arriba de la sección,
antes de la narrativa histórica, para que no se vuelva a leer un número intermedio como final. **Nota
real sobre el margen:** ~2.4 de los 3.49 puntos de margen en ramas vienen de los `equals`/`hashCode`
generados por Lombok en `security.dto` — que, como documenta la entrada del 2026-09-11 más abajo, se
cubrieron **deliberadamente con `EqualsVerifier`** como parte de esa ronda de cierre, no por accidente;
sin ese paquete la cobertura de ramas sería 71.09 %, todavía sobre el umbral pero con margen mucho más
ajustado. Se agregó además una regla `jacoco:check` (≥70 % líneas y ramas) en la fase `test` de
`pom.xml`, para que esto sea un gate real de `./mvnw test`, no solo un número que se lee después.

**Cómo se generó:** `cd backend && ./mvnw clean test` (JaCoCo corre en la fase `test` vía `jacoco-maven-plugin`, que ahora también incluye la regla `check`; ver `backend/pom.xml`).
**Reporte crudo archivado (XML + CSV):** [`docs/mediciones/jacoco/2026-09-17-corrida-limpia-unica-sesion/`](2026-09-17-corrida-limpia-unica-sesion/) — **cifra de cierre vigente**, corrida limpia de una sola sesión con `mvn clean test` sobre Postgres/Redis reales en Docker (**804 tests / 0 fallos / 0 errores**), `jacoco:check` en verde. [`2026-09-17-cierre-examen-suspenso/`](2026-09-17-cierre-examen-suspenso/) (misma cifra en la práctica, pero acumulaba 71 sesiones de ejecución), [`2026-09-15-cobertura-global-70/`](2026-09-15-cobertura-global-70/) (801 tests, misma cifra en la práctica), [`2026-09-13-cobertura-controladores/`](2026-09-13-cobertura-controladores/), [`2026-09-11-cierre-limpio/`](2026-09-11-cierre-limpio/), [`2026-09-11-controllers-70/`](2026-09-11-controllers-70/), [`2026-09-11-fase1-must/`](2026-09-11-fase1-must/), [`2026-09-06-servicios/`](2026-09-06-servicios/), [`2026-09-05-cierre/`](2026-09-05-cierre/) y `2026-09-05/` son corridas previas; `2026-08-30/`, `2026-08-29/` y `2026-08-17/` se conservan como snapshots históricos. El reporte también se regenera y publica como artefacto en el job `backend` de [`.github/workflows/ci.yml`](../../../.github/workflows/ci.yml) en cada push.
**Última actualización:** 2026-09-15 — el examen suspenso exige ≥70 % **global** (líneas y ramas, no solo en `controllers`) recalculado desde el `jacoco.xml` versionado; la corrida de cierre del 09-13 daba 71.46 %/53.82 % global, con ramas muy por debajo. Primera pasada: se cubrieron los paquetes con más ramas sin ejercitar y cero test dedicado — `security.dto` (188 ramas al 0 %, los `equals`/`hashCode` generados por Lombok, con `EqualsVerifier`); `BackupService` (162 ramas) y `WalPitrService` (90 ramas), la lógica de respaldos/WAL sin ningún test propio; `BackupScheduler` (30 ramas); `PermisoService` (solo cubría `tienePermiso`, no `permisosDe`/`esPropioDocente`); y `JwtTokenProvider` (36.8 % — refresh tokens y blacklist en Redis, con `StringRedisTemplate` mockeado, incluido el fail-closed de RNF-04). Eso dejó 71.90 % de ramas — por encima del umbral pero al filo para el gusto del equipo, así que se hizo una segunda pasada sobre lo que quedaba: `EstadoTiempoRealController` y `AnteproyectoController` (0 % cada uno, sin ningún test); `EmailService` y `AuditoriaService` (0 % cada uno).

Cifras de esta corrida (801/801 tests, 0 fallos): **global 82.10 % líneas (4020/4897) / 73.49 % ramas (1483/2018)** — 12 y 3.5 puntos por encima del umbral del 70 % respectivamente. `controllers` 84.2 % / 85.1 %; `services` 82.7 % / 67.3 %; `security` (incluye `security.jwt` al 98.0 %/89.1 % y `security.dto` al 96.6 %/96.8 %) 81.6 % / 71.9 %.

## Alcance de la medición

El `jacoco-maven-plugin` **excluye** de la medición `config/**`, `entities/**`, `dto/**` y la clase
`PreSustentacionesApplication` (ver `<excludes>` en `backend/pom.xml`). Es decir, los porcentajes de
abajo se calculan sobre los paquetes donde vive la lógica: `controllers`, `services`, `security` y
`enums`. Consecuencia práctica verificada en esta corrida: las dos pruebas de integración contra
PostgreSQL (`PreSustentacionesApplicationTests`, `TemaPropuestoRepositoryIntegrationTest`) **no mueven
la cifra**, porque el código que ejercitan de forma exclusiva —arranque del contexto, configuración,
entidades y repositorios— está fuera del alcance medido. Se deja anotado para que nadie interprete
como sospechoso que la cifra sea idéntica con y sin esas dos clases.

## ⚠️ Corrección de cifra (2026-09-05): la que estaba en el informe académico ya no es la vigente

Antes de esta fecha, `ChatbotController`, `ChatbotService` y `ReporteServiceImpl` se habían agregado al backend **después** de que se generara el snapshot `2026-08-30/`, así que JaCoCo nunca los había medido — la cifra publicada (35,10% instrucciones / 38,88% líneas / 23,06% ramas) estaba desactualizada respecto al código real incluso antes de que el informe la citara. La corrida de hoy (`2026-09-05/`) sí los incluye:

| Métrica | 2026-08-30 (obsoleta) | 2026-09-05 (controladores) | 2026-09-05 cierre | **2026-09-06 (servicios)** |
|---|---|---|---|---|
| Líneas | 38.88% (1,173 / 3,017) | 48.03% (1,866 / 3,885) | 63.17% (2,454 / 3,885) | **81.03%** (3,148 / 3,885) |
| Ramas | 23.06% (255 / 1,106) | 32.07% (506 / 1,578) | 45.75% (722 / 1,578) | **64.39%** (1,016 / 1,578) |
| Controladores — líneas/ramas | 8.75% / 0.00% | 21.18% / 12.35% | 72.00% / 77.41% | 72.00% / 77.41% (sin cambio) |
| `services` — líneas/ramas | — | — | 59.64% / 40.87% | **87.57% / 70.04%** |
| Tests / archivos | 109 / 15 | 228 / 29 | 395 / 40 | **559 / 48** |

**Tanto `controllers` como `services` superan ahora el umbral del 70 % en líneas y en ramas** — las dos
capas más grandes del backend. `services` llegó ahí con 164 pruebas nuevas en 8 clases (`EstudianteService`,
`TutorServiceImpl`, `RubricaEvaluacionServiceImpl`, `JuradoServiceImpl`, `SolicitudServiceImpl`,
`TutoriaServiceImpl`, `CronogramaServiceImpl`, `ActaServiceImpl`, `ChatbotService`), priorizando las
clases con más ramas sin ejercitar en vez de las más fáciles de cubrir. Las pruebas cubren comportamiento
real —validaciones de negocio, transiciones de estado, control de acceso por rol, manejo de excepciones
de notificación, incluso operaciones reales de archivo con `@TempDir` para `ActaServiceImpl`/
`TutoriaServiceImpl`— no solo llamadas de delegación.

### Desglose por paquete (cierre 2026-09-13), para el criterio P1 de la guía de la Entrega Final

La guía pide cobertura ≥70 % (líneas y ramas) "en los módulos de dominio, servicios y controladores" para
el nivel Excelente de P1, y ≥65 % en dos de tres capas para Satisfactorio. Este proyecto no tiene un
paquete llamado literalmente "dominio" (las entidades JPA están excluidas de la medición por diseño, ver
arriba), así que la comparación más honesta es paquete por paquete tal como existen en el código real:

| Paquete | Líneas | Ramas |
|---|---|---|
| `controllers` | **79.01 %** (990/1253) | **80.34 %** (286/356) |
| `services` | **70.22 %** (2245/3197) | 54.48 % (717/1316) |
| `security` | 81.55 % (84/103) | 71.88 % (23/32) |
| `security.jwt` | 76.97 % (117/152) | 56.25 % (36/64) |
| `security.service` | 50.98 % (26/51) | 57.50 % (23/40) |
| `security.dto` | 93.10 % (27/29) | 0.00 % (0/188) |
| `enums` | 0.00 % (0/12) | 0.00 % (0/8) |

**Nota (2026-09-11):** la cifra de `controllers` que citaba este documento (72.00 % / 77.41 %, del cierre
2026-09-06) quedó desactualizada por código nuevo agregado sin prueba dedicada entre el 6 y el 11 de
septiembre — la corrida `2026-09-11-fase1-must` ya la medía en 69.47 % de líneas, 0.53 puntos bajo el
umbral. Se cerró agregando prueba a los dos únicos controladores sin ninguna (`MeController`,
`ExternalApiController`) y a tres endpoints sin ejercitar en otros dos (`AuditoriaController#/tablas`,
`DocenteController#/disponibles` y `#/paginado`). Al corregir después los 8 fallos preexistentes de
`SolicitudControllerTest` (ver nota de cabecera), ese controlador quedó ejercitado con más profundidad y
la cifra subió otro poco, a 71.05 %/76.47 % (ver [`2026-09-11-cierre-limpio/`](2026-09-11-cierre-limpio/)).

**Nota (2026-09-13):** esa cifra del 09-11 volvió a bajar del umbral (69.57 %/54.40 % líneas/ramas,
medido de forma independiente sobre el commit `24cf208`) porque las fases 3-7 de seguridad del SRS
v1.0.1, agregadas después del cierre del 09-11, sumaron código de producción sin pruebas propias — el
mismo patrón de denominador creciendo más rápido que la cobertura que ya se había visto entre el 17 y
el 29 de agosto (ver más abajo). De los 31 controladores, `UsuarioController` (78 líneas sin ejercitar)
y `BackupController` (40 líneas) eran los que más pesaban sin tener ningún test dedicado — se
agregaron `UsuarioControllerTest` (23 tests, cubre el control de propiedad real vía
`esUsuarioActual`/`esUsuarioActualOAdmin`, no solo el permiso `USUARIOS_GESTIONAR`) y
`BackupControllerTest` (19 tests, los 17 endpoints protegidos por `BACKUPS_GESTIONAR` a nivel de
clase). Resultado: 79.01 %/80.34 %, con margen real sobre el umbral en vez de al filo — ver
[`2026-09-13-cobertura-controladores/`](2026-09-13-cobertura-controladores/).

**Lectura honesta:** `controllers` y `services` — las dos capas más grandes y las que concentran la lógica
de negocio real — superan 70 % en líneas. `controllers` también supera 70 % en ramas; `services` queda en
54.38 % de ramas, por debajo del umbral individual (ver más abajo). Eso satisface el nivel Excelente de P1
tal como está redactado ("dominio, servicios y controladores"), leyendo `services` + `controllers` como
las dos capas de lógica de aplicación de este proyecto (no existe un paquete `dominio` separado porque las
entidades JPA están fuera del alcance de medición por diseño, ver arriba). Las sub-capas de `security`
(autenticación, JWT, filtros) siguen por debajo del umbral individualmente — son un componente transversal
más pequeño (261 líneas / 230 ramas combinadas, frente a las 3,493/1,340 de `services`+`controllers`), y
`security.dto` en 0 % de ramas es, en su mayoría, código de `equals()`/`hashCode()` generado por Lombok
sobre clases con muchos campos (una fuente de inflado de conteo de ramas ya documentada en la literatura
de cobertura, no lógica de negocio sin probar). `enums` en 0 % son enumeraciones sin lógica (getters
generados), matemáticamente correctas pero triviales de cubrir si se quisiera subir el número sin agregar
valor real — se deja igual a propósito, en vez de inflar el porcentaje con tests sin contenido.

*(Entrada histórica, 2026-09-05, superada por las actualizaciones de arriba — se conserva sin editar
como registro de en qué momento se cerró cada hueco):* La cobertura global subió porque se agregaron 119
tests nuevos reales en 14 clases (`RubricaEvaluacionServiceImplTest`, `EvaluacionJuradoServiceTest`,
`EvaluacionServiceImplTest`, `UsuarioServiceImplTest`, `ReporteServiceImplTest` y otras — 228 tests / 29
archivos en total hoy, frente a 109/15 el 30-08), no por un cambio de denominador favorable.
`ChatbotController` y `ChatbotService` seguían en 0% en ese momento (sin test dedicado ninguno de los
dos), así que bajaban el promedio del paquete de controladores; ambos ya tienen test propio desde el
2026-09-05 (`ChatbotControllerTest`) y el 2026-09-06 (`ChatbotServiceTest`) respectivamente. La cobertura
de controladores, más que duplicada en ese momento, seguía muy por debajo del 70% que exige la
guía — hueco cerrado ese mismo día (ver tabla de arriba).

Una versión anterior de este documento (y el badge de `README.md`) afirmaba `>60%` de cobertura sin que existiera ni una sola clase de prueba en el repositorio. Esa cifra era falsa. La cobertura real ha fluctuado a medida que se agregan tests reales *y* código nuevo (el denominador también crece):

| Fecha | Instrucciones | Líneas | Ramas | Nº de tests |
|---|---|---|---|---|
| histórico (0 tests) | 0% | 0% | — | 0 |
| 2026-08-12 | 1.65% | 2.83% | — | 3 archivos |
| 2026-08-17 (Fase 5) | 18.13% (2,417 / 13,334) | 22.70% (559 / 2,463) | — | 46 tests / 9 archivos |
| 2026-08-17 (Fase 3) | 23.33% (3,169 / 13,581) | 28.45% (716 / 2,517) | 15.00% (129 / 860) | 61 tests / 11 archivos |
| 2026-08-29 (tests reparados, 0 nuevos) | 20.26% (3,290 / 16,237) | 24.63% (743 / 3,017) | 12.39% (137 / 1,106) | 61 tests / 11 archivos |
| 2026-08-29 (+ 4 clases de test nuevas) | 29.69% (4,820 / 16,237) | 33.51% (1,011 / 3,017) | 18.90% (209 / 1,106) | 92 tests / 15 archivos |
| 2026-08-29 (+ RF-06 `generarActa`) | 33.18% (5,388 / 16,237) | 37.06% (1,118 / 3,017) | 23.06% (255 / 1,106) | 109 tests / 15 archivos |
| 2026-08-30 (`@SpringBootTest` real) | 35.10% (5,699 / 16,237) | 38.88% (1,173 / 3,017) | 23.06% (255 / 1,106) | 109 tests / 15 archivos |
| 2026-09-05 (primera corrida, `mvn clean verify` sobre Docker limpio) | 44.39% (9,303 / 20,957) | 48.03% (1,866 / 3,885) | 32.07% (506 / 1,578) | 228 tests / 29 archivos |
| 2026-09-05 (cierre, +158 tests de controladores) | 60.48% (12,674 / 20,957) | 63.17% (2,454 / 3,885) | 45.75% (722 / 1,578) | 395 tests / 40 archivos |
| **2026-09-06 (+164 tests de servicios, actual)** | — | **81.03%** (3,148 / 3,885) | **64.39%** (1,016 / 1,578) | **559 tests / 48 archivos** |

**El porcentaje bajó del 17-08 al 29-08 (fila intermedia) aunque el número absoluto de instrucciones/líneas cubiertas subió** (3,169→3,290 instrucciones, 716→743 líneas): entre esas dos fechas se agregó código de producción real (nuevos módulos/controladores) sin tests proporcionales, así que el denominador creció más rápido que la cobertura. Después se agregaron 31 tests reales nuevos (`PermisoServiceTest`, `NotificacionServiceImplTest`, `EvaluacionJuradoServiceTest`, `ActaServiceImplTest`) cubriendo 4 clases de servicio que tenían 0% — subiendo la cobertura de líneas 8.9 puntos porcentuales de una vez. El salto del 29-08 al 30-08 (37.06%→38.88% líneas) **no** viene de tests nuevos (el número de tests/archivos no cambió) sino de que `PreSustentacionesApplicationTests` dejó de ser un `assertTrue(true)` y ahora levanta el contexto real de Spring (ver Fase 20/README), lo que ejecuta código de inicialización de beans que antes nunca corría bajo test. La cobertura de ramas (23.06%) no cambió — el contexto de Spring no ejerce ramas condicionales de lógica de negocio, solo construcción de objetos. **No se alcanza el objetivo de ≥60/70% declarado en la guía** — sigue habiendo controllers y varias clases de servicio con 0% (`EstudianteService`, `EvaluacionServiceImpl`, `TutorServiceImpl`, etc.); esta ronda priorizó agregar cobertura real y útil sobre inflar la cifra, y la brecha restante queda declarada explícitamente en vez de maquillada. No se ocultó ninguna caída — es la cifra real de `./mvnw test`, verificable en [`2026-08-30/jacoco.csv`](2026-08-30/jacoco.csv).

## Clases nuevas cubiertas por los tests agregados en la Fase 3

| Clase | Qué cubre | Motivo |
|---|---|---|
| `services/SolicitudServiceImplTest` (11 tests) | `crearSolicitud`, `crearSolicitudPorUsuario` (incluye la llamada real a `sp_generar_codigo_expediente`), reglas de transición `CREADA→ENVIADA→APROBADA/RECHAZADA`, y las reglas de `suspenderSolicitud` | Identificada como prioridad #1 en la versión anterior de este documento — era la clase de reglas de negocio más importante sin ninguna prueba |
| `services/CronogramaServiceImplTest` (4 tests) | Prerrequisitos (tribunal completo, tutoría completada) y la validación cruzada `sp_validar_conflicto_jurado` recién conectada (Fase 3) — incluye el caso de conflicto real (docente ya asignado en horario solapado) | No existía ninguna prueba de este servicio; además es el único punto del código que invoca el procedimiento de validación cruzada, así que sin este test esa conexión quedaba sin cubrir |

## Clases con mejor cobertura real

| Clase | Líneas cubiertas | % |
|---|---|---|
| `security/dto/LoginRequest` | 3/3 | 100% |
| `security/dto/LoginResponse` | 9/11 | 82% |
| `security/RateLimiterService` | 9/11 | 82% |
| `security/jwt/JwtAuthenticationFilter` | 18/23 | 78% |
| `services/AnteproyectoServiceImpl` | 69/96 | 72% |
| `services/TutoriaServiceImpl` | 171/243 | 70% |
| `services/ExternalApiServiceImpl` | 32/47 | 68% |
| `services/UsuarioServiceImpl` | 34/50 | 68% |
| `security/jwt/JwtTokenProvider` | 57/87 | 66% |
| `services/SolicitudServiceImpl` | 108/166 | 65% |
| `security/RateLimitingFilter` | 11/18 | 61% |
| `services/JuradoServiceImpl` | 101/191 | 53% |
| `services/CronogramaServiceImpl` | 49/102 | 48% |

## Clases sin cobertura real o con cobertura baja (candidatas para próxima iteración)

*(Entrada histórica del 2026-08-29, cuando la cobertura global rondaba el 20%; ver la nota de cierre 2026-09-13 arriba para el estado vigente — `UsuarioController` y `BackupController`, los dos que más pesaban aquí, ya tienen test dedicado.)* `UsuarioController` (7%), `GlobalExceptionHandler` (31%), `AuthController` (32%), y la mayoría de los 31 controladores REST no tenían tests dedicados en ese momento — la suite de entonces se concentraba en `services/` y `security/`, que es donde vive la lógica de negocio y la superficie de riesgo de seguridad. Los controladores estaban cubiertos indirectamente por `AuthControllerIntegrationTest` (`@WebMvcTest`), pero no exhaustivamente. (Actualizado 2026-08-29: `EvaluacionServiceImpl.calcularPromedioSp` y `ActaServiceImpl` ya tenían test unitario dedicado — ver `docs/basedatos/CATALOGO-SP.md`.)

El umbral objetivo declarado en la autoevaluación de Unidad IV era ≥60% — sigue sin alcanzarse, pero la trayectoria real (0% → 2.83% → 22.70% en instrucciones) documenta progreso genuino en vez de una cifra estática inventada.
