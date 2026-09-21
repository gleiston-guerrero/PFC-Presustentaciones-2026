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
              declaran esa misma URL; y el titulo que lleva DENTRO la imagen
              publicada dice lo mismo que el pie del informe.
  figuras     las tres figuras del informe llevan su procedencia incrustada en el
              PNG (titulo dibujado, entradas, huella de las entradas y cifras
              dibujadas) y coincide con volver a derivarla de los datos
              versionados; y las dos copias de cada una son el mismo archivo.
  evidencia   todo archivo del repositorio que un documento vigente cita existe.
  sus         media, DE, IC 95 % y alfa publicados == recalculados del CSV.
              p ajustados y decision de Holm publicados == recalculados; y el
              parrafo que los publica no afirma lo contrario de la decision.
  juicios     ningun archivo publico juzga la situacion academica de los companeros
              (reprobaron, se retiro de la carrera...); es el hecho que el expediente
              necesita declarar el que importa: quien ejecuto los commits.
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
import hashlib
import importlib.util
import io
import json
import math
import os
import re
import statistics
import subprocess
import sys
import tempfile

# Importar cifras-publicadas.py dejaria un .pyc en scripts/__pycache__ y el chequeo de
# higiene de verify.sh lo marcaria como suciedad que este mismo script produjo.
sys.dont_write_bytecode = True

# El calculo de las cifras de las figuras es el mismo que usa el generador: vive en un
# solo lugar a proposito (ver el encabezado de figuras_datos.py). La ruta se pone a mano
# para no depender de desde donde se invoque este script.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import figuras_datos as figdat  # noqa: E402

CSV_FORM = "docs/mediciones/sus/re-aplicacion/sus-respuestas-formulario.csv"
CSV_PAPEL = "docs/mediciones/sus/sus-respuestas.csv"
# Las dos exportaciones del mismo formulario, por caminos distintos: la hoja de
# respuestas (18-sep) y la descarga directa desde el formulario (21-sep).
CSV_CRUDO = "docs/mediciones/sus/re-aplicacion/respuestas-formulario-2026-09-18.csv"
CSV_DESCARGA = "docs/mediciones/sus/re-aplicacion/respuestas-descarga-formulario-2026-09-21.csv"
README_SUS = "docs/mediciones/sus/re-aplicacion/README.md"
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

    # El TITULO tiene que decir lo mismo que el pie del informe, y se lee DE LA IMAGEN.
    # Antes se leia el fuente de scripts/gen-figuras.py: la revision del 21-sep cambio las
    # DOS copias del PNG por una version vieja y esto seguia pasando en verde, porque el
    # generador no habia cambiado. La comparacion de las dos copias entre si y la
    # procedencia incrustada estan en comprobar_figuras().
    inf = "Informe-Final/figuras/fig-lighthouse-scores.png"
    titulo = (figdat.leer_texto_png(inf) or {}).get("Title", "")
    if not titulo:
        fail(f"{inf} no lleva el titulo incrustado: no se puede comprobar la imagen, solo "
             f"el generador (regenera con make docs)")
    elif "despliegue" not in titulo.lower() or "build" in titulo.lower():
        fail(f"el titulo dentro de la imagen ('{titulo}') no dice que las corridas son "
             f"contra el despliegue publico, que es lo que declara el pie del informe")
    else:
        ok("el titulo dentro de la imagen coincide con el pie del informe (despliegue publico)")

    mk = leer("Makefile")
    m = re.search(r"^LH_URL\s*\?=\s*(\S+)", mk, re.M)
    if not m or host not in m.group(1):
        fail(f"Makefile: LH_URL por defecto ({m.group(1) if m else 'ausente'}) "
             f"no es el servidor que miden los JSON")
    else:
        ok("make bench-lh mide por defecto el mismo servidor")


# ------------------------------------------------------------------- figuras
def comprobar_figuras():
    """Cada figura publicada lleva su procedencia DENTRO del PNG, y coincide.

    Hallazgo de la revision del 2026-09-21: el chequeo del titulo leia el fuente del
    generador, asi que el evaluador cambio las DOS copias del PNG por una version
    vieja y `make verify` respondio [OK] -- y encima afirmo que "la figura del
    informe es la generada desde los JSON" mientras el informe dibujaba los 68/61 de
    localhost.

    Comparar las dos copias entre si (lo que ya habia) no ve nada cuando se cambian
    las dos. Comparar los bytes contra una regeneracion tampoco sirve: matplotlib
    incrusta su version en el PNG, asi que fallaria en la maquina del evaluador, que
    es la misma clase de defecto que el bloque de bytecode con Lombok. Lo que se
    compara es la procedencia incrustada (titulo, entradas, huella de las entradas y
    cifras dibujadas) contra la que sale de volver a derivarla de los datos
    versionados, con el mismo calculo que usa el generador (scripts/figuras_datos.py).
    """
    print("--- Figuras: la procedencia va dentro de la imagen ---")
    raiz = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    for nombre, calcular in figdat.FIGURAS:
        gen = "docs/mediciones/perf/figuras/" + nombre
        inf = "Informe-Final/figuras/" + nombre
        if not (os.path.exists(gen) and os.path.exists(inf)):
            fail(f"falta una de las dos copias de {nombre} ({gen}, {inf})")
            continue
        if open(gen, "rb").read() != open(inf, "rb").read():
            fail(f"{inf} difiere de la generada ({gen}): el informe dibuja otra "
                 f"medicion (make docs regenera y copia; luego make pdf)")
            continue
        datos = calcular(raiz)
        if datos is None:
            fail(f"{nombre}: no se pudieron derivar las cifras de sus entradas")
            continue
        esperado = figdat.metadatos(datos)
        real = figdat.leer_texto_png(inf) or {}
        if not any(c in real for c in esperado):
            # Caso del ataque de la revision: una imagen vieja, de antes de que el
            # generador incrustara la procedencia. Un solo FAIL, no uno por clave.
            fail(f"{inf}: la imagen no lleva procedencia incrustada, asi que no es la que "
                 f"genera make docs hoy (se esperaba la huella {esperado['Huella']} de "
                 f"{len(datos['fuentes'])} entradas): es una version vieja o hecha por fuera")
            continue
        distintos = [c for c in esperado if real.get(c) != esperado[c]]
        for c in distintos:
            fail(f"{inf}: la procedencia incrustada no coincide con las entradas "
                 f"versionadas -- {c}: la imagen dice {real.get(c) or '(ausente)'!r} y "
                 f"los datos dan {esperado[c]!r} (regenera con make docs)")
        if not distintos:
            ok(f"{nombre}: la imagen lleva su procedencia y coincide con sus "
               f"{len(datos['fuentes'])} entradas (huella {esperado['Huella']}, "
               f"{esperado['Datos']})")


# ------------------------------------------------------- procedencia del SUS
def _fecha_iso(texto):
    """Fecha en ISO desde cualquiera de los dos formatos de exportacion."""
    m = re.search(r"(\d{4})/(\d{2})/(\d{2})", texto)          # 2026/09/18
    if m:
        return "%s-%s-%s" % m.groups()
    m = re.search(r"(\d{2})/(\d{2})/(\d{4})", texto)          # 18/09/2026
    if m:
        return "%s-%s-%s" % (m.group(3), m.group(2), m.group(1))
    return None


def _filas_exportacion(ruta):
    """(fecha, hora, consentimiento, rol, previo, q1..q10) de una exportacion.

    Las dos exportaciones traen el mismo dato con distinto formato: la descarga
    directa envuelve la linea entera entre comillas y escribe la fecha al reves.
    Se comparan los DATOS, no los bytes: el CSV que genera el formulario no es
    estable byte a byte entre descargas, y exigir bytes iguales haria fallar al
    verificador por como Google dibuja el archivo, no por lo que dice.
    """
    crudas = []
    for fila in csv.reader(io.open(ruta, encoding="utf-8-sig", newline="")):
        if not fila or not any(c.strip() for c in fila):
            continue
        crudas.append(next(csv.reader([fila[0]])) if len(fila) == 1 else fila)
    datos = []
    for fila in crudas[1:]:
        hora = re.search(r"\d{1,2}:\d{2}:\d{2}", fila[0])
        fecha = _fecha_iso(fila[0])
        if not hora or not fecha or len(fila) < 14:
            return None
        datos.append((fecha, hora.group(0), fila[1].strip(), fila[2].strip(),
                      fila[3].strip(), tuple(c.strip() for c in fila[4:14])))
    return datos


def comprobar_sus_procedencia():
    """Las cifras del SUS salen de la exportacion del formulario, y se puede probar.

    La revision del 21-sep dejo P1 en 70 %: *«las respuestas y el recalculo cuadran,
    pero su procedencia se apoya en capturas y no en una exportacion del servidor»*.
    La exportacion estaba versionada desde el 18-sep, pero ningun chequeo ataba las
    cifras publicadas a ella: todos leian la tabla ya procesada. Esto cierra ese
    tramo, que es el unico que se puede cerrar con codigo.
    """
    print("--- SUS: la tabla publicada sale de la exportacion del formulario ---")
    for ruta in (CSV_CRUDO, CSV_DESCARGA, CSV_FORM):
        if not os.path.exists(ruta):
            fail(f"falta {ruta}: no se puede comprobar la procedencia del SUS")
            return

    # 1. La tabla publicada se vuelve a derivar de la exportacion cruda.
    with tempfile.TemporaryDirectory() as tmp:
        derivado = os.path.join(tmp, "derivado.csv")
        r = subprocess.run([sys.executable, "-B", "scripts/sus-ingesta.py", CSV_CRUDO,
                            "--salida", derivado], capture_output=True, text=True,
                           encoding="utf-8", errors="replace")
        if r.returncode != 0:
            fail(f"sus-ingesta.py no pudo releer {CSV_CRUDO}: {r.stderr.strip()[:160]}")
        else:
            a = leer(derivado).replace("\r\n", "\n")
            b = leer(CSV_FORM).replace("\r\n", "\n")
            if a != b:
                fail(f"{CSV_FORM} no es lo que produce sus-ingesta.py desde {CSV_CRUDO}: "
                     f"la tabla publicada no se deriva de la exportacion (rehazla con "
                     f"python scripts/sus-ingesta.py {CSV_CRUDO})")
            else:
                ok(f"la tabla publicada se rederiva exacta de {os.path.basename(CSV_CRUDO)}")

    # 2. Las dos exportaciones, por caminos distintos, dicen lo mismo.
    hoja, descarga = _filas_exportacion(CSV_CRUDO), _filas_exportacion(CSV_DESCARGA)
    if hoja is None or descarga is None:
        fail("alguna exportacion del SUS no tiene el formato esperado (fecha, hora y 10 items)")
    elif len(hoja) != len(descarga):
        fail(f"las dos exportaciones no traen el mismo numero de respuestas: "
             f"{len(hoja)} en la hoja y {len(descarga)} en la descarga directa")
    else:
        distintas = [i for i, (x, y) in enumerate(zip(hoja, descarga), start=1) if x != y]
        if distintas:
            fail(f"las dos exportaciones del formulario discrepan en la(s) fila(s) "
                 f"{distintas}: el mismo formulario deberia dar el mismo dato")
        else:
            ok(f"las {len(hoja)} respuestas coinciden en las dos exportaciones "
               f"(hoja de respuestas y descarga directa)")

    # 3. La huella publicada es la del archivo: asi el evaluador, que tiene acceso de
    #    editor al formulario, exporta por su cuenta y compara sin creerle a nadie.
    huella = hashlib.sha256(io.open(CSV_CRUDO, "rb").read().replace(b"\r\n", b"\n")).hexdigest()
    if huella not in leer(README_SUS):
        fail(f"{README_SUS} no publica la huella sha256 de {os.path.basename(CSV_CRUDO)} "
             f"({huella[:16]}...): sin ella el evaluador no puede contrastar su propia "
             f"exportacion contra la versionada")
    else:
        ok(f"{os.path.basename(README_SUS)} publica la huella de la exportacion ({huella[:16]}...)")


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


# ------------------------------------------------------------- hashes de commit
RE_HASH = re.compile(r"(?<![0-9A-Za-z_/.=#-])([0-9a-f]{7,10}|[0-9a-f]{40})(?![0-9A-Za-z_])")


def _git(*a):
    return subprocess.run(["git", *a], capture_output=True, text=True,
                          encoding="utf-8", errors="replace").stdout.strip()


def comprobar_hashes():
    """Todo hash de commit citado en un documento vigente existe, ES un commit y es alcanzable.

    La revision final (2026-09-21) encontro `b1efc83`, citado como "commit anterior confirmado
    como ancestro -- sigue alcanzable", que no existe en un clon limpio. Era el hash del OBJETO
    DE ETIQUETA que la etiqueta tuvo antes de moverse: `git cat-file -t` lo da como `tag`, no
    como `commit`, y ninguna referencia lo alcanza. En la maquina de quien lo escribio existia
    (el objeto sigue en la base de objetos local), asi que un `git cat-file -e` no lo habria
    visto: por eso se exige el tipo y la alcanzabilidad desde las referencias que ve un clon
    (ramas, ramas remotas y etiquetas).

    Se saltan las lineas que hablan de md5/sha256/checksum: sus 8 caracteres hexadecimales no
    son de un commit.
    """
    print("--- Hashes citados: cada commit citado existe y es alcanzable ---")
    citas, malas, vistos = 0, [], {}
    for f in archivos_vigentes(exts=(".md", ".tex", ".cff")):
        for n, linea in enumerate(leer(f).splitlines(), 1):
            if re.search(r"md5|sha-?256|checksum", linea, re.I):
                continue
            for m in RE_HASH.finditer(linea):
                h = m.group(1)
                if not re.search(r"[a-f]", h) or not re.search(r"\d", h):
                    continue
                citas += 1
                if h not in vistos:
                    tipo = _git("cat-file", "-t", h)
                    if tipo != "commit":
                        vistos[h] = f"no es un commit (tipo: {tipo or 'no existe'})"
                    elif not _git("for-each-ref", "--contains", h, "refs/heads", "refs/remotes",
                                  "refs/tags", "--count=1"):
                        vistos[h] = "es un commit pero ninguna rama ni etiqueta lo alcanza"
                    else:
                        vistos[h] = None
                if vistos[h]:
                    malas.append((f, n, h, vistos[h]))
    if citas < 100:
        fail(f"solo se reconocieron {citas} hashes de commit: la regla dejo de casar")
    elif malas:
        for f, n, h, why in malas:
            fail(f"{f}:{n} cita `{h}`, y {why}: un clon limpio no lo tiene")
    else:
        ok(f"{citas} hashes de commit citados ({len(vistos)} distintos): todos existen y son alcanzables")


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

    # Los p CRUDOS en prosa ("t = 0,519, p = 0,608"): la revision final (punto 5a) vio que solo se
    # comprobaban los ajustados y la tabla, de modo que un p crudo tecleado a mano en un parrafo
    # (0,608 -> 0,008) no lo veia nadie. Se comprueba cada "p = 0,xxx" de un parrafo del SUS
    # contra los p crudos y ajustados calculados, con o sin la palabra "Holm".
    RE_PCRUDO = re.compile(r"(?<![\w<>])p\s*\$?\s*=\s*\$?\s*[−-]?(0[.,]\d{2,4})(?!\d)")
    conocidos = esperados_p | {round(h["p_crudo"], 4) for h in holm} | {0.05, 0.10}
    crudos, malos_crudos = 0, 0
    for f in archivos_vigentes():
        for parr in re.split(r"\n\s*\n", leer(f)):
            if not re.search(r"SUS|formulario|papel|hojas|Welch", parr):
                continue
            if re.search(r"Permutaci|k6|Lighthouse|latencia|p95|p50", parr):
                continue  # el contraste de rendimiento tiene sus propios p (P6)
            for m in RE_PCRUDO.finditer(parr):
                v = num(m.group(1))
                crudos += 1
                if not any(abs(v - p) < 5e-4 + 1e-9 for p in conocidos):
                    fail(f"{f}: cita p = {m.group(1)} en prosa del SUS; el calculo da p crudos "
                         f"{sorted(round(h['p_crudo'], 3) for h in holm)} y ajustados "
                         f"{sorted(round(h['p_ajustado'], 3) for h in holm)}")
                    malos_crudos += 1
    if crudos < 2:
        fail(f"solo se reconocieron {crudos} p crudos en prosa del SUS: la regla dejo de casar")
    elif not malos_crudos:
        ok(f"{crudos} p crudos citados en prosa del SUS coinciden con el calculo")

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


# ------------------------------------------------ juicios sobre las personas del equipo
# La revision final (7.4) encontro, en cuatro archivos publicos, el juicio academico sobre los
# companeros que ya se habia retirado de la caratula ("reprobaron la materia", "se retiro de la
# carrera"). Reaparecio por copia en otros archivos: la correccion a mano no bastaba. El hecho que
# el expediente necesita declarar es quien ejecuto los commits, no la situacion academica de un
# tercero, y menos en un repositorio publico y sin su firma.
JUICIOS = [
    re.compile(r"reprob(?:aron|[oó])\s+(?:la\s+)?materia", re.I),
    re.compile(r"(?:se\s+)?retir(?:aron|[oó])\s+de\s+la\s+carrera", re.I),
    re.compile(r"ya\s+no\s+forma(?:n)?\s+parte\s+del\s+programa", re.I),
    re.compile(r"(?:Moncayo|Zamora|Barreto|Xavier|Heider|Dominick|Carla)[^.\n]{0,120}\breprob", re.I),
    re.compile(r"no\s+est[aá]n\s+trabajando\s+en\s+las\s+observaciones", re.I),
    re.compile(r"per[ií]odo\s+regular[^.\n]{0,80}(?:reprob|integrantes)", re.I),
]
# El propio detector y sus mutaciones nombran las frases; no son un juicio.
EXENTOS_JUICIOS = ("scripts/ev2-documental.py", "scripts/mutaciones-gate.py")


def comprobar_juicios():
    print("--- Personas del equipo: ningun juicio academico en archivos publicos ---")
    r = subprocess.run(["git", "ls-files"], capture_output=True, text=True,
                       encoding="utf-8", errors="replace")
    revisados, hallados = 0, []
    for f in r.stdout.splitlines():
        if f in EXENTOS_JUICIOS or not f.endswith((".md", ".tex", ".cff", ".txt", ".yml", ".yaml", ".py", ".sh")):
            continue
        if f.startswith(("Frontend/", "backend/src/", "node_modules/")):
            continue
        revisados += 1
        for i, linea in enumerate(leer(f).splitlines(), 1):
            for rx in JUICIOS:
                if rx.search(linea):
                    hallados.append((f, i, " ".join(linea.split())[:110]))
                    break
    if revisados < 100:
        fail(f"solo se revisaron {revisados} archivos: la lista de archivos dejo de casar")
        return
    for f, i, l in hallados:
        fail(f"{f}:{i}: juicio sobre la situacion academica de una persona del equipo: '{l}'")
    if not hallados:
        ok(f"{revisados} archivos publicos revisados, ningun juicio sobre la situacion academica de los companeros")


def main():
    args = sys.argv[1:]
    comprobar_lighthouse()
    comprobar_figuras()
    comprobar_sus_procedencia()
    comprobar_evidencia()
    comprobar_hashes()
    comprobar_sus()
    comprobar_juicios()
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
