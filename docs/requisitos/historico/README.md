# Versiones superadas del SRS — no son el documento vigente

> ⚠️ **Nada de esta carpeta es la especificación vigente.** El SRS que rige es
> [`docs/requisitos/SRS-v1.0.1.tex`](../SRS-v1.0.1.tex) (y su PDF compilado). Lo de aquí se conserva
> por trazabilidad: para poder comparar qué decía cada versión y cuándo cambió.

## Por qué existe esta nota

La revisión individual del 2026-09-18 listó, entre las cifras contradictorias de P11, *«rutinas “8+2”
frente a “7+3”»*. Es un hallazgo correcto en el sentido literal: las dos cifras conviven en el
repositorio.

Pero no compiten:

| Dónde | Qué dice | Estado |
|---|---|---|
| [`SRS-v1.0.1.tex`](../SRS-v1.0.1.tex) | 10 rutinas SQL por nombre: **7 procedimientos y 3 funciones** | ✅ **Vigente** |
| `SRS-v1.0.0-2026-09-08.tex` (aquí) | 10 rutinas SQL: «8 procedimientos y 2 funciones de auditoría» | ⛔ **Superado** |

El desglose «8 + 2» era **incorrecto** y se corrigió en `7b7016c`: no cuadraba con el código bajo
ninguna lectura. El real, contado por nombre invocable desde Java, es **7 procedimientos y 3
funciones**; contando objetos del esquema materializado son **8 `PROCEDURE` + 5 `FUNCTION` = 13**,
porque 3 nombres conservan una sobrecarga adicional sin uso desde una fusión de ramas de agosto de
2026. Las dos cifras y la razón de la diferencia están declaradas en el SRS vigente y en
[`docs/basedatos/CATALOGO-SP.md`](../../basedatos/CATALOGO-SP.md).

Reproducible:

```bash
# 10 nombres distintos de rutina invocables
grep -rhoE "CREATE (OR REPLACE )?(PROCEDURE|FUNCTION) [a-zA-Z0-9_.]+" \
  backend/src/main/resources/db/migration/V*.sql \
  | awk '{print $NF}' | sed 's/.*\.//' | sort -u | wc -l

# 13 objetos en el esquema materializado (cuenta las sobrecargas)
grep -c "^CREATE \(OR REPLACE \)\?\(PROCEDURE\|FUNCTION\)" database/esquema.sql
```

`make verify` comprueba el primero en cada corrida (P11).

## Qué hay aquí

| Archivo | Qué es |
|---|---|
| `SRS-v1.0.0-2026-09-08.tex` / `.pdf` / `.docx` | SRS v1.0.0, superado por v1.0.1 |
| `SRS-v1.0.0-borrador-2026-08-17.tex` / `.pdf` | Borrador previo a la v1.0.0 |
| `SRS-v0.9.0-rc.md` | Versión candidata en Markdown, anterior al formato LaTeX |
| `matriz-v0.9.0-rc.csv` | Matriz de requisitos de esa candidata |

**No se corrigen retroactivamente.** Un documento fechado registra lo que se creía cierto cuando se
escribió; reescribirlo para que cuadre con la medición de hoy sería falsear el historial, que es
exactamente lo que este proyecto viene corrigiendo.
