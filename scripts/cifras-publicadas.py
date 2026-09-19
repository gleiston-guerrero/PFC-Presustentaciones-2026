#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Comprueba que toda cifra de cobertura publicada sea la de cierre, o diga de que corrida es.

QUE PROBLEMA RESUELVE
---------------------
P11 pide "cifras unicas" y se rompio tres veces seguidas por la misma causa: los
porcentajes de cobertura y el numero de pruebas estan escritos a mano en decenas
de archivos, asi que cada corrida nueva desincroniza los que nadie recuerda
tocar. La revision del 18-sep encontro conviviendo 82,10 / 82,03 / 82,96 y
559 / 801 / 804 pruebas. Ninguna era inventada --- cada una era la cifra real de
*alguna* corrida --- pero publicadas a la vez son una contradiccion.

Corregirlas una a una garantiza que vuelva a pasar. Este script lo convierte en
algo que se comprueba solo.

LA REGLA QUE APLICA
-------------------
Una cifra publicada en un documento vigente es valida si cumple UNA de dos:

  (a) coincide con la corrida canonica (docs/mediciones/jacoco/<CANONICA>/), o
  (b) dice explicitamente de que corrida es --- hay una fecha o el nombre de una
      carpeta de medicion en su mismo bloque de texto.

No se exige que todo el repositorio repita el mismo numero: se exige procedencia.
Un dato sin procedencia es exactamente lo que este proyecto viene corrigiendo
(ver docs/mediciones/DATA-PROVENANCE.md), asi que la regla es la misma que ya se
aplica a mano, nada mas que ejecutable.

QUE NO REVISA, Y POR QUE
------------------------
Los documentos historicos (bitacoras, informes fechados, evidencias de fase) se
saltan enteros: registran lo que era cierto cuando se escribieron. Corregir
retroactivamente un registro fechado para que cuadre con la medicion de hoy
seria falsear el historial. Igual las carpetas de corridas anteriores en
docs/mediciones/jacoco/: cada una documenta SU corrida.

Tampoco se revisan los desgloses por paquete (un decimal: "controllers 84.2 %"),
que no son la cifra global y no compiten con ella.

Uso:
    python scripts/cifras-publicadas.py            # informe legible
    python scripts/cifras-publicadas.py --lista    # ademas, el texto de cada hallazgo

Sale con 1 si encuentra una cifra publicada sin respaldo ni procedencia.
"""
import glob
import io
import os
import re
import sys
import xml.etree.ElementTree as ET

CANONICA = "docs/mediciones/jacoco/2026-09-19-cierre-definitivo"
XML = os.path.join(CANONICA, "jacoco.xml")
RESUMEN = os.path.join(CANONICA, "RESUMEN.md")

# Documentos que registran un momento pasado: no se tocan ni se revisan.
HISTORICOS = (
    "BITACORA", "INFORME-ERRORES", "CORRECCIONES-APLICADAS", "evidencias",
    "OBSERVACIONES", "AUTOEVALUACION", "auditoria/FASE", "ENTREGA-FINAL",
    "P4-RESOLUCION", "historico", "Informe-UNIDAD-4", "CHANGELOG",
    "docs/mediciones/jacoco/COVERAGE.md",
)

# Cada carpeta de docs/mediciones/jacoco/<fecha>/ documenta su propia corrida.
RE_CORRIDA = re.compile(r"docs/mediciones/jacoco/\d{4}-\d{2}-\d{2}")

# Cifra global: dos decimales. Un decimal es desglose por paquete, no compite.
RE_PCT = re.compile(r"(?<![\d.,])(\d{2})[.,](\d{2})\s*(?:\\,)?\s*%")
RE_PRUEBAS = re.compile(
    r"(?<![\d.,])(\d{3,4})\s*(?:/\s*\d{3,4}\s*)?(?:pruebas|tests|Tests run)", re.I)

# El contexto tiene que hablar de cobertura para que el porcentaje cuente.
CTX_COBERTURA = re.compile(r"cobertur|l[ií]nea|ramas|jacoco|branch|line", re.I)

# Procedencia declarada: una fecha, o una etiqueta de version. El SRS, por
# ejemplo, cuenta sus cifras "sobre la etiqueta v1.0.1", que fija la corrida
# igual de bien que una fecha.
RE_PROCEDENCIA = re.compile(
    r"\d{4}-\d{2}-\d{2}"
    r"|\d{1,2}[-/\s](?:ene|feb|mar|abr|may|jun|jul|ago|sep|oct|nov|dic)"
    r"|\d{1,2}\s+de\s+(?:enero|febrero|marzo|abril|mayo|junio|julio|agosto|"
    r"septiembre|octubre|noviembre|diciembre)"
    r"|\bv\d+\.\d+\.\d+\b",
    re.I)

RE_TITULO_MD = re.compile(r"^#{1,6}\s")
RE_TITULO_TEX = re.compile(r"^\\(?:sub)*section\*?\{")


def canonica():
    """Lee la cifra de cierre del expediente, no de una constante en el codigo."""
    if not os.path.isfile(XML):
        return None
    root = ET.parse(XML).getroot()
    cob = {}
    for c in root.findall("counter"):
        t = c.get("type")
        if t in ("LINE", "BRANCH"):
            co, mi = int(c.get("covered")), int(c.get("missed"))
            cob[t] = (co, co + mi, round(co / (co + mi) * 100, 2))
    pruebas = None
    if os.path.isfile(RESUMEN):
        m = re.search(r"Tests run:\s*(\d+)", io.open(RESUMEN, encoding="utf-8").read())
        if m:
            pruebas = m.group(1)
    return cob, pruebas


def vigente(ruta):
    r = ruta.replace("\\", "/")
    if "node_modules" in r or RE_CORRIDA.search(r):
        return False
    return not any(h in r for h in HISTORICOS)


def bloques(texto, es_tex):
    """Trocea el documento en bloques y le cuelga a cada uno su titulo mas cercano.

    La procedencia casi nunca esta pegada al numero: esta en el encabezado del
    bloque ("Salida real (2026-09-17)") o en la frase de al lado. Mirar solo una
    ventana de caracteres produce falsos positivos en documentos bien fechados.
    """
    es_titulo = RE_TITULO_TEX if es_tex else RE_TITULO_MD
    titulo, buf, ini = "", [], 0
    pos = 0
    for linea in texto.splitlines(keepends=True):
        if es_titulo.match(linea):
            if buf:
                yield ini, "".join(buf), titulo
            titulo, buf, ini = linea, [], pos
        elif not linea.strip():
            if buf:
                yield ini, "".join(buf), titulo
            buf, ini = [], pos + len(linea)
        else:
            buf.append(linea)
        pos += len(linea)
    if buf:
        yield ini, "".join(buf), titulo


def revisar(archivos, cob, pruebas):
    linea_pct = f"{cob['LINE'][2]:.2f}"
    rama_pct = f"{cob['BRANCH'][2]:.2f}"
    validos = {linea_pct, rama_pct}

    malas, conteos = [], []
    for f in archivos:
        try:
            txt = io.open(f, encoding="utf-8", errors="replace").read()
        except OSError:
            continue
        rel = f.replace("\\", "/")
        es_tex = f.endswith(".tex")
        for _, bloque, titulo in bloques(txt, es_tex):
            ctx = titulo + bloque
            tiene_fecha = bool(RE_PROCEDENCIA.search(ctx))
            for m in RE_PCT.finditer(bloque):
                val = f"{m.group(1)}.{m.group(2)}"
                if not (60 <= float(val) <= 100):
                    continue
                if not CTX_COBERTURA.search(ctx):
                    continue
                if val in validos or tiene_fecha:
                    continue
                malas.append((rel, val + " %", " ".join(ctx.split())[:140]))
            if pruebas:
                for m in RE_PRUEBAS.finditer(bloque):
                    if m.group(1) == pruebas or tiene_fecha:
                        continue
                    conteos.append(
                        (rel, m.group(1) + " pruebas", " ".join(ctx.split())[:140]))
    return malas, conteos


def main():
    datos = canonica()
    if not datos:
        print(f"ERROR: falta {XML}. Corre antes:")
        print("   cd backend && ./mvnw clean test")
        return 2
    cob, pruebas = datos

    lc, lt, lp = cob["LINE"]
    bc, bt, bp = cob["BRANCH"]
    print(f"Cifra canonica ({CANONICA}/):")
    print(f"   lineas {lc}/{lt} = {lp:.2f} %")
    print(f"   ramas  {bc}/{bt} = {bp:.2f} %")
    print(f"   pruebas: {pruebas or '(sin RESUMEN.md)'}\n")

    archivos = sorted({f for pat in ("**/*.md", "**/*.tex")
                       for f in glob.glob(pat, recursive=True) if vigente(f)})
    malas, conteos = revisar(archivos, cob, pruebas)
    print(f"Documentos vigentes revisados: {len(archivos)}\n")

    detalle = "--lista" in sys.argv
    for titulo, hallazgos in (("cobertura", malas), ("conteo de pruebas", conteos)):
        if hallazgos:
            print(f"*** {len(hallazgos)} cifra(s) de {titulo} sin respaldo ni procedencia ***")
            for f, v, ctx in hallazgos:
                print(f"   {v:>14s}  en {f}")
                if detalle:
                    print(f"                   ...{ctx}...")
            print()
        else:
            print(f"[OK] {titulo}: toda cifra publicada es la de cierre o declara su corrida.")

    if malas or conteos:
        print("\nArreglalo de una de dos formas:")
        print("  - pon la cifra de cierre, o")
        print("  - di de que corrida es (una fecha en el mismo parrafo basta).")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
