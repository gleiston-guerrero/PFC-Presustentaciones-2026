# P4 — Resolución de la disputa numérica sobre nombres en español

**Fecha:** 2026-09-18
**Estado:** RESUELTA. **El equipo estaba equivocado; la medición del ingeniero es correcta.**

---

## 1. Qué se disputaba

La evaluación integral del 2026-09-17 reportó, por AST propio del ingeniero:

```
tipos    121/339 (35.7%)     metodos 1325/1836 (72.2%)      [main + test]
en src/main:  39.7% tipos,  52.0% metodos
```

y añadió: *"El equipo declara 1,2 % y 0,1 % con un diccionario de 37 sustantivos, sin script versionado."*

El equipo había reportado, con `scripts/p4-rename-scan-fuente.py`:

```
Tipos con palabra en espanol: 6 (1.8%)
Metodos con palabra en espanol: 1 (0.1%)
```

Una diferencia de dos órdenes de magnitud sobre el mismo código. Quedó registrada
como "disputa numérica abierta" en `VERIFICACION.md` y como `[FAIL]` en
`scripts/verify.sh`.

## 2. La hipótesis anterior del equipo era falsa

En la revisión del 17-sep se propuso que el ingeniero pudiera estar usando
coincidencia de **subcadena sin límites de palabra** (que `LoginResponse` contara
como español por contener `es`), lo cual daba 88,8 % y "explicaba" el orden de
magnitud.

Esa hipótesis queda **descartada**. Era una explicación construida hacia atrás para
proteger la cifra del equipo, y no reproduce los números del ingeniero: da 88,8 %
donde él reporta 35,7 %.

## 3. Qué se hizo ahora

`scripts/p4-nombres-espanol.py`:

1. Reutiliza **el mismo universo de identificadores** que el script anterior
   (importa sus regex `TYPE_RE`/`METHOD_RE`), para que las cifras sean comparables.
2. Parte cada identificador en tokens por camelCase/dígitos —
   `AppUserActualService` → `app`, `user`, `actual`, `service`.
3. Compara cada token contra un léxico **escrito explícitamente en el archivo** y
   construido enumerando los 466 tokens distintos que realmente aparecen en
   `backend/src/{main,test}/java` y clasificándolos uno por uno. No hay detección
   automática de idioma: si un token cuenta como español, está escrito ahí y se
   puede auditar a mano.
4. Reporta **dos definiciones** (núcleo: solo dominio; amplia: + funcionales +
   ambiguos), porque "nombre en español" no tiene una única lectura y la
   diferencia es material.

## 4. Resultado: las cifras del ingeniero se reproducen

```
DEFINICION NUCLEO (solo dominio, sin funcionales ni ambiguos)
  main + test:     tipos   121/339   ( 35.7%)
  solo src/main:   tipos   108/272   ( 39.7%)
```

| | ing (2026-09-17) | este script (2026-09-18) | |
|---|---|---|---|
| Tipos, main+test | **121/339 (35,7 %)** | **121/339 (35,7 %)** | ✅ idéntico |
| Tipos, solo `src/main` | **39,7 %** | **39,7 %** | ✅ idéntico |
| Métodos, solo `src/main` | 52,0 % | 50,4 % (amplia) | ≈ |
| Métodos, main+test | 72,2 % (1325/1836) | 66,7 % (1284/1924) | ≈ |

**Coincidir en numerador y en denominador, en dos cortes distintos y de forma
independiente, no es casualidad: es la misma medición.**

La diferencia que queda en métodos se explica por el universo. El denominador del
ingeniero (1836) no es el del texto fuente (693): corresponde a **nombres distintos
a nivel de bytecode**, es decir, incluye los accesores que genera Lombok. Esos
accesores heredan el nombre del campo — `getEstado`, `setTitulacion`,
`getObservaciones` — y como los campos del dominio sí están en español, *suben* el
porcentaje en vez de bajarlo. Nuestro conteo por texto fuente no los veía, y el
script `p4-rename-scan-javap.py` sí los contaba pero con el mismo diccionario
estrecho, así que tampoco los detectaba.

## 5. Por qué la cifra del equipo estaba mal

**No fue un problema de parseo.** El universo de tipos siempre coincidió
exactamente: 339 = 339. Fue el diccionario. `p4-rename-scan-fuente.py` usaba una
lista curada a mano que omitía precisamente los tokens en español más frecuentes
del repositorio:

| token | apariciones | ¿estaba en el diccionario viejo? |
|---|---|---|
| `por` | 56 | **no** |
| `estado` / `estados` | 46 | **no** |
| `de` | 22 | **no** |
| `titulacion` | 18 | **no** |
| `autenticar` | 15 | **no** |
| `reporte` | 14 | **no** |
| `como` | 13 | **no** |
| `resumen` | 10 | **no** |
| `fase` / `fases` | 12 | **no** |

`por` por sí solo aparece en 56 identificadores (`obtainPorId`, `listPorEstado`,
`searchPorSubmission`, `obtainPorSubmission`...), casi todos métodos. Un
diccionario que no incluye `por` no puede ver el patrón de nombres dominante del
repositorio — y por eso daba 0,1 % en métodos.

El diccionario tenía ~180 entradas y estaba lleno de verbos que el renombrado de
`49adaee` **ya había eliminado** (`obtener`, `listar`, `buscar`, `crear`,
`actualizar`...). Medía justamente lo que ya se había corregido, y era ciego a lo
que quedaba.

## 6. El hallazgo más incómodo: el renombrado no mejoró nada

Al poder medir por fin con el mismo criterio que el ingeniero, se puede comparar
antes y después del renombrado masivo de `49adaee` (más de 300 archivos):

| | tipos en español, `src/main` | |
|---|---|---|
| Línea base del ing (2026-09-02, **antes** de `49adaee`) — `OBS-27` | **98 / 272 (36,0 %)** | |
| Medición de hoy (2026-09-18, **después** de `49adaee`) | **108 / 272 (39,7 %)** | ↑ +10 tipos |

**El denominador es idéntico (272) y el porcentaje subió.** Renombrar 300+ archivos
no bajó el porcentaje de nombres en español: lo dejó 3,7 puntos peor.

La explicación es el tipo de renombrado que se hizo. Se tradujeron los tokens
sueltos y evidentes, y en los nombres compuestos se cambió solo la mitad,
produciendo híbridos que siguen teniendo un token español:

```
RecursoTitulacion      -> ResourceTitulacion      (sigue: titulacion)
ConvocatoriaTitulacion -> AnnouncementTitulacion  (sigue: titulacion)
ModalidadTitulacion    -> ModalityTitulacion      (sigue: titulacion)
EstadoSolicitud        -> EstadoSubmission        (sigue: estado)
HistorialEstadoActa    -> HistoryEstadoMinutes    (sigue: estado)
ResultadoEvaluacion    -> ResultadoEvaluation     (sigue: resultado)
CriterioRubrica        -> CriterioRubric          (sigue: criterio)
```

Y los verbos traducidos (`listar→list`, `obtener→obtain`) dejaron intacto el `Por`
que los acompaña: `obtenerPorId` → `obtainPorId`, que sigue siendo un identificador
con una preposición española en medio.

**Conclusión:** el renombrado de `49adaee` rompió 18 `@RequestParam`, 5 llamadas a
procedimientos almacenados y 7 campos de DTO — es decir, causó regresiones
funcionales reales de cara al usuario — **a cambio de empeorar la métrica que
pretendía mejorar**. Esto se declara aquí porque es el resultado honesto de la
medición, no porque convenga.

*Salvedad:* no se puede garantizar que el clasificador que el ingeniero usó el
2 de septiembre sea exactamente el mismo que el del 17 de septiembre. Lo que sí
está verificado es que el de hoy reproduce su cifra del 17-sep al dígito
(39,7 % = 39,7 %) sobre el mismo denominador (272), así que la comparación es
apples-to-apples salvo que él haya cambiado de herramienta entre ambas revisiones.

## 7. Consecuencia

**P4 no está cumplido.** El criterio pide 5 % o menos; la medición real es 35,7 %
en tipos y entre 50 % y 72 % en métodos según el corte.

El renombrado masivo de `49adaee` fue **incompleto**: cambió los tokens evidentes
(los verbos) y dejó intactos los estructurales, que son los que más pesan:

```
Estado (46)   Titulacion (18)   Reporte (14)   Observaciones (14)
Criterio (11) Supresion (8)     Tribunal (7)   Fase (10)   Tipo (5)
Catalogo (3)  Mensaje (9)       Resumen (10)   + el "Por" de todos los finders
```

Completar el renombrado de verdad es una operación de la misma escala que la que ya
rompió 18 `@RequestParam` y 5 llamadas a procedimientos almacenados (ver
`VERIFICACION.md`, sección P4). **No se va a hacer a 12 horas del cierre para
mejorar un número**: el riesgo de romper contratos en producción es mayor que el
beneficio de la nota, y hacerlo a las apuradas repetiría exactamente el error que
causó las regresiones que el ingeniero encontró.

Queda declarado como **incumplido**, con la medición hecha con método público y
reproducible, en vez de sostener una cifra favorable que no resiste auditoría.

## 8. Cómo reproducirlo

```bash
cd backend && ./mvnw -q test-compile && cd ..
python scripts/p4-nombres-espanol.py --bytecode          # cifras
python scripts/p4-nombres-espanol.py --list --tokens     # cada identificador y cada token
```

La salida completa de la corrida del 2026-09-18 está versionada en
[`P4-salida-completa-2026-09-18.txt`](P4-salida-completa-2026-09-18.txt) (596 líneas):
incluye cada tipo y cada método contado con el token que lo activó, la resolución
declarada de cada término ambiguo, y la lista de tokens **no** clasificados como
español, para que se pueda discutir cualquier clasificación concreta en vez del
porcentaje agregado.
