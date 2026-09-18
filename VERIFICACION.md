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

## Re-verificación completa del 2026-09-18

Los 12 puntos se volvieron a correr contra el repositorio de hoy, en una sesión distinta y tras otros
commits, para comprobar que ninguna afirmación de este archivo quedó desactualizada. Resultado:

| # | Estado | Qué se corrió hoy y qué dio |
|---|---|---|
| P1 | 🟡 Parcial | `n=4 media=48.75 DE=1.44 IC95=[46.45,51.05]` — reproduce exacto; 15 filas en el CSV |
| P2 | ✅ Cumple | `./mvnw clean test`: **804 pruebas, 0 fallos**, `jacoco:check` pasa, **1 sesión** en el XML, LINE 82,03 %, BRANCH 73,49 % |
| P3 | ✅ Cumple | `./mvnw javadoc:javadoc`: **BUILD SUCCESS, exit 0, 0 errores**, con `doclint` activo (no hay `<doclint>` en ningún `pom.xml`); 731/768 elementos documentados (95,2 %) |
| P4 | 🔴 **No cumple** | Disputa **resuelta a favor del ing**; medición real 35,7 % de tipos contra un máximo de 5 % — ver abajo |
| P5 | ✅ Cumple | 6 corridas Lighthouse versionadas en `prod-runs/`; URL pública en la primera pantalla del README |
| P6 | ✅ Cumple | `\label{tab:holm-bonferroni}` presente y citado con `\ref` en `10-evaluacion-empirica.tex:85` |
| P7 | ✅ Cumple | Surefire de hoy: 11 + 2 + 3 = **16 pruebas del chatbot, 0 fallos** |
| P8 | ✅ Cumple | 102 endpoints de escritura; los 5 sin anotación son los exentos de pre-login (`login`, `refresh`, `logout`, `recuperar`, `reset`) |
| P9 | ⚠️ Ver nota | El tag `v1.1.0` existe y `CITATION.cff` + portada lo declaran, **pero apunta 37 commits atrás** |
| P10 | ✅ Cumple | Portada: 32 líneas, 0 referencias DOI, 0 notas de proceso, URL del repositorio presente |
| P11 | ✅ Cumple | 31 controladores, 10 rutinas SQL distintas, cero clases con nombre pre-P4 en el informe activo |
| P12 | 🟡 Parcial | Ningún commit vacío nuevo desde `f3d1ff4`; la conversación con el docente sigue sin ocurrir |

Todo lo anterior sale de correr `make verify` más `./mvnw clean test` y `./mvnw javadoc:javadoc` con
Postgres y Redis reales levantados. **Dos cambios respecto de la versión anterior de este archivo:**
P4 bajó de 🟡 a 🔴 (por honestidad, no por un criterio nuevo), y P2 quedó confirmado por una segunda
corrida limpia independiente que da exactamente las mismas cifras.

**Nota de P9, que es la más importante de todas:** el tag `v1.1.0` sigue apuntando a `8b1c1d2`. Todo lo
que está en esta tabla —incluidos `VERIFICACION.md`, `make verify` y `CONTRIBUCIONES.md`, los tres
entregables obligatorios cuya ausencia topa la nota al 40 %— vive en commits **posteriores** a ese tag.
Bajo la regla de la guía (*"lo que no esté dentro de la etiqueta no existe"*), nada de esto es visible
para una revisión que lea `v1.1.0`.

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

**Reproducción independiente (2026-09-18, otra sesión, tras otros commits):**
```
Tests run: 804, Failures: 0, Errors: 0, Skipped: 0
jacoco:check (jacoco-check) --- All coverage checks have been met.
BUILD SUCCESS -- Total time: 01:02 min -- 2026-09-18T11:12:42-05:00
sessioninfo en el XML: 1
BRANCH: 1483/2018 (73.49%)
LINE: 4017/4897 (82.03%)
```
Versionada en [`docs/mediciones/jacoco/2026-09-18-corrida-limpia-reproduccion/`](docs/mediciones/jacoco/2026-09-18-corrida-limpia-reproduccion/).
**Cifra por cifra idéntica** a la del 17-sep: la cobertura reportada no depende de qué corrida se haya
versionado. El valor de ramas coincide además, al dígito, con el que el propio ingeniero calculó sumando
los contadores del XML (1483/2018 = 73,49 %).

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

**Comando que resuelve la disputa (nuevo, 2026-09-18):**
```bash
python scripts/p4-nombres-espanol.py --bytecode
```

**Salida real (2026-09-18):**
```
DEFINICION NUCLEO (solo dominio, sin funcionales ni ambiguos)
  main + test:     tipos   121/339   ( 35.7%)   metodos  271/693   ( 39.1%)
  solo src/main:   tipos   108/272   ( 39.7%)   metodos  232/625   ( 37.1%)
DEFINICION AMPLIA (+ funcionales + ambiguos resueltos por contexto)
  main + test:     tipos   127/339   ( 37.5%)   metodos  369/693   ( 53.2%)
  solo src/main:   tipos   112/272   ( 41.2%)   metodos  315/625   ( 50.4%)
CONTEO SOBRE BYTECODE (javap, incluye metodos generados por Lombok)
  main + test: 430 clases
    todas las ocurrencias   1821/3581  ( 50.9%)
    nombres distintos       1284/1924  ( 66.7%)
```

**Veredicto: 🔴 el criterio NO se cumple, y la cifra que el equipo reportó antes era incorrecta.**

**La disputa numérica está resuelta, y a favor del ingeniero.** Se retracta la medición anterior
(1,8 % de tipos y 0,1 % de métodos): era un artefacto de un diccionario demasiado estrecho, no una
medición del código. Detalle completo en
[`docs/observaciones/P4-RESOLUCION-DISPUTA.md`](docs/observaciones/P4-RESOLUCION-DISPUTA.md).

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

3bis. **Auditoría sistemática del contrato JSON (2026-09-18) — 23 campos rotos más.**

La revisión manual del 17-sep encontró 7 campos y declaró explícitamente que no era exhaustiva. Lo
era aún menos de lo que parecía. Se escribió [`scripts/p4-contrato-json.py`](scripts/p4-contrato-json.py),
que calcula el nombre JSON que **realmente** emite cada campo (`@JsonProperty` si existe, si no el
nombre Java) y lo cruza contra cada `interface` de TypeScript del frontend, resolviendo `extends`.
Encontró **23 contratos rotos en 10 DTOs**:

| DTO | Campo Java | JSON que emitía | Angular lee |
|---|---|---|---|
| `EvaluationPanelistDTO` | `notaPanelist` | `notaPanelist` | `notaJurado` |
| `EvaluationPanelistDTO` | `nombrePanelist` | `nombrePanelist` | `nombreJurado` |
| `EvaluationPanelistDTO` | `rolePanelist` | `rolPanelist` ← *anotado, pero al nombre equivocado* | `rolJurado` |
| `EvaluationRubricResponse` | `nombrePanelist`, `notaTotalPanelist`, `rolePanelist` | 3 nombres en inglés | `nombreJurado`, `notaTotalJurado`, `rolJurado` |
| `MinutesDetalleDTO` | `tituloTopic`, `observacionesMinutes` | idem | `tituloTema`, `observacionesActa` |
| `MiStudentTutoradoDTO` | `tituloTopic`, `estadoTutoring`, `estadoSubmissionCodigo`, `estadoSubmissionNombre` | idem | `tituloTema`, `estadoTutoria`, `estadoSolicitudCodigo`, `estadoSolicitudNombre` |
| `ObservacionesSubmissionDTO` | `tituloTopic`, `nombreStudent`, `nombrePanelist`, `notaPanelist` | idem | `tituloTema`, `nombreEstudiante`, `nombreJurado`, `notaJurado` |
| `ReporteResumenDTO` | `totalSubmissions`, `totalMinutes`, `sustentacionesPorPeriod` | idem | `totalSolicitudes`, `totalActas`, `sustentacionesPorPeriodo` |
| `EstadoBackupsDTO` | `ultimoBackup`, `ultimoBackupHace`, `totalBackups` | idem | `ultimoRespaldo`, `ultimoRespaldoHace`, `totalRespaldos` |
| `ReporteActividadTeacherDTO` | `comoPanelist` | `comoPanelist` | `comoJurado` |

**El más grave es el primero, y es exactamente el flujo que el ing pidió demostrar en vivo**
(solicitud #3: *"demostrar el flujo de evaluación ponderada y la asignación de jurado desde el
frontend"*). `GET /api/v1/evaluacion-jurado/tribunal/{id}` devuelve `EvaluationPanelistDTO`, y
`evaluar-solicitud.component.ts:89` hace:

```ts
const sum = evals.reduce((acc, e) => acc + e.notaJurado, 0);
```

Con el contrato roto, `e.notaJurado` es `undefined`, la suma da **`NaN`**, y el promedio del tribunal
y la nota final ponderada quedan en `NaN`. Peor: `evaluar-solicitud.component.html:45` hace
`{{ evalJurado.notaJurado.toFixed(2) }}`, que sobre `undefined` lanza un **`TypeError` y rompe el
renderizado de la plantilla**. Y `getEvaluacionJuradoPorRol()` compara `e.rolJurado === rol`, que
nunca coincide, así que ningún miembro del tribunal se encuentra.

El patrón de fondo es siempre el mismo: **`@JsonProperty` aplicado de forma inconsistente dentro de
la misma clase.** En `EvaluationPanelistDTO`, `submissionId` y `panelistId` sí estaban anotados y sus
tres vecinos no. No fue un descuido puntual, fue sistemático.

**Limitación declarada:** el script solo cubre lo que está tipado. El frontend tiene ~45 métodos de
servicio que devuelven `Observable<any>`, sin interfaz contra la cual comparar. Esos quedan fuera y no
se declara esta auditoría como exhaustiva.

Verificado tras los 23 cambios: **804/804 pruebas en verde**, `jacoco:check` pasa, y el script vuelve
a salir con código 0.

3. **Campos de DTO/entidad sin `@JsonProperty` (7 encontrados en la ronda del 17-sep):**
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

**Disputa numérica de fondo: RESUELTA el 2026-09-18, el equipo estaba equivocado.**

La hipótesis anterior (que el ing usara coincidencia de subcadena ingenua, que daba 88,8 %) queda
descartada: era una explicación construida para defender nuestra cifra, y no era la correcta.

Al tokenizar los identificadores por camelCase y clasificar contra un léxico español completo, el
conteo de **tipos reproduce las cifras del ingeniero al dígito**:

| | ing (2026-09-17) | `p4-nombres-espanol.py` (2026-09-18) |
|---|---|---|
| Tipos, main+test | **121/339 (35,7 %)** | **121/339 (35,7 %)** ✅ idéntico |
| Tipos, solo `src/main` | **39,7 %** | **39,7 %** ✅ idéntico |
| Métodos, solo `src/main` | 52,0 % | 50,4 % (definición amplia) |
| Métodos, main+test | 72,2 % (1325/1836) | 66,7 % (1284/1924, bytecode, nombres distintos) |

Coincidir en numerador **y** denominador en dos cortes distintos no es casualidad: es la misma
medición. La diferencia en métodos se explica porque su universo (1836) es el de nombres distintos a
nivel de bytecode — es decir, **incluye los accesores que genera Lombok**, que heredan el nombre del
campo (`getEstado`, `setTitulacion`, `getObservaciones`) y por eso *suben* el porcentaje en vez de
bajarlo. Nuestro conteo por texto fuente no los veía.

**Por qué nuestra cifra anterior estaba mal.** No fue un problema de parseo — el universo de tipos
siempre coincidió (339 = 339). El diccionario de `p4-rename-scan-fuente.py` omitía justo los tokens
en español más frecuentes del código:

| token | apariciones | ¿estaba en el diccionario viejo? |
|---|---|---|
| `por` | 56 | no |
| `estado` / `estados` | 46 | no |
| `de` | 22 | no |
| `titulacion` | 18 | no |
| `autenticar` | 15 | no |
| `reporte` | 14 | no |
| `como` | 13 | no |

`por` solo ya aparece en 56 identificadores (`obtainPorId`, `listPorEstado`, `searchPorSubmission`),
casi todos métodos. Un diccionario que no lo incluye no puede ver el patrón de nombres dominante del
repositorio, y por eso daba 0,1 %.

**Consecuencia honesta:** P4 **no está cumplido**. El criterio pide 5 % o menos y la medición real es
35,7 % en tipos y ~50–72 % en métodos según el corte. El renombrado masivo de `49adaee` fue
incompleto: cambió los tokens evidentes y dejó intactos los estructurales (`Estado`, `Titulacion`,
`Criterio`, `Reporte`, `Observaciones`, `Supresion`, `Tipo`, `Fase`, y el `Por` de los finders).
Corregir esto de verdad es un renombrado de la misma escala que el que ya rompió los contratos de
la sección anterior, y no se va a hacer a 12 horas del cierre para maquillar un número: queda
declarado como incumplido y medido con método público.

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

**Actualización 2026-09-18:** el desfase ya no es de 22 commits sino de **37**, y la condición que
justificaba esperar (cerrar P10 y P12) se cumplió: P10 está ✅ y P12 quedó 🟡 por depender de una
conversación con el docente, que no va a cambiar por esperar más. **El re-etiquetado es ahora la acción
pendiente de mayor impacto de todo este archivo**, y no por una cuestión de prolijidad: dentro de esos
37 commits están `VERIFICACION.md`, `make verify` y `CONTRIBUCIONES.md` (creados en `9fd9d0c`, 17-sep
17:50). Son los tres entregables obligatorios cuya ausencia, según la propia hoja de cálculo del ing,
topa **todos** los puntos al 40 % y baja la nota de un rango de 4,00–6,97 a 2,92–4,00. Están escritos y
en `main` desde hace casi un día, pero fuera de la etiqueta que se evalúa.

---

## P10 — Carátula (peso 0,3)

**Criterio:** una carátula que solo contenga los datos de identificación y la URL del repositorio.

**Comando:**
```bash
git show HEAD:Informe-Final/secciones/00-portada.tex
```

**Veredicto (ronda anterior): 🟡 Parcial.** El juicio sobre la situación académica de compañeros ya se
retiró (verificado, no queda ningún comentario de ese tipo). **Pero el propio arreglo de P9 volvió a
violar el criterio**: el recuadro de "Identificadores de esta versión" ahora incluía el motivo del tag,
tres DOI y una nota sobre el estado de Zenodo — contenido de proceso, no de identificación.

**Re-verificado y corregido hoy (evaluación integral 2026-09-17):** el ing confirmó exactamente el
mismo defecto con dos síntomas concretos, verificados aquí contra el PDF real antes de tocar nada
(`Read` sobre `informe-final.pdf`, páginas 1–3): (1) la URL del repositorio se desbordaba a una página 2
casi en blanco — causa raíz real: el espaciado vertical fijo de la carátula (`\\[1.2cm]`, `\\[1.5cm]`,
etc.) sumaba más que `\textheight` (carta, márgenes de 1in), así que el último elemento (REPOSITORIO)
no cabía y se empujaba solo a la página 2; (2) la página 3 abría con el recuadro `\fbox` completo:
commit de cierre, "Motivo del tag" citando textualmente "el ing lo señaló como P9", tres DOI y "Nota
real sobre el DOI" — narrativa de proceso del examen, no datos de identificación.

**Corregido:** en `Informe-Final/secciones/00-portada.tex` se (a) redujo el espaciado vertical fijo de
la carátula para que quepa completa en una sola página, incluida la URL del repositorio, y (b) se
eliminó por completo el recuadro `\fbox` de "Identificadores de esta versión" (commit, motivo del tag,
tres DOI, nota sobre Zenodo) — ese contenido de proceso no vuelve a aparecer en ningún lugar del informe
impreso. La carátula ahora contiene únicamente: universidad, facultad, carrera, asignatura, título del
trabajo, la línea compacta "Informe Final — Tag v1.1.0" (que sigue satisfaciendo el criterio original de
P9 de declarar el tag en la portada), autores con ORCID, docente-director, fecha y la URL del
repositorio. Los identificadores técnicos (commit, DOI) siguen documentados donde corresponde —
`CITATION.cff`, `README.md` y `VERIFICACION.md`/`OBSERVACIONES.md` — sin duplicarlos como narrativa en
el PDF. Recompilado con `latexmk -pdf`: sin errores, 68 páginas (antes 67 con la carátula rota que
generaba una página casi en blanco); verificado visualmente que la carátula cabe completa en la página 1
y que la página 2 pasa directo al Resumen.

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

**Veredicto (ronda anterior): ✅ Cumple, con un desacuerdo de fondo sin resolver.** 31 controladores y
10 rutinas son la única cifra en todo el documento activo (verificado también con el esquema real: hay
13 objetos en `esquema.sql` por sobrecargas de la misma rutina, distinto de "10 rutinas con nombre
distinto" — ambas cifras son correctas, miden cosas distintas, no se corrigió esta ambigüedad en el
texto). Corregidas 17 citas de clases con nombre pre-P4 que quedaron desactualizadas tras el renombrado
de P4 (ver `OBSERVACIONES.md`, OBS-36).

**Re-verificado y corregido hoy (evaluación integral 2026-09-17).** El ing repitió el mismo punto con
tres detalles nuevos, verificados uno por uno:

1. **"13 objetos en esquema.sql por sobrecargas":** confirmado exacto —
   `grep -c "^CREATE \(OR REPLACE \)\?\(PROCEDURE\|FUNCTION\)" database/esquema.sql` → **13** (8
   `PROCEDURE` + 5 `FUNCTION`), contra 10 nombres distintos. Ya estaba señalado en la ronda anterior
   pero sin corregir en ningún texto — corregido hoy (ver punto 3).
2. **"`03-introduccion.tex:22` dice 30 controladores... el resto dice 31":** re-verificado con `grep`
   multilínea (no de una sola línea, ver punto 4) sobre `Informe-Final/secciones/*.tex` completo: **no
   hay ningún "30 controladores" vigente** — ya se había corregido en la ronda anterior (`OBS-36`,
   commit `6282d50`, antes de que se generara este PDF de evaluación integral). Confirmado también que
   no quedan citas de `UsuarioController`/`EvaluacionController` en ningún documento activo.
3. **"El SRS desglosa '8 procedimientos y 2 funciones', que no cuadra con el código":** confirmado
   exacto — `docs/requisitos/SRS-v1.0.1.tex:339` decía literalmente eso. El desglose real por nombre es
   7 procedimientos y 3 funciones (10 rutinas); por objeto del esquema materializado son 8 `PROCEDURE` +
   5 `FUNCTION` (13 objetos) — ninguna combinación da "8 y 2". **Corregido:** reescrita la oración para
   declarar ambas cifras correctamente (10 por nombre, 13 objetos reales, con la razón de la diferencia)
   y citar `CATALOGO-SP.md`. De paso, en la misma oración, se encontró y corrigió un error más no citado
   por el ing: decía "30 migraciones Flyway"; `find backend/src/main/resources/db/migration -iname
   "V*.sql" | wc -l` da **31**, no 30 — corregido también. SRS recompilado (`latexmk -pdf`, 80 páginas,
   sin errores) y verificado con `pdftotext` que el PDF final dice el texto corregido.
4. **"La búsqueda de DATA-PROVENANCE.md es de una sola línea y no detecta el '30'":** confirmado el
   defecto de metodología, aunque no encontró un "30" vigente hoy. Causa real: la búsqueda documentada
   corría `grep` línea por línea sobre el `.tex` **fuente**, donde LaTeX envuelve libremente el texto
   (p. ej. `...15\nrequisitos funcionales...31\ncontroladores REST...` — el número y la palabra caen en
   líneas de archivo distintas), así que un `grep` de una sola línea puede fallar en detectar una
   combinación real sin que se note — el hecho de que hoy no haya ningún "30" no prueba que la búsqueda
   sea confiable, ya que sencillamente no encontró nada tampoco antes por una fuente distinta. **Corregido:**
   la búsqueda ahora corre sobre el texto ya renderizado del PDF (`pdftotext -layout ... | grep`), donde
   LaTeX sí reflowa el texto a líneas completas — inmune al problema de line-wrap del `.tex` fuente.
   Re-ejecutada así hoy sobre `informe-final.pdf` y `SRS-v1.0.1.pdf`: sigue dando 31/10 en todo el
   documento activo, ahora con una metodología que si hubiera existido un "30" perdido por el wrapping,
   sí lo habría encontrado. Documentado en `docs/mediciones/DATA-PROVENANCE.md`.

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

**Veredicto (ronda anterior): 🟡 Parcial, honestamente sin cerrar.** La nota escrita existe
(`docs/observaciones/BITACORA-COMMITS-*.md`, `OBSERVACIONES.md` OBS-26), pero el ing señaló, con razón,
que describe los commits vacíos como **hipótesis** ("firma típica de un rebase"), no como hecho
confirmado, y que las cifras de desfase de fechas citadas en la nota eran imprecisas (decía "decenas"
de más de 1h cuando son 16 casos reales; decía "hasta 46h" cuando el máximo real es 51,3h, en
`00a39b2`) — **no corregidas en esta ronda**. La conversación con el docente y el equipo completo **no
ha ocurrido**: solo hay un correo de un integrante, sin respuesta y sin la presencia del resto del
equipo. Esto no se puede cerrar con más documentación — depende de que esa conversación suceda.

**Re-verificado hoy (evaluación integral 2026-09-17). Sigue 🟡 Parcial — el mismo punto sigue sin
poder cerrarse del todo, por la misma razón: no depende solo del equipo.** El ing repitió el hallazgo
con el detalle exacto de la imprecisión, y esta vez sí se corrigió lo que sí depende de nosotros:

- **Empíricamente re-confirmadas las dos cifras** que el ing señaló como incorrectas:
  `git log --format="%H %at %ct" | awk '...'` sobre el historial completo da exactamente **16** commits
  con más de 1 hora de desfase entre `AuthorDate` y `CommitDate` (no "decenas"), y el máximo real es
  **51,33 horas**, en `00a39b2` (autor `2026-09-09 00:50:42 +0000`, commit `2026-09-10 23:10:46 -0500`),
  no "hasta 46h". **Corregido en `OBSERVACIONES.md` (OBS-26)**, que citaba las cifras incorrectas — se
  reemplazaron por las cifras reales, con nota explícita de que fueron corregidas en esta ronda (no se
  reescribe la fila original, se anota encima, siguiendo el patrón de auditoría de este archivo).
- **El lenguaje de hipótesis ("firma típica de un rebase") se deja intacto, a propósito.** No es un
  defecto a corregir: git no registra en ningún objeto del historial *por qué* una fecha de autor y de
  commit difieren — solo se puede inferir el mecanismo por el patrón de las fechas, nunca confirmarlo
  como hecho. Convertir esa hipótesis en una afirmación categórica sería fabricar certeza que no existe,
  el mismo tipo de dato inventado que las reglas de esta guía prohíben en la dirección contraria.
- **La "conversación con el docente y el equipo completo" sigue sin existir, confirmado de nuevo hoy.**
  Releída la evidencia completa (`evidencia-correo-2026-09-13-punto1.png`): es un correo de un solo
  integrante (Alava Alvarado) al docente, sin respuesta a la fecha de esta nota, sin copia ni presencia
  de los otros tres integrantes del equipo. El propio correo, además, atribuye los tres commits vacíos
  del 2 de septiembre a "otra compañera" (la autora de esos commits según `git log`, Zamora Arias) —
  una afirmación que el propio análisis técnico de `BITACORA-COMMITS-2026-09-02.md` no sostiene como
  hecho: la nota técnica explica el patrón como una probable reescritura/rebase mecánica al cierre de
  una sesión de trabajo, no como una acción atribuible a una persona específica. **Esto no es algo que
  se pueda corregir escribiendo más documentación:** es una atribución hecha por un integrante real
  sobre otra integrante real, en un correo ya enviado, sin que ella ni el resto del equipo hayan
  podido responder. No se altera el correo (es evidencia histórica ya enviada) ni se decide aquí si la
  atribución es justa — eso le corresponde al equipo y al docente, no a esta verificación. Se deja
  constancia explícita de la discrepancia entre lo que el correo insinúa y lo que la propia nota técnica
  del equipo sostiene, para que quien lea esto no confunda una hipótesis mecánica con una acusación.
- **Aclaración importante (misma fecha, tras confirmar con el autor de este examen):** el correo es de
  un solo integrante porque este examen suspenso lo está cursando y sustentando Alava Alvarado **en
  solitario**. Los otros tres integrantes originales del equipo (Moncayo Loor, Zamora Arias, Barreto
  Rosado) reprobaron la materia en el período regular y no están trabajando en las observaciones de
  esta ronda de recuperación. Esto cambia cómo debe leerse "sin presencia del resto del equipo": no es
  una ausencia irregular ni una decisión unilateral que excluyó a los demás de una conversación que
  debían tener — la recuperación, tal como está planteada, ya no es un trabajo de equipo activo, así
  que una "conversación con el equipo completo" no es estructuralmente posible en este momento. Anotado
  también en `BITACORA-COMMITS-2026-09-02.md`.

---

## Resumen de honestidad de este archivo

**Actualizado el 2026-09-18, tras resolver la disputa numérica de P4.** De los 12 puntos:

- **9 ✅ Cumple** — P2, P3, P5, P6, P7, P8, P9, P10, P11, cada uno con al menos un defecto menor
  declarado.
- **2 🟡 Parcial** — P1 y P12, con una brecha real sin cerrar cada uno, y ninguna de las dos se cierra
  con más documentación: dependen de las hojas físicas del SUS y de una conversación con el docente.
- **1 🔴 No cumple** — **P4**. Cambió de 🟡 a 🔴 el 2026-09-18, y el cambio es **a la baja por
  honestidad, no al alza**: la "disputa numérica abierta" se resolvió **a favor del ingeniero**.
  Reproducimos su cifra al dígito (121/339 tipos, 35,7 %; 39,7 % en `src/main`) y se retracta la
  medición anterior del equipo (1,8 % / 0,1 %), que era un artefacto de un diccionario que omitía los
  tokens españoles más frecuentes del propio código. Peor aún: comparado con la línea base del ing
  antes del renombrado (98/272 = 36,0 %), el renombrado masivo de `49adaee` dejó la cifra **3,7 puntos
  peor** mientras rompía 18 `@RequestParam` y 5 llamadas a procedimientos almacenados. Detalle en
  [`docs/observaciones/P4-RESOLUCION-DISPUTA.md`](docs/observaciones/P4-RESOLUCION-DISPUTA.md).

Ningún punto se declaró "resuelto" para inflar este resumen, y el único punto que cambió de categoría
en la última ronda lo hizo para empeorar. Varios de los que ya estaban cerrados en `OBSERVACIONES.md`
antes de esta evaluación quedan aquí con matices que esa bitácora, por ser narrativa y cronológica, no
siempre deja igual de visibles a primera vista.

---

## Verificación adicional: el resto de la evaluación integral (secciones 1-3, 5-9)

La evaluación integral del ing no es solo la tabla P1-P12 (sección 4, ya cubierta arriba punto por
punto) — tiene ocho secciones más. Verificado cada una contra el estado real del repositorio hoy
(2026-09-17), ver `OBSERVACIONES.md` OBS-48 para el detalle completo con comandos:

**Hallazgo estructural que explica casi todo lo demás:** el PDF completo evalúa el commit `8b1c1d2`
(tag `v1.1.0`), no el HEAD actual. `git rev-list v1.1.0..HEAD --count` da **37 al 2026-09-18** (eran
22+ cuando se escribió esta sección; ver `OBS-44`). **Esto incluye los tres entregables obligatorios
`VERIFICACION.md`, `make verify` y `CONTRIBUCIONES.md`**, creados en `9fd9d0c` el 17-sep a las 17:50,
seis horas después del commit que el PDF evalúa — su ausencia es lo que topa toda la nota al 40 %. La
mayoría de los "no cumple" de las secciones 2, 3 y 6 de ese PDF ya estaban corregidos en commits
posteriores a ese tag, hechos en esta misma ronda — el documento simplemente no pudo verlos porque el
tag no se había movido todavía.

1. **Método común de evaluación:** sin verificación aplicable, es metodología.
2. **Riesgo de Piso 3 (SUS):** el riesgo de fondo (11 de 15 hojas con fecha imposible) **sigue sin
   resolverse** — no es algo que se pueda cerrar con documentación, requiere las hojas físicas y los
   participantes reales. Lo que sí está resuelto: el informe ya no reporta con falsa certeza. Los 7
   pasajes que el ing cita como contradictorios (`04:55`, `08:98`, `11:55`, `12:7`, `13:18`, `14:27`,
   `15:22`) ya dicen todos, de forma consistente, "n=4 verificable, 11 pendientes de confirmar" —
   corregido hoy mismo en el commit `8488d06` (P1), antes del tag que evalúa este PDF. Queda constancia
   explícita en `SUS-RESULTS.md` de que se recibió y se **rechazó** una segunda versión de las hojas con
   la fecha alterada — la misma que se le pidió a este asistente usar y se negó a usar, documentado ahí
   mismo.
3. **Pisos y entregables:** Piso 2 confirmado roto — el README manda compilar `SRS-v1.0.0.tex`, que no
   existe (reproducido: `latexmk` falla con "Could not find file"). **Corregido** a `SRS-v1.0.1.tex`. De
   paso, tres URLs más en el README seguían apuntando al repositorio antiguo (`carla22072004`, ya
   transferido) — corregidas, junto con el mismo defecto en `SRS-v1.0.1.tex` y `15-declaraciones.tex`.
   EV-1/EV-2/EV-3/EV-4 "no cumple": ya resueltos antes de esta sección (ver arriba, entregables
   obligatorios y P9).
4. **Evaluación por pendiente:** es la tabla P1-P12, cubierta arriba en detalle.
5. **Hoja de cálculo:** es el cálculo numérico del ing a partir de la tabla anterior — no hay nada del
   repositorio que verificar aquí, es su aritmética sobre sus propios porcentajes.
6. **Regresiones de la sección 1:** la única marcada "rota" (SUS) ya no lo está, ver punto 2. Las
   etiquetas huérfanas (`sec:chatbot`, `sec:anexos`, `lst:ci-api`) se confirmaron reales pero el propio
   ing las marca como preexistentes ("ya presentes en `f3d1ff4`"), no una regresión de esta ronda — no
   se fuerza una cita artificial. La tabla de Holm ya se cita (P6, `OBS-41`). `sus-analysis.ipynb`
   confirmado sin ejecutar (`execution_count: None`) — **ejecutado hoy**, confirma n=4, media=48.75,
   coincidiendo exacto con lo citado en el informe.
7. **Autoría y aporte individual:** ya lo documenta `CONTRIBUCIONES.md`. La mención de que "un asistente
   automatizado se negó a marcar P12 como cerrado" es real y está en `OBSERVACIONES.md` (OBS-26) — no es
   un defecto, es la misma disciplina de no inflar el estado que rige el resto de este archivo.
8. **Solicitudes para la defensa:** los ítems 2 (VERIFICACION.md/make verify/CONTRIBUCIONES.md) y 4 ya
   están satisfechos por el trabajo de esta ronda. El ítem 4 pedía tres cosas: reactivar `doclint` y
   corregir los 5 errores (hecho, `./mvnw javadoc:javadoc` da exit 0 sin apagar el chequeo), unificar
   el "30/31" (hecho, P11: 31 en todo el informe) y **unificar la narrativa del SUS** — esto último
   quedaba incompleto hasta el 2026-09-18: tres pasajes (`15-declaraciones.tex:38`,
   `12-amenazas-validez.tex:35`, `14-conclusiones.tex:41`) seguían diciendo que el SUS no se había
   aplicado, contradiciendo al capítulo 10 y a la propia sección 15 cuatro líneas más arriba.
   Corregidos en `e36f25e`. Los ítems 1, 3 y 5
   son acciones para el día de la defensa (mostrar hojas físicas, hacer una demo en vivo, reunión con el
   docente) — no son algo que este archivo pueda cerrar por adelantado.
9. **Alcance:** describe lo que el ing sí y no ejecutó (con y sin base de datos/Docker) — informativo,
   nada que verificar contra el repositorio.

**Recomendación derivada de este hallazgo estructural:** con P1-P12 ya atendidos en esta ronda (P1 y P4
con límites reales que no se pueden cerrar del todo; P12 pendiente del docente), corresponde mover el
tag ahora — es exactamente el "cierre real de toda la ronda" que `OBS-44` dejó pendiente. Mientras el
tag siga en `8b1c1d2`, cualquier nueva revisión seguirá viendo el estado de hace **37 commits**, sin
los tres entregables obligatorios y sin ninguna de las correcciones de P1-P12 de esta ronda.
