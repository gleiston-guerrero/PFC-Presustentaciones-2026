#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Cierra los avisos de javadoc usando como entrada los avisos de javadoc.

POR QUE ASI
-----------
La revision del 18-sep observo que `javadoc:javadoc` sale en 0 "pero con 100
avisos (el tope)". El tope es real: javadoc corta en 100 por defecto. Levantado
con `-Xmaxwarns`, la corrida da **682**.

El proyecto ya tenia un generador (scripts/javadoc-generate.py) que deduce donde
falta documentacion leyendo el codigo con expresiones regulares. Ese enfoque ya
fallo una vez de forma silenciosa: el escaner propio daba 95,2 % mientras javadoc
media 85,2 %, porque el escaner no veia que un bloque colocado debajo de una
anotacion no se asocia. Dos analizadores distintos discrepan, y el que decide es
javadoc.

Asi que este script no vuelve a analizar el codigo: **toma la salida de javadoc**
--archivo, linea y que falta exactamente-- y corrige justo ahi. Si javadoc no se
queja de algo, aqui no se toca nada.

QUE ARREGLA
-----------
  no main description  -> anade la frase de resumen al bloque que ya existe
  no @param for <x>    -> anade la etiqueta que falta
  no @return           -> anade la etiqueta que falta
  no comment           -> crea el bloque entero, encima de las anotaciones

QUE NO ARREGLA, Y POR QUE
-------------------------
  use of default constructor, which does not provide a comment

De los 180 avisos de este tipo, **162 de las 173 clases llevan una anotacion
Lombok de constructor** (@NoArgsConstructor, @AllArgsConstructor, @Data,
@Builder). javadoc analiza el fuente ANTES de que Lombok genere nada, asi que no
ve el constructor que si existe en el bytecode. Es un artefacto de la
herramienta, no una brecha de documentacion: escribir 162 constructores
explicitos a mano para callar el aviso romperia los @Builder y empeoraria el
codigo para mejorar un contador. Se declara en VERIFICACION.md con esta
comprobacion, en vez de maquillarse. Las 11 clases restantes que si carecen de
constructor explicito sin Lombok se atienden aparte.

USO
    cd backend && ./mvnw -o javadoc:javadoc "-DadditionalJOption=-Xmaxwarns 100000" > jd.log 2>&1
    python scripts/javadoc-cerrar-avisos.py jd.log --dry-run
    python scripts/javadoc-cerrar-avisos.py jd.log --aplicar

Recuerda borrar backend/target/site/apidocs antes de regenerar: si existe, el
plugin dice "everything is up to date" y no analiza nada.
"""
import collections
import glob
import io
import os
import re
import sys

RE_AVISO = re.compile(r'([A-Za-z0-9_$]+\.java):(\d+): warning: (.+?)\s*$')

PREFIJOS = [
    ('findAllBy', 'findall'), ('findFirstBy', 'findfirst'), ('findBy', 'find'),
    ('existsBy', 'exists'), ('countBy', 'count'), ('deleteBy', 'delete'),
    ('findAll', 'findall'), ('find', 'find'), ('exists', 'exists'),
    ('count', 'count'), ('delete', 'delete'),
]


def partir_camel(s):
    return re.sub(r'(?<!^)(?=[A-Z])', ' ', s).lower().replace('_', ' ')


def humanizar(cond):
    fuera = []
    for tok in re.split(r'(And|Or)', cond):
        if tok == 'And':
            fuera.append('y')
        elif tok == 'Or':
            fuera.append('o')
        elif tok:
            fuera.append(partir_camel(tok).strip())
    return ' '.join(fuera).strip()


def describir(nombre):
    """Frase de resumen a partir del nombre, con las convenciones de Spring Data."""
    for prefijo, clase in PREFIJOS:
        if nombre.startswith(prefijo):
            cond = humanizar(nombre[len(prefijo):])
            if clase == 'exists':
                return f"Indica si existe algún registro con {cond}." if cond else "Indica si existe el registro."
            if clase == 'count':
                return f"Cuenta los registros con {cond}." if cond else "Cuenta los registros."
            if clase == 'delete':
                return f"Elimina los registros con {cond}." if cond else "Elimina los registros."
            if clase == 'findall':
                return f"Devuelve todos los registros con {cond}." if cond else "Devuelve todos los registros."
            if clase == 'findfirst':
                return f"Devuelve el primer registro con {cond}." if cond else "Devuelve el primer registro."
            return f"Busca el/los registro(s) con {cond}." if cond else "Busca los registros."
    return partir_camel(nombre).capitalize() + "."


def describir_retorno(tipo):
    t = (tipo or "").strip()
    if t.startswith('Optional'):
        return "el registro si existe, vacío si no"
    if t.startswith(('List', 'Page', 'Set', 'Collection')):
        return "los resultados encontrados (vacío si no hay coincidencias)"
    if t.startswith('Map'):
        return "el mapa de resultados"
    if t.startswith('ResponseEntity'):
        return "la respuesta HTTP correspondiente"
    if t in ('boolean', 'Boolean'):
        return "true si se cumple la condición, false si no"
    if t in ('long', 'Long', 'int', 'Integer', 'short', 'Short'):
        return "el valor numérico calculado"
    if t in ('String',):
        return "el valor encontrado, o null si no existe"
    if t.endswith('[]'):
        return "el arreglo de resultados"
    return f"el valor de tipo {{@code {t}}} correspondiente" if t else "el resultado"


def sangria_de(linea):
    return linea[:len(linea) - len(linea.lstrip())]


def nombre_y_tipo(lineas, i):
    """Del elemento declarado en la linea i: (nombre, tipo de retorno o None)."""
    texto = " ".join(l.strip() for l in lineas[i:i + 4])
    m = re.search(r'\b(?:class|interface|enum|record)\s+([A-Za-z0-9_]+)', texto)
    if m:
        return m.group(1), None
    m = re.search(r'([A-Za-z0-9_<>,\[\]\.\? ]+?)\s+([A-Za-z0-9_]+)\s*\(', texto)
    if m:
        tipo = m.group(1).split()[-1] if m.group(1).split() else None
        return m.group(2), tipo
    m = re.search(r'\b([A-Za-z0-9_]+)\s*[;=]', texto)
    if m:
        return m.group(1), None
    return None, None


def localizar(lineas, i):
    """Del aviso en la linea i, devuelve (bloque, indice de la declaracion).

    javadoc no siempre apunta a la declaracion: para "no main description" y
    para las etiquetas que faltan senala una linea DENTRO del bloque de
    comentario. Hay que reconocer los dos casos o casi todo se omite --
    la primera version de este script aplicaba 1 de 147 resumenes por esto.
    """
    dentro = None
    s = lineas[i].strip()
    if s.startswith(('/**', '*', '*/')):
        ini = i
        while ini >= 0 and not lineas[ini].strip().startswith('/**'):
            ini -= 1
        fin = i
        while fin < len(lineas) and not lineas[fin].strip().endswith('*/'):
            fin += 1
        if ini >= 0 and fin < len(lineas):
            dentro = (ini, fin)

    if dentro:
        # la declaracion es la primera linea util tras el bloque y sus anotaciones
        j = dentro[1] + 1
        while j < len(lineas):
            s2 = lineas[j].strip()
            if not s2 or s2.startswith('@'):
                j += 1
                continue
            if s2.startswith(('"', ')')) or s2.endswith(('+', ',')):
                j += 1
                continue
            break
        return dentro, min(j, len(lineas) - 1)

    return bloque_encima(lineas, i), i


def bloque_encima(lineas, i):
    """(ini, fin) del bloque /** */ que documenta la linea i, saltando anotaciones."""
    j = i - 1
    while j >= 0:
        s = lineas[j].strip()
        if not s:
            j -= 1
            continue
        if s.endswith('*/'):
            fin = j
            while j >= 0 and not lineas[j].strip().startswith('/**'):
                j -= 1
            return (j, fin) if j >= 0 else None
        # anotacion, posiblemente multilinea: baja hasta equilibrar parentesis
        saldo = s.count(')') - s.count('(')
        if saldo > 0:
            while j >= 0 and saldo > 0:
                j -= 1
                if j < 0:
                    return None
                saldo += lineas[j].strip().count(')') - lineas[j].strip().count('(')
            j -= 1
            continue
        if s.startswith('@'):
            j -= 1
            continue
        return None
    return None


def inicio_anotaciones(lineas, i):
    """Primera linea del grupo de anotaciones que precede a la linea i."""
    j = i - 1
    primera = i
    while j >= 0:
        s = lineas[j].strip()
        if not s:
            j -= 1
            continue
        if s.endswith('*/'):
            break
        saldo = s.count(')') - s.count('(')
        if saldo > 0:
            k = j
            while k >= 0 and saldo > 0:
                k -= 1
                if k < 0:
                    return primera
                saldo += lineas[k].strip().count(')') - lineas[k].strip().count('(')
            if k >= 0 and lineas[k].strip().startswith('@'):
                primera = k
                j = k - 1
                continue
            return primera
        if s.startswith('@'):
            primera = j
            j -= 1
            continue
        break
    return primera


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    log = sys.argv[1]
    aplicar = '--aplicar' in sys.argv

    avisos = collections.defaultdict(list)
    mapa = {os.path.basename(p): p for p in
            glob.glob('backend/src/main/java/**/*.java', recursive=True)}
    sin_ruta = set()
    for l in io.open(log, encoding='utf-8', errors='replace'):
        m = RE_AVISO.search(l)
        if not m:
            continue
        arch, linea, msg = m.group(1), int(m.group(2)), m.group(3)
        if msg.startswith('use of default constructor'):
            continue
        ruta = mapa.get(arch)
        if not ruta:
            sin_ruta.add(arch)
            continue
        avisos[ruta].append((linea, msg))

    total = {'resumen': 0, 'param': 0, 'return': 0, 'bloque': 0, 'omitido': 0}
    tocados = 0

    for ruta, items in sorted(avisos.items()):
        lineas = io.open(ruta, encoding='utf-8', errors='replace').read().split('\n')
        # De abajo hacia arriba: insertar desplaza las lineas siguientes.
        cambios = 0
        for linea, msg in sorted(items, key=lambda x: -x[0]):
            i = linea - 1
            if i < 0 or i >= len(lineas):
                total['omitido'] += 1
                continue
            bloque, decl = localizar(lineas, i)
            nombre, tipo = nombre_y_tipo(lineas, decl)
            if not nombre:
                total['omitido'] += 1
                continue
            ind = sangria_de(lineas[decl])

            if msg == 'no comment':
                if bloque:
                    total['omitido'] += 1
                    continue
                ini = inicio_anotaciones(lineas, decl)
                nuevo = [f"{ind}/**", f"{ind} * {describir(nombre)}", f"{ind} */"]
                lineas[ini:ini] = nuevo
                total['bloque'] += 1
                cambios += 1
                continue

            if not bloque:
                total['omitido'] += 1
                continue
            b_ini, b_fin = bloque

            if msg == 'no main description':
                lineas.insert(b_ini + 1, f"{ind} * {describir(nombre)}")
                total['resumen'] += 1
                cambios += 1
            elif msg.startswith('no @param for '):
                par = msg[len('no @param for '):].strip()
                lineas.insert(b_fin, f"{ind} * @param {par} {partir_camel(par)}")
                total['param'] += 1
                cambios += 1
            elif msg == 'no @return':
                lineas.insert(b_fin, f"{ind} * @return {describir_retorno(tipo)}")
                total['return'] += 1
                cambios += 1
            else:
                total['omitido'] += 1

        if cambios:
            tocados += 1
            print(f"  {ruta.replace(os.sep, '/')}: {cambios}")
            if aplicar:
                io.open(ruta, 'w', encoding='utf-8', newline='').write('\n'.join(lineas))

    print(f"\n{tocados} archivo(s) con cambios")
    print(f"  bloques creados     : {total['bloque']}")
    print(f"  resúmenes añadidos  : {total['resumen']}")
    print(f"  @param añadidos     : {total['param']}")
    print(f"  @return añadidos    : {total['return']}")
    print(f"  omitidos            : {total['omitido']}")
    if sin_ruta:
        print(f"  sin ruta en src/main: {len(sin_ruta)} archivo(s)")
    if not aplicar:
        print("\n(--dry-run: no se escribio nada)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
