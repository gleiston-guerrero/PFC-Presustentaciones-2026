#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""P3 -- Contenido del Javadoc: @param que repiten el nombre, y bloques mal colocados.

POR QUE EXISTE
--------------
La revision del 19-sep dio P3 por "Cumple con reservas": por AST el 100 % de los
metodos publicos tiene Javadoc estructuralmente completo, pero "el contenido es
de plantilla: el 29 % de los `@param` son tautologicos". Es el mismo defecto de
siempre visto desde otro lado: un Javadoc que `javadoc` da por bueno y que un
lector no aprovecha.

    @param submissionId submissionId          <- tautologico
    @param studentId id del student           <- tautologico (traduce el nombre)
    @param submissionId identificador de la solicitud de pre-sustentacion  <- dice algo

QUE CUENTA COMO TAUTOLOGICO
---------------------------
Un `@param` cuya descripcion, una vez quitadas las palabras vacias (articulos,
preposiciones), NO aporta ninguna palabra que no este ya en el nombre del
parametro (partido en camelCase). "id de la submission" para `submissionId`
cae aqui: solo repite `id` y `submission`.

Es una medida deliberadamente estricta y mecanica, para poder comprobarla en
cada `make verify` en lugar de discutirla. No mide si la descripcion es BUENA,
solo si es distinta del nombre.

DOS DEFECTOS ESTRUCTURALES QUE ESTA MISMA MEDICION DESTAPO
----------------------------------------------------------
Al reescribir los @param aparecieron dos cosas que ni `javadoc` ni doclint dicen:

  * Javadoc APILADOS: un bloque de prosa real seguido de otro generado. `javadoc`
    solo enlaza el ULTIMO, asi que la prosa del primero se perdia en silencio
    (5 casos: MinutesRepository, SubmissionRepository x2, TopicProposedRepository,
    TopicController).
  * Javadoc DENTRO de un bloque de texto: uno de los generadores automaticos metio
    un comentario `/** ... */` en mitad de una consulta JPQL de ScheduleRepository.
    Hibernate lo tolera como comentario, asi que ninguna prueba fallaba, pero es
    basura dentro de la consulta.

Ambos se comprueban ahora en cada corrida.

Uso:
    python scripts/p3-param-tautologicos.py            # resumen
    python scripts/p3-param-tautologicos.py --lista    # cada caso

Sale con 1 si supera el umbral o si hay un defecto estructural.
"""
import collections
import glob
import io
import os
import re
import sys

sys.dont_write_bytecode = True

UMBRAL = 5.0   # % maximo tolerado
RAIZ = "backend/src/main/java"

VACIAS = set("el la los las de del un una unos unas en a al y o para por con que se su sus es".split())

# Un bloque Javadoc seguido de otro (con, a lo sumo, comentarios // entre medias).
RE_APILADOS = re.compile(r"\*/[ \t]*\r?\n(?:[ \t]*//[^\n]*\n)*[ \t]*/\*\*")


def tokens(s):
    s = re.sub(r"([a-z0-9])([A-Z])", r"\1 \2", s)
    return [t for t in re.findall(r"[a-záéíóúñ0-9]+", s.lower()) if t not in VACIAS]


def fuentes():
    return sorted(glob.glob(os.path.join(RAIZ, "**", "*.java"), recursive=True))


def recorrer():
    """(archivo, nombre, descripcion) de cada @param de cada bloque Javadoc."""
    for f in fuentes():
        txt = io.open(f, encoding="utf-8", errors="replace").read()
        for jd in re.finditer(r"/\*\*(.*?)\*/", txt, re.S):
            cuerpo = jd.group(1)
            for m in re.finditer(r"@param\s+(\S+)\s+(.*?)(?=\n\s*\*\s*@|\Z)", cuerpo, re.S):
                desc = re.sub(r"\n\s*\*", "", m.group(2)).strip()
                yield f.replace("\\", "/"), m.group(1), desc


def es_tautologico(nombre, desc):
    propios = set(tokens(nombre))
    return not [t for t in tokens(desc) if t not in propios]


def estructura():
    """Javadoc apilados y Javadoc dentro de bloques de texto."""
    malos = []
    for f in fuentes():
        txt = io.open(f, encoding="utf-8", errors="replace").read()
        rel = f.replace("\\", "/")
        for m in RE_APILADOS.finditer(txt):
            malos.append(f"{rel}:{txt[:m.start()].count(chr(10)) + 1}: dos Javadoc seguidos "
                         f"(javadoc solo enlaza el ultimo y descarta el primero)")
        for m in re.finditer(r'"""(.*?)"""', txt, re.S):
            if "/**" in m.group(1) or "@param" in m.group(1) or "*/" in m.group(1):
                malos.append(f"{rel}:{txt[:m.start()].count(chr(10)) + 1}: un comentario Javadoc "
                             f"dentro de un bloque de texto (consulta)")
    return malos


def main():
    total, taut, por_archivo, casos = 0, 0, collections.Counter(), []
    for f, nombre, desc in recorrer():
        total += 1
        if es_tautologico(nombre, desc):
            taut += 1
            por_archivo[os.path.basename(f)] += 1
            casos.append((f, nombre, desc))
    if total < 500:
        print(f"[FAIL] solo se encontraron {total} @param: la regla de busqueda dejo de casar")
        return 1
    pct = 100 * taut / total
    print(f"@param revisados: {total}   tautologicos: {taut} ({pct:.1f} %)   umbral: {UMBRAL} %")
    if "--lista" in sys.argv:
        for f, n, d in casos:
            print(f"   {os.path.basename(f)}: @param {n} {d!r}")
    elif taut:
        for arch, n in por_archivo.most_common(5):
            print(f"   {n:3d}  {arch}")

    malos = estructura()
    for x in malos:
        print(f"[FAIL] {x}")
    if malos:
        return 1
    print("[OK] ningun Javadoc apilado ni metido dentro de una consulta")

    if pct > UMBRAL:
        print(f"[FAIL] {pct:.1f} % de los @param repiten el nombre del parametro (maximo {UMBRAL} %)")
        return 1
    print(f"[OK] {pct:.1f} % de los @param son tautologicos (maximo {UMBRAL} %)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
