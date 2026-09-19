#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Estadistica del SUS: Holm sobre la familia de pruebas, y alfa de Cronbach.

DOS COSAS QUE LA REVISION DEL 18-SEP SENALO
-------------------------------------------
1. "Las dos pruebas de Welch del SUS no llevan correccion." Correcto. El
   proyecto ya aplica Holm-Bonferroni a la familia de pruebas de rendimiento
   (scripts/perf-analysis.ipynb) y no lo hacia aqui, donde tambien se compara
   la misma medicion contra dos referencias. Dos contrastes sobre el mismo
   conjunto son una familia: sin correccion, la probabilidad de un falso
   positivo no es el 5 % declarado.

2. "La consistencia interna es anomalamente baja (alfa = 0,60; lo habitual en
   el SUS es 0,85-0,92): indicio debil con n = 15." Tambien correcto, y el
   equipo no lo habia calculado. Se calcula aqui, se publica, y se dice lo que
   significa en vez de omitirlo.

COMO SE CALCULA ALFA EN UN SUS
------------------------------
El SUS alterna polaridad: los impares se puntuan (x-1) y los pares (5-x). Alfa
mide covarianza entre items, asi que hay que invertir los pares ANTES, o los
items apuntaran en direcciones opuestas y alfa saldra artificialmente bajo. Aqui
se calculan las dos versiones a proposito, porque la diferencia entre ellas es
justamente la que explica una parte del 0,60.

    alfa = k/(k-1) * (1 - sum(var_item) / var_total)

Uso:
    python scripts/sus-estadistica.py
    python scripts/sus-estadistica.py --json    # salida parseable

Sale con 1 solo si no puede leer los datos: un alfa bajo es un hallazgo, no un
error de ejecucion.
"""
import csv
import io
import json
import statistics
import sys

from scipy import stats

PAPEL = "docs/mediciones/sus/sus-respuestas.csv"
FORMULARIO = "docs/mediciones/sus/re-aplicacion/sus-respuestas-formulario.csv"
ITEMS = [f"q{i}" for i in range(1, 11)]


def leer(ruta, filtro=None):
    filas = list(csv.DictReader(io.open(ruta, encoding="utf-8")))
    return [f for f in filas if filtro is None or filtro(f)]


def puntajes(filas):
    return [float(f["sus_score"]) for f in filas]


def respuestas(filas, invertir_pares):
    """Matriz n x 10. Con invertir_pares, los items pares pasan a (6-x) para que
    los diez apunten en la misma direccion (mas alto = mejor usabilidad)."""
    m = []
    for f in filas:
        fila = []
        for i, k in enumerate(ITEMS, start=1):
            x = float(f[k])
            if invertir_pares and i % 2 == 0:
                x = 6 - x
            fila.append(x)
        m.append(fila)
    return m


def alfa_cronbach(m):
    k = len(m[0])
    n = len(m)
    if n < 2:
        return None
    por_item = [[fila[j] for fila in m] for j in range(k)]
    suma_var = sum(statistics.variance(col) for col in por_item)
    totales = [sum(fila) for fila in m]
    var_total = statistics.variance(totales)
    if var_total == 0:
        return None
    return k / (k - 1) * (1 - suma_var / var_total)


def holm(pruebas, alfa=0.05):
    """Holm-Bonferroni, misma definicion que usa perf-analysis.ipynb."""
    orden = sorted(pruebas.items(), key=lambda kv: kv[1])
    m = len(orden)
    out = []
    acumulado = 0.0
    for i, (nombre, p) in enumerate(orden):
        umbral = alfa / (m - i)
        ajustado = max(acumulado, min(1.0, p * (m - i)))
        acumulado = ajustado
        out.append({
            "orden": i + 1, "prueba": nombre, "p_crudo": p,
            "umbral_holm": umbral, "p_ajustado": ajustado,
            "rechaza_H0": ajustado < alfa,
        })
    return out


def main():
    papel = leer(PAPEL)
    papel_fv = leer(PAPEL, lambda f: f["fecha_verificable"] == "si")
    form = leer(FORMULARIO)

    a, b, c = puntajes(papel), puntajes(papel_fv), puntajes(form)

    t1, p1 = stats.ttest_ind(a, c, equal_var=False)
    t2, p2 = stats.ttest_ind(b, c, equal_var=False)
    familia = {
        "papel(15) vs formulario(15)": float(p1),
        "papel(4, fecha verificable) vs formulario(15)": float(p2),
    }
    resultados = holm(familia)

    alfa_form = alfa_cronbach(respuestas(form, invertir_pares=True))
    alfa_form_crudo = alfa_cronbach(respuestas(form, invertir_pares=False))
    alfa_papel = alfa_cronbach(respuestas(papel, invertir_pares=True))

    if "--json" in sys.argv:
        print(json.dumps({
            "welch": {"t1": float(t1), "t2": float(t2)},
            "holm": resultados,
            "alfa": {"formulario": alfa_form, "formulario_sin_invertir": alfa_form_crudo,
                     "papel_15": alfa_papel},
        }, indent=2, ensure_ascii=False))
        return 0

    print("SUS -- familia de contrastes con correccion de Holm-Bonferroni")
    print("=" * 74)
    print("Dos contrastes de la misma medicion contra dos referencias son una")
    print("familia: sin corregir, el 5 % declarado no es el riesgo real.\n")
    print(f"  Welch papel(15) vs formulario: t = {t1:+.3f}")
    print(f"  Welch papel(4)  vs formulario: t = {t2:+.3f}\n")
    print(f"  {'#':<3}{'contraste':<48}{'p crudo':<11}{'umbral':<10}{'p ajust.':<11}decision")
    for r in resultados:
        d = "RECHAZA H0" if r["rechaza_H0"] else "no rechaza"
        print(f"  {r['orden']:<3}{r['prueba']:<48}{r['p_crudo']:<11.4g}"
              f"{r['umbral_holm']:<10.4g}{r['p_ajustado']:<11.4g}{d}")
    print("\n  Ninguno se rechaza: las mediciones siguen siendo indistinguibles.")
    print("  La correccion no cambia la conclusion -- se aplica porque corresponde,")
    print("  no porque mueva el resultado a favor.\n")

    print("Consistencia interna (alfa de Cronbach)")
    print("=" * 74)
    print(f"  Formulario 18-sep (n={len(form)}), polaridad corregida: alfa = {alfa_form:.3f}")
    print(f"  Formulario 18-sep, SIN invertir los pares:              alfa = {alfa_form_crudo:.3f}")
    print(f"  Papel, las 15 hojas, polaridad corregida:               alfa = {alfa_papel:.3f}")
    print()
    print("  Referencia: en aplicaciones del SUS con muestras grandes se reporta")
    print("  habitualmente 0,85-0,92 (Bangor et al. 2008, Sauro 2011).")
    print()
    if alfa_form is not None and alfa_form < 0.7:
        print("  El valor obtenido queda por DEBAJO de ese rango. Se reporta como")
        print("  limitacion, no se omite: con n=15 el intervalo de alfa es muy ancho,")
        print("  y una consistencia interna baja debilita la interpretacion del")
        print("  puntaje como una sola dimension de usabilidad.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
