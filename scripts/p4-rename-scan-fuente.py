#!/usr/bin/env python3
"""ESTIMACION propia (no el script del ing, que no tenemos) del % de tipos y metodos que
aun contienen una palabra en espanol reconocible, contando por TEXTO FUENTE en
backend/src/{main,test}/java. Metodologia 100% transparente: se lista el diccionario usado,
para que se pueda auditar a mano -- no se reporta el numero como un hecho verificado unico,
sino como una estimacion con metodo declarado.

Limitacion conocida (ver p4-rename-scan-javap.py): el conteo por texto fuente NO ve los
metodos que Lombok genera en tiempo de compilacion (getters/setters/builder/equals/hashCode/
toString), asi que el total de metodos queda muy por debajo del real -- usar el script javap
para una cifra de metodos que incluya esos generados.

CORRECCION DE LA REVISION FINAL DEL 19-SEP
------------------------------------------
La expresion que reconocia metodos (METHOD_RE) exigia `public`, `private` o `protected`. Los
metodos de prueba de JUnit 5 son paquete-privados y los de una interfaz no llevan modificador,
asi que el instrumento descartaba 1.145 de 1.838 metodos --- justo los 809 @Test que se acababan
de renombrar --- y publicaba "693 metodos" (0,0 % / 4,3 %) sobre un universo que no era el del
proyecto. La cifra era correcta para lo que el instrumento veia, pero el instrumento no veia lo
que habia cambiado.

Ahora los metodos se leen de sus DECLARACIONES (metodos_declarados): se recorre el texto sin
comentarios ni cadenas, se sigue la profundidad de llaves, y en el cuerpo de cada clase o interfaz
se reconoce como metodo todo encabezado `[modificadores] Tipo nombre(...)` con o sin modificador
de visibilidad. Sigue sin ver los metodos que Lombok genera (para eso, p4-rename-scan-javap.py).

Uso: python scripts/p4-rename-scan-fuente.py (desde la raiz del repo, o desde backend/)"""
import re
import sys
from pathlib import Path

ROOT = Path("backend") if Path("backend/src/main/java").exists() else Path(".")
ROOTS = [ROOT / "src/main/java", ROOT / "src/test/java"]

TYPE_RE = re.compile(r'^\s*(?:@\w+(?:\([^)]*\))?\s*)*(?:public|private|protected)?\s*(?:static\s+|final\s+|abstract\s+)*(?:class|interface|enum|record)\s+(\w+)', re.MULTILINE)
METHOD_RE = re.compile(
    r'^\s*(?:@\w+(?:\([^)]*\))?\s*)*'
    r'(?:public|private|protected)\s+(?:static\s+|final\s+|abstract\s+|synchronized\s+|default\s+)*'
    r'(?!class\b|interface\b|enum\b|record\b|new\b)'
    r'(?:<[^>]+>\s*)?'
    r'[\w<>\[\],\s\.\?]+?\s+(\w+)\s*\([^;{]*\)\s*(?:throws\s+[\w,\s\.]+)?\s*[{;]',
    re.MULTILINE,
)

SPANISH_WORDS = [
    "Bitacora", "bitacora",
    "Solicitud", "Estudiante", "Docente", "Jurado", "Notificacion", "Anteproyecto",
    "Auditoria", "Cronograma", "Rubrica", "Carrera", "Facultad", "Permiso", "Evaluacion",
    "Tutoria", "Sala", "Recurso", "Modalidad", "Periodo", "Historial", "Proceso",
    "Progreso", "Convocatoria", "Linea", "Bloque", "Disponibilidad", "Conteo",
    "Seguimiento", "Depuracion", "Escala", "Evaluador", "Jornada", "Miembro", "Respaldo",
    "Usuario", "usuario", "Acta", "acta", "Tema", "tema",
    "obtener", "Obtener", "eliminar", "Eliminar",
    "buscar", "Buscar", "listar", "Listar", "validar", "Validar",
    "asignar", "Asignar", "verificar", "Verificar",
    "calcular", "Calcular", "enviar", "Enviar", "recibir", "Recibir", "mostrar", "Mostrar",
    "agregar", "Agregar", "quitar", "Quitar", "subir", "Subir",
    "bajar", "Bajar", "cerrar", "Cerrar", "abrir", "Abrir", "iniciar", "Iniciar",
    "terminar", "Terminar", "completar", "Completar", "aprobar", "Aprobar", "rechazar", "Rechazar",
    "revisar", "Revisar", "procesar", "Procesar", "contar", "Contar", "filtrar", "Filtrar",
    "ordenar", "Ordenar", "exportar", "Exportar", "importar", "Importar", "descargar", "Descargar",
    "cargar", "Cargar", "definir", "Definir", "establecer", "Establecer",
    "configurar", "Configurar", "ejecutar", "Ejecutar", "notificar", "Notificar", "activar", "Activar",
    "desactivar", "Desactivar", "habilitar", "Habilitar", "deshabilitar", "Deshabilitar",
    "bloquear", "Bloquear", "desbloquear", "Desbloquear", "reiniciar", "Reiniciar",
    "restaurar", "Restaurar", "borrar", "Borrar", "renombrar", "Renombrar", "reemplazar", "Reemplazar",
    "firmar", "Firmar", "depurar", "Depurar", "consultar", "Consultar", "armar", "Armar",
    "construir", "Construir", "sincronizar", "Sincronizar", "resumir", "Resumir",
    "actualizar", "Actualizar", "crear", "Crear", "generar", "Generar", "guardar", "Guardar",
    "registrar", "Registrar", "cambiar", "Cambiar", "restablecer", "Restablecer",
    "resolver", "Resolver",
]
SPANISH_RE = re.compile("|".join(re.escape(w) for w in SPANISH_WORDS))

PALABRAS_NO_METODO = {"if", "for", "while", "switch", "catch", "return", "new", "throw", "else", "do",
                      "try", "synchronized", "assert", "super", "this", "yield", "case", "default"}
MODIFICADORES = {"public", "protected", "private", "static", "final", "abstract", "synchronized",
                 "native", "default", "strictfp", "sealed", "non-sealed"}
RE_TIPO_DECL = re.compile(r"\b(?:class|interface|enum|record|@interface)\b")


def _sin_comentarios_ni_cadenas(t):
    """Reemplaza comentarios y literales por espacios (conserva saltos de linea y longitud)."""
    out, i, n = [], 0, len(t)
    while i < n:
        c = t[i]
        if t.startswith("//", i):
            j = t.find("\n", i)
            j = n if j < 0 else j
            out.append(" " * (j - i)); i = j
        elif t.startswith("/*", i):
            j = t.find("*/", i + 2)
            j = n if j < 0 else j + 2
            out.append("".join(ch if ch == "\n" else " " for ch in t[i:j])); i = j
        elif t.startswith('"""', i):
            j = t.find('"""', i + 3)
            j = n if j < 0 else j + 3
            out.append("".join(ch if ch == "\n" else " " for ch in t[i:j])); i = j
        elif c in "\"'":
            j = i + 1
            while j < n and t[j] != c:
                j += 2 if t[j] == "\\" else 1
            j = min(j + 1, n)
            out.append(" " * (j - i)); i = j
        else:
            out.append(c); i += 1
    return "".join(out)


def _quitar_anotaciones(h):
    """Quita @Nombre y @Nombre(...) (con parentesis balanceados) del inicio y del medio."""
    res, i, n = [], 0, len(h)
    while i < n:
        if h[i] == "@" and not h.startswith("@interface", i):
            j = i + 1
            while j < n and (h[j].isalnum() or h[j] in "_."):
                j += 1
            k = j
            while k < n and h[k].isspace():
                k += 1
            if k < n and h[k] == "(":
                d = 0
                while k < n:
                    d += (h[k] == "(") - (h[k] == ")")
                    k += 1
                    if d == 0:
                        break
                j = k
            i = j
        else:
            res.append(h[i]); i += 1
    return "".join(res)


def _nombre_de_metodo(encabezado):
    """Nombre si `encabezado` (hasta `{` o `;`) es la declaracion de un metodo; si no, None."""
    h = _quitar_anotaciones(encabezado).strip()
    p = h.find("(")
    if p < 0:
        return None
    previo = h[:p]
    if "=" in previo or RE_TIPO_DECL.search(previo):
        return None                                   # campo con inicializador, o una clase
    previo = re.sub(r"<[^<>]*(?:<[^<>]*>[^<>]*)*>", " ", previo)   # genericos, incluidos los anidados
    partes = [x for x in previo.split() if x not in MODIFICADORES]
    if len(partes) < 2:
        return None                                   # sin tipo de retorno: constructor o llamada
    nombre = partes[-1]
    if not re.fullmatch(r"\w+", nombre) or nombre in PALABRAS_NO_METODO:
        return None
    return nombre


def metodos_declarados(texto):
    """Nombres de los metodos DECLARADOS en el cuerpo de una clase/interfaz/enum/record,
    con o sin modificador de visibilidad."""
    t = _sin_comentarios_ni_cadenas(texto)
    pila, ini, par, res = [], 0, 0, []       # pila de cuerpos: "cls" o "otro"
    for i, c in enumerate(t):
        if c == "(":
            par += 1
        elif c == ")":
            par -= 1
        elif par > 0:
            continue                          # llaves dentro de parentesis (anotaciones, arrays)
        elif c == "{":
            enc = t[ini:i]
            # Una clase anonima (`new Tipo(...) {`) tambien tiene metodos declarados.
            anonima = bool(re.search(r"\bnew\b", enc)) and enc.rstrip().endswith(")")
            if anonima or (RE_TIPO_DECL.search(_quitar_anotaciones(enc).split("(")[0]) and "=" not in enc.split("(")[0]):
                pila.append("cls")
            else:
                if pila and pila[-1] == "cls":
                    nom = _nombre_de_metodo(enc)
                    if nom:
                        res.append(nom)
                pila.append("otro")
            ini = i + 1
        elif c == "}":
            if pila:
                pila.pop()
            ini = i + 1
        elif c == ";":
            if pila and pila[-1] == "cls":
                nom = _nombre_de_metodo(t[ini:i])
                if nom:
                    res.append(nom)
            ini = i + 1
    return res


def scan():
    all_types, es_types = [], []
    all_methods, es_methods = [], []
    files = []
    for r in ROOTS:
        files.extend(r.rglob("*.java"))
    for f in files:
        text = f.read_text(encoding="utf-8", errors="replace")
        for m in TYPE_RE.finditer(text):
            name = m.group(1)
            all_types.append((str(f), name))
            if SPANISH_RE.search(name):
                es_types.append((str(f), name))
        for name in metodos_declarados(text):
            all_methods.append((str(f), name))
            if SPANISH_RE.search(name):
                es_methods.append((str(f), name))
    return all_types, es_types, all_methods, es_methods

if __name__ == "__main__":
    all_types, es_types, all_methods, es_methods = scan()
    print(f"Tipos totales detectados (texto fuente, main+test): {len(all_types)}")
    print(f"Tipos con palabra en espanol (heuristica): {len(es_types)} ({len(es_types)/len(all_types)*100:.1f}%)")
    print(f"Metodos totales detectados (texto fuente, main+test): {len(all_methods)}")
    print(f"Metodos con palabra en espanol (heuristica): {len(es_methods)} ({len(es_methods)/len(all_methods)*100:.1f}%)")
    if "--list" in sys.argv:
        print()
        print("=== Tipos en espanol (nombre) ===")
        for f, n in es_types:
            print(f"  {n}  [{f}]")
        print()
        print("=== Metodos en espanol (nombre) ===")
        for f, n in es_methods:
            print(f"  {n}  [{f}]")
