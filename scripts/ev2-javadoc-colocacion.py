#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Detecta bloques Javadoc colocados DESPUES de las anotaciones: javac no los asocia.

QUE PROBLEMA RESUELVE
---------------------
La revision del 18-sep midio la cobertura de Javadoc en 85,2 % contando
interfaces, mientras el equipo declaraba 95,2 %. La causa que dio es concreta y
comprobable: hay bloques `/** ... */` escritos DESPUES de una anotacion y antes
de la firma del metodo. Por ejemplo:

    @Query("SELECT u FROM AppUser u ...")
    /**
     * Search paginado.
     * @param q q
     */
    Page<AppUser> searchPaged(@Param("q") String q, Pageable pageable);

Para una persona ese metodo esta documentado. Para javac no: el Javadoc tiene
que preceder a TODO el conjunto de modificadores y anotaciones del elemento. Ahi
el bloque queda huerfano, no se asocia a nada, y el metodo cuenta como sin
documentar -- que es exactamente la diferencia entre las dos mediciones.

Un contador propio que mire "hay un /** cerca" reproduce el 95,2 % y se equivoca.
Este script mira la COLOCACION, que es lo que decide.

USO
    python scripts/ev2-javadoc-colocacion.py           # informe
    python scripts/ev2-javadoc-colocacion.py --lista   # cada aparicion

Sale con 1 si encuentra alguno.
"""
import glob
import io
import re
import sys

# Una anotacion con argumentos anidados de hasta dos niveles.
def anotacion_encima(lineas, i):
    """Si la linea i viene precedida por una anotacion, devuelve su nombre.

    Se sube equilibrando parentesis en vez de buscar la anotacion con una sola
    expresion regular. La version anterior lo hacia con un patron que admitia
    tres niveles de anidamiento y aun asi fallaba con un `@Query` partido en
    varios literales concatenados cuyo JPQL trae parentesis
    (`LOWER(CONCAT('%', :texto, '%'))`): daba por bueno AuditRepository.java,
    que tenia el bloque mal colocado a la vista.
    """
    j = i - 1
    while j >= 0:
        s = lineas[j].strip()
        if not s:
            j -= 1
            continue
        saldo = s.count(')') - s.count('(')
        if saldo > 0:                      # cola de una anotacion multilinea
            while j >= 0 and saldo > 0:
                j -= 1
                if j < 0:
                    return None
                t = lineas[j].strip()
                saldo += t.count(')') - t.count('(')
            if j >= 0 and lineas[j].strip().startswith('@'):
                return lineas[j].strip().split('(')[0]
            return None
        if s.startswith('@'):
            # Una linea que ademas trae la declaracion completa
            # (`@GetMapping public List<X> f(...) { ... }`) no es una anotacion
            # colgando: es el elemento anterior, ya cerrado.
            if '{' in s or s.endswith((';', '}')):
                return None
            return s.split('(')[0]
        return None
    return None


def main():
    hallazgos = []
    for f in glob.glob("backend/src/main/java/**/*.java", recursive=True):
        lineas = io.open(f, encoding="utf-8", errors="replace").read().split("\n")
        for i, l in enumerate(lineas):
            if not l.strip().startswith("/**"):
                continue
            anot = anotacion_encima(lineas, i)
            if anot:
                hallazgos.append((f.replace("\\", "/"), i + 1, anot))

    if not hallazgos:
        print("[OK] ningun bloque Javadoc quedo despues de una anotacion.")
        return 0

    print(f"*** {len(hallazgos)} bloque(s) Javadoc colocados despues de una anotacion ***")
    print("javac NO los asocia al elemento: esos metodos cuentan como sin documentar.\n")

    por_archivo = {}
    por_anot = {}
    for f, l, a in hallazgos:
        por_archivo.setdefault(f, []).append(l)
        por_anot[a] = por_anot.get(a, 0) + 1

    print("Por anotacion que los precede:")
    for a, n in sorted(por_anot.items(), key=lambda x: -x[1]):
        print(f"   {n:4d}  {a}")
    print(f"\nArchivos afectados: {len(por_archivo)}")
    if "--lista" in sys.argv:
        for f, ls in sorted(por_archivo.items()):
            print(f"   {f}: lineas {', '.join(map(str, ls))}")
    else:
        for f, ls in sorted(por_archivo.items(), key=lambda x: -len(x[1]))[:8]:
            print(f"   {len(ls):3d}  {f}")
        if len(por_archivo) > 8:
            print(f"   ... y {len(por_archivo)-8} archivos mas (usa --lista)")

    print("\nArreglo: mover el bloque /** ... */ ARRIBA de las anotaciones.")
    return 1


if __name__ == "__main__":
    sys.exit(main())
