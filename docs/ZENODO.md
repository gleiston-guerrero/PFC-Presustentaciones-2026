# 🌐 REGISTRO DE IDENTIFICADOR PERSISTENTE DOI EN ZENODO — DEPÓSITO DEL SOFTWARE

**Proyecto:** Sistema de Gestión de Pre-Sustentaciones UTEQ  
**DOI que se cita (de concepto, resuelve siempre a la versión más reciente):**
[10.5281/zenodo.21988563](https://doi.org/10.5281/zenodo.21988563)  
**Última versión archivada:** `v1.0.1` → [10.5281/zenodo.22445216](https://doi.org/10.5281/zenodo.22445216)
(2026-09-06)  
**Estado de `v1.1.0`:** 🟡 **sin snapshot propio todavía.** Es una acción manual en zenodo.org y no se
declara un DOI que nadie pueda verificar. La evaluación integral del 17-sep lo señaló
(«el DOI declarado archiva la v1.0.1») y sigue abierto — procedimiento completo en
[«Cómo archivar una versión nueva»](#-cómo-archivar-una-versión-nueva-procedimiento-vigente).  
**Licencia:** MIT Open Source License  
**Alcance de este documento:** el DOI del **software** (el código de este repositorio). El
conjunto de datos de mediciones (k6, ZAP, Lighthouse, JaCoCo) se deposita por separado, con su
propia licencia CC-BY 4.0 y su propio DOI ([10.5281/zenodo.22398713](https://doi.org/10.5281/zenodo.22398713)),
siguiendo el principio de citación independiente entre software y datos — ver
[`ZENODO-DATASET.md`](ZENODO-DATASET.md).

---

## 📌 Versiones archivadas

Zenodo trata cada tag como una versión distinta bajo el mismo DOI de concepto.

**⚠️ Corrección de criterio (2026-09-11):** hasta esta fecha, este documento afirmaba que el tag
Git `v1.0.0` se conservaba intacto porque un DOI publicado ya lo usaba como referencia, y que por
eso el equipo creó `v1.0.1` en su lugar en vez de mover `v1.0.0`. Ese razonamiento es correcto desde
el punto de vista de citación académica, pero choca con un requisito operativo más importante: la
rúbrica del examen final del docente-director evalúa **literalmente el commit al que apunte el tag
`v1.0.0`** ("si lo dejan donde está hoy, reviso el commit viejo y todo lo que hicieron después no
cuenta"). Dejar `v1.0.0` en el commit de agosto significaba que ninguna de las correcciones de esta
entrega —incluidas las de esta misma sesión— contaba para la evaluación.

Se decidió mover `v1.0.0` al commit de cierre real, aceptando la consecuencia declarada
explícitamente: **el DOI `10.5281/zenodo.21988564` sigue siendo válido y sigue archivando el
contenido exacto de la versión de agosto** (Zenodo archiva un snapshot fijo, no una referencia viva
al tag), pero ese snapshot ya no coincide con lo que el tag Git `v1.0.0` apunta hoy. El commit
original queda preservado bajo el tag `v1.0.0-zenodo-archive`, para que la correspondencia con ese
DOI siga siendo verificable sin depender de que nadie recuerde el hash de memoria.

| Versión | Tag Git | DOI de la versión | Publicado | Notas |
|---|---|---|---|---|
| **v1.0.1** (informe/portada) | `v1.0.1` | [10.5281/zenodo.22445216](https://doi.org/10.5281/zenodo.22445216) | 6 sep 2026 | Cierre real de la Entrega Final: correcciones de las Entregas 1A/1B/3 aplicadas (cobertura 63,17 %, CSP endurecida, catálogo de SP completo, evidencia OWASP real) — ver `docs/observaciones/OBSERVACIONES.md`. |
| **v1.0.0** (movido, examen final) | `v1.0.0` | — (no vuelve a archivarse; ver `v1.0.0-zenodo-archive`) | movido 11 sep 2026 | Apunta hoy al commit de cierre real que se defiende en el examen final, no al release de agosto. |
| v1.0.0 (original) | `v1.0.0-zenodo-archive` | [10.5281/zenodo.21988564](https://doi.org/10.5281/zenodo.21988564) | 18 ago 2026 | El commit exacto que el DOI de agosto archivó, preservado bajo este nombre tras mover `v1.0.0`. |

- **Enlace permanente a la última versión:** [`https://doi.org/10.5281/zenodo.21988563`](https://doi.org/10.5281/zenodo.21988563) (DOI de concepto — usar este enlace cuando se quiera citar "el software" en general, no una corrida específica).
- **Registro de la versión actual:** `https://zenodo.org/records/22445216`
- **Badge oficial (cita siempre la última versión):**
  [![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.21988563.svg)](https://doi.org/10.5281/zenodo.21988563)

---

## 📦 Cómo archivar una versión nueva (procedimiento vigente)

### La integración con GitHub está rota, y no hace falta arreglarla

La integración automática GitHub → Zenodo de este proyecto quedó registrada sobre
`carla22072004/PFC-Presustentaciones-2026`, una ruta que **ya no existe bajo esa cuenta**: el
repositorio se transfirió a `gleiston-guerrero`. En el panel de Zenodo (Settings → GitHub) el
repositorio sigue apareciendo con el interruptor en ON y con tres intentos fallidos de `v0.9.0-rc`,
porque el webhook apunta a un lugar que la cuenta ya no controla.

Reactivarla exigiría que el **nuevo propietario** vincule su cuenta de GitHub con Zenodo y active el
repositorio allí. Eso no depende del equipo.

**No es necesario.** Zenodo publica igual con una subida manual, y el DOI que produce es exactamente
del mismo tipo que el que produciría la integración: mismo DOI de concepto, misma familia de
versiones, misma resolución. Lo único que cambia es quién sube el archivo. El registro publicado de
`v1.0.0` (DOI `10.5281/zenodo.21988564`) sigue intacto y es la raíz de la familia.

### El orden importa: primero la etiqueta, después Zenodo

Zenodo archiva el contenido que se le sube, y el paquete se genera **desde el tag de Git**. Si el tag
todavía apunta a un commit anterior al cierre, el snapshot archivaría ese estado viejo — que es
precisamente el problema que este punto vino a resolver.

> **Secuencia correcta:** mover la etiqueta al commit de cierre → generar el paquete → subir a Zenodo
> → actualizar el número de versión en este documento. Nunca al revés.

### Pasos

**1. Generar el paquete desde la etiqueta ya colocada**

```bash
sh scripts/zenodo-paquete.sh v1.1.0
```

Sale de `git archive` sobre el tag, no del directorio de trabajo: archiva exactamente el commit
etiquetado, sin archivos sin commitear, sin `target/` ni `node_modules/`. El script imprime el commit,
la fecha y los metadatos a copiar.

**2. Crear la versión nueva en Zenodo**

Entrar al registro existente con la cuenta que lo publicó y usar **«New version»**, *no* «New upload»:

<https://doi.org/10.5281/zenodo.21988563>

Ese botón conserva el **DOI de concepto** (`10.5281/zenodo.21988563`) y encadena la versión nueva a
las anteriores. Un «New upload» crearía una familia separada y rompería la cadena de citación — es el
error más fácil de cometer aquí.

**3. Subir y completar**

- Borrar el archivo de la versión anterior que Zenodo arrastra al formulario y subir el `.tar.gz` nuevo.
- Rellenar los metadatos con lo que imprime el script (título, versión, fecha, tipo `Software`,
  licencia MIT).
- Autores con sus ORCID, tal como están en [`CITATION.cff`](../CITATION.cff).
- **Publish.**

**4. Actualizar este repositorio**

Zenodo devuelve un DOI de versión nuevo. Añadirlo a la lista de `identifiers` de `CITATION.cff` y al
cuadro de versiones de este documento.

**No hay que cambiar el campo `doi:` de `CITATION.cff` ni el badge del README:** ambos citan el DOI de
concepto, que pasa a resolver solo a la versión nueva. Esa es justamente la razón de citar el concepto
y no una versión — hasta el 2026-09-18 el campo apuntaba a `22445216` (v1.0.1) mientras `version`
declaraba 1.1.0, la contradicción que señaló la evaluación integral.

---

## 🏷️ Metadatos del Registro y Cita Académica

- **Título del Registro:** Sistema de Gestión de Pre-Sustentaciones de Titulación UTEQ
- **Licencia:** MIT Open Source License
- **Repositorio GitHub:** `https://github.com/carla22072004/PFC-Presustentaciones-2026`
- **Autores CRediT:** Jean Pierre Alava Alvarado ([ORCID: 0009-0001-2878-2919](https://orcid.org/0009-0001-2878-2919)), Xavier Alejandro Moncayo Loor, Carla Esthefania Zamora Arias, Heider Dominick Barreto Rosado.
- **Verificación externa real (2026-09-06):** `curl https://zenodo.org/api/records/22445216` (API pública
  de Zenodo, no solo el badge) devuelve `version: v1.0.1`, `conceptdoi: 10.5281/zenodo.21988563`,
  licencia MIT, los mismos 4 autores en el mismo orden, y el tamaño de archivo (14.415.965 bytes)
  coincide exactamente con el `git archive` generado localmente sobre el tag `v1.0.1` — no solo con lo
  que muestra la interfaz de Zenodo. La verificación anterior (2026-08-30) sobre `v1.0.0` sigue siendo
  válida para esa versión.
- **Formato de Cita BibTeX (versión actual):**

```bibtex
@software{alava_alvarado_2026_presustentaciones,
  author       = {Alava Alvarado, Jean Pierre and Moncayo Loor, Xavier Alejandro and Zamora Arias, Carla Esthefania and Barreto Rosado, Heider Dominick},
  title        = {Sistema de Gestión de Pre-Sustentaciones de Titulación UTEQ},
  year         = 2026,
  publisher    = {Zenodo},
  version      = {v1.0.1},
  doi          = {10.5281/zenodo.22445216},
  url          = {https://doi.org/10.5281/zenodo.22445216}
}
```
