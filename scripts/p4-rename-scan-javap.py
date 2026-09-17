#!/usr/bin/env python3
"""Cuenta METODOS reales (incluyendo los generados por Lombok -- getters, setters,
builder(), build(), equals/hashCode/toString, etc.) leyendo las clases YA COMPILADAS
con `javap`, en vez de el codigo fuente. El escaneo por texto (p4-rename-scan-fuente.py) no
puede ver estos metodos porque no existen como texto en el .java, se generan recien al
compilar. Se excluyen constructores, inicializadores estaticos y metodos sinteticos/bridge
(lambda$..., access$..., cualquier nombre con '$') porque no son metodos con nombre real.

Requiere que el proyecto ya este compilado: `cd backend && ./mvnw -q compile` (main) y/o
`./mvnw -q test-compile` (main+test) antes de correr esto.

Uso: python scripts/p4-rename-scan-javap.py [--include-test] [--list]
  --include-test: tambien escanea target/test-classes (requiere test-compile antes)
  --list: imprime cada metodo en espanol encontrado, con su archivo .class"""
import re
import subprocess
import sys
from pathlib import Path

BACKEND = Path("backend") if Path("backend/pom.xml").exists() else Path(".")
INCLUDE_TEST = "--include-test" in sys.argv

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

METHOD_LINE_RE = re.compile(r'^\s*(?:[\w.<>\[\],? ]+\s)?(\w+)\(([^)]*)\)(?:\s+throws\s+[\w.,\s]+)?;\s*$')

def class_simple_name(class_decl_line):
    m = re.search(r'(?:class|interface|enum|@interface|record)\s+([\w.]+)', class_decl_line)
    if not m:
        return None
    return m.group(1).rsplit('.', 1)[-1].split('$')[-1].split('<')[0]

def methods_in_class(classfile):
    out = subprocess.run(["javap", "-p", str(classfile)], capture_output=True, text=True)
    lines = out.stdout.splitlines()
    if not lines:
        return []
    cname = None
    for l in lines:
        if re.search(r'\b(class|interface|enum|@interface|record)\b', l) and '{' in l:
            cname = class_simple_name(l)
            break
    methods = []
    for l in lines:
        l = l.strip()
        if not l or l.startswith("Compiled from") or l.startswith("}"):
            continue
        m = METHOD_LINE_RE.match(l)
        if not m:
            continue
        name = m.group(1)
        if name in ("static",):
            continue
        if cname and name == cname:
            continue  # constructor
        if '$' in name or name.startswith('lambda') or name.startswith('access'):
            continue  # synthetic/bridge/lambda
        methods.append(name)
    return methods

def main():
    roots = [BACKEND / "target/classes"]
    if INCLUDE_TEST:
        roots.append(BACKEND / "target/test-classes")
    classfiles = []
    for r in roots:
        classfiles.extend(sorted(r.rglob("*.class")))

    total = 0
    es_count = 0
    es_names = []
    for cf in classfiles:
        for name in methods_in_class(cf):
            total += 1
            if SPANISH_RE.search(name):
                es_count += 1
                es_names.append((str(cf), name))

    print(f"Clases .class analizadas: {len(classfiles)} ({'main+test' if INCLUDE_TEST else 'solo main'})")
    print(f"Metodos totales (incl. Lombok, excl. constructores/sinteticos): {total}")
    if total:
        print(f"Metodos con palabra en espanol: {es_count} ({es_count/total*100:.1f}%)")
    if "--list" in sys.argv:
        print()
        for f, n in es_names:
            print(f"  {n}  [{f}]")

if __name__ == "__main__":
    main()
