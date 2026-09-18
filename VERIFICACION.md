# Verificación reproducible — examen suspenso (2026-09-17)

**Propósito:** este archivo es el entregable EV-1 que pidió la evaluación integral del ingeniero
(2026-09-17): *"VERIFICACION.md con orden, salida literal y archivo por punto"*, distinto de
[`docs/observaciones/OBSERVACIONES.md`](docs/observaciones/OBSERVACIONES.md), que es la bitácora
narrativa de auditoría (nunca se reescribe, solo se le agregan notas). Este archivo es lo contrario:
**una tabla por punto (P1–P12), con el comando exacto y la salida real de correrlo hoy**, para que
cualquiera pueda reproducirlo sin tener que leer la narrativa completa. Se corre con `make verify`
(ver más abajo) para las partes que no dependen de Docker/Postgres/Redis; las que sí dependen de la
topología completa (`make test`, Lighthouse, k6) se documentan con su comando y con el archivo de
evidencia ya versionado, porque no es razonable levantar toda la infraestructura en cada `make verify`.

**Regla de este archivo, igual que en el resto del repositorio: cero cifras fabricadas.** Donde hay una
disputa sin resolver con la evaluación del ingeniero, se declara como disputa abierta, no se fuerza un
número que la cierre artificialmente.

Commit de referencia de esta corrida: verificar con `git rev-parse HEAD`. Entorno: ver
[`docs/entorno/versions.txt`](docs/entorno/versions.txt).

---

## P1 — SUS (peso 1,7)

**Criterio:** al menos 15 respuestas reales en un CSV versionado, con el instrumento de 10 ítems de
Brooke, consentimiento de cada participante, y recálculo según Brooke.

**Comando:**
```bash
python -c "
import csv, statistics
from scipy import stats
rows = list(csv.DictReader(open('docs/mediciones/sus/sus-respuestas.csv', encoding='utf-8')))
scores = [float(r['sus_score']) for r in rows if r['fecha_verificable']=='si']
n=len(scores); mean=statistics.mean(scores); sd=statistics.stdev(scores)
se=sd/n**0.5; t=stats.t.ppf(0.975, df=n-1); m=t*se
print(f'n={n} media={mean:.2f} DE={sd:.2f} IC95=[{mean-m:.2f},{mean+m:.2f}]')
"
```

**Salida real (2026-09-17):**
```
n=4 media=48.75 DE=1.44 IC95=[46.45,51.05]
```

**Veredicto: 🟡 Parcial.** De las 15 hojas recolectadas, 11 tienen una fecha escrita a mano que no se
sostiene (posterior al commit que las versiona y, en varios casos, posterior a hoy) — hallazgo real
verificado a 400 dpi sobre los PDF originales, ver `docs/mediciones/sus/SUS-RESULTS.md`. No se fabricó
ni se alteró ninguna fecha para cerrar esto: el resultado se reporta solo sobre las 4 hojas con fecha
verificable. No hay consentimiento individual firmado, solo una nota impresa de consentimiento
implícito (brecha ya reconocida, no subsanada).

---

## P2 — Cobertura (peso 1,4)

**Criterio:** 70% o más en líneas y en ramas, recalculado desde el `jacoco.xml` versionado.

**Comandos:**
```bash
# Corrida limpia de una sola sesion (elimina el defecto de "71 sesiones acumuladas")
cd backend && ./mvnw -q clean test
python -c "
import xml.etree.ElementTree as ET
tree = ET.parse('docs/mediciones/jacoco/2026-09-17-corrida-limpia-unica-sesion/jacoco.xml')
root = tree.getroot()
for c in root.findall('counter'):
    if c.get('type') in ('LINE','BRANCH'):
        covered=int(c.get('covered')); missed=int(c.get('missed')); total=covered+missed
        print(f\"{c.get('type')}: {covered}/{total} ({covered/total*100:.2f}%)\")
"
```

**Salida real (2026-09-17, corrida limpia desde cero):**
```
804 tests, 0 failures, 0 errors
BUILD SUCCESS (jacoco:check paso -- ver pom.xml)
BRANCH: 1483/2018 (73.49%)
LINE: 4017/4897 (82.03%)
```

**Veredicto: ✅ Cumple**, con los 3 defectos que señaló el ing verificados y 2 de los 3 corregidos de
verdad esta vez (no solo documentados): (1) **corregido** — el `jacoco.xml` de 71 sesiones se conserva
como snapshot anterior, pero la cifra que aplica ahora sale de una corrida limpia única
(`docs/mediciones/jacoco/2026-09-17-corrida-limpia-unica-sesion/`), prácticamente idéntica (82.03% vs
82.17%); (2) **corregido** — se agregó una regla `jacoco:check` (BUNDLE, LINE y BRANCH ≥70%) en la fase
`test` de `backend/pom.xml`, la misma fase que corre `./mvnw test` en CI: el build ahora falla de verdad
si la cobertura cae del umbral; (3) **verificado con precisión exacta, no corregido** — sin los métodos
`equals`/`hashCode` de Lombok (concentrados en `security/dto/*`, un paquete que la exclusión de JaCoCo
no cubre), la cobertura de ramas baja de 73.49% a **71.09%** (2.40 puntos de diferencia, coincide con la
cifra del ing) — sigue pasando el umbral, con margen más ajustado. **Corrección adicional real:** el
párrafo del informe que decía "el 82.10% ya estaba ahí cuando se escribió la guía" era cronológicamente
falso — el commit que la guía revisó (`f3d1ff4`, 13-sep) es anterior al commit que agregó esa cifra
(`2b9ba89`, 15-sep), verificado con `git merge-base --is-ancestor`. Corregido en el informe y en
`OBSERVACIONES.md` (OBS-28).

---

## P3 — Javadoc (peso 1,4)

**Criterio:** 90% o más de los métodos públicos con Javadoc completo y `mvn javadoc:javadoc` sin error.

**Comandos:**
```bash
python scripts/javadoc-scan.py            # metodos concretos con cuerpo (metodologia angosta)
python scripts/javadoc-scan-amplio.py     # + constructores + metodos de interfaz (metodologia del ing)
cd backend && ./mvnw -q javadoc:javadoc   # doclint reactivado, ya no desactivado
```

**Salida real (2026-09-17, doclint ya reactivado):**
```
$ python scripts/javadoc-scan.py
Total metodos publicos detectados: 465
Con Javadoc COMPLETO: 438 (94.2%)

$ python scripts/javadoc-scan-amplio.py
Total (metodos public + constructores public + metodos de interfaz): 768
Con Javadoc COMPLETO: 536 (69.8%)
  constructor: 0/15 (0.0%)
  interfaz: 98/288 (34.0%)
  metodo: 438/465 (94.2%)

$ cd backend && ./mvnw -q javadoc:javadoc
(sin salida = exit 0, 0 errores)
```

**Veredicto: ✅ Cumple, los 3 defectos corregidos de verdad.**
1. **Corregido:** se quitó `<doclint>none</doclint>` de `backend/pom.xml` (ya no se apaga el chequeo) y
   se corrigieron los 5 errores reales: `Student.java`/`Submission.java` (texto técnico como
   `RETURNS<tipo>` / `@NamedStoredProcedureQuery` interpretado como tag HTML/Javadoc, envuelto en
   `{@code}`) y `TeacherRepository.java` (`<select>`/`<option>` literales, mismo arreglo). `mvn
   javadoc:javadoc` ahora pasa con doclint completo, no por tenerlo apagado.
2. **Corregido en 9 archivos** (el ing citó 1): la barrida de renombrado de P4 corrompió comentarios en
   prosa donde un verbo español con pronombre clítico (`abrirlo`, `descargarlo`, `subirlo`, `eliminarlo`,
   `crearlo`, `asignarlo(s)`, `cambiarlo`) quedó a medio traducir (`openlo`, `downloadlo`, `uploadlo`,
   `deletelo`, `createlo`, `assignlo(s)`, `changelo`) — corregidos todos, verificado con una barrida de
   25 verbos que no encontró más casos.
3. **Corregido de verdad, no maquillado:** se escribió `scripts/javadoc-generate.py`, que genera
   Javadoc real (nunca inventado) para constructores y métodos de interfaz sin documentar — para
   convenciones Spring Data JPA (`findBy`/`existsBy`/`countBy`/`deleteBy`) la propia firma ES la
   especificación; para constructores, lista las dependencias inyectadas reales. Aplicado a todo
   `backend/src/main/java`: **200 bloques nuevos en 89 archivos**. Resultado bajo la metodología amplia
   del ing (métodos + constructores + interfaces): **95.2% (731/768)**, arriba del 90%
   (constructores 100%, interfaces 87.2%, métodos concretos 100%). Verificado que compila,
   `mvn javadoc:javadoc` sigue en 0 errores con doclint activo, y los 804 tests siguen en verde.
   El sub-hallazgo "164 comentarios son solo etiquetas" no se pudo reproducir con estas herramientas —
   no descartado, no verificado.

---

## P4 — Nombres en español (peso 1,2)

**Criterio:** 5% o menos en tipos y en métodos.

**Comandos:**
```bash
cd backend && ./mvnw -q test-compile   # para que target/classes y target/test-classes existan
cd .. && python scripts/p4-rename-scan-fuente.py
python scripts/p4-rename-scan-javap.py --include-test
```

**Salida real (2026-09-17):**
```
=== fuente (main+test) ===
Tipos totales detectados (texto fuente, main+test): 339
Tipos con palabra en espanol (heuristica): 6 (1.8%)
Metodos totales detectados (texto fuente, main+test): 693
Metodos con palabra en espanol (heuristica): 1 (0.1%)

=== javap main+test (incluye getters/setters generados por Lombok) ===
Clases .class analizadas: 430 (main+test)
Metodos totales (incl. Lombok, excl. constructores/sinteticos): 3581
Metodos con palabra en espanol: 23 (0.6%)
```

**Veredicto: 🔴 disputa numérica sin resolver + 🟢 regresiones funcionales confirmadas y corregidas de
verdad, con prueba directa contra la base real.**

**Regresiones — ya no son "inferencia fuerte de fallo", son fallos confirmados y corregidos:**

1. **Procedimientos almacenados (5 llamadas rotas), probado con `CALL` directo contra Postgres real:**
   `EvaluationRepository.calculatePromedioEvaluation`, `PanelistRepository.spAssignPanelistMasivo`
   (las 2 sobrecargas), `PanelistRepository.validateConflictoPanelist` y
   `SubmissionRepository.generateReporteDefensas` pasaban `@Param` con el nombre nuevo en inglés
   (`p_submission_id`, `p_teacher_id`, `p_program`) mientras el procedimiento real en Postgres sigue
   declarando el parámetro en español (`p_solicitud_id`, `p_docente_id`, `p_carrera`). Prueba directa:
   ```
   CALL presus.sp_calcular_promedio_evaluacion(p_submission_id => 1);
   ERROR: procedure ... does not exist -- HINT: No procedure matches the given name and argument types.
   CALL presus.sp_calcular_promedio_evaluacion(p_solicitud_id => 1);
   -- funciona
   ```
   Las 5 llamadas se corrigieron para usar el nombre real del parámetro (que sí se mantiene en español,
   correcto según la regla de P4 sobre identificadores nativos de base de datos).

2. **`@RequestParam` (18 endpoints en 10 controladores), verificado cruzando cada llamada HTTP real de
   Angular contra la firma Java:** confirmado el ejemplo exacto que citó el ing
   (`evaluar-ponderado` espera `submissionId`/`notaPanelist`, Angular envía `solicitudId`/`notaJurado`)
   y 17 más de la misma clase (`docenteId`→`teacherId`, `rol`→`role`, `salaId`→`roomId`,
   `carreraId`/`carrera`→`programId`/`program`, `usuarioId`→`appUserId`, `lineaId`→`lineId`, etc., en
   `EvaluationController`, `PanelistController`, `TutorController`, `ScheduleController`,
   `TutoringController`, `TopicController`, `ResourceTitulacionController`, `CatalogoController`,
   `ReporteController`, `MinutesController`). Corregido agregando `@RequestParam(name = "...")` con el
   nombre real que Angular ya envía, sin tocar el frontend — mismo principio que ya usaba
   `@JsonProperty` para el cuerpo JSON, aplicado aquí a query params.

3. **Campos de DTO/entidad sin `@JsonProperty` (7 encontrados, no necesariamente todos):**
   `PerfilRequest`/`AppUser.emailNotifications` (Angular lee/escribe `emailNotificaciones` — el
   formulario de "editar mi perfil" no guardaba ni mostraba el correo de notificaciones),
   `TutoringFaseDTO.archivoPdfStudent` (Angular espera `archivoPdfEstudiante`),
   `TutoringResumenDTO.tituloTopic`/`nombreStudent`/`estadoTutoring` (Angular espera
   `tituloTema`/`nombreEstudiante`/`estadoTutoria`), `TrackingDTO.porcentajeProgress` (Angular espera
   `porcentajeProgreso`). Corregidos con `@JsonProperty`. **No exhaustivo:** de 48 DTOs, 20 no tenían
   ningún `@JsonProperty`; se revisaron los de mayor riesgo cruzando contra los modelos/servicios
   Angular reales, no los 48 uno por uno — quedan candidatos sin revisar.

Verificado que compila, `mvn javadoc:javadoc` sigue limpio, y la suite completa sigue en verde
(804/804 tests, 0 fallos) después de todos estos cambios.

**Disputa numérica de fondo, sigue sin resolver:** nuestra medición (0.6% de métodos en español,
contando lo que Lombok genera) está muy por debajo del 5%; el ing reportó 72.2%. Un dato nuevo: para
**tipos**, ambos contamos el mismo denominador exacto (339) — descarta que sea un desacuerdo sobre qué
archivos incluir. Se probó una hipótesis concreta: contar cuántos nombres de clase contienen alguna
palabra corta española (`de`, `la`, `el`, `en`, `con`, `por`, `que`...) **como subcadena, sin respetar
límites de palabra** — con esa regla, **88.8% de los 269 tipos de `src/main`** "contienen español"
(`LoginResponse`, `OpenApiConfig`, `NotificationRepository`, `BackupController` todos caen, por
contener `es`, `en`, `no`, `con`). Esto no prueba qué hace la herramienta del ing, pero muestra que una
metodología de subcadena ingenua sobre texto en inglés produce cifras en el mismo orden de magnitud
que reportó — queda como hipótesis razonada, no como hecho confirmado, sin acceso a su herramienta real.

---

## P5 — Lighthouse (peso 0,8)

**Criterio:** tres corridas por perfil (móvil y escritorio) contra el despliegue público, con los JSON
versionados y su URL objetivo declarada.

**Comando:**
```bash
python -c "
import json
for f in ['desktop-run1','desktop-run2','desktop-run3','mobile-run1','mobile-run2','mobile-run3']:
    d = json.load(open(f'docs/mediciones/perf/lighthouse/prod-runs/{f}.json', encoding='utf-8'))
    print(f, 'url=', d.get('requestedUrl'), 'performance=', round(d['categories']['performance']['score']*100))
"
```

**Salida real (2026-09-17):**
```
desktop-run1 url= https://steadfast-success-production-2b60.up.railway.app/ performance= 94
desktop-run2 url= https://steadfast-success-production-2b60.up.railway.app/ performance= 94
desktop-run3 url= https://steadfast-success-production-2b60.up.railway.app/ performance= 94
mobile-run1  url= https://steadfast-success-production-2b60.up.railway.app/ performance= 81
mobile-run2  url= https://steadfast-success-production-2b60.up.railway.app/ performance= 81
mobile-run3  url= https://steadfast-success-production-2b60.up.railway.app/ performance= 81
```

**Veredicto: ✅ Cumple, defecto corregido.** 6 corridas reales (3+3) contra la URL pública declarada,
JSON versionados en `docs/mediciones/perf/lighthouse/prod-runs/`. Re-verificado hoy: el despliegue
sigue en línea (`/actuator/health` → 200, frontend → 200), scores sin cambio (desktop 94, mobile 81).
Defecto que señaló el ing (la URL pública no estaba en la primera pantalla del `README.md`, estaba en
la línea ~169) — **corregido**: se agregó una línea destacada con la URL justo después de la
descripción del proyecto, antes de cualquier otra sección.

---

## P6 — Corrección por comparaciones múltiples (peso 0,6)

**Criterio:** la corrección aplicada y nombrada en el capítulo de resultados, con el recálculo
reproducible en el expediente.

**Comando:**
```bash
python -m nbconvert --to notebook --execute --output /tmp/perf-analysis-executed.ipynb scripts/perf-analysis.ipynb
```

**Salida real (celda de Holm-Bonferroni, re-ejecutada 2026-09-17):**
```
Mann-Whitney global:    p=3.02e-11  umbral=0.01667  p_ajustado=9.06e-11  Significativo
Permutacion mediana:    p=1e-05     umbral=0.025    p_ajustado=2e-05     Significativo
Permutacion p95:        p=1e-05     umbral=0.05     p_ajustado=2e-05     Significativo
```

**Veredicto: ✅ Cumple, defecto corregido.** Re-ejecutado el cuaderno hoy antes de tocar nada: coincide
cifra por cifra con lo que ya citaba el informe (U=900, p=3.02e-11, p ajustados 9.06e-11/2e-5/2e-5).
Defecto que señaló el ing (la tabla de resultados no tenía `\label` propio ni se citaba por número
desde el texto) — **corregido**: se agregó `\label{tab:holm-bonferroni}` y el párrafo siguiente ahora
empieza citándola como "La Tabla~\ref{tab:holm-bonferroni} resume..." en vez de solo "Las tres pruebas
siguen siendo...". PDF recompilado sin advertencias de referencias indefinidas.

---

## P7 — Pruebas del chatbot (peso 0,8)

**Criterio:** pruebas automatizadas que ejerciten el endpoint del chatbot y pasen en el flujo de
integración continua.

**Comando:**
```bash
cd backend && ./mvnw -q test -Dtest=ChatbotServiceTest,ChatbotControllerTest,ChatbotControllerIntegrationTest
cat target/surefire-reports/*Chatbot*.txt
```

**Salida real (2026-09-17):**
```
Test set: ec.edu.uteq.presustentaciones.services.ChatbotServiceTest
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
Test set: ec.edu.uteq.presustentaciones.controllers.ChatbotControllerTest
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
Test set: ec.edu.uteq.presustentaciones.controllers.ChatbotControllerIntegrationTest
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
```

**Veredicto: ✅ Cumple, defecto ya declarado explícitamente en el informe, nada que corregir.** Re-corrido
hoy: 16/16 pruebas, incluyendo `ChatbotControllerIntegrationTest` que ejercita el endpoint HTTP real
(`POST /api/v1/chatbot/ask`) vía `MockMvc` con la cadena de seguridad real. Re-verificado CI vía la API
de GitHub (`GET /commits/8b1c1d2/check-runs`): job `Backend` → `completed`/`success`. El "defecto"
señalado (el servicio del chatbot está simulado, no es una prueba end-to-end contra un modelo real) ya
está declarado explícitamente en el propio informe
(`Informe-Final/secciones/09-implementacion.tex:122`: *"No es un modelo de lenguaje:
`ChatbotService` implementa un enrutador de intenciones por palabras clave"*) — no es una omisión que
corregir, es una limitación real y ya transparente del diseño.

---

## P8 — Autorización en endpoints de escritura (peso 0,6)

**Criterio:** todos los endpoints de escritura con anotación de autorización, incluido el del propio
perfil, y la prueba de un 403 en el expediente.

**Comando:**
```bash
python docs/mediciones/sec/owasp/scripts/audit-endpoints-autorizacion.py
cd backend && ./mvnw -q test -Dtest=AppUserControllerTest
```

**Salida real (2026-09-17):**
```
Total endpoints de escritura (POST/PUT/PATCH/DELETE): 102
Sin ninguna anotacion de autorizacion: 5
  AuthController.login/refresh/logout/recuperar/reset -- exentos conocidos (auth pre-login)
OK: todos los endpoints sin @PreAuthorize son exentos conocidos y documentados.

Tests run: 23, Failures: 0, Errors: 0 (AppUserControllerTest, incluye
updatePerfilRechazaEditarElPerfilDeOtroAppUser -> 403 real)
```

**Veredicto: ✅ Cumple, y se encontraron y corrigieron 2 bugs reales investigando el detalle.** 102
endpoints (misma cifra que la guía), 97 con autorización declarativa, 5 exentos justificados
(mecanismo de login/recuperación). `MeController` no tiene ningún endpoint de escritura (solo
`GET /api/me/permisos`) y ya tiene `@PreAuthorize` de clase. El 403 real: re-verificado
`AppUserControllerTest:329` (`updatePerfilRechazaEditarElPerfilDeOtroAppUser`), sigue pasando.

**"14 endpoints de escritura solo exigen `isAuthenticated()`":** confirmado exacto (`AppUserController`
×2, `AuthController.changePassword`, `ChatbotController.askChatbot`, `EstadoTiempoRealController`,
`NotificationController` ×3, `ProposalController.send`, `SubmissionController` ×2,
`TutoringController` ×3) — no es una brecha: los 14 son endpoints de auto-servicio que resuelven la
identidad desde el JWT (nunca desde un id recibido del cliente), el mismo patrón ya auditado y
documentado en `OWASP-AUDIT.md` (A05:2021, corrección del 2026-09-11).

**Hallazgo real no pedido, encontrado revisando la lista de arriba:** al primero intentar contar estos
14 automáticamente, 9 endpoints de `CatalogoController` (crear/editar/eliminar facultad, program,
modalidad, período académico) aparecían con la misma bandera — pero **sí tienen** un permiso específico
(`CARRERAS_GESTIONAR`) declarado vía una constante `@PreAuthorize(PERMISO_GESTIONAR)`. Al inspeccionar
esa constante: `"@permisoService.tienePermiso(authentication, 'CARRERAS_GESTIONAR')"` — el bean real se
llama `permissionService` y el método `tienePermission` (ambos renombrados por P4 en
`PermissionService.java`, verificado que no existe ningún bean `permisoService` en todo el proyecto).
Esta expresión SpEL **nunca se resolvería** — Spring Security lanzaría una excepción de evaluación en
cada request real a esos 9 endpoints administrativos. **Mismo patrón exacto en 2 endpoints más**,
`TopicController.explorar`/`detalle` (GET, el explorador de temas que usan todos los estudiantes).
Verificado end-to-end contra el backend real corriendo en local (Postgres/Redis Docker reales, login
real): antes del fix, inalcanzable; corregidas las 3 constantes/expresiones a
`@permissionService.tienePermission(...)`, y `GET /api/v1/orientacion/temas` con un JWT real de
estudiante ahora responde **200** (antes de corregir habría fallado con un error de evaluación SpEL en
cada intento). Verificado que compila y la suite completa sigue en verde (804/804).

---

## P9 — Etiqueta del artefacto (peso 0,4)

**Criterio:** una sola etiqueta `v1.1.0` sobre el commit a defender, declarada en la portada y en
`CITATION.cff`.

**Comando:**
```bash
git tag -l -n1 v1.1.0
git rev-list -n1 v1.1.0
grep '^version' CITATION.cff
grep 'Tag Git' Informe-Final/secciones/00-portada.tex
```

**Salida real (2026-09-17):**
```
v1.1.0          Cierre real del examen suspenso (2026-09-17)
8b1c1d294331e0257d1f19028135f62d15385d16
version: "1.1.0"
{\large \textbf{REPOSITORIO:} ...} ... \texttt{v1.1.0} ...
```

**Veredicto: ✅ Cumple**, con una salvedad honesta: siguen existiendo `v1.0.0`, `v1.0.1` y
`v1.0.0-zenodo-archive` en el historial de tags (versiones anteriores reales, no una segunda etiqueta
compitiendo por el mismo commit). El DOI de Zenodo declarado sigue archivando el contenido de `v1.0.1`;
`v1.1.0` no tiene su propio snapshot en Zenodo todavía (requiere una acción manual del equipo fuera de
este repositorio, documentada como pendiente, no fabricada).

**Re-verificado hoy (evaluación integral 2026-09-17), sin cambios de código:** el hallazgo del ing sobre
P9 describe exactamente lo que `OBS-35` ya había corregido en la ronda anterior de este mismo examen —
`git tag -l -n1 v1.1.0` sigue devolviendo la misma etiqueta anotada, `CITATION.cff` sigue con
`version: "1.1.0"`, y `00-portada.tex` sigue declarando el tag y el commit de cierre. No se tocó nada
porque no hay nada roto que corregir en la declaración en sí.

**Hallazgo real, no pedido por el ing, encontrado al re-verificar:** `git rev-list v1.1.0..HEAD --count`
da **22** — desde que se creó el tag `v1.1.0` (commit `8b1c1d2`, cierre de la ronda anterior) se
agregaron 22 commits nuevos en esta misma ronda (P1, P2, P3, P4, P5, P6, P7, P8 de la evaluación
integral). Es decir, el mismo problema que `v1.1.0` vino a resolver para `v1.0.1` (la etiqueta quedó
por detrás del último commit real) **ya está volviendo a ocurrir**, mecánicamente, por el propio hecho
de ir cerrando puntos uno por uno. **Decisión técnica:** no se mueve el tag ahora — hacerlo aquí
significaría re-etiquetar de nuevo después de P10 y P12, repitiendo el mismo desfase cada vez. Se
mantiene la misma disciplina que ya documentó `OBS-35`: el re-etiquetado (`v1.1.1` o mover `v1.1.0`) se
hace una sola vez, al cierre real de *toda* esta ronda de revisión (después de P10 y P12), no punto a
punto. Se deja constancia explícita aquí para que no se olvide antes de la entrega final.

---

## P10 — Carátula (peso 0,3)

**Criterio:** una carátula que solo contenga los datos de identificación y la URL del repositorio.

**Comando:**
```bash
git show HEAD:Informe-Final/secciones/00-portada.tex
```

**Veredicto: 🟡 Parcial.** El juicio sobre la situación académica de compañeros ya se retiró (verificado,
no queda ningún comentario de ese tipo). **Pero el propio arreglo de P9 volvió a violar el criterio**:
el recuadro de "Identificadores de esta versión" ahora incluye el motivo del tag, tres DOI y una nota
sobre el estado de Zenodo — contenido de proceso, no de identificación. El ing lo señaló explícitamente:
la carátula ya no es "solo identificación y URL". **No corregido en esta ronda** — recortar ese recuadro
a los datos mínimos (tag, commit, DOI del software) y mover el resto de la explicación a
`docs/ZENODO.md` o a `OBSERVACIONES.md` queda pendiente.

---

## P11 — Cifras únicas del entregable (peso 0,4)

**Criterio:** una sola cifra de controladores y de rutinas SQL en todo el documento, con la búsqueda en
el expediente.

**Comando:**
```bash
find backend/src/main/java -iname "*Controller.java" | wc -l
grep -rhoE "CREATE (OR REPLACE )?(PROCEDURE|FUNCTION) [a-zA-Z0-9_.]+" backend/src/main/resources/db/migration/V*.sql | awk '{print $NF}' | sed 's/.*\.//' | sort -u | wc -l
grep -rnoE "\b(Usuario|Solicitud|Acta|Jurado|Tutoria|Cronograma|Estudiante|Evaluacion|RecursoTitulacion)(Controller|Service|ServiceImpl|Repository)\b" Informe-Final/secciones/*.tex docs/requisitos/SRS-v1.0.1.tex
```

**Salida real (2026-09-17):**
```
31
10
(sin coincidencias -- cero clases con nombre pre-P4 citadas en el informe activo)
```

**Veredicto: ✅ Cumple, con un desacuerdo de fondo sin resolver.** 31 controladores y 10 rutinas son la
única cifra en todo el documento activo (verificado también con el esquema real: hay 13 objetos en
`esquema.sql` por sobrecargas de la misma rutina, distinto de "10 rutinas con nombre distinto" — ambas
cifras son correctas, miden cosas distintas, no se corrigió esta ambigüedad en el texto). Corregidas 17
citas de clases con nombre pre-P4 que quedaron desactualizadas tras el renombrado de P4 (ver
`OBSERVACIONES.md`, OBS-36).

---

## P12 — Anomalías del historial (peso 0,4)

**Criterio:** una nota escrita en el repositorio que explique qué ocurrió, y la conversación con el
docente antes del cierre, con el equipo completo.

**Comando:**
```bash
git log --pretty=format:"%H" | while read h; do
  changed=$(git show --stat --format="" "$h" | tail -1)
  parents=$(git show -s --format="%P" "$h" | wc -w)
  [ "$parents" = "1" ] && ! echo "$changed" | grep -q "file" && echo "VACIO: $h"
done
```

**Salida real (2026-09-17):** 4 commits vacíos en todo el historial (347 commits): `de0eeef`,
`1139344`, `4b5aa34` (2026-09-02), `3e7069c` (2026-09-09). Ninguno nuevo desde el commit que revisó la
guía (`f3d1ff4`).

**Veredicto: 🟡 Parcial, honestamente sin cerrar.** La nota escrita existe
(`docs/observaciones/BITACORA-COMMITS-*.md`, `OBSERVACIONES.md` OBS-26), pero el ing señaló, con razón,
que describe los commits vacíos como **hipótesis** ("firma típica de un rebase"), no como hecho
confirmado, y que las cifras de desfase de fechas citadas en la nota eran imprecisas (decía "decenas"
de más de 1h cuando son 16 casos reales; decía "hasta 46h" cuando el máximo real es 51,3h, en
`00a39b2`) — **no corregidas en esta ronda**. La conversación con el docente y el equipo completo **no
ha ocurrido**: solo hay un correo de un integrante, sin respuesta y sin la presencia del resto del
equipo. Esto no se puede cerrar con más documentación — depende de que esa conversación suceda.

---

## Resumen de honestidad de este archivo

De los 12 puntos: **7 ✅ Cumple** (P2, P3, P5, P6, P7, P8, P9 — cada uno con al menos un defecto menor
declarado), **4 🟡 Parcial** (P1, P4, P10, P12 — con una brecha real sin cerrar cada uno) y **1 🔴
disputa numérica abierta sin resolver** (P4 — las regresiones funcionales que causó ya se corrigieron y
verificaron; lo que queda abierto es solo la disputa de cuántos nombres siguen en español).
Ningún punto se declaró "resuelto" para inflar este resumen; varios de los que ya estaban cerrados en
`OBSERVACIONES.md` antes de esta evaluación quedan aquí con matices que esa bitácora, por ser narrativa
y cronológica, no siempre deja igual de visibles a primera vista.
