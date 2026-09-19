#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Escribe CONTRIBUCIONES.md entero (EV-4). Nada de su contenido se teclea.

La tabla de titularidad, el conteo, los autores y el reparto de correos salen de
scripts/ev4-contribuciones.py, que a su vez los saca de `git log`. Aqui solo se
monta el documento alrededor de esa salida.

Existe porque la parte narrativa tambien se pierde si se edita a mano: la
primera regeneracion se llevo por delante una seccion escrita directamente en el
.md. Lo que tenga que sobrevivir a una regeneracion vive en esta plantilla.

USO
    python scripts/ev4-generar-contribuciones.py
    python scripts/ev4-contribuciones.py --check    # comprueba el resultado

Despues hay que comitear: el chequeo espera que la cifra sea la del commit que
contiene el archivo (ver la seccion "El archivo se invalida a si mismo").
"""
import io
import re
import subprocess

gen = subprocess.run(
    ["python", "scripts/ev4-contribuciones.py"],
    capture_output=True, text=True, encoding="utf-8", errors="replace")
assert gen.returncode == 0, gen.stderr
salida = gen.stdout

# Se escribe la cifra que incluye el commit que traera este archivo: si se
# pusiera la de HEAD, el archivo naceria desfasado por uno.
total = re.search(r"^Commits al comitear: (\d+)$", salida, re.M).group(1)
en_head = re.search(r"^Commits: (\d+)$", salida, re.M).group(1)
autores = re.search(r"^Autores: (.+)$", salida, re.M).group(1)

BASE = "f3d1ff4"
correos = subprocess.run(["git", "log", "--format=%ae", f"{BASE}..HEAD"],
                         capture_output=True, text=True).stdout.split()
n_personal = sum(1 for c in correos if "gmail" in c)
n_inst = sum(1 for c in correos if "uteq.edu.ec" in c) + 1  # +1: el commit que trae esto
tabla = salida[salida.index("| Punto |"):salida.index("\nCommits sin punto declarado")]
sueltos = salida[salida.index("Commits sin punto declarado"):].strip()
n_sueltos = re.search(r"\((\d+)\)", sueltos).group(1)
sueltos_filas = "\n".join(
    "| `%s` | %s |" % (l.split(None, 1)[0], l.split(None, 1)[1])
    for l in sueltos.splitlines()[1:] if l.strip())

DOC = f"""# Contribuciones por punto — examen suspenso

**Entregable EV-4.** Titularidad declarada por punto, distinta de
[`CONTRIBUTORS.md`](CONTRIBUTORS.md) (roles CRediT generales de todo el proyecto, sin desglose por
punto de esta ronda).

> **Reescrito el 2026-09-19 tras la revisión individual del 18-sep**, que declaró EV-4 *No cumple* con
> cuatro defectos concretos: «sin firmas, sin correo institucional, sin columna de archivos, y con
> recuentos que no cuadran (dice 81 commits; son 89)». Los cuatro se responden abajo. El último es el
> que de verdad importaba, porque es el que se repite solo: cuando se corrigió 81 → 89, dos días
> después ya eran {total}. **Por eso la tabla dejó de escribirse y pasó a generarse.**

## Metodología: la tabla la produce `git`, no el equipo

Cada commit de esta ronda lleva su punto en el asunto (`fix(P4,EV-2): ...`). El script
[`scripts/ev4-contribuciones.py`](scripts/ev4-contribuciones.py) lee `git log`, reparte los commits
por punto, y saca de cada uno los archivos que tocó. La tabla de abajo es su salida literal, pegada
sin editar.

```bash
python scripts/ev4-generar-contribuciones.py    # reescribe este archivo entero
python scripts/ev4-contribuciones.py            # solo imprime la tabla
python scripts/ev4-contribuciones.py --check    # falla si este archivo no cuadra con git
```

El modo `--check` corre dentro de `make verify` y **hace fallar la verificación** si este archivo:

1. declara un total de commits distinto del que corresponde,
2. cita un commit que no existe,
3. cita un archivo que ya no está versionado,
4. omite un punto al que `git` sí atribuye commits,
5. **omite un commit cuyo asunto declara un punto** — sin esto el archivo envejece en silencio, que es
   como llegó a decir 81 cuando ya eran 89, o
6. no declara alguno de los autores reales del tramo.

Es la regla que impuso el propio evaluador —*«un punto atribuido en CONTRIBUCIONES.md que el historial
no respalda no cuenta para nadie»*— aplicada de forma ejecutable en vez de prometida.

### El archivo se invalida a sí mismo, y cómo se resuelve

Conviene decirlo antes de que se note: un conteo comprobado contra `HEAD` **no puede cuadrar nunca**,
porque el propio commit que actualiza este archivo lo deja desfasado en el instante siguiente. Esa es,
literalmente, la forma en que «81 commits» se quedó escrito mientras el historial llegaba a 89.

La cifra que se comprueba es, por eso, la del commit que **contiene** este archivo, no la de `HEAD`:

- si el archivo está comiteado, se cuenta el tramo hasta el último commit que lo tocó;
- si está modificado sin comitear, se espera `HEAD + 1`: el commit al que va.

Y el guardia contra la obsolescencia (punto 5) exceptúa un único commit: el que trae la versión vigente
de este archivo, cuyo hash no existía cuando se generó. Por eso el ciclo termina en vez de perseguirse
la cola: cada commit que cierra un punto obliga a regenerar, y el commit que regenera es la excepción.

## Autoría de esta ronda

**Tramo:** `f3d1ff4..HEAD` — desde el commit que revisó la guía original hasta hoy.
**Total: {total} commits.**
**Una sola persona, sin excepción:** Álava Alvarado, Jean Pierre.
Identidades de Git que usó en el tramo: {autores}

### Sobre el correo (defecto señalado: «sin correo institucional»)

Correcto y confirmado. Reparto real del tramo, con `git log --format=%ae {BASE}..HEAD | sort | uniq -c`:

| Correo | Commits |
|---|---:|
| `jeanalavaalavarado@gmail.com` (personal) | {n_personal} |
| `jalavaa@uteq.edu.ec` (institucional) | {n_inst} |

**Corregido a partir del 2026-09-19:** el repositorio quedó fijado a la identidad institucional, de
forma local y no solo global, para que no dependa de la máquina en la que se trabaje:

```bash
git config --local user.name  "Jean30042"
git config --local user.email "jalavaa@uteq.edu.ec"
```

De ahí en adelante todo commit de este repositorio lleva el correo institucional. La cifra de la tabla
crece con cada commit nuevo, así que este archivo se regenera y el chequeo la vuelve a comprobar.

**Lo que esto no hace, dicho antes de que lo pregunten:** no reescribe los {n_personal} commits
anteriores.
Reescribirlos cambiaría todos los hashes del tramo, incluidos los que esta misma tabla, `VERIFICACION.md`,
`OBSERVACIONES.md` y la etiqueta `v1.1.0` citan como evidencia — es decir, destruiría la trazabilidad
para maquillar un campo de metadatos. El correo personal en el historial pasado queda declarado como
defecto real, no corregido retroactivamente. La identidad institucional `jalavaa@uteq.edu.ec` ya existía
en el repositorio antes de esta ronda (identidad `jalavaa-dev`, ver la tabla histórica más abajo), así
que la correspondencia persona↔identidad es verificable con `git shortlog -sne --all`.

## Titularidad por punto

Salida literal de `python scripts/ev4-contribuciones.py`:

{tabla}
La columna **Archivos de evidencia** lista los archivos que más commits del punto tocaron, excluyendo
tres que toca casi todo y por eso no distinguen nada (`informe-final.pdf`, `SRS-v1.0.1.pdf` y
`docs/observaciones/OBSERVACIONES.md`). **Total archivos** es el recuento completo, sin excluir.
Para ver la lista entera de un punto:

```bash
git show --pretty= --name-only <sha>
```

**EV-3 no aparece** porque ningún commit lo nombra en el asunto: se cerró declarando la URL pública ya
existente en `README.md`, sin cambio de código. La revisión del 18-sep lo da por cumplido.

### Commits sin punto declarado ({n_sueltos} de {total})

Se listan en vez de repartirlos a ojo entre los puntos, que es exactamente el tipo de atribución que el
historial no respaldaría:

| Commit | Asunto |
|---|---|
{sueltos_filas}

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
| Álava Alvarado, Jean Pierre | `jalavaa@uteq.edu.ec` | Autor de los {total} commits del tramo | ____________________ |
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
"""

io.open("CONTRIBUCIONES.md", "w", encoding="utf-8", newline="").write(DOC)
print("escrito, commits =", total)
