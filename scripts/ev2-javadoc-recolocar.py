#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Mueve arriba de las anotaciones los bloques Javadoc que quedaron debajo.

Complementa a ev2-javadoc-colocacion.py, que solo detecta. Aqui se corrige.

DOS CASOS, Y POR QUE IMPORTAN
-----------------------------
(a) El elemento NO tiene Javadoc antes de las anotaciones. Entonces basta con
    mover el bloque intacto por encima del grupo de anotaciones.

(b) El elemento YA tiene un Javadoc arriba (frecuente en los repositorios: una
    linea de contexto sobre por que existe la consulta) y ademas uno abajo con
    las etiquetas @param/@return. Mover el de abajo dejaria dos bloques
    seguidos, y javac asocia solo el ultimo: se perderia la prosa del primero.
    Aqui se FUSIONAN -- se conserva la prosa del bloque de arriba y se le
    anaden las etiquetas del de abajo que aun no tenga, sin duplicar ninguna.

Es una transformacion sobre comentarios: no toca una sola linea de codigo
ejecutable. Aun asi, despues hay que recompilar, que es lo que decide.

USO
    python scripts/ev2-javadoc-recolocar.py --dry-run   # que haria
    python scripts/ev2-javadoc-recolocar.py --aplicar   # lo hace
"""
import glob
import io
import re
import sys

ANOT = r"@[\w.]+(?:\s*\((?:[^()]|\((?:[^()]|\([^()]*\))*\))*\))?"
RE_GRUPO = re.compile(
    r"(?P<sangria>[ \t]*)(?P<anots>" + ANOT + r"(?:[ \t]*\n[ \t]*" + ANOT + r")*)"
    r"[ \t]*\n(?P<pre>[ \t]*)(?P<doc>/\*\*(?:[^*]|\*(?!/))*\*/)")

RE_DOC_ARRIBA = re.compile(r"/\*\*(?:[^*]|\*(?!/))*\*/[ \t]*\n[ \t]*$")
RE_ETIQUETA = re.compile(r"^\s*\*\s*(@\w+)(?:\s+(\S+))?", re.M)


def etiquetas(doc):
    """Las etiquetas que ya declara un bloque, como (@param, nombre) o (@return, None)."""
    out = set()
    for m in RE_ETIQUETA.finditer(doc):
        out.add((m.group(1), m.group(2) if m.group(1) == "@param" else None))
    return out


def lineas_de_etiquetas(doc, ya):
    """Las lineas de etiqueta de `doc` que no estan ya en `ya`."""
    nuevas = []
    for linea in doc.splitlines():
        m = RE_ETIQUETA.match(linea)
        if not m:
            continue
        clave = (m.group(1), m.group(2) if m.group(1) == "@param" else None)
        if clave in ya:
            continue
        ya.add(clave)
        nuevas.append(linea.strip().lstrip("*").strip())
    return nuevas


def fusionar(doc_arriba, doc_abajo, sangria):
    """Conserva la prosa de arriba y le anade las etiquetas que le falten."""
    ya = etiquetas(doc_arriba)
    nuevas = lineas_de_etiquetas(doc_abajo, ya)
    if not nuevas:
        return doc_arriba
    cuerpo = doc_arriba.rstrip()
    assert cuerpo.endswith("*/")
    # Un bloque de una sola linea hay que abrirlo antes de anadirle etiquetas.
    if "\n" not in cuerpo:
        interior = cuerpo[3:-2].strip()
        lineas = [f"{sangria}/**", f"{sangria} * {interior}"]
    else:
        lineas = cuerpo[:-2].rstrip().splitlines()
    for n in nuevas:
        lineas.append(f"{sangria} * {n}")
    lineas.append(f"{sangria} */")
    return "\n".join(lineas)


def inicio_grupo_anotaciones(lineas, i):
    """Primera linea del grupo de anotaciones que precede a la linea i, o None.

    Sube equilibrando parentesis. La version anterior buscaba el grupo con una
    expresion regular y no veia un `@Query` partido en varios literales
    concatenados cuyo JPQL trae parentesis: dejaba sin tocar 5 bloques mal
    colocados que si detecta esta version (AuditRepository, MinutesRepository,
    SubmissionRepository x2, TopicProposedRepository).
    """
    j = i - 1
    primera = None
    while j >= 0:
        s = lineas[j].strip()
        if not s:
            j -= 1
            continue
        saldo = s.count(')') - s.count('(')
        if saldo > 0:
            k = j
            while k >= 0 and saldo > 0:
                k -= 1
                if k < 0:
                    return primera
                saldo += lineas[k].strip().count(')') - lineas[k].strip().count('(')
            t = lineas[k].strip() if k >= 0 else ''
            if t.startswith('@') and '{' not in t and not t.endswith((';', '}')):
                primera = k
                j = k - 1
                continue
            return primera
        if s.startswith('@') and '{' not in s and not s.endswith((';', '}')):
            primera = j
            j -= 1
            continue
        return primera
    return primera


def recolocar_lineas(texto):
    """Mueve por lineas los bloques que la version basada en regex no alcanza."""
    lineas = texto.split('\n')
    movidos = fusionados = 0
    i = 0
    while i < len(lineas):
        if not lineas[i].strip().startswith('/**'):
            i += 1
            continue
        ini_anot = inicio_grupo_anotaciones(lineas, i)
        if ini_anot is None:
            i += 1
            continue
        fin = i
        while fin < len(lineas) and not lineas[fin].strip().endswith('*/'):
            fin += 1
        if fin >= len(lineas):
            break
        sangria = lineas[ini_anot][:len(lineas[ini_anot]) - len(lineas[ini_anot].lstrip())]
        doc = lineas[i:fin + 1]
        resto = lineas[:ini_anot] + lineas[ini_anot:i] + lineas[fin + 1:]
        # renormaliza la sangria del bloque al nivel de las anotaciones
        doc = [sangria + l.strip() if l.strip().startswith(('*', '/**')) else l
               for l in doc]
        doc = [(sangria + ' ' + l.strip()) if l.strip().startswith('*') and not l.strip().startswith('/**')
               else (sangria + l.strip()) for l in doc]
        lineas = resto[:ini_anot] + doc + resto[ini_anot:]
        movidos += 1
        i = ini_anot + len(doc) + 1
    return '\n'.join(lineas), movidos, fusionados


def procesar(texto):
    """Devuelve (texto_nuevo, movidos, fusionados)."""
    movidos = fusionados = 0
    while True:
        m = RE_GRUPO.search(texto)
        if not m:
            break
        sangria = m.group("sangria")
        anots = m.group("anots")
        doc = m.group("doc")
        antes = texto[:m.start()]
        despues = texto[m.end():]

        prev = RE_DOC_ARRIBA.search(antes)
        if prev:
            nuevo_doc = fusionar(prev.group(0).rstrip(), doc, sangria)
            # `antes` ya conserva la sangria que precedia al bloque original: si
            # el bloque nuevo trae la suya, se suman y el `/**` queda corrido.
            antes = antes[:prev.start()] + nuevo_doc.lstrip() + "\n"
            fusionados += 1
            bloque = sangria + anots
        else:
            bloque = sangria + doc.replace("\n" + m.group("pre"), "\n" + sangria) \
                + "\n" + sangria + anots
            movidos += 1
        texto = antes + bloque + despues
    return texto, movidos, fusionados


def main():
    aplicar = "--aplicar" in sys.argv
    if not aplicar and "--dry-run" not in sys.argv:
        print(__doc__)
        return 2

    tot_m = tot_f = tot_a = 0
    for f in sorted(glob.glob("backend/src/main/java/**/*.java", recursive=True)):
        txt = io.open(f, encoding="utf-8", errors="replace").read()
        nuevo, m, fu = procesar(txt)
        # Segunda pasada por lineas, para los grupos de anotaciones que la
        # expresion regular no alcanza (un @Query concatenado con parentesis).
        nuevo, m2, _ = recolocar_lineas(nuevo)
        m += m2
        if m or fu:
            tot_m += m
            tot_f += fu
            tot_a += 1
            print(f"  {f.replace(chr(92), '/')}: {m} movido(s), {fu} fusionado(s)")
            if aplicar:
                io.open(f, "w", encoding="utf-8", newline="").write(nuevo)

    print(f"\n{tot_a} archivo(s): {tot_m} bloque(s) movidos, {tot_f} fusionados.")
    if not aplicar:
        print("(--dry-run: no se escribio nada)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
