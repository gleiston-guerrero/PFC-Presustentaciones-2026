#!/usr/bin/env python3
"""Ingiere el CSV exportado del formulario SUS y recalcula el puntaje de Brooke.

QUE PROBLEMA RESUELVE
---------------------
La evaluacion del 2026-09-17 acepta el calculo del SUS y rechaza la fecha: 11 de
las 15 hojas en papel llevan una fecha escrita a mano posterior al commit que las
versiona. La salida pedida es re-aplicar el instrumento con una fecha que pueda
verificar un tercero.

Este script cierra ese circuito. Lee el CSV tal como lo exporta el formulario
(Microsoft Forms o Google Forms), que trae la marca de tiempo del servidor de ese
tercero, y produce el CSV derivado con el puntaje recalculado.

LO QUE NO HACE, A PROPOSITO
---------------------------
- No inventa ni completa fechas. La fecha que reporta es, literalmente, la que
  viene en el archivo del tercero. Si una fila no la trae, se marca y no se
  cuenta como verificable.
- No escribe la hora del reloj local en ninguna parte. Una hora que pone el
  propio equipo es justo lo que no sirve como evidencia.
- No toca `sus-respuestas.csv` ni las hojas de `respuestas-crudas/`. La ronda en
  papel se conserva intacta; esta es una medicion nueva que se reporta junto a
  ella, no un reemplazo.

FORMULA (Brooke, 1996)
----------------------
    impares (1,3,5,7,9):  valor - 1
    pares   (2,4,6,8,10): 5 - valor
    SUS = (suma de las 10 contribuciones) * 2.5      -> rango 0-100

Uso:
    python scripts/sus-ingesta.py <csv-exportado> [--salida <csv>]
"""
import argparse
import csv
import datetime as dt
import os
import re
import statistics
import sys
import unicodedata

# Los 10 enunciados, en orden. Se usan para reconocer las columnas del CSV
# exportado, cuyo encabezado es el texto completo de la pregunta.
ITEMS = [
    "me gustaria utilizar este sistema con frecuencia",
    "innecesariamente complejo",
    "el sistema era facil de usar",
    "apoyo de un tecnico",
    "bien integradas",
    "demasiada inconsistencia",
    "aprenderian a usar este sistema muy rapidamente",
    "pesado/incomodo de usar",
    "confiado/seguro al usar el sistema",
    "aprender muchas cosas antes de poder empezar",
]


def norm(s):
    """Minusculas y sin acentos, para comparar encabezados sin depender de como
    los haya escrito la plataforma."""
    s = unicodedata.normalize("NFD", (s or "").lower())
    return "".join(c for c in s if unicodedata.category(c) != "Mn")


def sus(respuestas):
    """Puntaje de Brooke a partir de los 10 valores 1-5, en orden."""
    total = 0
    for i, v in enumerate(respuestas, start=1):
        total += (v - 1) if i % 2 else (5 - v)
    return total * 2.5


def localizar(encabezados):
    """Devuelve (indice_fecha, indice_rol, indice_previo, [10 indices de item])."""
    n = [norm(h) for h in encabezados]

    def buscar(*claves):
        for i, h in enumerate(n):
            if any(k in h for k in claves):
                return i
        return None

    i_fecha = buscar("marca temporal", "hora de finalizacion", "timestamp",
                     "completion time", "fecha de envio", "submitted")
    i_rol = buscar("rol dentro del sistema", "tu rol")
    i_prev = buscar("habias respondido antes", "antes este cuestionario")

    items = []
    for enunciado in ITEMS:
        idx = None
        for i, h in enumerate(n):
            if norm(enunciado) in h:
                idx = i
                break
        items.append(idx)
    return i_fecha, i_rol, i_prev, items


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("csv_entrada")
    ap.add_argument("--salida", default=None,
                    help="CSV derivado (por defecto, junto al de entrada)")
    args = ap.parse_args()

    if not os.path.isfile(args.csv_entrada):
        print(f"ERROR: no existe {args.csv_entrada}")
        return 2

    with open(args.csv_entrada, encoding="utf-8-sig", newline="") as f:
        muestra = f.read(4096)
        f.seek(0)
        try:
            dialecto = csv.Sniffer().sniff(muestra, delimiters=",;\t")
        except csv.Error:
            dialecto = csv.excel
        filas = list(csv.reader(f, dialecto))

    if len(filas) < 2:
        print("ERROR: el archivo no tiene filas de respuesta.")
        return 2

    encabezados, datos = filas[0], filas[1:]
    i_fecha, i_rol, i_prev, i_items = localizar(encabezados)

    faltan = [n + 1 for n, idx in enumerate(i_items) if idx is None]
    if faltan:
        print("ERROR: no se reconocieron los items " + ", ".join(map(str, faltan)))
        print("Los encabezados del CSV deben ser el texto completo de cada pregunta,")
        print("igual que en el README de docs/mediciones/sus/re-aplicacion/.")
        print("\nEncabezados encontrados:")
        for h in encabezados:
            print("   " + h)
        return 2
    if i_fecha is None:
        print("ERROR: el CSV no trae columna de marca de tiempo.")
        print("Sin la hora del servidor del tercero, esta ronda no aporta lo que se")
        print("pide. Exporta el archivo sin quitarle columnas.")
        return 2

    salidas, rechazos = [], []
    for nfila, fila in enumerate(datos, start=2):
        if not any((c or "").strip() for c in fila):
            continue
        try:
            valores = []
            for n, idx in enumerate(i_items, start=1):
                crudo = (fila[idx] or "").strip()
                m = re.search(r"[1-5]", crudo)
                if not m:
                    raise ValueError(f"item {n} vacio o fuera de 1-5 ({crudo!r})")
                valores.append(int(m.group(0)))
        except (IndexError, ValueError) as e:
            rechazos.append((nfila, str(e)))
            continue

        fecha = (fila[i_fecha] or "").strip() if i_fecha < len(fila) else ""
        salidas.append({
            "respuesta": len(salidas) + 1,
            "rol": (fila[i_rol] or "").strip() if i_rol is not None and i_rol < len(fila) else "",
            "respondio_en_papel": (fila[i_prev] or "").strip() if i_prev is not None and i_prev < len(fila) else "",
            "marca_tiempo_servidor": fecha,
            "fecha_verificable": "si" if fecha else "no",
            **{f"q{n}": v for n, v in enumerate(valores, start=1)},
            "sus_score": sus(valores),
        })

    if not salidas:
        print("ERROR: ninguna fila valida.")
        for nf, e in rechazos:
            print(f"   fila {nf}: {e}")
        return 1

    destino = args.salida or os.path.join(
        os.path.dirname(args.csv_entrada) or ".", "sus-respuestas-formulario.csv")
    campos = list(salidas[0].keys())
    with open(destino, "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=campos)
        w.writeheader()
        w.writerows(salidas)

    verificables = [r for r in salidas if r["fecha_verificable"] == "si"]
    puntajes = [r["sus_score"] for r in verificables]

    print(f"Respuestas leidas:            {len(salidas)}")
    print(f"Con marca de tiempo del servidor: {len(verificables)}")
    if rechazos:
        print(f"Filas rechazadas:             {len(rechazos)}")
        for nf, e in rechazos:
            print(f"   fila {nf}: {e}")
    print(f"\nCSV derivado: {destino}")

    if len(puntajes) >= 2:
        media = statistics.mean(puntajes)
        de = statistics.stdev(puntajes)
        n = len(puntajes)
        try:
            from scipy import stats
            t = stats.t.ppf(0.975, df=n - 1)
            margen = t * de / n ** 0.5
            ic = f"IC95=[{media - margen:.2f}, {media + margen:.2f}]"
        except ImportError:
            ic = "(IC95 no calculado: falta scipy)"
        print(f"\nn={n}  media={media:.2f}  DE={de:.2f}  {ic}")
    elif puntajes:
        print(f"\nn=1  puntaje={puntajes[0]:.2f}  (sin dispersion con una sola respuesta)")

    cuantos_papel = sum(1 for r in verificables
                        if norm(r["respondio_en_papel"]).startswith("si"))
    if verificables:
        print(f"\nDe las {len(verificables)} respuestas verificables, {cuantos_papel} "
              "declaran haber participado antes en la ronda de papel.")
    print("\nLa fecha de cada fila es la que trae el archivo del formulario; este")
    print("script no genera ninguna. Versiona el CSV exportado sin editarlo.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
