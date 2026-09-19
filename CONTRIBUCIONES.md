# Contribuciones por punto — examen suspenso

**Entregable EV-4.** Titularidad declarada por punto, distinta de
[`CONTRIBUTORS.md`](CONTRIBUTORS.md) (roles CRediT generales de todo el proyecto, sin desglose por
punto de esta ronda).

> **Reescrito el 2026-09-19 tras la revisión individual del 18-sep**, que declaró EV-4 *No cumple* con
> cuatro defectos concretos: «sin firmas, sin correo institucional, sin columna de archivos, y con
> recuentos que no cuadran (dice 81 commits; son 89)». Los cuatro se responden abajo. El último es el
> que de verdad importaba, porque es el que se repite solo: cuando se corrigió 81 → 89, dos días
> después ya eran 98. **Por eso la tabla dejó de escribirse y pasó a generarse.**

## Metodología: la tabla la produce `git`, no el equipo

Cada commit de esta ronda lleva su punto en el asunto (`fix(P4,EV-2): ...`). El script
[`scripts/ev4-contribuciones.py`](scripts/ev4-contribuciones.py) lee `git log`, reparte los commits
por punto, y saca de cada uno los archivos que tocó. La tabla de abajo es su salida literal, pegada
sin editar.

```bash
python scripts/ev4-contribuciones.py            # regenera la tabla
python scripts/ev4-contribuciones.py --check     # falla si este archivo no cuadra con git
```

El modo `--check` corre dentro de `make verify` y **hace fallar la verificación** si este archivo
declara un total de commits distinto del real, cita un commit que no existe, cita un archivo que ya no
está versionado, omite un punto al que git sí atribuye commits, o no declara alguno de los autores
reales del tramo. Es la regla que impuso el propio evaluador —*«un punto atribuido en
CONTRIBUCIONES.md que el historial no respalda no cuenta para nadie»*— aplicada de forma ejecutable en
vez de prometida.

## Autoría de esta ronda

**Tramo:** `f3d1ff4..HEAD` — desde el commit que revisó la guía original hasta hoy.
**Total: 98 commits.**
**Una sola persona, sin excepción:** Álava Alvarado, Jean Pierre.
Identidades de Git que usó en el tramo: Jean30042 <jalavaa@uteq.edu.ec>, Jean30042 <jeanalavaalavarado@gmail.com>

### Sobre el correo (defecto señalado: «sin correo institucional»)

Correcto y confirmado. Reparto real del tramo, con `git log --format=%ae f3d1ff4..HEAD | sort | uniq -c`:

| Correo | Commits |
|---|---:|
| `jeanalavaalavarado@gmail.com` (personal) | 92 |
| `jalavaa@uteq.edu.ec` (institucional) | 6 |

**Corregido a partir del 2026-09-19:** el repositorio quedó fijado a la identidad institucional, de
forma local y no solo global, para que no dependa de la máquina en la que se trabaje:

```bash
git config --local user.name  "Jean30042"
git config --local user.email "jalavaa@uteq.edu.ec"
```

De ahí en adelante todo commit de este repositorio lleva el correo institucional. La cifra de la tabla
crece con cada commit nuevo, así que este archivo se regenera y el chequeo la vuelve a comprobar.

**Lo que esto no hace, dicho antes de que lo pregunten:** no reescribe los 92 commits
anteriores.
Reescribirlos cambiaría todos los hashes del tramo, incluidos los que esta misma tabla, `VERIFICACION.md`,
`OBSERVACIONES.md` y la etiqueta `v1.1.0` citan como evidencia — es decir, destruiría la trazabilidad
para maquillar un campo de metadatos. El correo personal en el historial pasado queda declarado como
defecto real, no corregido retroactivamente. La identidad institucional `jalavaa@uteq.edu.ec` ya existía
en el repositorio antes de esta ronda (identidad `jalavaa-dev`, ver la tabla histórica más abajo), así
que la correspondencia persona↔identidad es verificable con `git shortlog -sne --all`.

## Titularidad por punto

Salida literal de `python scripts/ev4-contribuciones.py`:

| Punto | Commits | Archivos de evidencia (los mas tocados) | Total archivos |
|---|---|---|---|
| P1 | `f51db75`, `80e1c08`, `70035fa`, `e38cc0d`, `8488d06`, `05a2135`, `5d6102b`, `79b682a`, `d284a22`, `d42da11`, `23ddf41` | `docs/mediciones/sus/SUS-RESULTS.md`<br>`Informe-Final/secciones/10-evaluacion-empirica.tex`<br>`README.md`<br>`docs/mediciones/sus/re-aplicacion/README.md` | 32 |
| P2 | `2b9ba89`, `9067cad`, `e64d4c7`, `d14ddca`, `536a33b`, `23ddf41` | `Informe-Final/secciones/10-evaluacion-empirica.tex`<br>`docs/mediciones/jacoco/COVERAGE.md`<br>`scripts/verify.sh`<br>`VERIFICACION.md` | 77 |
| P3 | `2b9ba89`, `41c5bc9`, `ef5d83c`, `e38cc0d`, `e7c49ab`, `71a62d7`, `6aea088`, `5fe6991` | `VERIFICACION.md`<br>`backend/pom.xml`<br>`backend/src/main/java/ec/edu/uteq/presustentaciones/controllers/MinutesController.java`<br>`backend/src/main/java/ec/edu/uteq/presustentaciones/controllers/ProposalController.java` | 162 |
| P4 | `49adaee`, `63c7efd`, `e38cc0d`, `40426b3`, `6282d50`, `e7c49ab`, `a73821b`, `e7f0ce5`, `b75abae`, `536a33b`, `02b3fc6`, `556f02a`, `4cc6290`, `7263fbc`, `3593511`, `6a1e0b5`, `32f9847`, `93e6806`, `9fd99e9`, `20b378a` | `VERIFICACION.md`<br>`backend/src/main/java/ec/edu/uteq/presustentaciones/controllers/MinutesController.java`<br>`scripts/verify.sh`<br>`backend/src/main/java/ec/edu/uteq/presustentaciones/controllers/EvaluationController.java` | 582 |
| P5 | `73ec6c7`, `c5434ab`, `2161280`, `cc41724`, `e440644`, `f9c9483` | `README.md`<br>`Frontend/public/robots.txt`<br>`Informe-Final/secciones/10-evaluacion-empirica.tex`<br>`VERIFICACION.md` | 21 |
| P6 | `bd2cc84`, `201c2fa`, `6707768`, `c840c2f` | `Informe-Final/secciones/10-evaluacion-empirica.tex`<br>`CONTRIBUTORS.md`<br>`Informe-Final/secciones/00-portada.tex`<br>`Informe-Final/secciones/09-implementacion.tex` | 14 |
| P7 | `bd2cc84`, `b99bb72`, `c20ce4c` | `CONTRIBUTORS.md`<br>`Informe-Final/secciones/00-portada.tex`<br>`Informe-Final/secciones/09-implementacion.tex`<br>`Informe-Final/secciones/10-evaluacion-empirica.tex` | 14 |
| P8 | `bd2cc84`, `40426b3`, `cd4f770`, `1569635`, `b783e5e`, `93e6806` | `docs/mediciones/sec/owasp/scripts/audit-endpoints-autorizacion.py`<br>`docs/mediciones/sec/owasp/OWASP-AUDIT.md`<br>`CONTRIBUTORS.md`<br>`Informe-Final/secciones/00-portada.tex` | 16 |
| P9 | `8b1c1d2`, `7c9358b`, `cf686d2`, `22bdcd1`, `394d213`, `546b9bc`, `f065b3c`, `746ee5a`, `87f67c2` | `docs/ZENODO.md`<br>`CITATION.cff`<br>`Informe-Final/secciones/00-portada.tex`<br>`VERIFICACION.md` | 8 |
| P10 | `bd2cc84`, `85c13cf`, `d301e8b`, `9c16cd2`, `dfd93e3`, `e36f25e`, `2275fa6` | `Informe-Final/secciones/00-portada.tex`<br>`VERIFICACION.md`<br>`CONTRIBUCIONES.md`<br>`CONTRIBUTORS.md` | 19 |
| P11 | `bd2cc84`, `73771be`, `6282d50`, `53de5be`, `7b7016c`, `7210c75`, `dd3e192`, `95923ea` | `Informe-Final/secciones/10-evaluacion-empirica.tex`<br>`docs/requisitos/SRS-v1.0.1.tex`<br>`Informe-Final/secciones/08-diseno-arquitectura.tex`<br>`Informe-Final/secciones/13-trabajo-futuro.tex` | 40 |
| P12 | `bd2cc84`, `25f0896`, `28276f9`, `9bfa665`, `7a22271`, `efa4134`, `2275fa6`, `19c028a` | `VERIFICACION.md`<br>`CONTRIBUCIONES.md`<br>`docs/observaciones/BITACORA-COMMITS-2026-09-02.md`<br>`CONTRIBUTORS.md` | 17 |
| EV-1 | `9fd9d0c`, `feb9d64`, `f63e903`, `2275fa6` | `VERIFICACION.md`<br>`CONTRIBUCIONES.md`<br>`Makefile`<br>`scripts/verify.sh` | 5 |
| EV-2 | `9fd9d0c`, `feb9d64`, `20b378a`, `512608e`, `7d3d004` | `scripts/verify.sh`<br>`CONTRIBUCIONES.md`<br>`.gitignore`<br>`Makefile` | 51 |
| EV-4 | `9fd9d0c`, `feb9d64`, `2275fa6`, `512608e`, `1c9c9da`, `142c35f`, `eefc32c` | `CONTRIBUCIONES.md`<br>`scripts/ev4-contribuciones.py`<br>`Makefile`<br>`VERIFICACION.md` | 44 |

La columna **Archivos de evidencia** lista los archivos que más commits del punto tocaron, excluyendo
tres que toca casi todo y por eso no distinguen nada (`informe-final.pdf`, `SRS-v1.0.1.pdf` y
`docs/observaciones/OBSERVACIONES.md`). **Total archivos** es el recuento completo, sin excluir.
Para ver la lista entera de un punto:

```bash
git show --pretty= --name-only <sha>
```

**EV-3 no aparece** porque ningún commit lo nombra en el asunto: se cerró declarando la URL pública ya
existente en `README.md`, sin cambio de código. La revisión del 18-sep lo da por cumplido.

### Commits sin punto declarado (8 de 98)

Se listan en vez de repartirlos a ojo entre los puntos, que es exactamente el tipo de atribución que el
historial no respaldaría:

| Commit | Asunto |
|---|---|
| `ae60bb8` | docs: registrar hash del commit del CSV en OBSERVACIONES.md |
| `54b5c9f` | docs: backfill del hash real de la aclaracion de equipo en OBS-47 |
| `eca9f02` | fix(docs): corrige comando SRS roto y URLs del repo antiguo; ejecuta notebook  |
| `987bd62` | docs: backfill del hash real en OBS-48 |
| `6459d16` | fix(informe): eliminar las 2 etiquetas huerfanas y verificar el conteo de ruti |
| `4474ad1` | fix(docs): corregir las 263 citas de clases rotas desde 49adaee y un component |
| `17e113a` | docs: backfill del hash real en OBS-52 |
| `8887990` | fix(seccion-6): retirar el commit vacio 4b5aa34 como evidencia y corregir el m |

## Por qué un solo autor

Este examen suspenso lo está cursando y sustentando **Álava Alvarado** en solitario. Los otros tres
integrantes originales (Moncayo Loor, Zamora Arias, Barreto Rosado) reprobaron la materia en el período
regular y no están trabajando en esta ronda de recuperación — no es que se hayan desentendido de un
trabajo que seguía siendo colectivo: la recuperación, tal como está planteada, ya no lo es.

**No se declara ningún punto como trabajo colectivo de esta ronda porque el historial no lo respalda.**
Eso no dice quién entendió o decidió qué —los cuatro participaron en fases anteriores— sino literalmente
quién ejecutó los commits que cierran cada punto, que es lo único que un `git log` puede verificar.

La ronda la ejecutó Álava Alvarado **con asistencia de un modelo de lenguaje** (ver la declaración de uso
de IA en `Informe-Final/secciones/15-declaraciones.tex`). La autoría de los commits es humana y no se
reparte con la herramienta.

## Contribución histórica al proyecto completo

`git shortlog -sne --all`, con las identidades múltiples de la misma persona agrupadas (la tabla de
correspondencia identidad↔persona está en [`CONTRIBUTORS.md`](CONTRIBUTORS.md)):

| Integrante | Identidades de Git | Commits |
|---|---|---|
| Álava Alvarado, Jean Pierre | `Jean30042 <jeanalavaalavarado@gmail.com>`, `jalavaa-dev <jalavaa@uteq.edu.ec>` | 174 |
| Zamora Arias, Carla Esthefania | `carla22072004 <czamoraa5@uteq.edu.ec>`, `Carla Esthefania Zamora Arias <czamoraa5@uteq.edu.ec>` | 137 |
| Barreto Rosado, Heider Dominick | `dominick1245 <dominickelyolo@gmail.com>`, `dominick1245 <144386724+dominick1245@users.noreply.github.com>` | 45 |
| Moncayo Loor, Xavier Alejandro | `XAML25 <xavierloor52@gmail.com>` | 13 |

403 commits únicos en `HEAD`. La diferencia con la suma de la columna sale de que `git shortlog --all`
cuenta también commits que solo existen en ramas o reflog no fusionados a `main`, no de contarlos dos
veces en el mismo historial.

## Firmas

Quien declara la titularidad de esta ronda responde por su contenido. La firma manuscrita va sobre la
copia impresa que se entrega en la defensa; aquí queda la declaración y la identidad verificable.

| Integrante | Correo institucional | Participación en esta ronda | Firma |
|---|---|---|---|
| Álava Alvarado, Jean Pierre | `jalavaa@uteq.edu.ec` | Autor de los 98 commits del tramo | ____________________ |
| Moncayo Loor, Xavier Alejandro | — | Ninguna (reprobó el período regular) | ____________________ |
| Zamora Arias, Carla Esthefanía | `czamoraa5@uteq.edu.ec` | Ninguna (reprobó el período regular) | ____________________ |
| Barreto Rosado, Heider Dominick | — | Ninguna (reprobó el período regular) | ____________________ |

**Declaración:** lo afirmado en este archivo sale de `git log` sobre el repositorio público y es
reproducible con los comandos citados arriba. No se atribuye a ninguna persona trabajo que el historial
no respalde, ni se reclama para esta ronda trabajo de rondas anteriores.

_Álava Alvarado, Jean Pierre — `jalavaa@uteq.edu.ec` — 2026-09-19._

**Las tres filas sin firma son una afirmación del autor, no de los firmantes.** Los otros tres
integrantes no han visto ni suscrito este documento; se les nombra para que conste quién compone el
equipo original y por qué no participan, no para atribuirles una declaración. Si el docente necesita su
confirmación, hay que pedírsela a ellos directamente.
