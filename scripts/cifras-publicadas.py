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
# En LaTeX el porcentaje se escribe "82.01\,\%": hasta la revision del 19-sep esta
# expresion no admitia la barra invertida antes del %, asi que NINGUNA cifra del
# informe se revisaba (cambiar 82.01 por 85.01 en el .tex no lo veia nadie).
RE_PCT = re.compile(r"(?<![\d.,])(\d{2})[.,](\d{2})\s*(?:\\,)?\s*\\?%")
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
    r"|\bv\d+\.\d+\.\d+\b"
    r"|(?<![\d.])\d{1,2}-(?:0[1-9]|1[0-2])(?![\d-])",   # "13-09": dd-mm sin año
    re.I)

# Frases con las que un texto declara que esa cifra es la que rige AHORA. Una
# cifra asi no puede ampararse en llevar fecha: si dice que es la de cierre,
# tiene que ser la de cierre.
RE_VIGENTE = re.compile(
    r"cifra de cierre|de cierre definitiv|corrida de cierre|cifra vigente"
    r"|la que aplica|cifra de cierre vigente|badge|shields\.io",
    re.I)

# Marcas de tiempo presente ("hoy 82.13 %", "actualmente 823 pruebas"): una cifra que las lleva justo
# delante afirma ser la de AHORA, y una fecha cercana (de otra cifra del mismo parrafo, como el
# "63.17 % en el cierre del 2026-09-05, hoy 82.13 %") no puede ampararla.
RE_ACTUAL = re.compile(r"\b(?:hoy|actualmente|ahora|actual(?:es)?|today|currently|now)\b", re.I)

# Distancia (en caracteres) a la que la procedencia cuenta como "junto a la cifra".
CERCA = 300

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


def fecha_de_cierre():
    """La fecha MAS RECIENTE que el resumen de la corrida canonica registra.

    Una cifra fechada con ESA fecha afirma ser la de la corrida de cierre, y entonces tiene
    que serlo: "JaCoCo, 2026-09-20, 809 tests" lleva fecha y procedencia, pero contradice la
    corrida de cierre (que da otra cifra). La fecha adjunta no puede amparar un dato falso
    sobre la corrida vigente. Las fechas anteriores no cuentan: el mismo dia pudo haber mas
    de una corrida o cifras de cierre superadas, y esas SI son historia legitima.
    """
    if not os.path.isfile(RESUMEN):
        return None
    fechas = re.findall(r"\d{4}-\d{2}-\d{2}", io.open(RESUMEN, encoding="utf-8").read())
    return max(fechas) if fechas else None


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


RE_FRACCION = re.compile(r"(?<![\d.,/])(\d[\d.,]{2,6})\s*/\s*(\d[\d.,]{2,6})(?![\d.,/])")


def _num(s):
    return int(s.replace(",", "").replace(".", ""))


def corridas_archivadas():
    """(cubierto, total) de LINE y BRANCH de TODA corrida versionada.

    Una fraccion publicada tiene que corresponder a alguna corrida real. Sin
    esto, la regla de la procedencia deja pasar una cifra de cierre vencida:
    basta con que su parrafo lleve una fecha. Asi es como 4019/4901 seguia
    publicado cuando la corrida de cierre ya daba 4022/4904 -- un par que
    ninguna corrida del expediente respalda.
    """
    pares = set()
    for x in glob.glob("docs/mediciones/jacoco/*/jacoco.xml"):
        try:
            root = ET.parse(x).getroot()
        except ET.ParseError:
            continue
        for c in root.findall("counter"):
            if c.get("type") in ("LINE", "BRANCH"):
                co, mi = int(c.get("covered")), int(c.get("missed"))
                pares.add((co, co + mi))
    return pares


def revisar_fracciones(archivos, pares):
    totales = {tot for _, tot in pares}
    # Un total "casi igual" al de cierre (4901 frente a 4904) es la firma de una cifra
    # vencida, y no lo registra ninguna corrida, asi que el filtro de arriba lo
    # dejaba pasar: asi se colo 4019/4901 en VERIFICACION.md junto a "corrida de cierre".
    datos = canonica()
    cierre = {t for _, t, _ in datos[0].values()} if datos else set()
    malas = []
    for f in archivos:
        try:
            txt = io.open(f, encoding="utf-8", errors="replace").read()
        except OSError:
            continue
        for m in RE_FRACCION.finditer(txt):
            try:
                co, tot = _num(m.group(1)), _num(m.group(2))
            except ValueError:
                continue
            if co > tot:
                continue
            # Solo se revisan los totales que alguna corrida archivada registra
            # como globales. Sin este filtro se marcaban los desgloses por
            # paquete (2245/3197) y numeros sin relacion (205871/205871), y una
            # salida llena de ruido no la lee nadie.
            if tot not in totales and not any(abs(tot - c) <= 15 for c in cierre):
                continue
            ventana = txt[max(0, m.start() - 160): m.end() + 160]
            if not CTX_COBERTURA.search(ventana):
                continue
            if (co, tot) not in pares:
                malas.append((f.replace("\\", "/"), f"{co}/{tot}",
                              " ".join(ventana.split())[:130]))
    return malas


def conocidos():
    """Porcentajes y conteos de pruebas que ALGUNA fuente historica del expediente registra.

    La procedencia (una fecha en el parrafo) exime a una cifra de ser la de
    cierre, pero no de existir: "85.01 % (2026-09-18)" lleva fecha y no lo
    respalda ninguna corrida. Se acepta una cifra fechada solo si el expediente
    la registra en algun sitio --- las carpetas de corridas y los documentos
    historicos, que documentan lo que era cierto cuando se escribieron.
    """
    pct, cnt = set(), set()
    fuentes = [f for pat in ("**/*.md", "**/*.tex")
               for f in glob.glob(pat, recursive=True)
               if "node_modules" not in f
               and (not vigente(f) or RE_CORRIDA.search(f.replace("\\", "/")))]
    for f in fuentes:
        try:
            txt = io.open(f, encoding="utf-8", errors="replace").read()
        except OSError:
            continue
        for m in RE_PCT.finditer(txt):
            pct.add(f"{m.group(1)}.{m.group(2)}")
        for m in RE_PRUEBAS.finditer(txt):
            cnt.add(m.group(1))
    for x in glob.glob("docs/mediciones/jacoco/*/jacoco.xml"):
        try:
            for c in ET.parse(x).getroot().findall("counter"):
                if c.get("type") in ("LINE", "BRANCH"):
                    co, mi = int(c.get("covered")), int(c.get("missed"))
                    pct.add(f"{co / (co + mi) * 100:.2f}")
        except ET.ParseError:
            pass
    return pct, cnt


def revisar(archivos, cob, pruebas):
    linea_pct = f"{cob['LINE'][2]:.2f}"
    rama_pct = f"{cob['BRANCH'][2]:.2f}"
    validos = {linea_pct, rama_pct}

    pct_conocidos, cnt_conocidos = conocidos()
    f_cierre = fecha_de_cierre()
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
                if val in validos:
                    continue
                # Una cifra que el expediente registro alguna vez (de una corrida vieja)
                # solo se acepta si dice de que corrida es JUNTO a la cifra. Antes bastaba
                # con que el bloque entero llevara una fecha, en cualquier sitio: revertir un
                # documento vigente a una cifra caduca pero un dia cierta pasaba en verde
                # (revision final, 2026-09-21). Se mira el entorno inmediato de ESA cifra.
                if val in pct_conocidos:
                    cerca = bloque[max(0, m.start() - CERCA): m.end() + CERCA]
                    pegado = bloque[max(0, m.start() - 80): m.end() + 80]
                    if (RE_PROCEDENCIA.search(cerca) and not RE_VIGENTE.search(pegado)
                            and not (f_cierre and f_cierre in pegado)
                            and not RE_ACTUAL.search(bloque[max(0, m.start() - 40): m.start()])):
                        continue
                malas.append((rel, val + " %", " ".join(ctx.split())[:140]))
            if pruebas:
                for m in RE_PRUEBAS.finditer(bloque):
                    if m.group(1) == pruebas:
                        continue
                    # Solo cuentan como "el numero de pruebas de la suite" las cifras
                    # de ese orden de magnitud: 140 o 106 son las pruebas de un modulo.
                    if not 500 <= int(m.group(1)) <= 1200:
                        continue
                    # Un conteo historico, lo registre o no algun archivo, se acepta si dice
                    # de que dia es JUNTO a la cifra. Una fecha en algun otro punto del
                    # bloque no basta: es como se colaba "860 pruebas" en una tabla que
                    # ademas hablaba del 18-sep, y como pasaba "809" por "823" en un
                    # parrafo fechado mas arriba.
                    cerca = bloque[max(0, m.start() - CERCA): m.end() + CERCA]
                    pegado = bloque[max(0, m.start() - 80): m.end() + 80]
                    if (RE_PROCEDENCIA.search(cerca) and not RE_VIGENTE.search(pegado)
                            and not (f_cierre and f_cierre in pegado)
                            and not RE_ACTUAL.search(bloque[max(0, m.start() - 40): m.start()])):
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
    fracciones = revisar_fracciones(archivos, corridas_archivadas())
    print(f"Documentos vigentes revisados: {len(archivos)}\n")

    detalle = "--lista" in sys.argv
    for titulo, hallazgos in (("cobertura", malas), ("conteo de pruebas", conteos),
                              ("fraccion cubierto/total", fracciones)):
        if hallazgos:
            print(f"*** {len(hallazgos)} cifra(s) de {titulo} sin respaldo ni procedencia ***")
            for f, v, ctx in hallazgos:
                print(f"   {v:>14s}  en {f}")
                if detalle:
                    print(f"                   ...{ctx}...")
            print()
        else:
            print(f"[OK] {titulo}: toda cifra publicada es la de cierre o declara su corrida.")

    if fracciones:
        print("Una fraccion cubierto/total tiene que corresponder a una corrida")
        print("versionada en docs/mediciones/jacoco/. Si no cuadra con ninguna,")
        print("la cifra no la respalda el expediente aunque lleve fecha.")
    if malas or conteos or fracciones:
        print("\nArreglalo de una de dos formas:")
        print("  - pon la cifra de cierre, o")
        print("  - di de que corrida es (una fecha en el mismo parrafo basta).")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
