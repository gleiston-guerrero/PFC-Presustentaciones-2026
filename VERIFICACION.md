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

## Estado actual, re-verificado el 2026-09-19

Los 12 puntos se volvieron a correr contra el repositorio de hoy. Esta tabla y el «Resumen de honestidad»
de más abajo tienen que decir lo mismo, y `make verify` lo comprueba (`scripts/ev1-verificacion.py`), igual
que comprueba que cada bloque marcado `<!-- ev1:run -->` reproduce literalmente su salida. Resultado:

| # | Estado | Qué se corrió hoy y qué dio |
|---|---|---|
| P1 | 🟡 Parcial | **Cifra de cierre (ronda del 18-sep):** `n=15 media=52.83 DE=12.06 IC95=[46.16,59.51]` — reproduce exacto desde el CSV sellado por un tercero. La ronda en papel (`n=4 media=48.75`) queda como registro histórico. Abierto: α = 0,599 y el origen de las 11 hojas retractadas |
| P2 | ✅ Cumple | `./mvnw clean test`: **823 pruebas, 0 fallos** (todas las anotadas se ejecutan), `jacoco:check` pasa, **1 sesión** en el XML, LINE **82,13 %** (4054/4936), BRANCH **73,52 %** (1491/2028) — corrida de cierre, regenerada el 2026-09-20 |
| P3 | ✅ Cumple | `./mvnw javadoc:javadoc`: **BUILD SUCCESS, 0 errores**, `doclint` activo. Escáner propio: 777/778 (**99,9 %**). `@param` tautológicos: **35,2 % → 0,0 %**. Avisos con el tope levantado: **682 → 170**, y los 170 restantes son un artefacto de que javadoc no ve los constructores que genera Lombok (162 de 163 clases lo confirman) — ver la sección P3 |
| P4 | ✅ Cumple | Renombrado completado: **0,0 %** de tipos y **0,2 %** de métodos sobre el universo completo de 1856 (antes 35,7 % y 39,1 %); 1,5 %/3,4 % bajo la definición más amplia. **Nombres de `@Test`: 0 de 823 con palabras en español** (eran 789; ver la sección P4) |
| P5 | ✅ Cumple | 6 corridas Lighthouse versionadas en `prod-runs/`; URL pública en la primera pantalla del README |
| P6 | ✅ Cumple | `\label{tab:holm-bonferroni}` presente y citado con `\ref` en `10-evaluacion-empirica.tex:85` |
| P7 | ✅ Cumple | **18 pruebas del chatbot, 0 fallos**, y la de integración usa el servicio **real**: se retiró el `@MockBean ChatbotService` que la revisión del 18-sep señaló. Verificado por mutación (romper el servicio hace fallar la prueba) |
| P8 | ✅ Cumple | 102 endpoints de escritura; los 5 sin anotación son los exentos de pre-login (`login`, `refresh`, `logout`, `recuperar`, `reset`). **Desde la revisión final también los 111 GET**: los 28 que quedan sin `@PreAuthorize` validan el acceso, delegan en un servicio que lo valida (comprobado) o son catálogos; 9 métodos sin guarda eran huecos reales y se cerraron |
| P9 | ✅ Cumple | Tag `v1.1.0` **sobre el commit de cierre** (desfase 0) y **v1.1.0 archivada en Zenodo tres veces**: el snapshot vigente es el del 2026-09-21, DOI `10.5281/zenodo.22865913`, commit `6515713` (el primero, del 19-sep, `10.5281/zenodo.22839517`, y el segundo, del 20-sep, `10.5281/zenodo.22854267`, quedaron superados). `make verify` comprueba que el tag no se adelante al snapshot con nada que no sea el registro del DOI |
| P10 | ✅ Cumple | Portada: 32 líneas, 0 referencias DOI, 0 notas de proceso, URL del repositorio presente |
| P11 | ✅ Cumple | 31 controladores, 10 rutinas SQL distintas, cero clases con nombre pre-P4 en el informe activo |
| P12 | 🟡 Parcial | Ningún commit vacío nuevo desde `f3d1ff4`; la conversación con el docente sigue sin ocurrir |

Todo lo anterior sale de correr `make verify` más `./mvnw clean test` y `./mvnw javadoc:javadoc` con
Postgres y Redis reales levantados.

**Qué cambió respecto de la versión anterior de este archivo (2026-09-18):** P4 pasó de 🔴 a ✅ (el
renombrado se hizo de verdad; ver su sección), P3 y P9 dejaron de tener reservas propias del expediente
(contenido del Javadoc y metadatos del registro de Zenodo, ambos corregidos y comprobados), y las salidas
de los bloques reproducibles dejaron de escribirse a mano.

**Nota de P9, que sigue siendo la más importante de todas:** los tres entregables obligatorios
—`VERIFICACION.md`, `make verify` y `CONTRIBUCIONES.md`, cuya ausencia topa la nota al 40 %— tienen que
estar **dentro de la etiqueta** (*«lo que no esté dentro de la etiqueta no existe»*). Por eso la etiqueta
`v1.1.0` se mueve como último paso de cada ronda, y `make verify` **falla** —ya no avisa— si no está en
`HEAD` (`python scripts/p9-etiqueta.py`). Aquí no se escribe a qué commit apunta: ese dato cambia cada
vez que se mueve, y una cifra que hay que reescribir a mano es una cifra que envejece.
---

## P1 — SUS (peso 1,7)

**Criterio:** al menos 15 respuestas reales en un CSV versionado, con el instrumento de 10 ítems de
Brooke, consentimiento de cada participante, y recálculo según Brooke.

> **Corregido el 2026-09-19.** La revisión individual del 18-sep señaló, con razón, que esta sección
> «verifica un resultado ya retirado (n = 4, 48,75), no el 52,83 que se informa». Era cierto: el
> informe ya reportaba la ronda del 18-sep mientras este archivo seguía recalculando las 4 hojas de
> papel. Un verificador que comprueba una cifra distinta de la que se publica no verifica nada.

**Comando:**
<!-- ev1:run -->
```bash
python scripts/sus-estadistica.py
```

**Salida real (2026-09-19):**
```
SUS -- familia de contrastes con correccion de Holm-Bonferroni
==========================================================================
Dos contrastes de la misma medicion contra dos referencias son una
familia: sin corregir, el 5 % declarado no es el riesgo real.
  Welch papel(15) vs formulario: t = +0.519
  Welch papel(4)  vs formulario: t = -1.278
  #  contraste                                       p crudo    umbral    p ajust.   decision
  1  papel(4, fecha verificable) vs formulario(15)   0.2204     0.025     0.4408     no rechaza
  2  papel(15) vs formulario(15)                     0.6077     0.05      0.6077     no rechaza
  Ninguno se rechaza: las mediciones siguen siendo indistinguibles.
  La correccion no cambia la conclusion -- se aplica porque corresponde,
  no porque mueva el resultado a favor.
Consistencia interna (alfa de Cronbach)
==========================================================================
  Formulario 18-sep (n=15), polaridad corregida: alfa = 0.599
  Formulario 18-sep, SIN invertir los pares:              alfa = 0.267
  Papel, las 15 hojas, polaridad corregida:               alfa = 0.619
  Referencia: en aplicaciones del SUS con muestras grandes se reporta
  habitualmente 0,85-0,92 (Bangor et al. 2008, Sauro 2011).
  El valor obtenido queda por DEBAJO de ese rango. Se reporta como
  limitacion, no se omite: con n=15 el intervalo de alfa es muy ancho,
  y una consistencia interna baja debilita la interpretacion del
  puntaje como una sola dimension de usabilidad.
```

`make verify` no se limita a imprimir esto: **asegura** que la media del CSV sellado sea 52,83 y que
haya 15 respuestas. Si el informe y el expediente se separan, la verificación falla.

**Veredicto: 🟡 Parcial.** Lo que cumple y lo que no:

- ✅ **La cifra que se publica es la que se verifica.** Ronda del 18-sep, n = 15, en un formulario
  alojado por un tercero que sella cada respuesta con la hora de su propio servidor, y con
  consentimiento individual explícito en las 15 — lo que cierra también la objeción de que el
  consentimiento era solo una nota impresa.
- ✅ **Corrección por comparaciones múltiples aplicada.** Los dos contrastes de Welch son una familia
  y no la llevaban; ahora llevan Holm-Bonferroni, con la misma definición que la familia de pruebas
  de rendimiento. Ninguno se rechaza: la corrección no cambia la conclusión, y se aplica porque
  corresponde, no porque mueva el resultado a favor.
- ❌ **Consistencia interna baja: α = 0,599**, frente al 0,85–0,92 habitual en el SUS. La observación
  del 18-sep se confirma al dígito. No es un defecto de la ronda nueva —la de papel da 0,619— y el
  mecanismo se ve en la tercera fila: sin invertir los ítems pares α cae a 0,267, así que los
  participantes sí percibieron la polaridad alternada, pero no de forma lo bastante consistente. Con
  α = 0,599 y n = 15, el 52,83 es un **indicador débil**, no una medición consolidada.
- ✅ **Reclutamiento documentado (lo pidió la revisión del 18-sep).** El enlace se publicó en un
  servidor de Discord donde ya estaban los estudiantes del curso; respuesta voluntaria y asíncrona, sin
  sesiones convocadas — de ahí las quince horas distintas entre 11:36 y 16:56. Composición recontada
  desde el CSV: 7 estudiantes, 5 docentes, 2 coordinadores, 1 administrador. Es autoselección, y se
  declara como tal.
- ✅ **Cronología con hora de servidor de Google, no del equipo.** «Sellada» no es un sello criptográfico:
  son nueve capturas cuyo código coincide línea por línea con el archivo versionado, y el evaluador
  puede abrir el formulario él mismo (acceso de editor). Apps Script guardado **11:16**, formulario
  creado **11:22**, primera respuesta **11:36:04**. El commit de las 16:37 es la hora en que se subió el
  archivo al repositorio, no en que se escribió; no haberlo comiteado en su momento fue un descuido
  real, y es lo que hizo posible la lectura del evaluador. Capturas en
  `docs/mediciones/sus/re-aplicacion/evidencia/`.
- 🟡 **Quién entregó la segunda versión de las hojas: declarado, no verificado.** El equipo declara que
  fue Barreto Rosado, que no participa en esta ronda ni ha confirmado nada. No explica las fechas y no
  traslada la responsabilidad de haberlas incorporado al expediente sin comprobarlas.
- ❌ **El origen de las 11 hojas retractadas sigue abierto.** La ronda del 18-sep es una muestra
  nueva: ninguno de los 15 declara haber respondido antes en papel. No cierra el Piso 3.

> **Corrección de una afirmación anterior (2026-09-19).** Este archivo y `SUS-RESULTS.md` sostuvieron
> que *«el script documenta el formulario que ya existía, no lo generó»* y que el registro de
> ejecuciones de Apps Script estaría vacío. **Las dos cosas eran incorrectas.** El historial del
> proyecto, fechado por Google, dice 11:16 — seis minutos antes de que se creara el formulario. El
> script sí lo creó. La cronología real refuta la objeción de forma más directa, sin depender del
> argumento indirecto de la «fuente común», y la corrección se deja escrita en vez de reemplazar el
> texto en silencio.

Detalle completo en `docs/mediciones/sus/SUS-RESULTS.md`.

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
806 tests, 0 failures, 0 errors
BUILD SUCCESS (jacoco:check paso -- ver pom.xml)
BRANCH: 1483/2018 (73.49%)
LINE: 4017/4897 (82.03%)
```

**Reproducción independiente (2026-09-18, otra sesión, tras otros commits):**
```
Tests run: 806, Failures: 0, Errors: 0, Skipped: 0
jacoco:check (jacoco-check) --- All coverage checks have been met.
BUILD SUCCESS -- Total time: 01:02 min -- 2026-09-18T11:12:42-05:00
sessioninfo en el XML: 1
BRANCH: 1483/2018 (73.49%)
LINE: 4017/4897 (82.03%)
```
Versionada en [`docs/mediciones/jacoco/2026-09-18-corrida-limpia-reproduccion/`](docs/mediciones/jacoco/2026-09-18-corrida-limpia-reproduccion/).
**Cifra por cifra idéntica** a la del 17-sep: la cobertura reportada no depende de qué corrida se haya
versionado. El valor de ramas coincide además, al dígito, con el que el propio ingeniero calculó sumando
los contadores del XML (1483/2018 = 73,49 %, corrida del 2026-09-17).

**Corrida de cierre definitiva (2026-09-20, la que se publica, tras la revisión final):**
```
Tests run: 823, Failures: 0, Errors: 0, Skipped: 0
jacoco:check (jacoco-check) --- All coverage checks have been met.
BUILD SUCCESS
sessioninfo en el XML: 1
BRANCH: 1491/2028 (73.52%)
LINE:   4054/4936 (82.13%)
```
La corrida anterior de cierre (2026-09-19, 809 pruebas: LINE 4022/4904, BRANCH 1483/2018) quedó superada
al cerrar el punto 5b de la revisión final (autorización de los `GET`): se añadieron 14 pruebas y 32 líneas
instrumentadas de código de producción que ellas cubren.
Versionada en [`docs/mediciones/jacoco/2026-09-19-cierre-definitivo/`](docs/mediciones/jacoco/2026-09-19-cierre-definitivo/).
Las 7 líneas de diferencia entre el 17-18 de septiembre y la corrida del 19 (4904 instrumentadas en vez de 4897) eran
código que agregaron las correcciones posteriores al 18-sep, no un cambio de método de medición: 4 de
las regresiones de contrato y 3 de los constructores explícitos que hubo que declarar al cerrar los
avisos de Javadoc (ver P3).

**Por qué se volvió a correr:** la revisión del 18-sep encontró conviviendo en los documentos vigentes
tres cifras de cobertura (82,10 / 82,03 / 82,96) y tres conteos de pruebas (559 / 801 / 804). Ninguna
era inventada —cada una era la cifra real de *alguna* corrida— pero publicadas a la vez son una
contradicción. Se unificó todo contra este `jacoco.xml` y se agregó
[`scripts/cifras-publicadas.py`](scripts/cifras-publicadas.py) a `make verify`: extrae cada cifra de
cobertura y cada conteo de pruebas de los documentos **vigentes** (las bitácoras y los informes fechados
se saltan a propósito: registran lo que era cierto cuando se escribieron) y los contrasta contra el XML
canónico. Si alguien publica un número que el expediente no respalda, `make verify` falla.

**Veredicto: ✅ Cumple**, con los 3 defectos que señaló el ing verificados y 2 de los 3 corregidos de
verdad esta vez (no solo documentados): (1) **corregido** — el `jacoco.xml` de 71 sesiones se conserva
como snapshot anterior, pero la cifra que aplica ahora sale de una corrida limpia única
(`docs/mediciones/jacoco/2026-09-17-corrida-limpia-unica-sesion/`), prácticamente idéntica (82.03% vs
82.17%); (2) **corregido** — se agregó una regla `jacoco:check` (BUNDLE, LINE y BRANCH ≥70%) en la fase
`test` de `backend/pom.xml`, la misma fase que corre `./mvnw test` en CI: el build ahora falla de verdad
si la cobertura cae del umbral; (3) **verificado con precisión exacta, no corregido** — sin los métodos
`equals`/`hashCode` de Lombok (concentrados en `security/dto/*`, un paquete que la exclusión de JaCoCo
no cubre), la cobertura de ramas (corrida del 2026-09-17) baja de 73.49% a **71.09%** (2.40 puntos de diferencia, coincide con la
cifra del ing) — sigue pasando el umbral, con margen más ajustado. **Corrección adicional real:** el
párrafo del informe que decía "el 82.10% ya estaba ahí cuando se escribió la guía" era cronológicamente
falso — el commit que la guía revisó (`f3d1ff4`, 13-sep) es anterior al commit que agregó esa cifra
(`2b9ba89`, 15-sep), verificado con `git merge-base --is-ancestor`. Corregido en el informe y en
`OBSERVACIONES.md` (OBS-28).

### Cómo reproducir la suite (y por qué un `./mvnw test` a secas falla)

Las pruebas de integración usan PostgreSQL y Redis reales, así que **necesitan Docker levantado** y las credenciales
de `.env`. Lo más corto es `make test` o `make verify`, que las cargan solos. Con Maven directo hay que exportar
`DB_USERNAME` y `DB_PASSWORD` antes de `./mvnw clean test`: sin ellos caen a los valores por defecto de
`application.properties`, que no coinciden con la base que crea Docker Compose, y fallan las pruebas que tocan la
base con un error de autenticación (15 de 823 en una corrida de comprobación), sin que sea un defecto del código.
Además la versión de Lombok del proyecto **no es compatible con JDK 25** (el evaluador lo comprobó en su máquina);
se desarrolla y se mide con JDK 21. Sin Docker, `make verify-rapido` corre todo lo
que no lo necesita y deja lo demás en `[WARN]`, no verificado. Las dos mutaciones que solo mata la suite contra
Postgres real no se pueden demostrar sin Docker; no es una omisión del arnés sino su límite.

### Revisión final del 2026-09-19: 809 pruebas anotadas, 806 ejecutadas

**Lo que se señaló:** *«Explicar la brecha entre los 809 `@Test` que cuenta el AST y las 806 pruebas»* (revisión del 2026-09-19; era la suite de entonces, no la de hoy, que tiene 823 pruebas)
*«de la corrida de cierre.»*

**Verificado: no era un desajuste de conteo, era un defecto.** Comparando, clase por clase, los métodos
`@Test` del código fuente con los casos que Surefire ejecutó, faltaban exactamente tres, los tres en
`PasswordPolicyValidatorTest`: estaban dentro de una clase **`static` anidada sin `@Nested`**
(`RegisterIntegrationTest`). JUnit 5 no descubre una clase estática anidada y Surefire excluye por defecto las
clases internas, de modo que esas pruebas **existían, contaban como pruebas del proyecto y no se habían
ejecutado nunca**: son las que comprueban `POST /api/v1/auth/register` con la política de contraseñas REAL
activa (contraseña común → 400, contraseña corta → 400, contraseña válida → 201). Un conteo que incluye
pruebas que no corren es una cifra que miente, aunque nadie la hubiera inventado.

**Qué se hizo:**

- Las tres pasaron a su propio archivo, `RegisterPasswordPolicyIntegrationTest`, y **desde esa revisión se ejecutan**:
  **806 → 809 pruebas, 0 fallos, 0 errores** (2026-09-19). Se corrió la suite completa contra PostgreSQL y Redis reales.
- Como nunca se habían ejecutado, no había garantía de que **pudieran** fallar. Se comprobó por mutación: al
  desactivar en `PasswordPolicyValidator` el rechazo de contraseñas comunes, la primera de las tres se cae.
- **La cobertura no se movió** (LINE 4022/4904, BRANCH 1483/2018): el endpoint de registro ya lo ejercitaban otras
  clases. Cambia el conteo de pruebas, y por eso se republicó la corrida canónica y todo lugar que decía 806.
- `scripts/p2-pruebas-ejecutadas.py`, dentro de `make verify`, comprueba dos cosas: sin compilar, que ninguna
  clase estática anidada contenga `@Test` sin `@Nested`; y, tras la suite, que **cada método anotado aparezca entre
  los casos que Surefire ejecutó** (hoy 823 y 823). Se probó por mutación: reintroducir una clase estática con
  un `@Test` hace salir 1.

---

---

## P3 — Javadoc (peso 1,4)

**Criterio:** 90% o más de los métodos públicos con Javadoc completo y `mvn javadoc:javadoc` sin error.

**Comandos:**
<!-- ev1:run -->
```bash
python scripts/javadoc-scan.py
python scripts/javadoc-scan-amplio.py
```

**Salida real (hoy; `python scripts/ev1-verificacion.py --actualizar` la regenera):**
```
Total metodos publicos detectados: 466
Con Javadoc COMPLETO: 466 (100.0%)
Incompletos/sin doc: 0
Meta 90%: 420 documentados (faltan 0 mas)
Total (metodos public + constructores public + metodos de interfaz): 779
Con Javadoc COMPLETO: 778 (99.9%)
  constructor: 25/25 (100.0%)
  interfaz: 287/288 (99.7%)
  metodo: 466/466 (100.0%)
Meta 90%: 702 documentados (faltan 0 mas)
```

Y `cd backend && ./mvnw javadoc:javadoc`, con `doclint` activo: **BUILD SUCCESS, 0 errores, 170 avisos**
(los 170, más abajo). `make verify` lo corre de verdad, borrando antes `target/site/apidocs` para que el
plugin no diga «up to date» sin haber mirado nada.

**Salida de la primera corrida (2026-09-17, antes de las correcciones) — registro histórico, sin marca, no se
reejecuta:**
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
   `mvn javadoc:javadoc` sigue en 0 errores con doclint activo, y los 806 tests de entonces (2026-09-19) siguen en verde.
   El sub-hallazgo "164 comentarios son solo etiquetas" no se pudo reproducir con estas herramientas —
   no descartado, no verificado.

### Revisión del 2026-09-19: el veredicto anterior era optimista

La revisión individual del 18-sep midió **85,2 %** contando interfaces, donde este archivo declaraba
**95,2 %**, y dio una causa concreta: *«92 bloques están colocados después de `@Query` y `javac` no los
asocia»*. **Tenía razón, y la causa es exactamente esa.** Lo que sigue es lo que se encontró al
comprobarlo.

**1. El defecto existía.** 75 bloques Javadoc en 31 archivos estaban escritos *debajo* de la anotación:

```java
@Query("SELECT u FROM AppUser u ...")
/**
 * Search paginado.
 * @param q q
 */
Page<AppUser> searchPaged(@Param("q") String q, Pageable pageable);
```

Para una persona ese método está documentado. Para `javac` no: el Javadoc debe preceder a *todo* el
grupo de modificadores y anotaciones. Ahí el bloque queda huérfano y el método cuenta como sin
documentar. Detectado con `scripts/ev2-javadoc-colocacion.py`, corregido con
`scripts/ev2-javadoc-recolocar.py` (61 bloques movidos, 14 fusionados con el que ya tenían encima,
conservando la prosa y añadiendo solo las etiquetas que faltaban).

**2. Los dos escáneres propios tenían la misma ceguera, y por eso daban 95,2 %.** Subían desde la
firma saltando líneas que empiezan por `@` — pero las líneas de continuación de un `@Query` multilínea
empiezan por comillas o por `)`, así que el escáner se detenía ahí. Corregido con un salto que
equilibra paréntesis, igual que hace `javac`. **El 95,2 % no era una cifra inflada a propósito: era una
medición con un defecto que producía el resultado favorable.**

**3. Al recolocar aparecieron 3 errores de javadoc que llevaban tiempo ahí.** Genéricos crudos en
`@return` (`ResponseEntity<List<TopicPropuestoDTO>>`) que `doclint` lee como HTML mal formado. Eran
invisibles **porque el bloque no se procesaba**: la confirmación más directa de que el diagnóstico del
18-sep era correcto. Envueltos en `{@code}`.

**4. Los «100 avisos» eran el tope, no el total.** `javadoc` corta en 100 avisos por defecto
(`-Xmaxwarns`). Levantado el tope, la corrida real da:

```
BUILD SUCCESS -- 0 errores, 682 avisos
   247  no comment
   180  use of default constructor, which does not provide a comment
   147  no main description
    71  no @param for <x>
    37  no @return
```

`make verify` corre ahora `javadoc:javadoc` con el tope levantado y **publica ese número** en cada
corrida, en vez de dejarlo escondido detrás del corte.

**5. Los 682 avisos se cerraron: quedan 170, y los 170 son de una sola causa.**

| Tipo de aviso | Antes | Ahora | Cómo se cerró |
|---|---:|---:|---|
| `no comment` | 247 | **0** | bloque generado sobre las anotaciones |
| `no main description` | 147 | **0** | frase de resumen añadida al bloque existente |
| `no @param for <x>` | 71 | **0** | etiqueta añadida |
| `no @return` | 37 | **0** | etiqueta añadida |
| `use of default constructor` | 180 | 170 | 10 constructores explícitos; los 170 restantes, abajo |
| **Total** | **682** | **170** | 0 errores, BUILD SUCCESS |

El generador (`scripts/javadoc-cerrar-avisos.py`) **no vuelve a analizar el código**: toma la salida de
`javadoc` —archivo, línea y qué falta exactamente— y corrige justo ahí. Es deliberado: el escáner
propio ya se equivocó una vez por analizar por su cuenta, y cuando dos analizadores discrepan el que
decide es `javadoc`.

**Los 170 que quedan son un artefacto de la herramienta, no una brecha de documentación.** `javadoc`
analiza el fuente **antes** de que Lombok genere nada, así que no ve el constructor que sí existe en el
bytecode. Comprobado, no afirmado: de las 163 clases con ese aviso, **162 llevan una anotación Lombok
de constructor** (`@NoArgsConstructor`, `@AllArgsConstructor`, `@Data`, `@Builder`); la única restante
es un `enum`, cuyo constructor implícito no puede ser público ni documentarse. Escribir 162
constructores a mano para callar el contador rompería los `@Builder` y empeoraría el código para
mejorar un número, así que se declara en vez de maquillarse.

**Veredicto revisado: ✅ Cumple.**

| | |
|---|---|
| `mvn javadoc:javadoc` con `doclint` activo | ✅ BUILD SUCCESS, **0 errores** |
| Escáner propio, metodología amplia | **99,9 %** (777/778), sobre el umbral del 90 % |
| Bloques huérfanos bajo una anotación | ✅ **0** (eran 75, más 5 que el detector no veía) |
| Avisos de `javadoc` sin tope | **170**, todos de la misma causa declarada |
| Avisos que señalan documentación ausente | ✅ **0** (eran 502) |

Nota de trazabilidad: los 10 constructores explícitos son código, están cubiertos por las pruebas, y
por eso la cobertura pasó de 82,03 % (4017/4897, corrida limpia del 2026-09-18) a 82,01 % (4022/4904, corrida del 2026-09-19).
Desde el punto 5b de la revisión final la cifra es otra, **4054/4936 (82,13 %)**.

### Revisión del 2026-09-19 (tarde): el contenido era de plantilla

La revisión final dio P3 por «cumple con reservas»: por AST el Javadoc está completo, pero
*«el contenido es de plantilla: el 29 % de los `@param` son tautológicos»*. Es exacto, y es el mismo
defecto visto desde otro lado: un bloque que `javadoc` da por bueno y un lector no aprovecha.

**Medida.** No conocemos el criterio exacto del evaluador; usamos uno mecánico y estricto
(`scripts/p3-param-tautologicos.py`): un `@param` es tautológico si su descripción, quitadas las
palabras vacías, no aporta ninguna palabra que el nombre del parámetro no tenga ya (`@param submissionId
submissionId`, o `id de la submission`). Sobre el commit evaluado da **376 de 1068 = 35,2 %** — más
alto que el 29 % del evaluador, así que lo que baje con esta medida baja con la suya.

| | Antes | Ahora |
|---|---|---|
| `@param` tautológicos | **376 / 1068 (35,2 %)** | **0 / 1059 (0,0 %)** |
| Umbral que impone `make verify` | — | ≤ 5 % |
| Javadoc apilados (prosa real perdida) | 5 | **0** |
| Javadoc dentro de una consulta JPQL | 1 | **0** |

**Lo que se descubrió al reescribirlos, y no estaba en ninguna revisión:**

1. **Cinco Javadoc apilados.** Un bloque de prosa real —con la explicación de por qué la consulta usa
   sentinelas de fecha, o de por qué hace `CAST(:nivel AS string)`— seguido de otro generado.
   `javadoc` solo enlaza el último, así que **la prosa del primero se perdía sin que nada avisara**
   (`MinutesRepository`, `SubmissionRepository` ×2, `TopicProposedRepository`, `TopicController`). Se
   fusionaron: ahora esa explicación sí aparece en el Javadoc publicado.
2. **Un comentario Javadoc metido dentro de una consulta JPQL** (`ScheduleRepository.findConflictos`,
   desde el commit `6aea088`, 2026-09-17, y por tanto dentro de `v1.1.0`). Un generador automático lo
   insertó en mitad del bloque de texto de la consulta, con un resumen sin sentido («F u n c t i o n.»).
   Hibernate lo tolera como comentario, por eso ninguna prueba falló; pero era basura dentro de una
   consulta, y `javadoc` tampoco lo ve. **Se quitó y la consulta volvió a su forma original.**
3. Una constante de `enum` con su Javadoc duplicado.

Los tres los detecta ahora `make verify`, y se comprobó que los detecta: contra el estado de `HEAD` antes
de arreglarlos, el script marca los 7 (5 apilados, 1 en la consulta, 1 duplicado).

**Lo que sigue siendo de plantilla, dicho sin adornos.** El resumen de muchos bloques es genérico
(«Search con filtros.», «Main.»), y 100 de los 680 `@return` son la misma frase («los resultados
encontrados (vacío si no hay coincidencias)»). No es tautológico —dice algo, y es cierto— pero no es
prosa escrita para el método. Los 170 avisos de `javadoc` (artefacto de Lombok) tampoco cambian.
**Esto no cierra P3 por completo; lo deja donde el evaluador dijo: «cumple con reservas», con una
reserva menos.**

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
<!-- ev1:run -->
```bash
python scripts/p4-nombres-espanol.py
```

**Salida real (hoy):**
```
==========================================================================
P4 -- Identificadores con palabra en espanol (metodo declarado)
==========================================================================
Universo: 339 tipos y 1856 metodos en backend/src/{main,test}/java
Lexico: 155 terminos de dominio, 18 funcionales, 6 ambiguos
DEFINICION NUCLEO (solo dominio, sin funcionales ni ambiguos)
  main + test:
    tipos       0/339   (  0.0%)
    metodos     3/1856  (  0.2%)
  solo src/main:
    tipos       0/272   (  0.0%)
    metodos     1/915   (  0.1%)
DEFINICION AMPLIA (+ funcionales + ambiguos resueltos por contexto)
  main + test:
    tipos       5/339   (  1.5%)
    metodos    63/1856  (  3.4%)
  solo src/main:
    tipos       4/272   (  1.5%)
    metodos    24/915   (  2.6%)
CONTRASTE con la evaluacion del 2026-09-17 (AST del ingeniero)
    el ing reporto: tipos 121/339 (35.7%), metodos 1325/1836 (72.2%)
    este script:    tipos 5/339 (1.5%), metodos 63/1856 (3.4%)
    El universo de tipos coincide exacto (339). La diferencia en el
    total de metodos (1856 aqui vs 1836 del ing el 17-sep) es ahora el mismo
    universo: las declaraciones con o sin modificador (antes 693 por exigir public/
    private/protected). Sigue sin ver los metodos que Lombok genera; ver
    p4-rename-scan-javap.py para el conteo sobre bytecode.
```

**El conteo sobre bytecode, y por qué dejó de reejecutarse (revisión final del 2026-09-19).**
El bloque anterior publicaba una sola cifra sobre bytecode, `nombres distintos 102/1923 (5,3 %)`, y el
evaluador señaló dos cosas, ambas ciertas:

1. **Estaba por encima del 5 %**, en un punto que se declaraba cumplido. La cifra salía de la definición
   *amplia*, que suma las palabras `actual`, `base`, `error`, `final`, `me` y `real`, **que este mismo expediente
   declara inglesas y no renombra** (ver arriba). Contarlas como españolas inflaba el resultado; el criterio de la
   guía se mide con el *núcleo* de dominio. Ahora el conteo informa las tres definiciones por separado, con el
   criterio marcado, y la cota superior (la que incluye esas palabras) se llevó por debajo del 5 % de todos modos
   renombrando los 20 nombres de prueba que contenían `Error` (ahora `Failure`, que además es más exacto).
2. **No reproducía en su máquina.** Los métodos que genera Lombok solo existen en el bytecode si Lombok estuvo
   activo al compilar; en un JDK donde no compila, el universo es otro (el 19-sep, allí dio `102/1923` frente a
   `66/2658` de aquí) y el bloque «fallaba» sin que nada hubiera cambiado. Se le quitó entonces la marca
   `ev1:run`, y sin Lombok el script avisa y no imprime porcentajes en vez de imprimir unos que no son
   comparables.

> **La cifra vuelve al conjunto verificable (revisión del 2026-09-21).** El evaluador dejó dicho que con eso
> *«la cifra de bytecode salió del conjunto verificable y ya no la comprueba nadie»*. Tenía razón, y al volver a
> marcarla apareció la consecuencia: **las cifras publicadas estaban vencidas**. Decían `13/1923` y `82/1923
> (4,3 %)`; hoy el mismo comando da `13/1939` y `82/1939 (4,2 %)`. No es que algo empeorara — el universo creció
> porque el punto 5b añadió `validateAccessById` y sus pruebas (+2 nombres en `main`, +16 en `main + test`), y
> nadie volvió a publicar el conteo. Es justo el defecto que el evaluador anticipó: una cifra que nadie comprueba
> se queda atrás en silencio.
>
> La marca es `ev1:run slow needs=backend/target/classes`, y así queda condicionada en vez de suprimida:
>
> - **`needs=backend/target/classes`** — si no hay clases compiladas, el bloque se **omite** (el verificador
>   imprime cuántos omitió), que es lo que pasará en una máquina donde Lombok no compile: ahí la compilación
>   falla y no hay clases. Es el mismo mecanismo que ya usan los dos bloques que necesitan los informes de
>   Surefire.
> - **`slow`** — son 430 llamadas a `javap`: EV-1 completo pasó de segundos a **3 m 36 s** por este bloque, así
>   que `make verify-rapido` lo salta y la corrida completa lo ejecuta. En esa corrida las clases están **recién
>   compiladas**, porque P2 corre `mvnw clean test` antes de que EV-1 lea este archivo: la cifra que se compara
>   sale del árbol actual, no de un compilado viejo.
> - Si hubiera clases compiladas **sin** Lombok, el script no imprime porcentajes y el bloque falla a propósito,
>   diciendo que hay que recompilar con JDK 21. Ese árbol es un build a medias, no un entorno distinto.
>
> **Límite declarado.** Donde no se pueda compilar, esta cifra se omite y no se verifica: el expediente lo dice
> en vez de publicar un número que nadie contrastó. La mutación **M51** lo prueba en los dos sentidos.

**Comando** (con las clases compiladas: `cd backend && ./mvnw -q test-compile`):
<!-- ev1:run slow needs=backend/target/classes -->
```bash
python scripts/p4-nombres-espanol.py --bytecode | sed -n '/^CONTEO SOBRE BYTECODE/,$p'
```

**Salida real (2026-09-21):**
```
CONTEO SOBRE BYTECODE (javap, incluye metodos generados por Lombok)
  solo main: 362 clases
    NUCLEO (dominio: el criterio de la guia)
      nombres distintos    11/1072  (  1.0%)   ocurrencias    15/2660  (  0.6%)
    + funcionales (de, con, no, por...)
      nombres distintos    15/1072  (  1.4%)   ocurrencias    19/2660  (  0.7%)
    + ambiguas (actual, base, error, final, me, real): tambien son palabras inglesas, cota superior
      nombres distintos    43/1072  (  4.0%)   ocurrencias    66/2660  (  2.5%)
  main + test: 430 clases
    NUCLEO (dominio: el criterio de la guia)
      nombres distintos    13/1939  (  0.7%)   ocurrencias    17/3601  (  0.5%)
    + funcionales (de, con, no, por...)
      nombres distintos    18/1939  (  0.9%)   ocurrencias    22/3601  (  0.6%)
    + ambiguas (actual, base, error, final, me, real): tambien son palabras inglesas, cota superior
      nombres distintos    82/1939  (  4.2%)   ocurrencias   105/3601  (  2.9%)
```

**Antes del renombrado (2026-09-18) — registro histórico, sin marca, no se reejecuta:**
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

**Veredicto: ✅ Cumple.** La brecha de los nombres de los tests, que aquí se declaraba, se cerró el 2026-09-20 (ver más abajo).

Este punto pasó por dos fases y conviene leerlas en orden.

**Fase 1 — la disputa numérica se resolvió a favor del ingeniero.** Se retractó la medición anterior
del equipo (1,8 % de tipos y 0,1 % de métodos): era un artefacto de un diccionario demasiado estrecho,
no una medición del código. Con el léxico correcto se reprodujo su cifra al dígito: 121/339 (35,7 %) y
39,7 % en `src/main`. Detalle en
[`docs/observaciones/P4-RESOLUCION-DISPUTA.md`](docs/observaciones/P4-RESOLUCION-DISPUTA.md).

**Fase 2 — hecho el renombrado que faltaba (2026-09-18).**

| | antes | ahora |
|---|---|---|
| Tipos, main+test | 121/339 (**35,7 %**) | **0/339 (0,0 %)** |
| Tipos, `src/main` | 108/272 (**39,7 %**) | **0/272 (0,0 %)** |
| Métodos, main+test | 271/693 (**39,1 %**) | **3/1856 (0,2 %)** |
| Métodos, `src/main` | 232/625 (**37,1 %**) | **1/915 (0,1 %)** |

**Una corrección al instrumento que cambia el denominador de esa tabla (revisión final, 2026-09-20).** Las
filas de métodos usaban un universo de **693** métodos (625 en `src/main`). El ing señaló, con razón, que la
expresión que los reconocía (`scripts/p4-rename-scan-fuente.py:22`) exigía `public`, `private` o `protected`, y
los `@Test` de JUnit 5 son paquete-privados y los métodos de interfaz no llevan modificador: **el instrumento
descartaba 1.145 de 1.838 métodos, justo los 809 `@Test` que se acababan de renombrar.** La cifra «0/693» era
correcta para lo que veía y no decía nada de lo que había cambiado. Ahora los métodos se leen de sus
declaraciones (recorriendo llaves y clases, con o sin modificador, incluidas las clases anónimas), y el universo
era **1838, el mismo que cuenta el AST del ing**: 913 en `src/main` (465 públicos + 26 protegidos + 134 privados +
288 de interfaz) y 925 en pruebas. **Hoy es 1856** (915 + 941): el punto 5b de la revisión final añadió 2 métodos
de producción (`validateAccessById`, `validateOwnAccountOrManager`) y 16 de prueba (14 `@Test` y 2 auxiliares); el
resultado no se movió. Sobre ese universo completo el resultado sigue siendo holgado: 0,2 % con el
núcleo y 3,4 % con la definición más amplia. El script además **se niega a medir** si algún `@Test` queda fuera
del universo (lo comprueba en cada corrida; con el defecto reintroducido sale 1: «809 anotados y solo 68 métodos
de prueba detectados»).

Bajo la definición **más amplia posible** (sumando palabras funcionales y cognados inglés/español):
1,5 % de tipos y 3,4 % de métodos — también por debajo del 5 %. Sobre bytecode y solo `src/main`:
4,0 % de nombres distintos (cota superior, con las palabras inglesas incluidas).

**Lo que no se renombró, y por qué.** Quedan `error`, `base`, `final`, `real` y `me` (`errorHandler`,
`deleteBase`, `EvaluationFinal`, `mimeReal`, `MeController`). Son palabras **inglesas**, idénticas a su
cognado español; renombrarlas empeoraría el código. El propio conteo del ingeniero las excluye: su
cifra de 121/339 se reprodujo con la definición que las deja fuera.

**La brecha de los nombres de `@Test`, y cómo se cerró (2026-09-20).** Hasta hoy este archivo decía: *«436 de
807 nombres de método `@Test` siguen en español; traducirlos token a token produce inglés agramatical, se deja
declarado»*. La revisión final lo puntuó con 40 % en la lectura estricta, y el motivo se ve al medir bien:

- **La cifra «436 de 807» estaba escrita a mano** en `verify.sh`, no medida. El conteo real, con un diccionario
  de palabras españolas, era **789 de 809** (97,5 %): casi todos los nombres de prueba eran frases como
  `saveEvaluationLanzaExcepcionSiLaSubmissionNoExists`. El lexicón de `p4-nombres-espanol.py` (términos de
  dominio) no las veía, y por eso decía 0 %.
- **El argumento de no hacerlo era en parte cierto y en parte una excusa.** Es cierto que traducir palabra por
  palabra no da prosa inglesa pulida. Pero un nombre de método de prueba no necesita serlo: necesita estar en
  inglés y decir qué comprueba, y eso sí se logra.

**Qué se hizo:** 789 nombres de `@Test` traducidos con un diccionario de 541 palabras (verbos de prueba,
artículos, conectores), **sin tocar el cuerpo de ninguna prueba**; se omiten los artículos que el inglés no
necesita y se corrigieron a mano los casos que salían mal (los de guion bajo y siete más). Resultado, por ejemplo:

```
saveEvaluationLanzaExcepcionSiLaSubmissionNoExists  ->  saveEvaluationThrowsExceptionIfSubmissionNotExists
obtainByIdRechazaConsultarElProfileDeOtroAppUser    ->  obtainByIdRejectsViewProfileOfOtherAppUser
activateInvocaElServicioYDevuelve200                ->  activateInvokesServiceAndReturns200
```

**Lo que sigue siendo cierto, dicho sin adornos:** no todo queda en inglés de manual. Hay construcciones como
`IfAppUserNotExists` o `NotHas…` (calcos de «no existe», «no tiene»): legibles y en inglés, no idiomáticas. Se
prefirió eso a dejar 789 frases en español. Y `p4-tests-espanol.py` es un diccionario finito: una palabra
española que no esté en él no se ve (por eso la tolerancia es cero y el diccionario se amplía cuando aparece).
Los métodos de `src/main` con una palabra española son 16 de 1225 (1,3 %), la mayoría atados a nombres de
campos de entidades (`findByActivoTrue`, `findAllByOrderByCategoriaAscNombreAsc`); renombrarlos exige tocar el
esquema, y se dejan por estar bajo el techo del 5 %.

Ahora `make verify` mide esto en cada corrida (`scripts/p4-tests-espanol.py`, tolerancia cero) en lugar de una
advertencia con la cifra tecleada. La medición se sigue reportando separada (`src/main` y `main+test`).

**Cómo se hizo, y por qué no rompió nada esta vez.** El renombrado de `49adaee` falló porque los
nombres que viajan por HTTP eran *implícitos*: salían del nombre del identificador Java, así que
renombrar cambiaba el contrato en silencio. Aquí se invirtió el orden: primero se hizo explícito todo
nombre externo (`scripts/p4-congelar-contrato.py`: 531 campos JSON + 163 query params y path variables
= **694 nombres anclados**), y solo después se renombró (`scripts/p4-renombrar.py`).

Las dos reglas del renombrador:

1. **Límites CamelCase, no subcadena.** `Estado(?![a-z_])` acepta `EstadoSubmission` y
   `countByEstadoCodigo`, y rechaza `Estados`, que es su propio token. Esto evita los plurales
   corrompidos de `49adaee` (`roles`→`rolees`). El `_` cuenta como letra, así que ningún identificador
   snake_case de base de datos coincide (`estados_acta`, `p_solicitud_id`).
2. **Toda cadena y todo comentario se enmascaran, salvo la JPQL.** Todo nombre externo vive dentro de
   una cadena, así que se protegen todos de una vez — incluidos los que uno no pensó en enumerar. Los
   comentarios se protegen porque esta evaluación reprocha que el renombrado anterior los dañó.

**Se rehízo dos veces desde cero**, y conviene dejarlo escrito: el primer intento enmascaraba
anotaciones enteras por regex y fallaba con `@SqlResultSetMapping` (tres niveles de paréntesis); el
segundo no protegía los comentarios y produjo prosa medio traducida («necesita de all modos»,
«prácticamente all las entidades»), que es el defecto ya señalado; el tercero no manejaba los bloques
de texto `"""` y convirtió «Por favor» en «By favor». Cada fallo se detectó revisando el diff o con la
suite, se revirtió al commit de congelado y se rehízo.

**Comentarios: prosa intacta, referencias actualizadas.** Proteger los comentarios dejaba 615 errores
de `doclint`, porque un Javadoc también contiene referencias al código (`@param`, `{@link}`, `@see`).
`scripts/p4-javadoc-refs.py` recorre solo los comentarios y renombra únicamente esas referencias,
nunca la prosa.

**Acoplamientos por cadena que la compilación no detecta** y hubo que alinear a mano: el SpEL de
`@PreAuthorize` (136 usos), `ReflectionTestUtils.setField(..., "retencionDias")`, y
`Sort.by(Sort.Direction.DESC, "fecha")` — este último nombra una propiedad de entidad por cadena y sin
corregirlo el endpoint paginado de auditoría devolvía 400.

**Comandos de verificación:**
```bash
python scripts/p4-nombres-espanol.py --bytecode   # la medicion
python scripts/p4-contrato-json.py                # que el contrato sigue intacto
cd backend && ./mvnw clean test                   # 823/823
cd backend && ./mvnw javadoc:javadoc              # 0 errores, doclint activo
```

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
   `TutoringController`, `TopicController`, `ResourceDegreeController`, `CatalogController`,
   `ReportController`, `MinutesController`). Corregido agregando `@RequestParam(name = "...")` con el
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
| `MinutesDetailDTO` | `tituloTopic`, `observacionesMinutes` | idem | `tituloTema`, `observacionesActa` |
| `MyStudentTuteeDTO` | `tituloTopic`, `estadoTutoring`, `estadoSubmissionCodigo`, `estadoSubmissionNombre` | idem | `tituloTema`, `estadoTutoria`, `estadoSolicitudCodigo`, `estadoSolicitudNombre` |
| `ObservationsSubmissionDTO` | `tituloTopic`, `nombreStudent`, `nombrePanelist`, `notaPanelist` | idem | `tituloTema`, `nombreEstudiante`, `nombreJurado`, `notaJurado` |
| `ReportSummaryDTO` | `totalSubmissions`, `totalMinutes`, `sustentacionesPorPeriod` | idem | `totalSolicitudes`, `totalActas`, `sustentacionesPorPeriodo` |
| `StatusBackupsDTO` | `ultimoBackup`, `ultimoBackupHace`, `totalBackups` | idem | `ultimoRespaldo`, `ultimoRespaldoHace`, `totalRespaldos` |
| `ReportActivityTeacherDTO` | `comoPanelist` | `comoPanelist` | `comoJurado` |

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

Verificado tras los 23 cambios (2026-09-18): **804/804 pruebas en verde**, `jacoco:check` pasa, y el script vuelve
a salir con código 0.

3. **Campos de DTO/entidad sin `@JsonProperty` (7 encontrados en la ronda del 17-sep):**
   `PerfilRequest`/`AppUser.emailNotifications` (Angular lee/escribe `emailNotificaciones` — el
   formulario de "editar mi perfil" no guardaba ni mostraba el correo de notificaciones),
   `TutoringPhaseDTO.archivoPdfStudent` (Angular espera `archivoPdfEstudiante`),
   `TutoringSummaryDTO.tituloTopic`/`nombreStudent`/`estadoTutoring` (Angular espera
   `tituloTema`/`nombreEstudiante`/`estadoTutoria`), `TrackingDTO.porcentajeProgress` (Angular espera
   `porcentajeProgreso`). Corregidos con `@JsonProperty`. **No exhaustivo:** de 48 DTOs, 20 no tenían
   ningún `@JsonProperty`; se revisaron los de mayor riesgo cruzando contra los modelos/servicios
   Angular reales, no los 48 uno por uno — quedan candidatos sin revisar.

Verificado que compila, `mvn javadoc:javadoc` sigue limpio, y la suite completa sigue en verde
(804/804 tests el 2026-09-18, 0 fallos) después de todos estos cambios.

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

> **Reserva cosmética cerrada (revisión final, 2026-09-20).** El evaluador confirmó la figura (94/81, misma URL,
> mismas cifras que los seis JSON) y dejó una reserva: *«el título interno del PNG aún dice "build de
> producción" donde el pie dice "despliegue público real"»*. Era cierto: `scripts/gen-figuras.py` titulaba la
> figura con el texto de cuando las corridas eran contra un build local. Ahora dice «despliegue público real»,
> la imagen se regeneró desde los JSON y se copió al informe (recompilado).

> **Corrección: lo que se afirmó aquí el 20-sep era falso (revisión del 2026-09-21).** Este pasaje decía que
> `ev2-documental.py` «falla si el título vuelve a contradecir al pie (mutación M36, detectada)». No era cierto:
> el chequeo leía el **título en el fuente** de `scripts/gen-figuras.py`, no el de la imagen. El evaluador lo
> demostró cambiando **las dos** copias del PNG por una versión vieja: `make verify` pasó en verde y encima
> afirmó *«la figura del informe es la generada desde los JSON»* mientras el informe dibujaba los 68/61 de
> `localhost`. Reproducido aquí con las dos versiones viejas del archivo (la de 68/61 y la de «build de
> producción»): las dos pasaban con salida 0.
>
> **Por qué las dos salidas obvias no servían.** Comparar las dos copias entre sí ya estaba, y no ve nada cuando
> se cambian las dos. Comparar los bytes contra una regeneración tampoco: matplotlib incrusta su propia versión
> dentro del PNG (chunk `tEXt` `Software`), así que los bytes cambian de una máquina a otra y el gate fallaría en
> la del evaluador — la misma clase de defecto que el bloque de bytecode que depende de Lombok.
>
> **Lo que se hizo.** El generador incrusta la **procedencia dentro del PNG** (chunks `tEXt`: título dibujado,
> archivos de entrada, huella `sha256` de esas entradas y cifras dibujadas) y `ev2-documental.py` la lee **de la
> imagen** y la vuelve a derivar de los datos versionados, con el mismo cálculo que usa el generador
> (`scripts/figuras_datos.py`, que existe para que ese cálculo no viva duplicado). Cubre las **tres** figuras del
> informe, no solo la de Lighthouse: las otras dos tenían la misma ceguera. Los píxeles no cambiaron —solo se
> añadieron los metadatos—, comprobado comparando el `sha256` de los chunks `IDAT` antes y después.
>
> | Mutación | Qué inyecta | Qué la ve |
> |---|---|---|
> | M19 | **una** copia del PNG es una versión vieja | las dos copias dejan de ser el mismo archivo |
> | M36 | el título cambia en el fuente y la imagen **no** se regenera | el título incrustado ya no es el que se dibujaría hoy |
> | M48 | **las dos** copias pasan a ser la versión vieja (el ataque del evaluador) | la imagen no lleva procedencia incrustada |
> | M49 | cambia una medición (`score` de un JSON de Lighthouse) y la figura publicada ya no la dibuja | la huella y las cifras incrustadas ya no coinciden con las entradas |
> | M50 | la figura se vuelve a titular «build de producción» **y se regenera** (el defecto del 19-sep) | el título que lleva la imagen no dice lo que declara el pie |
>
> Las cinco se detectan; con el chequeo anterior, M48, M49 y M50 pasaban en verde.
>
> **Límite declarado.** Esto prueba que la imagen la produjo este generador a partir de estas entradas, no que
> los píxeles dibujen eso: un PNG hecho a mano con los metadatos correctos pasaría. Cierra el defecto real —una
> figura vieja publicada sin que nadie se entere— y no pretende cerrar la fabricación deliberada de una imagen.

**Comando:**
<!-- ev1:run -->
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
mobile-run1 url= https://steadfast-success-production-2b60.up.railway.app/ performance= 81
mobile-run2 url= https://steadfast-success-production-2b60.up.railway.app/ performance= 81
mobile-run3 url= https://steadfast-success-production-2b60.up.railway.app/ performance= 81
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

**Comando** (los informes salen de `cd backend && ./mvnw -q test -Dtest=ChatbotServiceTest,ChatbotControllerTest,ChatbotControllerIntegrationTest`,
o de la suite completa que corre `make verify`):
<!-- ev1:run needs=backend/target/surefire-reports -->
```bash
grep -h -E "^(Test set|Tests run)" backend/target/surefire-reports/*Chatbot*.txt | sed -E 's/, Time elapsed.*//'
```

**Salida real (hoy; 5 + 2 + 11 = 18 pruebas):**
```
Test set: ec.edu.uteq.presustentaciones.controllers.ChatbotControllerIntegrationTest
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
Test set: ec.edu.uteq.presustentaciones.controllers.ChatbotControllerTest
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
Test set: ec.edu.uteq.presustentaciones.services.ChatbotServiceTest
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
```

> **Corregido el 2026-09-19.** La revisión del 18-sep observó que *«en la prueba MockMvc el servicio
> sigue simulado»*. **Tenía razón, y la respuesta que daba antes esta sección no valía.** Aquí se
> argumentaba que el "defecto" era la ausencia de un modelo de lenguaje real, algo ya declarado en el
> informe. Pero él no hablaba de eso: hablaba de que la prueba declaraba
> `@MockBean ChatbotService` y acto seguido afirmaba probar el chatbot. Lo que probaba era el
> transporte —ruta, filtro JWT, serialización— con la lógica del asistente sustituida por un
> `when(...).thenReturn(...)`: **la prueba habría pasado igual con el servicio roto.**

**Corrección aplicada.** `ChatbotService` no tiene dependencias externas —sin base de datos, sin HTTP,
sin estado: solo lee `SecurityContextHolder` y hace coincidencia de palabras— así que simularlo nunca
fue necesario. Ahora se importa el servicio **real**
(`@Import({SecurityConfig.class, ChatbotService.class})`) y las aserciones son sobre su salida
verdadera. Se añadieron dos pruebas: la respuesta por defecto ante un mensaje sin palabra conocida, y
un recorrido por las 6 ramas de intención comprobando que cada una devuelve lo suyo (si dos ramas se
cruzan al reordenar los `if`, se detecta).

**Verificado por mutación, no por afirmación:** al cambiar `msg.contains("anteproyecto")` por una
cadena inexistente en el servicio real, la clase **falla** (exit 1). Con el `@MockBean` anterior habría
seguido pasando. Esa es la diferencia entre probar el chatbot y probar el transporte.

**Veredicto: ✅ Cumple.** 18/18 pruebas del chatbot, 0 fallos, con el servicio real en la prueba de
integración. La limitación de diseño que sí es real —que no hay un modelo de lenguaje detrás, sino un
enrutador de intenciones por palabras clave— sigue declarada en el informe
(`Informe-Final/secciones/09-implementacion.tex:122`), y es una cosa distinta de la que se señaló.

---

## P8 — Autorización en endpoints de escritura y lectura (peso 0,6)

**Criterio:** todos los endpoints de escritura con anotación de autorización, incluido el del propio
perfil, y la prueba de un 403 en el expediente.

**Comando:**
<!-- ev1:run -->
```bash
python docs/mediciones/sec/owasp/scripts/audit-endpoints-autorizacion.py
```

**Salida real (hoy):**
```
Total endpoints de escritura (POST/PUT/PATCH/DELETE): 102
Sin ninguna anotacion de autorizacion: 5
  AuthController.login (PostMapping, L71) -- exento conocido (auth pre-login)
  AuthController.refresh (PostMapping, L130) -- exento conocido (auth pre-login)
  AuthController.logout (PostMapping, L196) -- exento conocido (auth pre-login)
  AuthController.recover (PostMapping, L357) -- exento conocido (auth pre-login)
  AuthController.reset (PostMapping, L386) -- exento conocido (auth pre-login)
OK: todos los endpoints sin @PreAuthorize son exentos conocidos y documentados.
Total endpoints de lectura (GET): 111
Sin @PreAuthorize: 28
  AppUserController.obtainById (GET, L100) -- valida el acceso en el metodo
  EvaluationPanelistController.obtain (GET, L58) -- delega en EvaluationPanelistService.java, que valida
  EvaluationPanelistController.obtainPanel (GET, L82) -- delega en EvaluationPanelistService.java, que valida
  PanelistController.listBySubmission (GET, L92) -- valida el acceso en el metodo
  PanelistController.obtainTutor (GET, L164) -- valida el acceso en el metodo
  PanelistController.obtainInfoPanelist (GET, L218) -- valida el acceso en el metodo
  ProposalController.obtainBySubmission (GET, L60) -- delega en ProposalServiceImpl.java, que valida
  ProposalController.viewPdf (GET, L72) -- delega en ProposalServiceImpl.java, que valida
  ProposalController.verify (GET, L97) -- delega en ProposalServiceImpl.java, que valida
  RoomController.list (GET, L27) -- catalogo (catalogo de salas)
  RoomController.listPaged (GET, L37) -- catalogo (catalogo de salas)
  RubricController.list (GET, L34) -- catalogo (catalogo de rubricas)
  RubricController.obtain (GET, L42) -- catalogo (catalogo de rubricas)
  RubricController.criteria (GET, L93) -- catalogo (criterios de la rubrica)
  ScheduleController.availability (GET, L101) -- catalogo (franjas libres de un dia, sin datos de personas)
  ScheduleController.verifyAvailability (GET, L117) -- catalogo (true/false de una sala, sin datos de personas)
  ScheduleController.byAppUser (GET, L150) -- valida el acceso en el metodo
  ScheduleController.bySubmission (GET, L161) -- valida el acceso en el metodo
  SubmissionController.listMySubmissions (GET, L94) -- valida el acceso en el metodo
  SubmissionController.obtain (GET, L292) -- valida el acceso en el metodo
  SubmissionController.obtainTracking (GET, L333) -- valida el acceso en el metodo
  TutorController.myStudents (GET, L54) -- valida el acceso en el metodo
  TutorController.bySubmission (GET, L86) -- valida el acceso en el metodo
  TutoringController.obtainTutoringsStudent (GET, L54) -- valida el acceso en el metodo
  TutoringController.obtainTutoringsTeacher (GET, L71) -- valida el acceso en el metodo
  TutoringController.obtainSummary (GET, L91) -- valida el acceso en el metodo
  TutoringController.obtainPhases (GET, L110) -- valida el acceso en el metodo
  TutoringController.obtainPdfPhase (GET, L242) -- valida el acceso en el metodo
OK: todo GET sin @PreAuthorize valida el acceso, delega en un servicio que lo valida o es un catalogo.
```

Y la prueba del 403 real, `AppUserControllerTest` (incluye `updatePerfilRechazaEditarElPerfilDeOtroAppUser`):
<!-- ev1:run needs=backend/target/surefire-reports -->
```bash
grep -h -E "^(Test set|Tests run)" backend/target/surefire-reports/*AppUserControllerTest.txt | sed -E 's/, Time elapsed.*//'
```

**Salida real (hoy):**
```
Test set: ec.edu.uteq.presustentaciones.controllers.AppUserControllerTest
Tests run: 23, Failures: 0, Errors: 0, Skipped: 0
```

**Veredicto: ✅ Cumple, y se encontraron y corrigieron 2 bugs reales investigando el detalle.** 102
endpoints (misma cifra que la guía), 97 con autorización declarativa, 5 exentos justificados
(mecanismo de login/recuperación). `MeController` no tiene ningún endpoint de escritura (solo
`GET /api/me/permisos`) y ya tiene `@PreAuthorize` de clase. El 403 real: re-verificado
`AppUserControllerTest:329` (`updatePerfilRechazaEditarElPerfilDeOtroAppUser`), sigue pasando.

**"14 endpoints de escritura solo exigen `isAuthenticated()`":** confirmado exacto (`AppUserController`
×2, `AuthController.changePassword`, `ChatbotController.askChatbot`, `StatusLiveController`,
`NotificationController` ×3, `ProposalController.send`, `SubmissionController` ×2,
`TutoringController` ×3) — no es una brecha: los 14 son endpoints de auto-servicio que resuelven la
identidad desde el JWT (nunca desde un id recibido del cliente), el mismo patrón ya auditado y
documentado en `OWASP-AUDIT.md` (A05:2021, corrección del 2026-09-11).

**Hallazgo real no pedido, encontrado revisando la lista de arriba:** al primero intentar contar estos
14 automáticamente, 9 endpoints de `CatalogController` (crear/editar/eliminar facultad, program,
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
cada intento). Verificado que compila y la suite completa sigue en verde (806/806).

### Revisión final del 2026-09-20 (punto 5b): la autorización de los `GET` también se verifica

**Lo que se señaló:** el verificador solo miraba `POST`/`PUT`/`PATCH`/`DELETE`; *«quitar la autorización de un
GET, fuera del alcance que ellos declaran»* sobrevivía a la prueba de mutaciones.

**Verificado: era cierto, y el hueco no era solo de verificación.** Al ampliar el auditor a los 111 `GET`, 28 no
llevan `@PreAuthorize` (eran 31 antes de esta ronda; se anotaron 3). Se revisó cada uno leyendo su cuerpo y el del
servicio al que llama. 22 estaban bien (validan quién pregunta, o delegan en un servicio que lo valida, o son
catálogos sin datos de personas). **9 métodos eran huecos reales**: cualquier usuario autenticado podía leer datos
de solicitudes ajenas.

| Endpoint | Qué exponía | Corrección |
|---|---|---|
| `GET /api/panelistas/solicitud/{id}`, `/tutor/solicitud/{id}`, `/info/{id}/{usuario}` | quién compone el tribunal y quién tutela una solicitud cualquiera | `SubmissionAccessService.validateAccessById`: ADMIN, estudiante dueño, panelista, tutor, o un permiso de tribunal/revisión/calificación |
| `GET /api/tutores/solicitud/{id}` | el tutor asignado a una solicitud cualquiera | la misma guarda |
| `GET /api/tutores` | **todas** las tutorías de la institución | `@PreAuthorize` con `TRIBUNAL_TUTOR_ASIGNAR` (el frontend no lo usa) |
| `GET /api/cronogramas`, `/estudiante/{id}`, `/usuario/{id}`, `/solicitud/{id}` | la defensa programada, con la solicitud completa embebida, de cualquier estudiante | `list` y `byStudent` exigen permiso de cronograma o de reportes; `byAppUser` exige ser ese usuario (la identidad sale del token) o gestionar el cronograma; `bySubmission` usa la guarda de solicitud |

Quedan sin cambios, y ahora
**nombradas una por una** con su razón en el auditor, las franjas libres de un día y la disponibilidad de una sala:
son agregados sin datos de personas.

**Cómo se comprueba ahora** (`docs/mediciones/sec/owasp/scripts/audit-endpoints-autorizacion.py`, ya dentro de
`make verify`): todo `GET` sin `@PreAuthorize` tiene que cumplir **una de tres cosas** que el script comprueba, no
que se declaran: validar el acceso en el propio método, delegar en un servicio (el script abre el servicio y exige
que la marca de validación siga ahí) o figurar como catálogo con su razón. Un `GET` nuevo que no encaje en ninguna
**hace fallar el verificador**. Salida real, arriba, en el bloque marcado.

**Pruebas nuevas** (14; suite 809 → **823**, 0 fallos): denegación de un usuario ajeno y comprobación de que el
servicio ni siquiera se consulta (`PanelistControllerTest`, `TutorControllerTest`, `ScheduleControllerTest`), la
guarda misma con sus cuatro casos (solicitud inexistente, ajeno, dueño, quien asigna el tribunal;
`SubmissionAccessServiceTest`), y dos comprobaciones por reflexión de que `GET /api/tutores` y `GET /api/cronogramas`
siguen anotados.

**Mutaciones** (3 nuevas, las 3 detectadas): M37 quita el `@PreAuthorize` de `GET /api/tutores`, M38 quita la guarda
de un `GET` por solicitud, M39 quita la comprobación de identidad del calendario.

**Límite, dicho sin adornos:** el auditor confía en las *marcas* (`validateAccess…`, `resolveAppUserId(`…), no
ejecuta la guarda. Que una marca esté presente no prueba que la lógica que hay detrás sea correcta; eso lo prueban las
pruebas unitarias de arriba, y solo para los endpoints que se tocaron. Un `GET` que valide con una marca ya
aceptada pero mal razonada no lo vería el auditor.

---

## P9 — Etiqueta del artefacto (peso 0,4)

**Criterio:** una sola etiqueta `v1.1.0` sobre el commit a defender, declarada en la portada y en
`CITATION.cff`.

**Comando:**
<!-- ev1:run -->
```bash
git tag -l -n1 v1.1.0
git cat-file -t v1.1.0
grep '^version' CITATION.cff
grep -n 'v1.1.0' Informe-Final/secciones/00-portada.tex
```

**Salida real (hoy):**
```
v1.1.0          v1.1.0 — cierre del examen suspenso (2026-09-21)
tag
version: "1.1.0"
16:{\large Informe Final --- Tag \texttt{v1.1.0}}\\[0.8cm]
```

(El hash al que apunta la etiqueta no se pega aquí: cambia cada vez que se mueve, y una cifra que hay
que reescribir a mano es una cifra que envejece. `python scripts/p9-etiqueta.py` comprueba que la
etiqueta sea anotada y esté en `HEAD`.)

> **Cerrado el 2026-09-19.** La revisión del 18-sep dejó P9 así: *«El DOI de concepto resuelve, pero su
> última versión es la v1.0.1; la v1.1.0 no está archivada (lo declaran)»*. Ya está archivada.

**Veredicto: ✅ Cumple.**

| | |
|---|---|
| Tag `v1.1.0` | Sobre el commit de cierre, **desfase 0** respecto de `HEAD` al etiquetar |
| DOI de esta versión | [`10.5281/zenodo.22865913`](https://doi.org/10.5281/zenodo.22865913) (2026-09-21) |
| Commit archivado | `6515713`; el tag se movió después solo para registrar este DOI |
| Segundo snapshot (superado) | `10.5281/zenodo.22854267`, commit `a60ae4c`, 2026-09-20 |
| Primer snapshot (superado) | `10.5281/zenodo.22839517`, commit `35d8199`, 2026-09-19 |
| Cadena de versiones | `v1.0.0 → v1.0.1 → v1.1.0` bajo el DOI de concepto `10.5281/zenodo.21988563` |

Antes de mover la etiqueta se comprobó: árbol limpio, nada sin pushear, **CI verde 3/3** sobre el
commit destino, y el commit anterior (`87f67c2`) confirmado como ancestro — sigue alcanzable, así que
no se pierde la correspondencia con lo que se revisó el 18-sep. El paquete se generó con `git archive`
sobre el tag, no desde el directorio de trabajo: 1341 archivos, sin `target/`, sin `node_modules`, sin
`__pycache__`, y los únicos `.env` son las dos plantillas `.env.example`.

**Un problema de orden que conviene declarar.** Un DOI no existe hasta que se publica, así que los
commits que lo registran son por fuerza posteriores al snapshot que archiva. Para que eso no se
convierta en una discrepancia silenciosa, `scripts/p9-snapshot-zenodo.py` —enganchado a `make verify`—
exige que **lo único** que separe el commit archivado del commit etiquetado sea el registro del propio
DOI. Si aparece código, una prueba o una medición, falla.

**Metadatos del registro, verificados el 2026-09-19 leyendo la página pública.** Al publicar, el campo
`Version` quedó como `v3` (el correlativo que Zenodo pone cuando se deja vacío) y la descripción quedó
la de v1.0.1; ambos **corregidos** desde «Editar», porque los metadatos sí se pueden cambiar tras
publicar aunque los archivos no. Queda declarado, no corregido, que tres campos nombran la cuenta de
GitHub anterior a la transferencia (`carla22072004`) y que uno de ellos cita `tree/v1.0.0` en vez de
`tree/v1.1.0`. **No son enlaces rotos:** GitHub conserva la redirección (`HTTP 301` hacia
`gleiston-guerrero`, comprobado). Detalle en [`docs/ZENODO.md`](docs/ZENODO.md).

Salvedad que se mantiene: siguen existiendo `v1.0.0`, `v1.0.1` y `v1.0.0-zenodo-archive` en el
historial de tags. Son versiones anteriores reales, no una segunda etiqueta compitiendo por el mismo
commit.

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
<!-- ev1:run -->
```bash
cat Informe-Final/secciones/00-portada.tex
```

**Salida real (hoy), el archivo completo:**
```
\begin{titlepage}
\centering
\vspace*{0.4cm}
{\LARGE \textbf{UNIVERSIDAD TÉCNICA ESTATAL DE QUEVEDO}}\\[0.3cm]
{\large Facultad de Ciencias de la Computación}\\[0.2cm]
{\large Carrera de Ingeniería de Software (Rediseño)}\\[0.5cm]
{\large \textbf{ASIGNATURA: Aplicaciones Web}}\\[0.3cm]
{\large Quinto Nivel --- Período Académico 2026-2027 PPA}\\[0.7cm]
{\Large \textbf{SISTEMA DE GESTIÓN DE PRE-SUSTENTACIONES UTEQ:}}\\[0.2cm]
{\Large \textbf{DISEÑO, IMPLEMENTACIÓN Y EVALUACIÓN EMPÍRICA}}\\[0.3cm]
{\large \textbf{DE UNA PLATAFORMA WEB PARA LA AUTOMATIZACIÓN DEL PROCESO}}\\[0.1cm]
{\large \textbf{DE PRE-SUSTENTACIÓN DE TRABAJOS DE TITULACIÓN}}\\[0.6cm]
{\large Informe Final --- Tag \texttt{v1.1.0}}\\[0.8cm]
\vfill
{\large \textbf{AUTORES:}}\\[0.4cm]
{\large Alava Alvarado, Jean Pierre \quad --- \quad ORCID: \href{https://orcid.org/0009-0001-2878-2919}{0009-0001-2878-2919}}\\[0.15cm]
{\large Moncayo Loor, Xavier Alejandro \quad --- \quad ORCID: no registrado}\\[0.15cm]
{\large Zamora Arias, Carla Esthefania \quad --- \quad ORCID: \href{https://orcid.org/0009-0000-7556-0457}{0009-0000-7556-0457}}\\[0.15cm]
{\large Barreto Rosado, Heider Dominick \quad --- \quad ORCID: \href{https://orcid.org/0009-0004-5561-1391}{0009-0004-5561-1391}}\\[0.6cm]
{\large \textbf{DOCENTE-DIRECTOR:}}\\[0.3cm]
{\large Ing. Guerrero Ulloa Gleiston Cíceron, Mg.}\\[0.6cm]
{\large \textbf{FECHA:} Septiembre de 2026}\\[0.4cm]
{\large \textbf{REPOSITORIO:} \url{https://github.com/gleiston-guerrero/PFC-Presustentaciones-2026}}
\vspace*{0.3cm}
\end{titlepage}
```

El archivo completo son 32 líneas: universidad, facultad, carrera, asignatura, título, tag, autores con
ORCID, docente-director, fecha y URL del repositorio. **Ningún recuadro de notas de proceso, ningún
DOI, ningún juicio sobre compañeros** — comprobable con `wc -l` y con el propio comando de arriba.
`make verify` lo chequea de forma automática (bloque P10: 0 referencias DOI, 0 notas de proceso).

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
<!-- ev1:run -->
```bash
find backend/src/main/java -iname "*Controller.java" | wc -l
grep -rhoE "CREATE (OR REPLACE )?(PROCEDURE|FUNCTION) [a-zA-Z0-9_.]+" backend/src/main/resources/db/migration/V*.sql | awk '{print $NF}' | sed 's/.*\.//' | sort -u | wc -l
grep -rnoE "\b(Usuario|Solicitud|Acta|Jurado|Tutoria|Cronograma|Estudiante|Evaluacion|RecursoTitulacion)(Controller|Service|ServiceImpl|Repository)\b" Informe-Final/secciones/*.tex docs/requisitos/SRS-v1.0.1.tex || echo "(sin coincidencias -- cero clases con nombre pre-P4 citadas en el informe activo)"
```

**Salida real (hoy):**
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
   no quedan citas de `AppUserController`/`EvaluationController` en ningún documento activo.
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

**Comando** (un solo `git diff-tree` por commit; el ciclo anterior hacía dos `git show` y era el doble de
lento, con el mismo resultado):
<!-- ev1:run slow -->
```bash
git rev-list --min-parents=1 --max-parents=1 HEAD | while read h; do
  [ -z "$(git diff-tree --no-commit-id -r --name-only "$h")" ] && echo "VACIO: $h"
done
```

**Salida real (hoy):**
```
VACIO: 3e7069c5ea40c596908dc7ef0661fea55c627ed9
VACIO: 4b5aa34b493fff3e6356a8128ff7438897301acf
VACIO: 1139344d3a3eb0db76c384147b54e55a79f2fa5a
VACIO: de0eeef0d177d4344ad8dc74a9055d9c470c621e
```

4 commits vacíos en todo el historial, todos anteriores al commit que revisó la guía (`f3d1ff4`): tres del 2026-09-02
y uno del 2026-09-09. **Ninguno nuevo** — `make verify` lo comprueba en cada corrida contando los
vacíos en el rango `f3d1ff4..HEAD`.

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
  Rosado) no participan en esta ronda de recuperación y no tienen commits en el tramo `f3d1ff4..HEAD`, lo que
  es verificable con `git log`. *(Redacción corregida el 2026-09-19: aquí se explicaba la ausencia con
  la situación académica de cada uno. La revisión del 18-sep observó en P10 que el juicio sobre los
  compañeros retirado de la carátula había reaparecido en otros archivos, y tenía razón: lo que este
  expediente necesita declarar es quién ejecutó los commits, no el expediente académico de un tercero.)*
  Esto cambia cómo debe leerse "sin presencia del resto del equipo": no es
  una ausencia irregular ni una decisión unilateral que excluyó a los demás de una conversación que
  debían tener — la recuperación, tal como está planteada, ya no es un trabajo de equipo activo, así
  que una "conversación con el equipo completo" no es estructuralmente posible en este momento. Anotado
  también en `BITACORA-COMMITS-2026-09-02.md`.

---

## EV-2 — El verificador, probado con mutaciones (revisión final del 2026-09-19)

**Lo que se señaló:** el evaluador probó `make verify` con **15 mutaciones: detecta 5 y sobreviven 10**.
*«Es fuerte comparando código contra código y ciego comparando documento contra medición.»* Nombró las
que sobreviven: cambiar una cifra de cobertura o del SUS en el informe, invertir la conclusión de una
prueba estadística, apuntar Lighthouse a `localhost`, borrar un archivo de evidencia citado, y mover la
etiqueta (que solo avisaba).

**Verificado: es cierto.** Antes de esta revisión ningún detector contrastaba un documento contra su
dato (P1 comprobaba el CSV, no lo que el informe decía de él), así que esas mutaciones sobrevivían por
construcción. Se escribió un arnés que inyecta el defecto, corre los detectores y restaura el archivo.
Incluso con los detectores nuevos, la primera ejecución dejó **6 de 23 vivas** (las cifras de cobertura
del informe ×3, el alfa de Cronbach y dos conclusiones estadísticas). Arreglarlas destapó tres defectos
reales que llevaban ahí desde antes:

1. **`cifras-publicadas.py` nunca revisó una sola cifra del informe.** Su expresión regular para el
   porcentaje no admitía la forma de LaTeX (`82.01\,\%`, cifra de la corrida del 2026-09-19), así que cambiar 82.01 por 85.01 en el `.tex` no
   lo veía nadie. Corregida; al revisar por primera vez el informe apareció narrativa histórica con
   cifras sin fecha pegada, que ahora la lleva.
2. **Una fracción vencida (`4019/4901`) seguía publicada en `VERIFICACION.md`** junto a «corrida de
   cierre», cuando la cifra es `4022/4904`. El detector la ignoraba porque su total (4901) no lo registra
   ninguna corrida. Ahora se revisan también los totales casi iguales al de cierre.
3. **Cinco citas a archivos que ya no existen**, en documentos vigentes: `V5__roles_y_privilegios.sql` y
   `V6__indices_optimizacion_consultas.sql` (se renumeraron a V11 y V12 al fusionar 54 commits) y tres
   enlaces a `SRS-v1.0.0.pdf`/`.tex` (movidos a `docs/requisitos/historico/`). Los enlaces rotos estaban
   ahí; nada comprobaba que lo citado existiera.

**Lo que hay ahora** (`make verify`, sección «EV-2 — Documentos contra sus datos»):

| Comprobación | Script | Qué contrasta |
|---|---|---|
| Lighthouse | `ev2-documental.py` | Las 6 corridas miden la URL pública (nunca `localhost`), todas la misma; el reporte, el informe y `make bench-lh` declaran esa misma; el título que lleva **dentro** la imagen dice lo mismo que el pie |
| Figuras del informe | `ev2-documental.py` | Las tres llevan su procedencia incrustada en el PNG (título, entradas, huella de las entradas, cifras dibujadas), coincide con volver a derivarla de los datos versionados, y las dos copias de cada una son el mismo archivo |
| Evidencia citada | `ev2-documental.py` | Todo archivo del repositorio que un documento vigente cita existe |
| Hashes citados | `ev2-documental.py` | Todo hash de commit citado en un documento vigente existe, **es un commit** (no el objeto de una etiqueta) y lo alcanza alguna rama o etiqueta: lo que ve un clon limpio |
| SUS | `ev2-documental.py` | Media, DE, IC 95 % y α publicados == recalculados del CSV; p ajustados y decisión de Holm == calculados, la frase junto al p no afirma lo contrario, y **todo `p = …` de un párrafo del SUS**, se nombre o no a Holm, es un p crudo o ajustado calculado |
| Rendimiento (P6) | `ev2-documental.py --nb` | Los p ajustados de la familia de 3 pruebas, en informe y `k6/README.md`, == los que imprime el cuaderno al ejecutarse |
| Cobertura y pruebas | `cifras-publicadas.py` | Porcentajes, conteos y fracciones publicados son los de cierre, o de una corrida que el expediente registra y que se nombra junto a la cifra |
| Etiqueta | `p9-etiqueta.py` | Anotada y **en `HEAD`**: ya **falla** en vez de avisar (solo avisa con `--rapido`, para trabajar en local) |
| Bloques de este archivo | `ev1-verificacion.py` | Cada bloque marcado `ev1:run` reproduce su salida; la tabla coincide con el resumen |
| Titularidad | `ev4-contribuciones.py --check` | Tramo **y** todo el historial: totales, reparto por persona, identidades sin dueño |
| El propio verificador | `mutaciones-gate.py` | Inyecta 51 defectos y exige que cada uno haga salir a algún detector distinto de 0 |

**Resultado del arnés: 51 detectadas, 0 sobreviven** (48 sin `--nb`: las 3 de rendimiento, M13-M15, necesitan la salida del cuaderno; y **M51** se declara omitida si no hay clases compiladas, porque entonces el bloque que la mata tampoco corre — una mutación que nadie mira no es una que sobrevive, pero tampoco una detectada). Cubre las seis del evaluador, más: fracción
vencida, JSON de contrato, `@PreAuthorize` retirado de un `POST`, SpEL hacia un bean inexistente, y los
tres defectos de Javadoc de P3.

**Tres cierres de la revisión final (punto 5, 2026-09-20) y uno posterior.** La revisión reportó que sobrevivían cuatro mutaciones y
una anomalía menor; se atendieron así:

- **5a — p crudo citado en prosa suelta: era la ceguera real, y se cerró.** El verificador comprobaba los p ajustados
  (frases con «ajustado» o «tras Holm») y la tabla de Holm, pero no un `t = 0,519, p = 0,608` escrito a mano en un
  párrafo. `ev2-documental.py` comprueba ahora cada `p = …` de un párrafo del SUS contra los p crudos y ajustados que
  calcula `sus-estadistica.py`. Mutación **M40** (el p crudo de Welch, de 0,608 a 0,008, en `SUS-RESULTS.md`): detectada; y se comprobó
  que con el detector anterior (`git show HEAD:scripts/ev2-documental.py`) esa misma mutación **sobrevivía** (sale 0).
- **Hash citado que no existía (revisión de 2026-09-21).** `VERIFICACION.md` afirmaba que el commit anterior a mover la
  etiqueta, `b1efc8…` (siete caracteres, los seis primeros aquí), «sigue alcanzable». El evaluador no lo encontró en su clon, y tenía razón: ese hash era el del
  **objeto de etiqueta** que `v1.1.0` tuvo antes de moverse (`git cat-file -t` sobre ese hash da `tag`, y apuntaba al commit
  `87f67c2`). En esta máquina existía, porque el objeto suelto sigue en la base local, y por eso nadie lo vio: un
  `git cat-file -e` da éxito. Corregido a `87f67c2`, que es un commit, es ancestro de `main` y está en el remoto.
  Para que no vuelva, `ev2-documental.py` comprueba ahora los **274 hashes citados en documentos vigentes (161
  distintos)**: cada uno tiene que ser de tipo `commit` y estar alcanzable desde ramas, ramas remotas o etiquetas (se
  saltan las líneas de md5/sha256, cuyos 8 caracteres hexadecimales no son de un commit). Mutaciones **M43** (el hash
  del objeto de etiqueta, el caso real) y **M44** (un hash inventado), las dos detectadas. Con la regla puesta, el único
  hallazgo de todo el repositorio fue ese hash. (Aquí se cita truncado a propósito: completo, el detector marcaría esta misma explicación.)
- **Comparador de cifras que admitía «la cifra de ayer» (revisión de 2026-09-21).** El evaluador cambió `823` por `809`
  en un documento vigente y `cifras-publicadas.py` **pasó en verde**; con `860` sí fallaba. Lo reproduje en
  `08-diseno-arquitectura.tex` y en `13-trabajo-futuro.tex`. La causa: una cifra que el expediente registró alguna
  vez se aceptaba si el *bloque entero* llevaba una fecha en cualquier sitio, aunque estuviera lejos del número. Ahora
  la procedencia tiene que estar **junto a la cifra** (300 caracteres), y hay una segunda regla: una cifra fechada
  con la **fecha de la corrida de cierre vigente** (la más reciente que registra su `RESUMEN.md`) tiene que ser la de
  cierre; la fecha pegada no ampara un dato falso sobre la corrida vigente. Al endurecerlo, 13 pasajes históricos
  legítimos (cifras de corridas de septiembre narradas en el informe y en este archivo) quedaron sin fecha junto a la
  cifra; se les puso, con datos comprobados en las carpetas de corridas, en vez de aflojar la regla. Mutaciones
  **M45** (la del evaluador), **M46** (cifra vencida junto a la fecha de cierre) y **M47** (el «hoy» del informe, cambiado a la
  cobertura de ayer, con una fecha vieja de otra cifra al lado): las tres detectadas, y las tres **pasaban en verde** con el
  comparador anterior (comprobado con `git show HEAD:scripts/cifras-publicadas.py`). M47 destapó un hueco que el
  evaluador no había nombrado: una fecha cercana amparaba también las afirmaciones en presente («hoy…»); ahora una cifra
  precedida de «hoy», «actualmente» o «ahora» tiene que ser la de cierre, sin excusa de fecha. Límite, dicho sin adornos: quien escriba una
  cifra vieja **con una fecha vieja verídica al lado** la hace pasar; eso es historia narrada, y el comparador no puede
  distinguirla de la que sí lo es, pero ya no puede hacerlo sin escribir una fecha que se ve.
- **5b — un `GET` sin autorización:** ver P8; era además un hueco del producto, no solo del verificador.
- **5c — el detector de credenciales se disparaba con prosa: primero se dijo «no se reproduce», y era un error de esa
  respuesta.** La primera vez se probaron cuatro ediciones de `SUS-RESULTS.md` (M07, M09, M12, M40), ninguna con la
  palabra «clave», y se concluyó que no había nada que arreglar. La revisión siguiente lo precisó: *«la palabra
  española "clave" dispara un falso positivo en cualquier prosa. Menor y no tocado»*. Tenía razón. `ev2-credenciales.py`
  marcaba `la clave: 823pruebas`, `clave: p=0.608/0.220` y `Contraseña: 2026-09-20`, así que añadir una frase así a un
  `.md` o `.tex` bastaba para que `make verify` saliera 1 con «credenciales en claro». Reproducido sobre el archivo
  real: se añadió una frase con «clave» a `SUS-RESULTS.md` y el detector de antes salía **1**; el nuevo sale **0**.
  Arreglo: las claves en español (`clave`, `contraseña`) solo cuentan en archivos de código o configuración, o en prosa
  con el valor **entrecomillado** (un ejemplo literal, no una frase); las claves en inglés (`password`, `secret`) cuentan
  en todas partes, porque el hallazgo original estaba en un `.md`; y una fecha o una cifra con separadores nunca es una
  contraseña. `python scripts/ev2-credenciales.py --autoprueba` fija 13 casos en los dos sentidos y corre dentro de
  `make verify`. Mutaciones: **M41** (se desactiva el filtro de prosa) y **M42** (se reescribe una contraseña literal en
  `backend/INSTRUCCIONES.md`), las dos detectadas. Límite: un valor de solo dígitos sin separadores (`12345678`) sigue
  contando como contraseña posible; la regla no lo exime a propósito.

**Límites, dichos sin adornos:**

- Las 15 mutaciones del evaluador no las tenemos; nombró 6 clases de las 10 que sobreviven. Se cubren
  esas seis y otras, **pero no se puede afirmar que sean las mismas 10**.
- Es un contraste de cifras *ancladas* a una frase. Quien reescriba una cifra con una redacción que
  ninguna regla reconoce, no la ve. Por eso cada regla exige encontrar un mínimo de casos (si la
  expresión deja de casar, falla en vez de pasar en silencio) y por eso existe el arnés.
- No se comprueba que el texto que rodea a una cifra *diga lo correcto*, solo que la cifra coincida.
- La mutación de la etiqueta exige que `make verify` se corra **con la etiqueta en `HEAD`**: mover la
  etiqueta es siempre el último paso.

---

## EV-1 y EV-4 — Salidas pegadas a mano y conteos que se contradicen (revisión final del 2026-09-19)

**EV-1, lo que se señaló:** *«Reproduje ocho bloques literalmente; tres no reproducen por cifras
obsoletas (Javadoc 734/768 frente a 777/778, "403 commits" frente a 436, "3 pruebas" frente a 5). La tabla
resumen se contradice a sí misma en dos filas.»*

**Verificado: los tres, y las dos filas.** Cada uno tenía la misma causa —una salida escrita a mano que
envejeció cuando alguien tocó el código— y en dos casos ni siquiera era la salida del comando de encima:

| Lo que se señaló | Qué había | Qué era |
|---|---|---|
| Javadoc 734/768 | Salida del escáner del 17-sep (`768`, `536`, 69,8 %) bajo el comando de hoy | Cifra vencida; el escáner da hoy **777/778** |
| «403 commits» | La línea `total commits: 403` **no la imprimía el comando** de encima | Tecleada; el historial tiene más y crece con cada commit |
| «3 pruebas» | `ChatbotControllerIntegrationTest: Tests run: 3` | Eran 3 hasta que se retiró el `@MockBean` del chatbot (P7); ahora son **5** (y 18 en total) |
| Fila **P4** de la tabla | `✅ Cumple en src/main`, mientras la nota de debajo decía «P4 bajó de 🟡 a 🔴» | La nota era de la ronda anterior |
| Nota de **P9** | «el tag sigue apuntando a `8b1c1d2`», bajo una fila que decía «desfase 0» | La nota era de antes de mover la etiqueta |

**Cómo se arregla para que no vuelva a pasar** (`scripts/ev1-verificacion.py`, dentro de `make verify`):

1. **Un bloque de comandos precedido por `<!-- ev1:run -->` se ejecuta, y la salida que sigue tiene que ser
   idéntica** a lo que imprime. Hay 12 bloques marcados (SUS, Javadoc, P4 ×2 —fuente y bytecode—, Lighthouse,
   chatbot, autorización ×2, etiqueta, carátula, cifras únicas, historial). `--actualizar` reescribe las
   salidas desde la corrida real: ya nadie las teclea. Los bloques *sin* marca son salidas históricas fechadas
   (una corrida de Maven, la medición «antes») y se rotulan como tales. Tres llevan condición: dos necesitan
   los informes de Surefire y el del bytecode necesita las clases compiladas; si falta, el bloque se omite y
   el verificador dice cuántos omitió, en vez de fallar por algo que el entorno no puede producir.
2. **La tabla del principio y el «Resumen de honestidad» tienen que decir lo mismo**, punto por punto.
3. Lo volátil no se pega: el hash al que apunta la etiqueta cambia cada vez que se mueve, así que ya no
   aparece; lo comprueba `scripts/p9-etiqueta.py`.

**Un error mío al construirlo, que conviene dejar dicho:** la primera versión ejecutaba los comandos con el
`bash` de PATH, que en Windows es el de WSL; devolvió un error de WSL y `--actualizar` **lo escribió en las
11 salidas**. Se restauró y ahora se busca el bash de Git explícitamente, y el script se niega a comparar o
escribir si la salida es un error de WSL. Se descubrió porque los 11 bloques «fallaron» a la vez con el mismo
mensaje, no por una revisión.

**EV-4, lo que se señaló:** *«Tres de las cuatro filas no llevan firma, por no participar»* y, en las
correcciones finales, *«los conteos de `CONTRIBUCIONES.md` se contradicen consigo mismos»*.

**Los conteos: verificado y corregido.** El archivo decía a la vez «**121** commits» en el tramo y «**403**
commits únicos» en el historial, cuya tabla sumaba **369**, con una explicación (`git shortlog --all`) para
dos números que no coincidían. El total del tramo ya se generaba y se comprobaba; el del historial completo
seguía tecleado. Ahora sale de `git log` con la misma regla (contado hasta el commit que contiene el
archivo) y el chequeo falla si una identidad de Git no pertenece a nadie o si la suma no da el total:

| Integrante | Antes (tecleado) | Al 2026-09-19 (de `git log`; crece con cada commit) |
|---|---:|---:|
| Álava Alvarado | 174 | 258 |
| Zamora Arias | 137 | 128 |
| Barreto Rosado | 45 | 45 |
| Moncayo Loor | 13 | 13 |
| **Total** | «403» (la tabla sumaba 369) | **444** (la tabla suma 444) |

**Las firmas: no se pueden cerrar desde el repositorio, y no se finge.** Tres de las cuatro filas quedan sin
firma porque esos integrantes no participan en la ronda, y una firma solo puede ponerla quien firma. El
documento lo dice —«las tres filas sin firma son una afirmación del autor, no de los firmantes»— y no
atribuye a nadie una declaración que no hizo. Lo que sí queda resuelto es que la ausencia de firma es
explícita y explicada; si el docente necesita esas firmas, hay que pedírselas a ellos.

**Sigue abierto, declarado:** los 92 commits con correo personal de esta ronda no se pueden reescribir sin
cambiar todos los hashes que este expediente cita como evidencia (ver `CONTRIBUCIONES.md`).

---

## Personas del equipo — juicio académico en archivos públicos (revisión final, corrección 7.4)

**Lo que se señaló:** retirar de **cuatro archivos públicos** el juicio académico sobre los compañeros.

**Verificado: eran cuatro, y por eso el arreglo de P10 no bastó.** El 2026-09-16 se retiró de la carátula;
pero la misma frase había sido copiada en otros sitios, y arreglar el sitio que el evaluador vio no
arregla las copias:

| Archivo | Qué decía |
|---|---|
| `CITATION.cff` (2 comentarios) | Daba como razón de la falta de ORCID de un integrante su situación en el programa |
| `CONTRIBUTORS.md` | Lo mismo, para el mismo integrante |
| `docs/observaciones/BITACORA-COMMITS-2026-09-02.md` | Explicaba la ausencia de tres integrantes en esta ronda con su resultado en la materia |
| `docs/observaciones/OBSERVACIONES.md` (OBS-23 y OBS-47) | Repetía las dos explicaciones anteriores |

**Corregido:** esos textos dicen ahora lo único que este expediente puede sostener con `git log` y con la
API de ORCID —quién ejecutó los commits y qué identificadores están registrados—, sin razón adjunta. En los
dos archivos que son bitácora (que por regla no se reescribe) se dejó una nota fechada del cambio en vez de
borrar sin dejar rastro. Sobre por qué esto no debe reaparecer, ver `CONTRIBUCIONES.md`: el expediente
académico de un tercero no es del equipo publicarlo, y menos en un repositorio público y en un documento que
esas personas no han firmado.

**Y para que no vuelva:** `scripts/ev2-documental.py` revisa **todos** los archivos de texto que `git`
rastrea —no solo los «vigentes»— buscando esas fórmulas, y falla si aparece alguna. Se probó con tres
mutaciones (una por archivo tipo), que se detectan. Advertencia honesta: es una lista de fórmulas conocidas;
una redacción distinta del mismo juicio no la vería. Cubre exactamente lo que se encontró, y por eso este
propio archivo describe el asunto sin citar las frases.

---

## Seguridad — Credenciales escritas en claro (señalado el 2026-09-18)

**Lo que se señaló:** *«`k6/load-test.js:28` tiene escrita la contraseña de la cuenta de administrador
de demostración, y `backend/INSTRUCCIONES.md:64` una contraseña de base de datos. No las probé.»*

**Verificado: las dos existían.** Ninguna era de producción —`admin123` era de la cuenta que siembra
`DemoDataSeeder`, que está anotado `@Profile("dev")` y nunca se instancia fuera de ese perfil;
`postgreAdmin19` era la base local de Docker— pero **ese argumento no lo puede comprobar quien lee el
repositorio**: una cadena con pinta de credencial se lee como una credencial, y el lector no tiene
forma de saber que no lo es. Se retiran por eso, no por el riesgo directo.

**Corregido:**

- `k6/load-test.js` lee las credenciales del entorno (`K6_USER` / `K6_PASS`) y **falla con un mensaje
  explícito** si faltan, en vez de llevarlas escritas. Hallazgo de paso: el valor que estaba escrito
  (`admin123`) **ya no era el del sembrador**, así que ese `setup()` habría fallado igualmente —
  pidiéndolas por entorno el fallo es explícito en vez de silencioso.
- `backend/INSTRUCCIONES.md` usa `${DB_USERNAME}` / `${DB_PASSWORD}` y remite a `.env.example`, con la
  razón escrita en el propio archivo.

**Y para que no vuelvan:** `scripts/ev2-credenciales.py`, enganchado a `make verify`, revisa los
archivos que `git` rastrea —los que ve cualquiera que clone— y falla si aparece un literal de aspecto
credencial. Acepta las dos formas, entrecomillada (`password: 'admin123'`) y suelta al estilo
`.properties` (`spring.datasource.password=postgreAdmin19`), porque los dos hallazgos originales tenían
una forma distinta cada uno.

**Dos cosas sobre el detector, dichas porque importan más que el detector:**

1. **Una primera versión marcaba 28 sitios, todos falsos** (`token = jwtService.generateToken(...)`,
   `token = localStorage.getItem(...)`). Un detector que grita en falso se termina ignorando, que es el
   mismo defecto que se está corrigiendo. Se restringió a literales con al menos un dígito.
2. **Una segunda versión no detectaba nada**, porque exigía comillas y la forma original de
   `INSTRUCCIONES.md` no las lleva. Se comprobó reintroduciendo las dos líneas: **las dos disparan**, y
   las cinco formas de código normal que antes daban falso positivo, no.

**Veredicto: ✅ Cumple**, con las dos credenciales retiradas y una comprobación que falla si vuelven.

---

## Resumen de honestidad de este archivo

**Actualizado el 2026-09-19.** De los 12 puntos, agrupados como en la tabla del principio (el formato de
estas tres líneas lo lee `scripts/ev1-verificacion.py`, que falla si un punto cambia de categoría en una
tabla y no en la otra):

- **✅ Cumple** — P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, cada uno con reservas o brechas declaradas en su sección.
- **🟡 Parcial** — P1, P12.

**P1 y P12** tienen una brecha real sin cerrar, y ninguna de las dos se cierra con más documentación:
dependen del origen de las 11 hojas del SUS y de una conversación con el docente y el equipo completo.

**P4** pasó por 🟡 → 🔴 → ✅ y vale la pena el detalle: primero se resolvió la «disputa numérica abierta» **a favor del
ingeniero** (se reprodujo su cifra al dígito y se retractó la del equipo, que era un artefacto de un diccionario
demasiado estrecho), luego se hizo el renombrado de identificadores, y el 2026-09-20 se tradujeron también los
789 nombres de `@Test` que la brecha declarada dejaba en español. Hoy: **0,0 %** de tipos y métodos bajo la
definición del ing, 1,5 %/3,4 % bajo la lectura más amplia, y 0 de 809 nombres de prueba con palabras del
diccionario español. Detalle en
[`docs/observaciones/P4-RESOLUCION-DISPUTA.md`](docs/observaciones/P4-RESOLUCION-DISPUTA.md).

Ningún punto se declaró «resuelto» para inflar este resumen. Varios de los que ya estaban cerrados en
`OBSERVACIONES.md` quedan aquí con matices que esa bitácora, por ser narrativa y cronológica, no deja
igual de visibles a primera vista.

---

## Verificación adicional: el resto de la evaluación integral (secciones 1-3, 5-9)

> **Registro histórico del 2026-09-17/18: no describe el estado actual.** Los commits, el tag `8b1c1d2` y
> los «37 commits de desfase» que aparecen aquí son de ese día. Lo que era cierto entonces se conserva sin
> reescribir (reescribirlo sería falsear el historial); el estado de hoy está en la tabla del principio.

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
   coincidiendo exacto con lo que el informe citaba entonces. *(Nota del 2026-09-19: ese cuaderno
   calcula la ronda en papel, que desde la ronda del 18-sep ya no es la cifra de cierre. El cierre lo
   calcula `scripts/sus-estadistica.py` y `make verify` lo asegura.)*
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

**Recomendación derivada de este hallazgo estructural (2026-09-18) — cumplida el 2026-09-19:** la
etiqueta se movió al commit de cierre, y desde entonces se mueve como último paso de cada ronda. Ya no es
una recomendación que dependa de que alguien se acuerde: `make verify` falla si la etiqueta no está en
`HEAD` (`scripts/p9-etiqueta.py`), y el registro de Zenodo se comprueba contra su API pública
(`scripts/p9-zenodo-registro.py`).
