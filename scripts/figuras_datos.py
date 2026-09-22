#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Las cifras que dibujan las figuras de rendimiento, en un solo lugar.

POR QUE EXISTE
--------------
La revision del 2026-09-21 encontro que el verificador comprobaba el TITULO de la
figura de Lighthouse leyendo scripts/gen-figuras.py, es decir el GENERADOR y no la
IMAGEN. El evaluador cambio las DOS copias del PNG por una version vieja y
`make verify` respondio [OK], afirmando encima que "la figura del informe es la
generada desde los JSON" mientras el informe dibujaba los 68/61 de localhost.

Las dos salidas obvias no sirven:

  * comparar las dos copias entre si ya estaba, y pasa en verde en cuanto se
    cambian las dos (fue exactamente el ataque);
  * comparar el PNG publicado contra una regeneracion tampoco: matplotlib
    incrusta su propia version en el archivo (chunk tEXt "Software"), asi que los
    bytes cambian de una maquina a otra y el gate fallaria en la del evaluador,
    que es la misma clase de defecto que el bloque de bytecode con Lombok.

Lo que se hace: el generador incrusta la PROCEDENCIA dentro del PNG (chunks tEXt
con el titulo dibujado, los archivos de entrada, una huella de esas entradas y las
cifras dibujadas) y el verificador la lee DE LA IMAGEN y la vuelve a derivar de los
datos versionados.

Este modulo tiene el calculo porque lo necesitan los dos lados. Si viviera
duplicado, generador y verificador podrian derivar y el gate se quedaria ciego sin
que nadie se entere. No importa matplotlib a proposito: leer una imagen no deberia
necesitarlo.

Se llama con guion bajo y no con guion porque es un modulo que se importa; los
scripts ejecutables de scripts/ van con guion.
"""
import hashlib
import json
import os
import statistics
import zlib

CATEGORIAS_LH = ("performance", "accessibility", "best-practices", "seo")

# Los titulos viven aqui, no en el generador: el verificador compara el titulo que
# lleva la imagen contra estos, para que una imagen vieja se note.
TITULO_LH = "Lighthouse -- promedio por categoria y perfil (despliegue publico real)"
TITULO_K6 = "k6 -- p95 de latencia por corrida (50 VUs pico)"
TITULO_CACHE = "GET /api/v1/universidades -- cache fria vs caliente"

DIR_LH = "docs/mediciones/perf/lighthouse/prod-runs"
GENERADOR = "scripts/gen-figuras.py"

# Corridas k6 validas. run1/run2 se excluyen a proposito: usaban una metodologia
# distinta e incompatible (login en cada iteracion, endpoint /catalogos/carreras
# inexistente) y no cumplen el umbral http_req_failed<1% (ver k6/README.md,
# seccion "Corridas invalidas conservadas como evidencia") -- mezclarlas en el
# mismo grafico que las 5 validas presentaria datos no comparables como si fueran
# mediciones de rendimiento validas.
CORRIDAS_K6 = tuple(range(3, 8))


def _huella(raiz, relativos):
    """sha256 (12 hex) de las entradas, con los saltos de linea normalizados.

    Normaliza CRLF porque el arbol de trabajo en Windows puede tener los .txt en
    CRLF y el mismo dato daria otra huella segun la maquina. Incluye el nombre de
    cada entrada para que renombrar o reordenar tambien cambie la huella.
    """
    h = hashlib.sha256()
    for rel in relativos:
        h.update(rel.encode("utf-8") + b"\0")
        with open(os.path.join(raiz, rel), "rb") as f:
            h.update(f.read().replace(b"\r\n", b"\n"))
        h.update(b"\0")
    return h.hexdigest()[:12]


def _cifras(valores):
    return "/".join("%.1f" % v for v in valores)


def lighthouse(raiz):
    """Promedios por categoria y perfil de las corridas versionadas en prod-runs/.

    Solo los .json que estan directamente en prod-runs/: las carpetas *-PREVIOUS
    guardan corridas retiradas y no entran en la figura.
    """
    d = os.path.join(raiz, DIR_LH)
    if not os.path.isdir(d):
        return None
    nombres = sorted(n for n in os.listdir(d) if n.endswith(".json"))
    if not nombres:
        return None

    perfiles = {"desktop": [], "mobile": []}
    for nombre in nombres:
        perfil = "desktop" if nombre.startswith("desktop") else "mobile"
        with open(os.path.join(d, nombre), encoding="utf-8") as f:
            datos = json.load(f)
        perfiles[perfil].append([round(datos["categories"][c]["score"] * 100)
                                 for c in CATEGORIAS_LH])

    def promedio(filas):
        if not filas:
            return [0.0] * len(CATEGORIAS_LH)
        return [sum(f[i] for f in filas) / len(filas) for i in range(len(CATEGORIAS_LH))]

    escritorio, movil = promedio(perfiles["desktop"]), promedio(perfiles["mobile"])
    fuentes = ["%s/%s" % (DIR_LH, n) for n in nombres]
    return {
        "titulo": TITULO_LH,
        "categorias": list(CATEGORIAS_LH),
        "escritorio": escritorio,
        "movil": movil,
        "fuentes": fuentes,
        "datos": "desktop=%s;mobile=%s" % (_cifras(escritorio), _cifras(movil)),
        "huella": _huella(raiz, fuentes),
    }


def k6(raiz):
    """p95 de http_req_duration por corrida k6 valida (ver CORRIDAS_K6)."""
    fuentes, etiquetas, p95s, faltantes = [], [], [], []
    for n in CORRIDAS_K6:
        rel = "k6/run%d-summary.json" % n
        if not os.path.exists(os.path.join(raiz, rel)):
            faltantes.append(rel)
            continue
        with open(os.path.join(raiz, rel), encoding="utf-8") as f:
            datos = json.load(f)
        m = datos["metrics"]["http_req_duration"]
        p95s.append(m["values"]["p(95)"] if "values" in m else m["p(95)"])
        etiquetas.append("run%d" % n)
        fuentes.append(rel)
    if not p95s:
        return None
    return {
        "titulo": TITULO_K6,
        "etiquetas": etiquetas,
        "p95": p95s,
        "faltantes": faltantes,
        "fuentes": fuentes,
        "datos": ";".join("%s=%.1f" % (e, v) for e, v in zip(etiquetas, p95s)),
        "huella": _huella(raiz, fuentes),
    }


def cache(raiz):
    """Muestras crudas de latencia con cache fria y caliente, en ms."""
    fuentes = ["k6/cache-cold-samples.txt", "k6/cache-warm-samples.txt"]
    if not all(os.path.exists(os.path.join(raiz, f)) for f in fuentes):
        return None

    def ms(rel):
        with open(os.path.join(raiz, rel), encoding="utf-8") as f:
            return [float(l.strip()) * 1000 for l in f if l.strip()]

    fria, caliente = ms(fuentes[0]), ms(fuentes[1])
    if not fria or not caliente:
        return None
    return {
        "titulo": TITULO_CACHE,
        "fria": fria,
        "caliente": caliente,
        "fuentes": fuentes,
        # n y mediana son las dos cifras que el boxplot deja leer; la huella cubre
        # el resto de la distribucion.
        "datos": "fria=n%d/mediana%.1f;caliente=n%d/mediana%.1f" % (
            len(fria), statistics.median(fria), len(caliente), statistics.median(caliente)),
        "huella": _huella(raiz, fuentes),
    }


# Las tres figuras que el informe incluye (10-evaluacion-empirica.tex).
FIGURAS = (
    ("fig-lighthouse-scores.png", lighthouse),
    ("fig-k6-p95-por-corrida.png", k6),
    ("fig-cache-fria-vs-caliente.png", cache),
)


def metadatos(datos):
    """Los chunks tEXt que el generador incrusta y el verificador vuelve a derivar."""
    return {
        "Title": datos["titulo"],
        "Fuentes": ",".join(datos["fuentes"]),
        "Huella": datos["huella"],
        "Datos": datos["datos"],
        "Generador": GENERADOR,
    }


def _chunks_png(datos):
    """Recorre un PNG y devuelve [(tipo, inicio, fin_con_crc)] desde la firma."""
    if datos[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    fuera, i = [], 8
    while i + 8 <= len(datos):
        largo = int.from_bytes(datos[i:i + 4], "big")
        tipo = datos[i + 4:i + 8]
        fuera.append((tipo, i, i + 12 + largo))
        if tipo == b"IEND":
            break
        i += 12 + largo
    return fuera


def huella_pixeles(ruta):
    """sha256 (12 hex) de los datos de imagen (chunks IDAT) de un PNG.

    Es lo que se ve, no lo que el archivo dice de si mismo. La revision del
    2026-09-22 senalo que comprobar solo los metadatos deja pasar "una imagen
    antigua con los metadatos nuevos": esta huella se incrusta en la propia imagen
    al generarla, asi que una imagen a la que se le copien los metadatos de otra
    delata que sus pixeles no son los que su procedencia declara.
    """
    with open(ruta, "rb") as f:
        datos = f.read()
    trozos = _chunks_png(datos)
    if trozos is None:
        return None
    h = hashlib.sha256()
    for tipo, ini, fin in trozos:
        if tipo == b"IDAT":
            h.update(datos[ini + 8:fin - 4])
    return h.hexdigest()[:12]


def sellar_pixeles(ruta):
    """Incrusta en el PNG la huella de sus propios pixeles, como chunk tEXt.

    Se hace despues de escribir el archivo porque la huella no existe hasta que la
    imagen esta rasterizada. Devuelve la huella incrustada.
    """
    huella = huella_pixeles(ruta)
    with open(ruta, "rb") as f:
        datos = f.read()
    trozos = _chunks_png(datos)
    cuerpo = b"Pixeles\x00" + huella.encode("ascii")
    chunk = (len(cuerpo).to_bytes(4, "big") + b"tEXt" + cuerpo
             + zlib.crc32(b"tEXt" + cuerpo).to_bytes(4, "big"))
    ini_iend = next(ini for tipo, ini, _ in trozos if tipo == b"IEND")
    with open(ruta, "wb") as f:
        f.write(datos[:ini_iend] + chunk + datos[ini_iend:])
    return huella


def leer_texto_png(ruta):
    """Chunks tEXt de un PNG como {clave: valor}, sin dependencias externas.

    Devuelve None si el archivo no es un PNG. Recorre la estructura de chunks
    (largo, tipo, datos, crc) desde la firma de 8 bytes.
    """
    with open(ruta, "rb") as f:
        datos = f.read()
    if datos[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    texto, i = {}, 8
    while i + 8 <= len(datos):
        largo = int.from_bytes(datos[i:i + 4], "big")
        tipo = datos[i + 4:i + 8]
        if tipo == b"tEXt":
            clave, _, valor = datos[i + 8:i + 8 + largo].partition(b"\0")
            texto[clave.decode("latin-1")] = valor.decode("latin-1")
        elif tipo == b"IEND":
            break
        i += 12 + largo
    return texto
