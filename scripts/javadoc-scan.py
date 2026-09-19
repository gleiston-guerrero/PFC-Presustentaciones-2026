#!/usr/bin/env python3
"""Escanea backend/src/main/java buscando metodos/constructores publicos y si tienen
Javadoc COMPLETO: descripcion real + @param por cada parametro + @return si no es void
+ @throws por cada excepcion chequeada declarada. Imprime un resumen y, con
--list-missing, el detalle por archivo para priorizar el trabajo.

Uso: python scripts/javadoc-scan.py [ruta a backend/src/main/java] [--list-missing]"""
import re
import sys
from pathlib import Path

ROOT = Path(sys.argv[1]) if len(sys.argv) > 1 and not sys.argv[1].startswith("--") else Path("backend/src/main/java")

UNCHECKED_HINTS = {
    "RuntimeException", "IllegalArgumentException", "IllegalStateException",
    "NullPointerException", "UnsupportedOperationException",
}

METHOD_RE = re.compile(
    r'^\s*(?:@\w+(?:\([^)]*\))?\s*)*'
    r'public\s+(?:static\s+|final\s+|abstract\s+|synchronized\s+|default\s+)*'
    r'(?!class\b|interface\b|enum\b|record\b)'
    r'(?:<[^>]+>\s*)?'
    r'(?P<ret>[\w<>\[\],\s\.\?]+?)\s+'
    r'(?P<name>\w+)\s*\((?P<params>[^;{]*)\)\s*(?:throws\s+(?P<throws>[\w,\s\.]+))?\s*\{',
    re.MULTILINE,
)

def parse_param_names(params_str):
    params_str = params_str.strip()
    if not params_str:
        return []
    depth = 0
    parts = []
    cur = ""
    for ch in params_str:
        if ch in "<(":
            depth += 1
        elif ch in ">)":
            depth -= 1
        if ch == "," and depth == 0:
            parts.append(cur)
            cur = ""
        else:
            cur += ch
    parts.append(cur)
    names = []
    for p in parts:
        p = p.strip()
        if not p:
            continue
        p = re.sub(r'^(final\s+)?(?:@\w+(?:\([^)]*\))?\s*)*', '', p).strip()
        tokens = p.replace('...', ' ').split()
        if tokens:
            names.append(tokens[-1].lstrip('[]'))
    return names

def extract_javadoc_block(text, start_idx):
    lines_before = text[:start_idx].splitlines()
    j = len(lines_before) - 1
    # Sube saltando el grupo de anotaciones y modificadores. Una anotacion puede
    # ocupar varias lineas (@Query con la consulta partida en literales), y sus
    # lineas de continuacion NO empiezan por '@': hay que equilibrar parentesis
    # para saber donde empieza de verdad. javac asocia el Javadoc que precede a
    # todo el grupo, asi que contar de otra forma mide algo que no es Javadoc.
    while j >= 0:
        s = lines_before[j].strip()
        if s == '':
            j -= 1
            continue
        if s.endswith('*/'):
            break
        saldo = s.count(')') - s.count('(')
        if saldo > 0:                      # cola de una anotacion multilinea
            while j >= 0 and saldo > 0:
                j -= 1
                if j < 0:
                    return None
                t = lines_before[j].strip()
                saldo += t.count(')') - t.count('(')
            if j < 0 or not lines_before[j].strip().startswith('@'):
                return None
            j -= 1
            continue
        if s.startswith('@'):
            j -= 1
            continue
        return None
    if j < 0 or not lines_before[j].strip().endswith('*/'):
        return None
    k = j
    block_lines = []
    while k >= 0:
        block_lines.append(lines_before[k])
        if lines_before[k].strip().startswith('/**'):
            break
        k -= 1
    else:
        return None
    if not lines_before[k].strip().startswith('/**'):
        return None
    return '\n'.join(reversed(block_lines))

def is_complete(block, param_names, ret_type, throws_types):
    if block is None:
        return False, "sin bloque /** */"
    body_lines = [l.strip().lstrip('*').strip() for l in block.splitlines()]
    desc_lines = [l for l in body_lines if l and not l.startswith('@') and l not in ('/**', '*/')]
    if not desc_lines:
        return False, "sin descripcion"
    missing = []
    for p in param_names:
        if not re.search(rf'@param\s+{re.escape(p)}\b', block):
            missing.append(f"@param {p}")
    ret_type_clean = ret_type.strip()
    if ret_type_clean not in ("void",) and '@return' not in block:
        missing.append("@return")
    for t in throws_types:
        t = t.strip().split('.')[-1]
        if not t or t in UNCHECKED_HINTS or t.endswith("RuntimeException"):
            continue
        if not re.search(rf'@throws\s+{re.escape(t)}\b', block) and not re.search(rf'@exception\s+{re.escape(t)}\b', block):
            missing.append(f"@throws {t}")
    if missing:
        return False, "faltan: " + ", ".join(missing)
    return True, "ok"

results = []

for f in ROOT.rglob('*.java'):
    text = f.read_text(encoding='utf-8', errors='replace')
    seen = set()
    for m in METHOD_RE.finditer(text):
        span = m.span()
        if span[0] in seen:
            continue
        seen.add(span[0])
        name = m.group('name')
        ret = m.group('ret').strip()
        params = parse_param_names(m.group('params'))
        throws = m.group('throws').split(',') if m.group('throws') else []
        block = extract_javadoc_block(text, span[0])
        ok, reason = is_complete(block, params, ret, throws)
        line_no = text[:span[0]].count('\n') + 1
        results.append((str(f.relative_to(ROOT)), name, ok, reason, line_no))

total = len(results)
documented = sum(1 for r in results if r[2])
print(f"Total metodos publicos detectados: {total}")
print(f"Con Javadoc COMPLETO: {documented} ({documented/total*100:.1f}%)" if total else "sin metodos")
print(f"Incompletos/sin doc: {total-documented}")
need = int(total*0.9) + (0 if total*0.9 == int(total*0.9) else 1)
print(f"Meta 90%: {need} documentados (faltan {max(0, need-documented)} mas)")

if '--list-missing' in sys.argv:
    by_file = {}
    for fpath, name, ok, reason, line in results:
        if not ok:
            by_file.setdefault(fpath, []).append((name, line, reason))
    for fpath in sorted(by_file, key=lambda k: -len(by_file[k])):
        print(f"\n{fpath} ({len(by_file[fpath])} sin doc completo):")
        for name, line, reason in sorted(by_file[fpath], key=lambda x: x[1]):
            print(f"  L{line}: {name}  [{reason}]")
