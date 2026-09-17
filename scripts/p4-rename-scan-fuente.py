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
        for m in METHOD_RE.finditer(text):
            name = m.group(1)
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
