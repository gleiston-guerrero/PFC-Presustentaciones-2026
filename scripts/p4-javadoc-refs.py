#!/usr/bin/env python3
"""P4 -- Actualiza las REFERENCIAS a identificadores dentro de los comentarios.

EL EQUILIBRIO QUE RESUELVE
--------------------------
`p4-renombrar.py` protege los comentarios enteros, y eso es deliberado: la
evaluacion del 2026-09-17 senala que el renombrado anterior "dano" 164
comentarios ("para openlo en el navegador sin downloadlo"), porque el reemplazo
entro en la prosa en espanol de los Javadoc.

Pero un Javadoc no es solo prosa. Tambien contiene **referencias** al codigo:
`@param <nombre>`, `{@link Clase#metodo}`, `@see`, `@throws`. Esas si nombran
identificadores, y si no se renombran junto con ellos, `doclint` falla. Tras el
renombrado quedaban 615 errores de `javadoc:javadoc`, todos de dos tipos:
"reference not found" y "@param name not found".

Este script recorre solo los comentarios y renombra **unicamente** lo que es una
referencia:

  - el nombre del parametro en `@param <nombre>` (y su descripcion, si la
    descripcion es exactamente el mismo nombre repetido, que es el patron que
    deja la documentacion autogenerada)
  - el contenido de `{@link ...}` y `{@linkplain ...}`
  - el tipo en `@see`, `@throws` y `@exception`
  - el contenido de `{@code ...}` solo cuando es un identificador suelto, no una
    frase

La prosa no se toca.

Uso:  python scripts/p4-javadoc-refs.py [--dry-run]
"""
import importlib.util
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
_spec = importlib.util.spec_from_file_location(
    "p4_renombrar", os.path.join(HERE, "p4-renombrar.py"))
_ren = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_ren)

# Mapa acumulado de todos los lotes, aplicado de mayor a menor longitud.
MAPA = {}
for lote in _ren.LOTES.values():
    MAPA.update(lote)
TOKENS = sorted(MAPA, key=len, reverse=True)

ID = r'[A-Za-z_][\w.#()<>,\[\]\s]*'


def renombrar_ref(txt):
    """Aplica el mapa a un fragmento que sabemos que es una referencia."""
    for tok in TOKENS:
        txt = _ren.patron(tok).sub(MAPA[tok], txt)
    return txt


def procesar_comentario(c):
    # {@link X} / {@linkplain X}
    c = re.sub(r'(\{@link(?:plain)?\s+)([^}]+)(\})',
               lambda m: m.group(1) + renombrar_ref(m.group(2)) + m.group(3), c)
    # {@code X} solo si es un identificador suelto (sin espacios ni puntuacion)
    c = re.sub(r'(\{@code\s+)([A-Za-z_][\w.#()]*)(\})',
               lambda m: m.group(1) + renombrar_ref(m.group(2)) + m.group(3), c)
    # @see / @throws / @exception TIPO
    c = re.sub(r'(@(?:see|throws|exception)\s+)([A-Za-z_][\w.#()]*)',
               lambda m: m.group(1) + renombrar_ref(m.group(2)), c)

    # @param nombre [descripcion]
    def param(m):
        pre, nombre, resto = m.group(1), m.group(2), m.group(3)
        nuevo = renombrar_ref(nombre)
        # la documentacion autogenerada repite el nombre como descripcion
        if resto.strip() == nombre:
            resto = resto.replace(nombre, nuevo)
        return pre + nuevo + resto
    c = re.sub(r'(@param\s+)(<?[A-Za-z_]\w*>?)([^\n]*)', param, c)
    return c


def main():
    dry = "--dry-run" in sys.argv
    tocados = 0
    for p in _ren.archivos():
        txt = open(p, encoding="utf-8", errors="replace").read()
        piezas, pos = [], 0
        cambio = False
        for a, b, clase in _ren._cadenas(txt):
            if clase not in ("line", "block"):
                continue
            nuevo = procesar_comentario(txt[a:b])
            if nuevo != txt[a:b]:
                cambio = True
            piezas.append(txt[pos:a])
            piezas.append(nuevo)
            pos = b
        if not cambio:
            continue
        piezas.append(txt[pos:])
        if not dry:
            open(p, "w", encoding="utf-8").write("".join(piezas))
        tocados += 1
    print(f"Comentarios con referencias actualizadas en {tocados} archivos"
          + ("  [DRY RUN]" if dry else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
