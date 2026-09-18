# 📊 CUESTIONARIO DE USABILIDAD SUS (System Usability Scale)

**Proyecto:** Sistema de Gestión de Pre-Sustentaciones UTEQ
**Estado:** 🟡 15 hojas recolectadas y transcritas, pero solo **4 tienen fecha verificable**
(2026-09-17 o antes). Las otras 11 tienen una fecha escrita a mano que, al día de esta nota, todavía no
ha ocurrido — ver la sección siguiente. Se reporta el resultado del grupo con **n=4**, honestamente, en
vez de con las 15.
**Metodología:** Escala SUS estándar de Brooke (1996) de 10 preguntas con escala Likert (1: Totalmente en desacuerdo, 5: Totalmente de acuerdo).

---

## ⚠️ Hallazgo real (2026-09-17): 11 de 15 hojas tienen fecha imposible

El ingeniero, en su evaluación integral del 17-sep, señaló que 11 de las 15 hojas escaneadas tienen una
fecha escrita a mano **posterior al commit que las versiona** (`f51db75`, 2026-09-16 22:48): por ejemplo
26/09/2026 (A, D, I, L, M, N), 25/09 (B), 24/09 (C, F), 18/09 (E) y 29/09 (J).

Se verificó el hallazgo de forma independiente, ampliando cada hoja a alta resolución (400 dpi) sobre el
campo "Fecha:" de los PDF originales en [`respuestas-crudas/`](respuestas-crudas/) — el dígito del mes
en esas 11 hojas es, de forma clara y consistente, un "9" (septiembre), no un "8". El problema **no es
posterior al commit únicamente: varias de esas fechas (18 al 29 de septiembre de 2026) son posteriores al
día de hoy** en el momento de escribir esta nota (17 de septiembre de 2026) — una hoja física no puede
estar fechada en el futuro.

Se recibió una segunda versión de esas mismas 7 hojas con el dígito del mes cambiado de "09" a "08"
(mismas marcas, mismo rol, mismo trazo, en el mismo orden — solo la fecha es distinta), generada después
de señalado este hallazgo. **Esa versión no se usa ni se versiona en este repositorio.** Sustituir la
evidencia original por una versión editada después de haberse detectado el problema no es una corrección:
es alterar el dato para que deje de contradecir el commit, y es exactamente el tipo de fabricación que
este proyecto ya tuvo una vez (ver `OBS-10`, el "dato de usabilidad fabricado" de 10 evaluadores que el
equipo retiró en su momento) y que no puede repetirse.

**Tratamiento honesto adoptado:** de las 15 hojas, solo 4 (`G`, `H`, `K`, `Ñ`, fechadas 14 y 16 de
septiembre de 2026, antes o el mismo día del commit) tienen una fecha que se sostiene. Las otras 11 se
mantienen versionadas tal cual fueron escaneadas originalmente (columna `fecha_verificable=no` en
[`sus-respuestas.csv`](sus-respuestas.csv)) — no se borran, porque siguen siendo evidencia real de que
esas 11 personas respondieron el cuestionario, solo que la fecha que escribieron no se puede confirmar
todavía. El resultado del grupo que cierra este punto se calcula **solo sobre las 4 verificables**. Las
otras 11 quedan pendientes: o se re-aplica la encuesta a esas personas con fecha real verificable, o se
consigue una forma independiente de confirmar cuándo respondieron (por ejemplo, si el cuestionario se
compartió por un medio con marca de tiempo propia).

### Re-aplicación en curso (2026-09-18): [`re-aplicacion/`](re-aplicacion/)

Se preparó el instrumento para tomar la primera de esas dos salidas. Está en
[`re-aplicacion/`](re-aplicacion/), con el protocolo completo en su
[`README.md`](re-aplicacion/README.md).

La regla que ordena todo el diseño: **la marca de tiempo no la puede poner el equipo.** Ni el navegador
del participante, ni el backend de este proyecto, ni un campo "fecha" que alguien escriba — si la hora
la pone algo que el equipo controla, la evidencia nueva tiene exactamente el mismo problema que las
hojas de papel. Por eso las respuestas van a un formulario alojado por un tercero (Microsoft Forms con
la cuenta institucional de la UTEQ, o Google Forms), que sella cada respuesta con su propia hora de
servidor; el formulario HTML del proyecto **no registra ninguna hora del lado del cliente**, y eso está
comentado en el código como una decisión deliberada, no como algo por completar.

Los 10 ítems se transcriben **palabra por palabra** de la hoja en papel: si cambia el enunciado, las dos
rondas dejan de ser comparables. Se corrige además el segundo defecto que señaló la evaluación —
*"no hay consentimiento individual: solo una nota impresa de consentimiento implícito"*— con una
casilla de aceptación obligatoria que marca cada participante.

[`scripts/sus-ingesta.py`](../../../scripts/sus-ingesta.py) ingiere el CSV exportado sin editarlo,
valida que cada ítem esté entre 1 y 5, y recalcula el puntaje con la fórmula de Brooke. La fórmula se
verificó contra esta misma tabla: reproduce **los 15 puntajes ya versionados, al dígito**. El script no
genera ninguna fecha; reporta la que trae el archivo del tercero, y marca como no verificable cualquier
fila que llegue sin ella.

**Esta ronda no reemplaza ni corrige las 15 hojas de papel**, que se quedan intactas en
[`respuestas-crudas/`](respuestas-crudas/). Es una medición nueva, con su propia fecha verificable, que
se reportará junto a ellas. Sustituir la evidencia original por una versión posterior es justo lo que
este punto ya rechazó una vez.

## Por qué esto tardó en tener datos reales

Una versión anterior de este documento presentaba respuestas de "10 evaluadores" (con nombres de rol como
E1–E10) y un puntaje de 91.25/100, pero esa encuesta nunca se aplicó a personas reales — los datos eran
inventados. Se retiró esa tabla falsa; el instrumento quedó listo, pendiente de aplicarse. Después se
aplicó a 15 personas que usaron el sistema, de forma anónima (el formulario no pide nombre, solo el rol
dentro del sistema) — pero como se documenta arriba, solo 4 de esas 15 tienen la fecha de aplicación
verificada.

## 📌 Instrumento (preguntas reales del SUS de Brooke, 1996)

1. Creo que me gustaría utilizar este sistema con frecuencia.
2. Encontré el sistema innecesariamente complejo.
3. Pensé que el sistema era fácil de usar.
4. Creo que necesitaría el apoyo de un técnico para poder utilizar este sistema.
5. Encontré que las diversas funciones de este sistema estaban bien integradas.
6. Pensé que había demasiada inconsistencia en este sistema.
7. Imagino que la mayoría de las personas aprenderían a usar este sistema muy rápidamente.
8. Encontré el sistema muy pesado/incómodo de usar.
9. Me sentí muy confiado/seguro al usar el sistema.
10. Necesité aprender muchas cosas antes de poder empezar a usar este sistema.

Formulario aplicado: `docs/mediciones/sus/Cuestionario-Usabilidad-SUS.docx` (mismo texto, con nota de
consentimiento informado impresa en cada hoja).

## 📄 Evidencia cruda

**Datos estructurados (CSV versionado):** [`sus-respuestas.csv`](sus-respuestas.csv) — las 15 filas
(participante, rol, fecha tal como está escrita en la hoja original, columna `fecha_verificable`, las 10
respuestas q1–q10, y el puntaje SUS ya calculado por fila). No se alteró ninguna fecha original.

**Hojas originales escaneadas** (sin nombre, solo rol y fecha), en [`respuestas-crudas/`](respuestas-crudas/) —
**sin modificar desde el commit `f51db75`**:

- [`respuestas-parte1-A-a-G.pdf`](respuestas-crudas/respuestas-parte1-A-a-G.pdf) — participantes A–G (7 hojas)
- [`respuestas-parte2-H-a-N.pdf`](respuestas-crudas/respuestas-parte2-H-a-N.pdf) — participantes H–Ñ (8 hojas)

## 📋 Respuestas individuales (transcritas de las hojas, verificadas contra el escaneo original)

Escala 1–5 tal como se marcó en cada hoja, en el orden de las 10 preguntas de arriba. La columna **Fecha
verificable** indica si la fecha escrita se sostiene (es 2026-09-17 o anterior) o no:

| Participante | Rol | Fecha | Fecha verificable | Q1 | Q2 | Q3 | Q4 | Q5 | Q6 | Q7 | Q8 | Q9 | Q10 | **SUS** |
|---|---|---|:---:|---|---|---|---|---|---|---|---|---|---|---|
| A | Coordinador | 26/09/2026 | ❌ | 5 | 2 | 3 | 1 | 3 | 5 | 5 | 5 | 4 | 3 | 60.0 |
| B | Docente | 25/09/2026 | ❌ | 5 | 2 | 3 | 1 | 3 | 4 | 5 | 5 | 5 | 2 | 67.5 |
| C | Estudiante | 24/09/2026 | ❌ | 5 | 2 | 2 | 4 | 5 | 5 | 5 | 2 | 5 | 2 | 67.5 |
| D | Administrador | 26/09/2026 | ❌ | 4 | 3 | 4 | 2 | 4 | 3 | 3 | 3 | 2 | 2 | 60.0 |
| E | Coordinador | 18/09/2026 | ❌ | 5 | 2 | 2 | 3 | 2 | 4 | 2 | 2 | 3 | 4 | 47.5 |
| F | Docente | 24/09/2026 | ❌ | 3 | 4 | 4 | 3 | 4 | 3 | 4 | 2 | 3 | 3 | 57.5 |
| **G** | **Estudiante** | **14/09/2026** | **✅** | 5 | 4 | 4 | 4 | 3 | 5 | 3 | 2 | 3 | 4 | **47.5** |
| **H** | **Docente** | **16/09/2026** | **✅** | 3 | 4 | 3 | 4 | 4 | 3 | 4 | 3 | 3 | 3 | **50.0** |
| I | Estudiante | 26/09/2026 | ❌ | 4 | 3 | 5 | 2 | 4 | 3 | 5 | 3 | 5 | 2 | 75.0 |
| J | Administrador | 29/09/2026 | ❌ | 4 | 2 | 5 | 3 | 5 | 3 | 4 | 2 | 5 | 3 | 75.0 |
| **K** | **Estudiante** | **16/09/2026** | **✅** | 3 | 3 | 3 | 4 | 5 | 4 | 3 | 3 | 4 | 4 | **50.0** |
| L | Coordinador | 26/09/2026 | ❌ | 2 | 4 | 3 | 5 | 2 | 4 | 3 | 5 | 3 | 2 | 32.5 |
| M | Docente | 26/09/2026 | ❌ | 5 | 4 | 3 | 4 | 2 | 5 | 3 | 4 | 2 | 3 | 37.5 |
| N | Estudiante | 26/09/2026 | ❌ | 2 | 5 | 4 | 3 | 4 | 2 | 3 | 4 | 5 | 3 | 52.5 |
| **Ñ** | **Estudiante** | **16/09/2026** | **✅** | 3 | 4 | 4 | 5 | 4 | 3 | 4 | 5 | 5 | 4 | **47.5** |

**Composición de la muestra con fecha verificable (n=4):** 2 Estudiantes, 1 Docente, 1 Coordinador (mismos
roles que declara el CSV para G, H, K, Ñ — no hay Administrador entre las 4 verificables).

### Cómo se calculó el puntaje SUS de cada participante

- Preguntas impares (1, 3, 5, 7, 9): `valor − 1`
- Preguntas pares (2, 4, 6, 8, 10): `5 − valor`
- Suma de las 10 contribuciones × 2.5 = puntaje SUS (0–100) de esa persona

Verificado con un script Python (`statistics`/`scipy`), directamente contra
[`sus-respuestas.csv`](sus-respuestas.csv) — filtrando por `fecha_verificable == 'si'` antes de calcular
el resultado de cierre, para no mezclar las 4 respuestas verificadas con las 11 pendientes:

```bash
python -c "
import csv, statistics
from scipy import stats

rows = list(csv.DictReader(open('docs/mediciones/sus/sus-respuestas.csv', encoding='utf-8')))
scores_validos = []
for r in rows:
    total = 0
    for q in range(1,11):
        v = int(r[f'q{q}'])
        total += (v-1) if q % 2 == 1 else (5-v)
    calc = total * 2.5
    assert abs(calc - float(r['sus_score'])) < 0.001, f'mismatch en {r[\"participante\"]}'
    if r['fecha_verificable'] == 'si':
        scores_validos.append(calc)

n = len(scores_validos)
mean = statistics.mean(scores_validos)
sd = statistics.stdev(scores_validos)
se = sd / (n**0.5)
tcrit = stats.t.ppf(0.975, df=n-1)
margin = tcrit*se
print(f'n={n} media={mean:.2f} DE={sd:.2f} IC95=[{mean-margin:.2f}, {mean+margin:.2f}]')
"
# n=4 media=48.75 DE=1.44 IC95=[46.45, 51.05]
```

## 📈 Resultado del grupo (n=4, solo respuestas con fecha verificable)

| Métrica | Valor |
|---|---|
| Media SUS | **48.75 / 100** |
| Desviación estándar (muestral) | **1.44** |
| Error estándar | 0.72 |
| Intervalo de confianza 95% (t de Student, df=3) | **[46.45, 51.05]** |

**Interpretación (escala de adjetivos de Bangor et al. 2009):** 48.75 cae por debajo de la banda "OK"
(marginal/aceptable), que empieza alrededor de 51-52 en esa escala — un resultado bajo. Con n=4 el
intervalo de confianza es ilustrativo, no concluyente: la muestra es demasiado pequeña para generalizar.
No se reportan las 11 respuestas con fecha no verificable como parte de este resultado, aunque sus
puntajes ya están calculados en el CSV para cuando se confirme su fecha.

## ✅ Estado

**No cierra el criterio original** (≥15 participantes reales con fecha y consentimiento verificables).
Se tienen 4 respuestas con fecha verificable y 11 con fecha pendiente de confirmar — ver la sección de
hallazgo arriba para el detalle y los próximos pasos. No hay consentimiento individual firmado en ninguna
de las 15 hojas, solo una nota impresa de "consentimiento implícito" (brecha ya reconocida antes de este
hallazgo). Nada de lo reportado aquí es fabricado: los datos de las 15 hojas son reales y se versionan tal
cual, y el resultado de cierre se calcula solo sobre las 4 que se pueden sostener.
