# Contribuciones por punto — examen suspenso (2026-09-17)

**Propósito:** este archivo es el entregable EV-4 que pidió la evaluación integral del ingeniero
(2026-09-17): *"titularidad declarada por punto"*, distinto de
[`CONTRIBUTORS.md`](CONTRIBUTORS.md) (roles CRediT generales de todo el proyecto, sin desglose por
punto de esta ronda de revisión). **Metodología: cada fila sale de `git log`, no de lo que cada
integrante recuerda o reporta** — el propio ing señaló que "un punto atribuido en CONTRIBUCIONES.md que
el historial no respalda no cuenta", así que esto se generó al revés: primero el historial, después la
atribución, nunca al contrario.

## Autoría de los cierres de esta ronda (P1–P12)

Todos los commits de esta ronda (desde `f3d1ff4`, el commit que revisó la guía original, hasta hoy)
son de **Álava Alvarado, Jean Pierre** (`Jean30042 <jeanalavaalavarado@gmail.com>`). Verificado con:

```bash
git log --format="%H|%an|%ae|%ad|%s" --date=short f3d1ff4..HEAD | cut -d'|' -f2,3 | sort -u
```

Salida real (2026-09-17):
```
Jean30042|jeanalavaalavarado@gmail.com
```

Un único autor, sin excepción, en los 30+ commits de esta ronda.

**Por qué (aclaración P12, evaluación integral 2026-09-17):** este examen suspenso lo está cursando y
sustentando Álava Alvarado en solitario. Los otros tres integrantes originales del equipo (Moncayo
Loor, Zamora Arias, Barreto Rosado) reprobaron la materia en el período regular y no están trabajando
en las observaciones de esta ronda de recuperación — no es que se hayan desentendido de un trabajo que
seguía siendo colectivo, es que la recuperación, tal como está planteada, ya no lo es. Ver también
[`docs/observaciones/BITACORA-COMMITS-2026-09-02.md`](docs/observaciones/BITACORA-COMMITS-2026-09-02.md).

| Punto | Peso | Commit(s) de cierre real | Autor (`git log`) |
|---|---|---|---|
| P1 (SUS) | 1,7 | `f51db75`, `70035fa`, `8488d06` | Álava Alvarado |
| P2 (Cobertura) | 1,4 | `9067cad` | Álava Alvarado |
| P3 (Javadoc) | 1,4 | `41c5bc9` | Álava Alvarado |
| P4 (Nombres en español) | 1,2 | `49adaee` | Álava Alvarado |
| P5 (Lighthouse) | 0,8 | `73ec6c7`, `2161280` | Álava Alvarado |
| P6 (Comparaciones múltiples) | 0,6 | `bd2cc84` (ronda anterior), `201c2fa` (re-verificación) | Álava Alvarado |
| P7 (Pruebas del chatbot) | 0,8 | `bd2cc84` (ronda anterior), `b99bb72` (re-verificación) | Álava Alvarado |
| P8 (Autorización) | 0,6 | `bd2cc84` (ronda anterior), `40426b3` (fix real) | Álava Alvarado |
| P9 (Etiqueta) | 0,4 | `8b1c1d2` | Álava Alvarado |
| P10 (Carátula) | 0,3 | `bd2cc84` (retiro del recuadro), `85c13cf` | Álava Alvarado |
| P11 (Cifras únicas) | 0,4 | `bd2cc84` (ronda anterior), `6282d50` (limpieza real) | Álava Alvarado |
| P12 (Historial) | 0,4 | `28276f9` (re-verificación; sigue 🟡 parcial) | Álava Alvarado |

**No se declara ningún punto como trabajo colectivo de esta ronda porque el historial no lo respalda.**
Esto no es una afirmación sobre quién entendió o decidió qué —los cuatro integrantes participaron en
fases anteriores del proyecto, ver la sección siguiente— es literalmente quién ejecutó los commits que
cierran cada punto de esta revisión puntual, que es lo único que un `git log` puede verificar.

## Contribución histórica al proyecto completo (los 347 commits)

Desglose real por identidad de Git, `git shortlog -sne --all`, con las identidades múltiples de la misma
persona agrupadas (ver [`CONTRIBUTORS.md`](CONTRIBUTORS.md) para la tabla de correspondencia
identidad↔persona, ya documentada ahí):

| Integrante | Identidades de Git | Commits |
|---|---|---|
| Álava Alvarado, Jean Pierre | `Jean30042 <jeanalavaalavarado@gmail.com>`, `jalavaa-dev <jalavaa@uteq.edu.ec>` | 174 |
| Zamora Arias, Carla Esthefania | `carla22072004 <czamoraa5@uteq.edu.ec>`, `Carla Esthefania Zamora Arias <czamoraa5@uteq.edu.ec>` | 137 |
| Barreto Rosado, Heider Dominick | `dominick1245 <dominickelyolo@gmail.com>`, `dominick1245 <144386724+dominick1245@users.noreply.github.com>` | 45 |
| Moncayo Loor, Xavier Alejandro | `XAML25 <xavierloor52@gmail.com>` | 13 |

Total: 369 commits en las cuatro identidades agrupadas (347 commits únicos en `HEAD`; la diferencia
sale de que `git shortlog --all` cuenta también commits que solo existen en ramas/reflog no fusionadas
a `main`, no de contarlos dos veces en el mismo historial).

## Nota honesta sobre la concentración de esta ronda

El ingeniero señaló en su evaluación integral que los 27 commits posteriores a la guía son de una sola
persona, y que `OBSERVACIONES.md` (OBS-26) registra que un asistente automatizado se negó dos veces a
marcar P12 como cerrado cuando se le pidió. Ambas observaciones son correctas y se confirman con este
mismo archivo: la ronda de revisión de esta guía la ejecutó Álava Alvarado usando un asistente de IA
(Claude, ver la declaración de uso de IA en `Informe-Final/secciones/15-declaraciones.tex`), sin
commits de los otros tres integrantes en este tramo específico. Esto no cambia la titularidad
intelectual del sistema completo (ver la tabla CRediT de `CONTRIBUTORS.md`, que sí refleja el trabajo
de los cuatro a lo largo de todo el proyecto), pero sí es la respuesta honesta a "quién hizo el cierre
de esta guía": una persona, con asistencia de IA, verificando contra el repositorio real en cada punto.
