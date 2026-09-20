#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Contrasta lo que dicen los documentos con lo que dicen los datos.

POR QUE EXISTE
--------------
`make verify` era fuerte comparando codigo contra codigo (contrato JSON, SpEL,
umbral de JaCoCo) y ciego comparando DOCUMENTO contra MEDICION. La revision del
19-sep lo probo con 15 mutaciones: detecto 5 y sobrevivieron 10. Entre las que
sobrevivieron:

  1. cambiar una cifra de cobertura o del SUS en el informe
  2. invertir la conclusion de una prueba estadistica
  3. apuntar Lighthouse a localhost
  4. borrar un archivo de evidencia citado

Este script cierra esas cuatro. La regla es la de siempre en este repositorio:
un numero publicado no se escribe, se DERIVA de los datos versionados, y aqui
se vuelve a derivar y se compara.

QUE COMPRUEBA
-------------
  lighthouse  las corridas versionadas se hicieron contra la URL publica (nunca
              localhost) y todas contra la misma; el reporte y el informe
              declaran esa misma URL.
  evidencia   todo archivo del repositorio que un documento vigente cita existe.
  sus         media, DE, IC 95 % y alfa publicados == recalculados del CSV.
              p ajustados y decision de Holm publicados == recalculados; y el
              parrafo que los publica no afirma lo contrario de la decision.
  rendimiento (solo con --nb) los p ajustados de la familia de 3 pruebas de
              caché fria vs caliente, publicados en el informe y en k6/README,
              == los que imprime el cuaderno perf-analysis.ipynb al ejecutarse.

QUE NO COMPRUEBA
----------------
Es un contraste de cifras ANCLADAS en una frase: si alguien reescribe la cifra
con una redaccion que ninguna regla reconoce, no la ve. Por eso cada regla exige
encontrar al menos un caso (si una regex deja de casar con nada, falla en vez
de pasar en silencio), y por eso hay un arnes de mutaciones
(scripts/mutaciones-gate.py) que inyecta el defecto y exige que esto salga 1.

Uso:
    python scripts/ev2-documental.py                 # todo salvo el cuaderno
    python scripts/ev2-documental.py --nb salida.json   # + familia de rendimiento

Sale con 1 si algun documento contradice a su dato.
"""
import csv
import glob
import importlib.util
import io
import json
import math
import os
import re
import statistics
import subprocess
import sys

# Importar cifras-publicadas.py dejaria un .pyc en scripts/__pycache__ y el chequeo de
# higiene de verify.sh lo marcaria como suciedad que este mismo script produjo.
sys.dont_write_bytecode = True

CSV_FORM = "docs/mediciones/sus/re-aplicacion/sus-respuestas-formulario.csv"
CSV_PAPEL = "docs/mediciones/sus/sus-respuestas.csv"
PROD_RUNS = "docs/mediciones/perf/lighthouse/prod-runs"
REPORTE_LH = "docs/mediciones/perf/lighthouse/LIGHTHOUSE-REPORT.md"
INFORME_10 = "Informe-Final/secciones/10-evaluacion-empirica.tex"
K6_README = "k6/README.md"

FALLOS = []


def ok(msg):
    print(f"  [OK]   {msg}")


def fail(msg):
    print(f"  [FAIL] {msg}")
    FALLOS.append(msg)


def leer(ruta):
    return io.open(ruta, encoding="utf-8", errors="replace").read()


def _cargar_cifras():
    """Reutiliza el criterio de 'documento vigente' de cifras-publicadas.py."""
    spec = importlib.util.spec_from_file_location(
        "cifras_publicadas", os.path.join(os.path.dirname(__file__), "cifras-publicadas.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def archivos_vigentes(exts=(".md", ".tex")):
    cif = _cargar_cifras()
    r = subprocess.run(["git", "ls-files"], capture_output=True, text=True,
                       encoding="utf-8", errors="replace")
    return [f for f in r.stdout.splitlines()
            if f.endswith(exts) and cif.vigente(f)]


def num(s):
    """'52,83' / '52.83' -> 52.83"""
    return float(s.replace(",", "."))


# ---------------------------------------------------------------- lighthouse
def comprobar_lighthouse():
    print("--- Lighthouse: las corridas son contra la URL publica ---")
    jsons = sorted(glob.glob(os.path.join(PROD_RUNS, "*.json")))
    if not jsons:
        fail("no hay corridas versionadas en prod-runs/")
        return
    urls = set()
    for f in jsons:
        d = json.load(io.open(f, encoding="utf-8"))
        for clave in ("requestedUrl", "finalUrl", "finalDisplayedUrl"):
            u = d.get(clave)
            if not u:
                continue
            urls.add(u)
            if re.search(r"//(localhost|127\.|0\.0\.0\.0|\[::1\]|[^/]*\.local\b)", u, re.I):
                fail(f"{f}: {clave} apunta a un servidor local ({u}); "
                     f"el informe declara despliegue publico")
    if not FALLOS:
        ok(f"{len(jsons)} corridas, ninguna contra localhost")

    hosts = {re.sub(r"^https?://([^/]+).*", r"\1", u) for u in urls}
    if len(hosts) != 1:
        fail(f"las corridas no miden el mismo servidor: {sorted(hosts)}")
        return
    host = hosts.pop()
    ok(f"las {len(jsons)} corridas miden el mismo servidor: {host}")

    for doc in (REPORTE_LH, INFORME_10):
        if host not in leer(doc):
            fail(f"{doc} no declara el servidor que miden los JSON ({host})")
        else:
            ok(f"{doc} declara ese mismo servidor")

    # La revision del 19-sep encontro el informe dibujando 68/61 (localhost) bajo un
    # pie que decia "despliegue publico": la figura se regenero en docs/ y nadie la
    # copio. El defecto estaba en la IMAGEN, asi que ningun texto lo delataba.
    gen = "docs/mediciones/perf/figuras/fig-lighthouse-scores.png"
    inf = "Informe-Final/figuras/fig-lighthouse-scores.png"
    if open(gen, "rb").read() == open(inf, "rb").read():
        ok("la figura de Lighthouse del informe es la generada desde los JSON")
    else:
        fail(f"{inf} difiere de la generada ({gen}): el informe dibuja otra medicion "
             f"(make docs regenera y copia; luego make pdf)")

    mk = leer("Makefile")
    m = re.search(r"^LH_URL\s*\?=\s*(\S+)", mk, re.M)
    if not m or host not in m.group(1):
        fail(f"Makefile: LH_URL por defecto ({m.group(1) if m else 'ausente'}) "
             f"no es el servidor que miden los JSON")
    else:
        ok("make bench-lh mide por defecto el mismo servidor")


# ----------------------------------------------------------------- evidencia
RAICES = ("docs", "scripts", "Informe-Final", "k6", ".github", "backend/src",
          "Frontend/src", "backend/INSTRUCCIONES.md")
RE_MD_LINK = re.compile(r"\]\(([^)\s#]+)(?:#[^)]*)?\)")
RE_BACKTICK = re.compile(r"`((?:%s)[^`\s*<>{}$|]*\.[A-Za-z0-9]{1,6})`" % "|".join(
    re.escape(r) for r in RAICES))
RE_TEXTTT = re.compile(r"\\texttt\{((?:%s)[^}]*)\}" % "|".join(re.escape(r) for r in RAICES))

# Rutas que un documento cita pero que NO viven en git, por diseno.
GENERADAS = ("target/", ".verify-tmp", "node_modules", "site/apidocs")


def comprobar_evidencia():
    print("--- Evidencia citada: todo archivo citado existe ---")
    citas, rotas = 0, []
    for f in archivos_vigentes():
        base = os.path.dirname(f)
        txt = leer(f)
        cand = []
        if f.endswith(".md"):
            for m in RE_MD_LINK.finditer(txt):
                t = m.group(1)
                if re.match(r"[a-z]+:", t) or t.startswith("mailto"):
                    continue
                cand.append(os.path.normpath(os.path.join(base, t)))
            for m in RE_BACKTICK.finditer(txt):
                cand.append(os.path.normpath(m.group(1)))
        else:
            for m in RE_TEXTTT.finditer(txt):
                cand.append(os.path.normpath(m.group(1).replace("\\_", "_")))
        for c in cand:
            c = c.replace("\\", "/")
            if any(g in c for g in GENERADAS) or "..." in c or "*" in c:
                continue
            citas += 1
            if not os.path.exists(c):
                rotas.append((f, c))
    if citas < 50:
        fail(f"solo se reconocieron {citas} citas de archivos: las reglas de "
             f"reconocimiento dejaron de casar")
        return
    if rotas:
        for f, c in sorted(set(rotas)):
            fail(f"{f} cita {c}, que no existe")
    else:
        ok(f"{citas} citas a archivos del repositorio, todas existen")


# ----------------------------------------------------------------------- SUS
def _resumen(xs):
    from scipy import stats
    n, media, de = len(xs), statistics.mean(xs), statistics.stdev(xs)
    m = stats.t.ppf(0.975, df=n - 1) * de / math.sqrt(n)
    return {"n": n, "media": media, "de": de, "lo": media - m, "hi": media + m}


def resumen_sus():
    rows = list(csv.DictReader(io.open(CSV_FORM, encoding="utf-8")))
    return _resumen([float(r["sus_score"]) for r in rows])


def resumen_papel():
    """Las dos lecturas de la ronda en papel: las 15 hojas y las 4 de fecha verificable."""
    rows = list(csv.DictReader(io.open(CSV_PAPEL, encoding="utf-8")))
    return [_resumen([float(r["sus_score"]) for r in rows]),
            _resumen([float(r["sus_score"]) for r in rows if r["fecha_verificable"] == "si"])]


def estadistica_sus():
    r = subprocess.run([sys.executable, "scripts/sus-estadistica.py", "--json"],
                       capture_output=True, text=True, encoding="utf-8",
                       env={**os.environ, "PYTHONDONTWRITEBYTECODE": "1"})
    if r.returncode != 0:
        return None
    return json.loads(r.stdout)


# Cada regla: (nombre, regex con UN grupo = la cifra, clave del dato).
RE_2D = r"(\d{2}[.,]\d{2})"
REGLAS_SUS = [
    ("media (SUS = ..)", re.compile(r"SUS\s*=\s*\**\s*" + RE_2D + r"(?!\d)"), "media"),
    ("media (media ../100)", re.compile(r"media\s+" + RE_2D + r"\s*/\s*100"), "media"),
    ("media (formulario del 18-sep (..))",
     re.compile(r"formulario del 18-sep\s*\(" + RE_2D + r"\)"), "media"),
    ("DE", re.compile(r"\bDE\s+" + RE_2D + r"(?!\d)"), "de"),
    ("DE (desviación estándar ..)",
     re.compile(r"desviaci[oó]n est[aá]ndar\s+" + RE_2D), "de"),
    ("IC95 inferior", re.compile(r"\[\s*" + RE_2D + r"\s*(?:[,–-]|\\?,|--)\s*\d{2}[.,]\d{2}\s*\]"), "lo"),
    ("IC95 superior", re.compile(r"\[\s*\d{2}[.,]\d{2}\s*(?:[,–-]|\\?,|--)\s*" + RE_2D + r"\s*\]"), "hi"),
    ("alfa (formulario)",
     re.compile(r"(?:\\alpha|α|de Cronbach)\s*\$?\s*=\s*\$?\s*(0[.,]\d{3})"), "alfa"),
]


def comprobar_sus():
    print("--- SUS: media, DE, IC 95 % y alfa publicados == recalculados ---")
    if not os.path.isfile(CSV_FORM):
        fail(f"falta {CSV_FORM}")
        return
    res = resumen_sus()
    est = estadistica_sus()
    if est is None:
        fail("scripts/sus-estadistica.py fallo")
        return
    papel = resumen_papel()
    esperado = {"media": res["media"], "de": res["de"], "lo": res["lo"], "hi": res["hi"],
                "alfa": est["alfa"]["formulario"]}

    hallados = 0
    for f in archivos_vigentes():
        txt = leer(f)
        for nombre, rx, clave in REGLAS_SUS:
            for m in rx.finditer(txt):
                v = num(m.group(1))
                dec = 3 if clave == "alfa" else 2
                # El alfa del papel (0,619) y el sin invertir (0,267) son otros
                # dos numeros legitimos del mismo apartado: se aceptan.
                validos = [round(esperado[clave], dec)]
                if clave != "alfa":
                    # La comparacion papel/formulario publica tambien los
                    # intervalos y medias del papel: son datos reales de otra
                    # muestra, no una cifra del formulario mal copiada.
                    validos += [round(p[clave], dec) for p in papel]
                if clave == "alfa":
                    validos += [round(est["alfa"]["papel_15"], 3),
                                round(est["alfa"]["formulario_sin_invertir"], 3)]
                hallados += 1
                if not any(abs(v - x) < 10 ** -dec / 2 + 1e-9 for x in validos):
                    fail(f"{f}: publica {nombre} = {m.group(1)}, el CSV da "
                         f"{round(esperado[clave], dec)}")
    if hallados < 12:
        fail(f"solo se reconocieron {hallados} cifras del SUS publicadas: las "
             f"reglas dejaron de casar")
    elif not any("SUS" in x or "media" in x for x in FALLOS):
        ok(f"{hallados} cifras del SUS publicadas (media, DE, IC 95 %, alfa) "
           f"coinciden con el recalculo")

    comprobar_holm_sus(est)


def comprobar_holm_sus(est):
    """p ajustados y decision de Holm del SUS: publicados == calculados."""
    holm = est["holm"]
    ninguno = not any(h["rechaza_H0"] for h in holm)
    esperados_p = set()
    for h in holm:
        esperados_p.add(round(h["p_crudo"], 3))
        esperados_p.add(round(h["p_ajustado"], 3))

    # Filas de la tabla de Holm: | # | contraste | p crudo | umbral | p ajustado | decision |
    filas, malas = 0, 0
    RE_FILA = re.compile(
        r"^\|\s*(\d)\s*\|[^|]*\|\s*(0[.,]\d+)\s*\|\s*(0[.,]\d+)\s*\|\s*(0[.,]\d+)\s*\|\s*([^|]+?)\s*\|\s*$",
        re.M)
    for f in archivos_vigentes(exts=(".md",)):
        for m in RE_FILA.finditer(leer(f)):
            i = int(m.group(1)) - 1
            if not 0 <= i < len(holm):
                continue
            h = holm[i]
            filas += 1
            crudo, umbral, aj = num(m.group(2)), num(m.group(3)), num(m.group(4))
            decision = m.group(5).lower()
            rechaza_dice = ("rechaza" in decision and "no" not in decision.split("rechaza")[0])
            if (abs(crudo - h["p_crudo"]) > 5e-5 or abs(aj - h["p_ajustado"]) > 5e-5
                    or abs(umbral - h["umbral_holm"]) > 5e-4):
                fail(f"{f}: fila {i + 1} de Holm publica p={m.group(2)} / umbral "
                     f"{m.group(3)} / ajustado {m.group(4)}; el calculo da "
                     f"{h['p_crudo']:.4f} / {h['umbral_holm']} / {h['p_ajustado']:.4f}")
                malas += 1
            if rechaza_dice != h["rechaza_H0"]:
                fail(f"{f}: fila {i + 1} de Holm decide "
                     f"'{m.group(5)}' pero el calculo da rechaza_H0={h['rechaza_H0']}")
                malas += 1
    if filas == 0:
        fail("no se encontro la tabla de Holm del SUS en ningun documento vigente")
    elif not malas:
        ok(f"{filas} fila(s) de la tabla de Holm del SUS coinciden con el calculo "
           f"(p, umbral, ajustado y decision)")

    # p ajustados citados en prosa, y que el parrafo no diga lo contrario.
    RE_PAJ = re.compile(r"ajustad[oa]s?\D{0,40}?(0[.,]\d{3})(?:\D{0,15}?(0[.,]\d{3}))?"
                        r"|(0[.,]\d{3})\**\s*tras Holm")
    NEGATIVO = re.compile(r"ninguno|ninguna|no rechaza|no se rechaza|tampoco|"
                          r"no significativ|indistinguible|sin diferencia", re.I)
    citas, malas = 0, 0
    for f in archivos_vigentes():
        for parr in re.split(r"\n\s*\n", leer(f)):
            # No se exige "Holm" en el parrafo: el que dice "con p ajustado de 0.441
            # --- tampoco significativo" no lo nombra, y es justo el que hay que ver.
            if not re.search(r"SUS|formulario|papel|hojas", parr):
                continue
            for m in RE_PAJ.finditer(parr):
                for g in m.groups():
                    if not g:
                        continue
                    citas += 1
                    v = num(g)
                    if not any(abs(v - p) < 5e-4 + 1e-9 for p in esperados_p):
                        fail(f"{f}: cita un p del SUS = {v}; el calculo da "
                             f"{sorted(esperados_p)}")
                        malas += 1
                # La conclusion tiene que acompanar a la cifra, en la misma frase: un
                # "indistinguibles" tres lineas mas arriba no dice nada de ESTE p.
                frase = parr[m.start(): m.end() + 110]
                if ninguno and not NEGATIVO.search(frase):
                    fail(f"{f}: publica un p ajustado del SUS sin decir junto a el que no "
                         f"se rechaza ('{' '.join(frase.split())[:80]}...'); el calculo no "
                         f"rechaza ninguno")
                    malas += 1
    if citas < 3:
        fail(f"solo se reconocieron {citas} p ajustados del SUS en prosa: las reglas "
             f"dejaron de casar")
    elif not malas:
        ok(f"{citas} p ajustados del SUS citados en prosa coinciden, y ninguno "
           f"contradice la decision (ninguna se rechaza)")

    # La conclusion de Welch tambien: si ninguna se rechaza, no puede decir lo contrario.
    afirma = re.compile(r"(?<!\w)(?:una|la|existe(?:n)?|hay)\s+diferencia\s+significativa|"
                        r"difieren\s+significativamente", re.I)
    for f in archivos_vigentes():
        for parr in re.split(r"\n\s*\n", leer(f)):
            if "Welch" not in parr:
                continue
            for m in afirma.finditer(parr):
                previo = parr[max(0, m.start() - 30): m.start()]
                if ninguno and not NEGATIVO.search(previo):
                    fail(f"{f}: el parrafo de Welch afirma una diferencia significativa "
                         f"('{' '.join(parr[m.start(): m.end() + 40].split())}'); el calculo "
                         f"no rechaza ninguna")


# ------------------------------------------------------------ rendimiento (P6)
def filas_notebook(ruta_json):
    nb = json.load(io.open(ruta_json, encoding="utf-8"))
    RE = re.compile(r"^\s*(\d)\s+(.+?)\s+([0-9.]+e?-?\d*)\s+([0-9.]+)\s+([0-9.]+e?-?\d*)\s+"
                    r"((?:No )?[Ss]ignificativo)\s*$", re.M)
    for c in nb["cells"]:
        if c["cell_type"] != "code":
            continue
        out = "".join("".join(o.get("text", "")) for o in c.get("outputs", [])
                      if o.get("output_type") == "stream")
        f = RE.findall(out)
        if len(f) == 3:
            return [(float(x[2]), float(x[4]), x[5]) for x in f]
    return None


def _cient(s):
    """'$9.06\\times10^{-11}$' / '9.06×10⁻¹¹' / '2e-05' -> float"""
    s = s.replace("$", "").replace("\\,", "").replace("{", "").replace("}", "")
    sup = str.maketrans("⁰¹²³⁴⁵⁶⁷⁸⁹⁻", "0123456789-")
    s = s.replace("\\times10^", "e").replace("×10", "e").replace("×10^", "e")
    m = re.match(r"^\s*([0-9.]+)\s*(?:e\s*)?(-?[0-9]+)?\s*$", s.translate(sup)) if "e" not in s else None
    if m and m.group(2):
        return float(m.group(1)) * 10 ** int(m.group(2))
    try:
        return float(s.translate(sup).replace(" ", ""))
    except ValueError:
        m = re.match(r"^([0-9.]+)e(-?[0-9]+)$", s.translate(sup))
        return float(m.group(1)) * 10 ** int(m.group(2)) if m else None


def comprobar_rendimiento(ruta_json):
    print("--- Rendimiento (P6): p ajustados publicados == los que imprime el cuaderno ---")
    filas = filas_notebook(ruta_json)
    if not filas:
        fail("no se encontro la tabla de Holm en la salida del cuaderno")
        return
    # informe: | Prueba & $p$ crudo & umbral & $p$ ajustado & Decision \\
    RE_TEX = re.compile(r"^(?:Mann-Whitney[^&]*|Permutaci[oó]n[^&]*)&\s*([^&]+?)\s*&\s*([^&]+?)\s*&"
                        r"\s*([^&]+?)\s*&\s*([^\\]+?)\s*\\\\", re.M)
    RE_MD = re.compile(r"^\|\s*(?:Mann-Whitney|Permutaci)[^|]*\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|"
                       r"\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|\s*$", re.M)
    for doc, rx in ((INFORME_10, RE_TEX), (K6_README, RE_MD)):
        pub = rx.findall(leer(doc))
        if len(pub) != 3:
            fail(f"{doc}: no se reconocieron las 3 filas de la tabla de Holm de rendimiento")
            continue
        malo = False
        for i, (crudo, umbral, aj, dec) in enumerate(pub):
            c_nb, a_nb, d_nb = filas[i]
            vc, va = _cient(crudo), _cient(aj)
            if vc is None or va is None:
                fail(f"{doc}: fila {i + 1}, no se pudo leer '{crudo}' / '{aj}'")
                malo = True
                continue
            if abs(va - a_nb) > 0.02 * a_nb or abs(vc - c_nb) > 0.02 * c_nb:
                fail(f"{doc}: fila {i + 1} publica p={crudo} / ajustado={aj}; el cuaderno "
                     f"imprime {c_nb:g} / {a_nb:g}")
                malo = True
            if dec.strip().lower() != d_nb.lower():
                fail(f"{doc}: fila {i + 1} decide '{dec.strip()}'; el cuaderno dice '{d_nb}'")
                malo = True
        if not malo:
            ok(f"{doc}: las 3 filas (p crudo, ajustado y decision) coinciden con el cuaderno")


def main():
    args = sys.argv[1:]
    comprobar_lighthouse()
    comprobar_evidencia()
    comprobar_sus()
    if "--nb" in args:
        comprobar_rendimiento(args[args.index("--nb") + 1])
    print()
    if FALLOS:
        print(f"*** {len(FALLOS)} contradiccion(es) entre un documento y su dato ***")
        return 1
    print("[OK] los documentos vigentes no contradicen a sus datos")
    return 0


if __name__ == "__main__":
    sys.exit(main())
