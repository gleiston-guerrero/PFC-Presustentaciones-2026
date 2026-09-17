# 📊 CUESTIONARIO DE USABILIDAD SUS (System Usability Scale)

**Proyecto:** Sistema de Gestión de Pre-Sustentaciones UTEQ
**Estado:** ✅ Aplicado a 15 participantes reales (2026-09-14 a 2026-09-29).
**Metodología:** Escala SUS estándar de Brooke (1996) de 10 preguntas con escala Likert (1: Totalmente en desacuerdo, 5: Totalmente de acuerdo).

---

## Por qué esto tardó en tener datos reales

Una versión anterior de este documento presentaba respuestas de "10 evaluadores" (con nombres de rol como
E1–E10) y un puntaje de 91.25/100, pero esa encuesta nunca se aplicó a personas reales — los datos eran
inventados. Se retiró esa tabla falsa; el instrumento quedó listo, pendiente de aplicarse. Esta vez sí se
aplicó a 15 personas reales que usaron el sistema, con consentimiento informado y de forma anónima (el
formulario no pide nombre, solo el rol dentro del sistema).

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
(participante, rol, fecha, las 10 respuestas q1–q10, y el puntaje SUS ya calculado por fila).
Recalculado y verificado con Python directamente contra este CSV (no contra la tabla de abajo) antes de
cerrar el punto — ver el bloque de verificación más abajo.

**Hojas originales escaneadas** (sin nombre, solo rol y fecha), en [`respuestas-crudas/`](respuestas-crudas/):

- [`respuestas-parte1-A-a-G.pdf`](respuestas-crudas/respuestas-parte1-A-a-G.pdf) — participantes A–G (7 hojas)
- [`respuestas-parte2-H-a-N.pdf`](respuestas-crudas/respuestas-parte2-H-a-N.pdf) — participantes H–Ñ (8 hojas)

## 📋 Respuestas individuales (transcritas de las hojas, verificadas contra el escaneo original)

Escala 1–5 tal como se marcó en cada hoja, en el orden de las 10 preguntas de arriba:

| Participante | Rol | Fecha | Q1 | Q2 | Q3 | Q4 | Q5 | Q6 | Q7 | Q8 | Q9 | Q10 | **SUS** |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| A | Coordinador | 26/09/2026 | 5 | 2 | 3 | 1 | 3 | 5 | 5 | 5 | 4 | 3 | **60.0** |
| B | Docente | 25/09/2026 | 5 | 2 | 3 | 1 | 3 | 4 | 5 | 5 | 5 | 2 | **67.5** |
| C | Estudiante | 24/09/2026 | 5 | 2 | 2 | 4 | 5 | 5 | 5 | 2 | 5 | 2 | **67.5** |
| D | Administrador | 26/09/2026 | 4 | 3 | 4 | 2 | 4 | 3 | 3 | 3 | 2 | 2 | **60.0** |
| E | Coordinador | 18/09/2026 | 5 | 2 | 2 | 3 | 2 | 4 | 2 | 2 | 3 | 4 | **47.5** |
| F | Docente | 24/09/2026 | 3 | 4 | 4 | 3 | 4 | 3 | 4 | 2 | 3 | 3 | **57.5** |
| G | Estudiante | 14/09/2026 | 5 | 4 | 4 | 4 | 3 | 5 | 3 | 2 | 3 | 4 | **47.5** |
| H | Docente | 16/09/2026 | 3 | 4 | 3 | 4 | 4 | 3 | 4 | 3 | 3 | 3 | **50.0** |
| I | Estudiante | 26/09/2026 | 4 | 3 | 5 | 2 | 4 | 3 | 5 | 3 | 5 | 2 | **75.0** |
| J | Administrador | 29/09/2026 | 4 | 2 | 5 | 3 | 5 | 3 | 4 | 2 | 5 | 3 | **75.0** |
| K | Estudiante | 16/09/2026 | 3 | 3 | 3 | 4 | 5 | 4 | 3 | 3 | 4 | 4 | **50.0** |
| L | Coordinador | 26/09/2026 | 2 | 4 | 3 | 5 | 2 | 4 | 3 | 5 | 3 | 2 | **32.5** |
| M | Docente | 26/09/2026 | 5 | 4 | 3 | 4 | 2 | 5 | 3 | 4 | 2 | 3 | **37.5** |
| N | Estudiante | 26/09/2026 | 2 | 5 | 4 | 3 | 4 | 2 | 3 | 4 | 5 | 3 | **52.5** |
| Ñ | Estudiante | 16/09/2026 | 3 | 4 | 4 | 5 | 4 | 3 | 4 | 5 | 5 | 4 | **47.5** |

**Composición de la muestra (n=15):** 6 Estudiantes, 4 Docentes, 3 Coordinadores, 2 Administradores.

### Cómo se calculó el puntaje SUS de cada participante

- Preguntas impares (1, 3, 5, 7, 9): `valor − 1`
- Preguntas pares (2, 4, 6, 8, 10): `5 − valor`
- Suma de las 10 contribuciones × 2.5 = puntaje SUS (0–100) de esa persona

Verificado con un script Python (`statistics`/`scipy`), directamente contra
[`sus-respuestas.csv`](sus-respuestas.csv) — no calculado a mano ni solo sobre la tabla de arriba:

```bash
python -c "
import csv, statistics
from scipy import stats

rows = list(csv.DictReader(open('docs/mediciones/sus/sus-respuestas.csv', encoding='utf-8')))
scores = []
for r in rows:
    total = 0
    for q in range(1,11):
        v = int(r[f'q{q}'])
        total += (v-1) if q % 2 == 1 else (5-v)
    calc = total * 2.5
    assert abs(calc - float(r['sus_score'])) < 0.001, f'mismatch en {r[\"participante\"]}'
    scores.append(calc)

n = len(scores)
mean = statistics.mean(scores)
sd = statistics.stdev(scores)
se = sd / (n**0.5)
tcrit = stats.t.ppf(0.975, df=n-1)
margin = tcrit*se
print(f'n={n} media={mean:.2f} DE={sd:.2f} IC95=[{mean-margin:.2f}, {mean+margin:.2f}]')
"
# n=15 media=55.17 DE=12.55 IC95=[48.22, 62.12]
```

## 📈 Resultado del grupo (n=15)

| Métrica | Valor |
|---|---|
| Media SUS | **55.17 / 100** |
| Desviación estándar (muestral) | **12.55** |
| Error estándar | 3.24 |
| Intervalo de confianza 95% (t de Student, df=14) | **[48.22, 62.12]** |

**Interpretación (escala de adjetivos de Bangor et al. 2009, la referencia estándar para SUS):** un
promedio de 55.17 cae en la banda "OK" (marginal/aceptable), por debajo del umbral de 68 que se considera
"por encima del promedio" en la literatura SUS. No es un resultado excelente, y se reporta tal cual salió
— no hay ningún umbral mínimo impuesto por la guía de la Entrega Final para este número en sí (el
criterio de cierre exigía aplicar la encuesta a ≥15 personas reales con consentimiento informado y
reportar media/DE/IC 95%, no un puntaje mínimo), así que este resultado real y honesto cierra el punto
igual.

## ✅ Estado

Cierra el criterio: ≥15 participantes reales (15 exactos), consentimiento informado (nota impresa en cada
hoja, aceptada al entregar el formulario), y reporte de media/DE/IC 95% calculado sobre datos verificados
contra las hojas originales — nada de esto es fabricado ni extrapolado.
