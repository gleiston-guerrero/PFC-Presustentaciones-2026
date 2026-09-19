#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Comprueba que cada expresion SpEL de @PreAuthorize apunte a algo que existe.

POR QUE
-------
La revision del 18-sep cerro P8 asi: *"Los SpEL corregidos resuelven; 97 de 102
endpoints de escritura anotados; **el verificador no detecta un SpEL roto**"*.

Es exacto. `audit-endpoints-autorizacion.py` comprueba que la anotacion ESTE,
como cadena de texto. No comprueba que lo que hay dentro signifique algo. Un
`@PreAuthorize("@permissionService.tienePermiso(...)")` --con el nombre viejo en
espanol, por ejemplo-- pasaria ese chequeo y fallaria en tiempo de ejecucion,
que es justo el modo de fallo que introdujo el renombrado de P4.

La diferencia importa: una anotacion presente pero rota no protege el endpoint,
y ademas lo rompe. Contar anotaciones da una falsa sensacion de cobertura.

QUE COMPRUEBA
-------------
Para cada `@PreAuthorize` del codigo:

  1. Si invoca un bean (`@algoService.metodo(...)`), que exista una clase
     Spring cuyo nombre de bean sea ese, y que tenga ese metodo PUBLICO con
     un numero de argumentos compatible.
  2. Si usa `hasRole` / `hasAuthority` / `hasAnyRole`, que el rol citado exista
     entre los que siembran las migraciones.
  3. Que la expresion no quede vacia ni sin cerrar parentesis.

Lo que NO hace: evaluar SpEL de verdad. Eso solo lo hace Spring al arrancar.
Esto detecta la clase de error que el renombrado puede introducir en masa --
un nombre que dejo de existir-- que es el riesgo real de este proyecto.

Uso:
    python scripts/p8-spel-vivo.py
    python scripts/p8-spel-vivo.py --lista

Sale con 1 si alguna expresion apunta a algo que no existe.
"""
import glob
import io
import os
import re
import sys

RE_PREAUTH = re.compile(r'@PreAuthorize\s*\(\s*"((?:[^"\\]|\\.)*)"\s*\)')
RE_BEAN = re.compile(r'@([a-zA-Z_][\w]*)\.([a-zA-Z_][\w]*)\s*\(')
RE_ROL = re.compile(r"has(?:Any)?(?:Role|Authority)\s*\(\s*'([^']+)'")
RE_CLASE = re.compile(r'\b(?:public\s+)?(?:final\s+)?class\s+([A-Za-z_]\w*)')
RE_METODO_PUB = re.compile(
    r'public\s+(?:static\s+)?(?:final\s+)?[\w.<>\[\],\s?]+?\s+([a-zA-Z_]\w*)\s*\(([^)]*)\)')
RE_COMPONENTE = re.compile(r'@(Service|Component|Repository|Controller|RestController)\b')


def nombre_de_bean(clase):
    """Convencion de Spring: primera letra en minuscula, salvo dos mayusculas."""
    if len(clase) > 1 and clase[0].isupper() and clase[1].isupper():
        return clase
    return clase[0].lower() + clase[1:]


def beans_del_proyecto():
    """{nombre de bean: {metodo publico: (min_args, max_args)}}."""
    out = {}
    for f in glob.glob('backend/src/main/java/**/*.java', recursive=True):
        txt = io.open(f, encoding='utf-8', errors='replace').read()
        if not RE_COMPONENTE.search(txt):
            continue
        m = RE_CLASE.search(txt)
        if not m:
            continue
        metodos = {}
        for mm in RE_METODO_PUB.finditer(txt):
            args = mm.group(2).strip()
            n = 0 if not args else len([a for a in args.split(',') if a.strip()])
            nombre = mm.group(1)
            lo, hi = metodos.get(nombre, (n, n))
            metodos[nombre] = (min(lo, n), max(hi, n))
        out[nombre_de_bean(m.group(1))] = metodos
    return out


def roles_declarados():
    """Roles que siembran las migraciones, en mayusculas."""
    roles = set()
    for f in glob.glob('backend/src/main/resources/db/migration/V*.sql'):
        txt = io.open(f, encoding='utf-8', errors='replace').read()
        for m in re.finditer(r"INSERT\s+INTO\s+\S*roles?\b[^;]*?;", txt, re.I | re.S):
            for v in re.findall(r"'([A-ZÁÉÍÓÚÑ_]{3,})'", m.group(0)):
                roles.add(v.upper())
    return roles


def contar_args(expr, inicio):
    """Numero de argumentos de la llamada que empieza en `inicio` (un '(')."""
    nivel, args, actual = 0, 0, False
    for i in range(inicio, len(expr)):
        c = expr[i]
        if c == '(':
            nivel += 1
            continue
        if c == ')':
            nivel -= 1
            if nivel == 0:
                return args + (1 if actual else 0)
            continue
        if nivel == 1 and c == ',':
            args += 1
            actual = False
            continue
        if nivel >= 1 and not c.isspace():
            actual = True
    return -1


def main():
    if not os.path.isdir('backend/src/main/java'):
        print('ERROR: corre esto desde la raiz del repositorio.')
        return 2

    beans = beans_del_proyecto()
    roles = roles_declarados()
    fallos, total, con_bean = [], 0, 0

    for f in sorted(glob.glob('backend/src/main/java/**/*.java', recursive=True)):
        txt = io.open(f, encoding='utf-8', errors='replace').read()
        rel = f.replace('\\', '/')
        for m in RE_PREAUTH.finditer(txt):
            expr = m.group(1)
            total += 1
            linea = txt[:m.start()].count('\n') + 1

            if not expr.strip():
                fallos.append((rel, linea, 'expresion vacia', expr))
                continue
            if expr.count('(') != expr.count(')'):
                fallos.append((rel, linea, 'parentesis sin cerrar', expr))
                continue

            for bm in RE_BEAN.finditer(expr):
                con_bean += 1
                bean, metodo = bm.group(1), bm.group(2)
                if bean not in beans:
                    fallos.append((rel, linea, f'no existe el bean @{bean}', expr))
                    continue
                if metodo not in beans[bean]:
                    cercanos = [x for x in beans[bean]
                                if x.lower().startswith(metodo[:4].lower())]
                    pista = f' (¿{", ".join(sorted(cercanos)[:3])}?)' if cercanos else ''
                    fallos.append(
                        (rel, linea, f'@{bean} no tiene el metodo publico {metodo}{pista}', expr))
                    continue
                n = contar_args(expr, bm.end() - 1)
                lo, hi = beans[bean][metodo]
                if n >= 0 and not (lo <= n <= hi):
                    fallos.append(
                        (rel, linea,
                         f'@{bean}.{metodo} recibe {n} argumento(s); declara entre {lo} y {hi}',
                         expr))

            for rm in RE_ROL.finditer(expr):
                rol = rm.group(1).replace('ROLE_', '').upper()
                if roles and rol not in roles:
                    fallos.append((rel, linea, f'rol "{rol}" no lo siembra ninguna migracion', expr))

    print(f'Expresiones @PreAuthorize analizadas: {total}')
    print(f'  invocaciones a un bean dentro de ellas: {con_bean}')
    print(f'  beans Spring detectados en el proyecto: {len(beans)}')
    print(f'  roles declarados en migraciones: {len(roles) or "(no se detectaron)"}')

    if not fallos:
        print('\n[OK] toda expresion SpEL apunta a un bean, metodo y rol que existen.')
        return 0

    print(f'\n*** {len(fallos)} expresion(es) SpEL rota(s) ***')
    print('Una anotacion presente pero rota no protege el endpoint: ademas lo rompe.\n')
    for f, l, motivo, expr in fallos:
        print(f'   {f}:{l}')
        print(f'      {motivo}')
        if '--lista' in sys.argv:
            print(f'      expresion: {expr[:110]}')
    return 1


if __name__ == '__main__':
    sys.exit(main())
