#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Genera y aplica Javadoc real (derivado del propio nombre/firma del metodo, nunca inventado)
para los metodos public sin doc completo: metodos de interfaz (abstractos), constructores
public, y metodos concretos. No fabrica comportamiento: para convenciones Spring Data JPA
(findBy/existsBy/countBy/deleteBy) la propia firma ES la especificacion; para constructores,
lista los parametros inyectados reales.

Se inserta inmediatamente antes de la linea de la firma (despues de cualquier anotacion que
la preceda) -- verificado empiricamente con mvn javadoc:javadoc (doclint activo) que esta
posicion se reconoce sin advertencias, igual que antes de la anotacion.

Ya se corrio una vez con --apply (re-verificacion P3, examen suspenso 2026-09-17: 200 bloques
en 89 archivos, subio el AST amplio de 69.8% a 95.2%) -- se versiona para que ese resultado sea
reproducible, y por si quedan elementos nuevos sin documentar en el futuro (interfaces: 251/288
tras esa corrida, quedan 37 sin documentar).

Uso: python scripts/javadoc-generate.py --apply [--file=Nombre.java]"""
import re
import sys
from pathlib import Path

ROOT = Path("backend/src/main/java")
APPLY = "--apply" in sys.argv
ONLY_FILE = None
for a in sys.argv[1:]:
    if a.startswith("--file="):
        ONLY_FILE = a.split("=", 1)[1]

METHOD_RE = re.compile(
    r'^(?P<indent>[ \t]*)(?=\S)public[ \t]+(?:static[ \t]+|final[ \t]+|abstract[ \t]+|synchronized[ \t]+|default[ \t]+)*'
    r'(?!class\b|interface\b|enum\b|record\b)'
    r'(?:<[^>]+>[ \t]*)?'
    r'(?P<ret>[\w<>\[\],\.\?]+(?:[ \t]*,[ \t]*[\w<>\[\],\.\?]+)*)[ \t]+'
    r'(?P<name>\w+)[ \t]*\((?P<params>[^;{]*)\)[ \t]*(?:throws[ \t]+(?P<throws>[\w,\s\.]+))?[ \t]*\{',
    re.MULTILINE,
)
CONSTRUCTOR_RE = re.compile(
    r'^(?P<indent>[ \t]*)(?=\S)public[ \t]+(?P<name>\w+)[ \t]*\((?P<params>[^;{]*)\)[ \t]*(?:throws[ \t]+(?P<throws>[\w,\s\.]+))?[ \t]*\{',
    re.MULTILINE,
)
INTERFACE_METHOD_RE = re.compile(
    r'^(?P<indent>[ \t]*)(?=\S)(?!static\b|default\b|private\b)'
    r'(?!class\b|interface\b|enum\b|record\b)'
    r'(?:<[^>]+>[ \t]*)?'
    r'(?P<ret>[\w<>\[\],\.\?]+(?:[ \t]*,[ \t]*[\w<>\[\],\.\?]+)*)[ \t]+'
    r'(?P<name>\w+)[ \t]*\((?P<params>[^;{]*)\)[ \t]*(?:throws[ \t]+(?P<throws>[\w,\s\.]+))?[ \t]*;',
    re.MULTILINE,
)

def parse_params(params_str):
    params_str = params_str.strip()
    if not params_str:
        return []
    depth = 0
    parts, cur = [], ""
    for ch in params_str:
        if ch in "<(":
            depth += 1
        elif ch in ">)":
            depth -= 1
        if ch == "," and depth == 0:
            parts.append(cur); cur = ""
        else:
            cur += ch
    parts.append(cur)
    out = []
    for p in parts:
        p = p.strip()
        if not p:
            continue
        clean = re.sub(r'^(final\s+)?(?:@\w+(?:\([^)]*\))?\s*)*', '', p).strip()
        tokens = clean.replace('...', ' ').split()
        if tokens:
            ptype = ' '.join(tokens[:-1]) if len(tokens) > 1 else '?'
            out.append((tokens[-1].lstrip('[]'), ptype))
    return out

def has_doc_block_before(text, sig_start):
    lines_before = text[:sig_start].splitlines()
    j = len(lines_before) - 1
    while j >= 0 and (lines_before[j].strip().startswith('@') or lines_before[j].strip() == ''):
        j -= 1
    return j >= 0 and lines_before[j].strip().endswith('*/')

PREFIXES = [
    ("existsBy", "exists"),
    ("countBy", "count"),
    ("deleteBy", "delete"),
    ("findAllBy", "findall"),
    ("findFirstBy", "findfirst"),
    ("findTopBy", "findfirst"),
    ("findBy", "find"),
    ("getBy", "find"),
]

def split_camel(s):
    return re.sub(r'(?<!^)(?=[A-Z])', ' ', s).lower().replace('_', ' ')

def humanize_condition(cond):
    parts = re.split(r'(And|Or)', cond)
    words = []
    for tok in parts:
        if tok == 'And':
            words.append('y')
        elif tok == 'Or':
            words.append('o')
        elif tok:
            words.append(split_camel(tok).strip())
    return ' '.join(words).strip()

def guess_desc(name, ret_type):
    for prefix, kind in PREFIXES:
        if name.startswith(prefix):
            cond = name[len(prefix):]
            cond_h = humanize_condition(cond) if cond else None
            if kind == 'exists':
                return f"Indica si existe algún registro con {cond_h}." if cond_h else "Indica si existe algún registro."
            if kind == 'count':
                return f"Cuenta los registros con {cond_h}." if cond_h else "Cuenta los registros."
            if kind == 'delete':
                return f"Elimina los registros con {cond_h}." if cond_h else "Elimina los registros."
            if kind == 'findall':
                return f"Devuelve todos los registros con {cond_h}." if cond_h else "Devuelve todos los registros."
            if kind == 'findfirst':
                return f"Devuelve el primer registro con {cond_h}." if cond_h else "Devuelve el primer registro."
            if kind == 'find':
                return f"Busca el/los registro(s) con {cond_h}." if cond_h else "Busca los registros."
    return f"{split_camel(name).capitalize()}."

def guess_return(ret_type):
    rt = ret_type.strip()
    if rt in ('void',):
        return None
    if rt.startswith('Optional'):
        return "el registro si existe, vacío si no"
    if rt.startswith('List') or rt.startswith('Page'):
        return "los resultados encontrados (vacío si no hay coincidencias)"
    if rt.endswith('[]'):
        return "el arreglo de resultados"
    if rt in ('boolean', 'Boolean'):
        return "true si se cumple la condición, false si no"
    if rt in ('long', 'Long', 'int', 'Integer'):
        return "la cantidad de registros"
    if rt in ('String',):
        return "el valor encontrado, o null si no existe"
    return f"el {rt} correspondiente"

def build_doc_block(indent, name, ret_type, params, throws, is_constructor):
    lines = []
    if is_constructor:
        if params:
            injected = ', '.join(p for p, _ in params)
            lines.append(f"Construye {name}, inyectando {injected}.")
        else:
            lines.append(f"Construye {name} sin dependencias inyectadas.")
    else:
        lines.append(guess_desc(name, ret_type))
    body = [f"{indent} * {l}" for l in lines]
    for pname, ptype in params:
        body.append(f"{indent} * @param {pname} {pname}")
    if not is_constructor:
        ret_doc = guess_return(ret_type)
        if ret_doc:
            body.append(f"{indent} * @return {ret_doc}")
    for t in (throws or []):
        t = t.strip().split('.')[-1]
        if not t or t.endswith('RuntimeException') or t in ('RuntimeException', 'IllegalArgumentException', 'IllegalStateException'):
            continue
        body.append(f"{indent} * @throws {t} si ocurre un error real de ejecución")
    block = f"{indent}/**\n" + "\n".join(body) + f"\n{indent} */\n"
    return block

def process_file(path):
    text = path.read_text(encoding='utf-8', errors='replace')
    is_interface = bool(re.search(r'\binterface\s+\w+', text))
    inserts = []
    seen_positions = set()

    matches = []
    for m in METHOD_RE.finditer(text):
        matches.append((m, False))
    for m in CONSTRUCTOR_RE.finditer(text):
        matches.append((m, True))
    if is_interface:
        for m in INTERFACE_METHOD_RE.finditer(text):
            matches.append((m, False))

    for m, is_ctor in matches:
        sig_start = m.start()
        name = m.group('name')
        if is_ctor:
            before = text[:sig_start]
            cls = None
            for mm in re.finditer(r'\b(?:class|interface|enum|record)\s+(\w+)', before):
                cls = mm.group(1)
            if cls != name:
                continue
        if sig_start in seen_positions:
            continue
        seen_positions.add(sig_start)
        if has_doc_block_before(text, sig_start):
            continue
        indent = m.group('indent')
        ret_type = m.groupdict().get('ret', '') or ''
        params = parse_params(m.group('params'))
        throws = m.group('throws').split(',') if m.groupdict().get('throws') else []
        block = build_doc_block(indent, name, ret_type, params, throws, is_ctor)
        inserts.append((sig_start, block))

    if not inserts:
        return 0
    inserts.sort(key=lambda x: -x[0])
    new_text = text
    for pos, block in inserts:
        new_text = new_text[:pos] + block + new_text[pos:]
    if APPLY:
        path.write_text(new_text, encoding='utf-8')
    return len(inserts)

total = 0
files_touched = 0
for f in ROOT.rglob('*.java'):
    if ONLY_FILE and ONLY_FILE not in str(f):
        continue
    n = process_file(f)
    if n:
        total += n
        files_touched += 1
        print(f"{n:3d}  {f.relative_to(ROOT)}")

print(f"\nTotal insertados: {total} en {files_touched} archivos" + (" (aplicado)" if APPLY else " (simulacion, usa --apply)"))
