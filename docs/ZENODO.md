# 🌐 REGISTRO DE IDENTIFICADOR PERSISTENTE DOI EN ZENODO — DEPÓSITO DEL SOFTWARE

**Proyecto:** Sistema de Gestión de Pre-Sustentaciones UTEQ  
**DOI que se cita (de concepto, resuelve siempre a la versión más reciente):**
[10.5281/zenodo.21988563](https://doi.org/10.5281/zenodo.21988563)  
**Última versión archivada:** `v1.1.0` → [10.5281/zenodo.22883939](https://doi.org/10.5281/zenodo.22883939)
(2026-09-21; cuarto snapshot, con el contenido final)  
**Estado de `v1.1.0`:** ✅ **Archivada el 2026-09-19.** La evaluación integral del 17-sep señaló que
«el DOI declarado archiva la v1.0.1», y la del 18-sep que «la v1.1.0 no está archivada». Ya lo está:
el registro encadena `v1.0.0 → v1.0.1 → v1.1.0` bajo el mismo DOI de concepto.

### Qué commit contiene exactamente el snapshot archivado

| | |
|---|---|
| DOI de esta versión | [10.5281/zenodo.22883939](https://doi.org/10.5281/zenodo.22883939) |
| Registro | <https://zenodo.org/records/22883939> |
| **Commit archivado** | **`37e6006`** |
| Archivo | `PFC-Presustentaciones-2026-v1.1.0-2026-09-21-final.tar.gz`, 1166 archivos, md5 `c4ff8a4555510a2adbaf737d00e69e15` |
| Generado con | `sh scripts/zenodo-paquete.sh v1.1.0` (`git archive` sobre el tag, no sobre el directorio de trabajo) |

**Es la cuarta vez que se archiva `v1.1.0`.** Cada vez que la revisión del ingeniero encontró algo, la
corrección cambió código o scripts, y un snapshot que no contiene la corrección deja de servir como
evidencia de ella:

| Snapshot | Registro | Commit | Por qué quedó superado |
|---|---|---|---|
| 1.º (2026-09-19) | `22839517` | `35d8199` | Anterior a las correcciones de la revisión final |
| 2.º (2026-09-20) | `22854267` | `a60ae4c` | Anterior al punto 5 (autorización de los `GET`, p crudo del SUS, 809 → 823 pruebas) |
| 3.º (2026-09-21) | `22865913` | `6515713` | Anterior a los cinco puntos de la revisión del 21-sep y a la procedencia del SUS |
| **4.º (2026-09-21)** | **`22883939`** | **`37e6006`** | **Vigente** |

La etiqueta no se renombró en ninguna (la revisión evalúa `v1.1.0` por nombre) y se archivó de nuevo el
contenido final bajo el mismo nombre. Los tres registros anteriores quedan como snapshots **superados**; el
DOI de concepto resuelve ya al nuevo. Comprobado el 2026-09-21 contra la API pública: el archivo publicado
tiene el mismo md5 (`c4ff8a45…`) y el mismo tamaño (33 938 890 bytes) que el paquete generado localmente, un
solo archivo en el registro, y los metadatos (`Version v1.1.0`, fecha `2026-09-21`, DOI de concepto intacto)
son los esperados.

> **Reserva abierta en el registro.** La descripción publicada aún dice «arnés de 40 mutaciones»: se arrastró
> la de la versión anterior al crear esta. El código archivado tiene **54**. Los archivos de un registro
> publicado no se pueden reemplazar, pero la descripción sí se edita, y esa corrección queda pendiente sobre
> el propio registro `22883939`. Se deja dicho aquí en vez de esperar a que lo encuentre la revisión.

**Por qué el tag queda por delante del commit archivado, y por qué no es una contradicción.** El DOI
no existe hasta que se publica, así que los commits que lo registran —este archivo, `CITATION.cff`,
`VERIFICACION.md`— son necesariamente posteriores al snapshot. Es un problema de orden, no de
contenido: **lo único que separa `6515713` del commit etiquetado es el registro de ese mismo DOI.**
Comprobable en un comando:

```bash
git diff --stat 6515713..v1.1.0
```

`make verify` lo comprueba en cada corrida (`scripts/p9-snapshot-zenodo.py`): si entre el commit
archivado y el tag aparece cualquier cambio que no sea registro del DOI, **falla**. Sin eso, la frase
de arriba sería una promesa; con eso, es una comprobación.

### Estado de los metadatos del registro, verificado el 2026-09-19

Al publicar quedaron mal dos campos, y se corrigieron desde «Editar» — Zenodo permite cambiar
metadatos después de publicar y solo prohíbe cambiar los archivos. Comprobado leyendo la página
pública del registro, no dando por buena la edición:

| Campo | Estado |
|---|---|
| `Version` | ✅ `v1.1.0`. Al publicar quedó como `v3`, el correlativo que Zenodo pone cuando el campo se deja vacío, que no corresponde a ninguna versión de este proyecto |
| `Description` | ✅ Describe los cambios de v1.1.0 y su estado medido. Al publicar se arrastró la de v1.0.1 |
| `Version` en la cadena | ✅ `v1.0.0 → v1.0.1 → v1.1.0` bajo el mismo DOI de concepto |

### Lo que la revisión final del 2026-09-19 pidió corregir (P9), y cómo se comprueba

Dio P9 «cumple con reservas»: el tarball depositado es bit a bit el `git archive` del commit
declarado (1153 de 1153 archivos), **pero los metadatos del registro están mal en tres ejes**. Es
exacto; la API pública del registro lo confirma:

| Eje | Cómo está en el registro | Qué tiene que decir | Dónde se edita |
|---|---|---|---|
| 1. Cuenta antigua | *Repository URL* = `github.com/carla22072004/PFC-Presustentaciones-2026` | `https://github.com/gleiston-guerrero/PFC-Presustentaciones-2026` | Editar → sección *Software* → *Repository URL* |
| 2. Versión anterior | *Related works* → **Is supplement to** = `github.com/carla22072004/…/tree/v1.0.0` | `https://github.com/gleiston-guerrero/PFC-Presustentaciones-2026/tree/v1.1.0` (URL, relación *Is supplement to*, tipo *Software*) | Editar → *Related works* → cambiar ese identificador |
| 3. Sin dataset | No hay ningún identificador que apunte al conjunto de datos | Agregar `10.5281/zenodo.22398713` (DOI), relación **Is supplemented by**, tipo *Dataset* | Editar → *Related works* → agregar |

Una corrección que este archivo dijo antes —que el bloque *External resources* «no es editable»— era
una conclusión sin comprobar: los tres datos salen de campos del formulario (`code:codeRepository` y
`related_identifiers` en la API), y los tres son editables. El bloque de la página es lo que el registro
muestra a partir de ellos.

**Estado: corregido y comprobado el 2026-09-19.** Los tres ejes se editaron desde «Editar» y `python
scripts/p9-zenodo-registro.py` los lee de la API pública: cuenta vigente, `tree/v1.1.0`, dataset enlazado. El
bloque *External resources* de la página pasó a mostrar `gleiston-guerrero/PFC-Presustentaciones-2026`, *Release:
v1.1.0*.

**Comprobación, sin fiarse de lo que uno recuerde haber guardado:**

```bash
python scripts/p9-zenodo-registro.py
```

Lee `https://zenodo.org/api/records/22883939` y falla mientras alguno de los tres ejes siga mal (además
de exigir `Version = v1.1.0` y que la descripción no lleve texto de instrucción pegado). Corre dentro de
`make verify`. Los datos esperados salen de `CITATION.cff` y de `ZENODO-DATASET.md`, no de constantes.

**El dataset, una precisión que conviene saber antes de que la haga otro:** el depósito enlazado
(`10.5281/zenodo.22398713`) es de **2026-09-05** y contiene las mediciones de rendimiento, seguridad y
calidad web (35 archivos: k6, ZAP, Lighthouse, JaCoCo; se listó el `.zip` público). **No incluye ningún
dato del SUS**, ni la ronda en papel ni la del 18-sep (n=15): esos viven en el repositorio. Enlazarlo
cierra lo que se pidió; incorporar esa ronda al dataset sería una versión nueva de *ese* depósito, y
no se hizo.  
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
| **v1.1.0** (cierre del examen suspenso, contenido final) | `v1.1.0` | [10.5281/zenodo.22883939](https://doi.org/10.5281/zenodo.22883939) | 21 sep 2026 | Snapshot del commit `37e6006`. Cierra los cinco puntos de calidad del verificador de la revisión del 21-sep y la procedencia del SUS. |
| v1.1.0 (tercer snapshot, superado) | `v1.1.0` | [10.5281/zenodo.22865913](https://doi.org/10.5281/zenodo.22865913) | 21 sep 2026 | Snapshot del commit `6515713`. Cierra el punto 5 de la revisión anterior (autorización de los `GET`, p crudo del SUS, P12 documentado). |
| v1.1.0 (segundo snapshot, superado) | `v1.1.0` | [10.5281/zenodo.22854267](https://doi.org/10.5281/zenodo.22854267) | 20 sep 2026 | Snapshot del commit `a60ae4c`. Cierra EV-2, EV-4, P1, P3, P4, P7, P8, P9, P10, P11 y las credenciales en claro de las evaluaciones del 17 y 18 de septiembre. Sigue publicado; lo sustituye el de arriba. |
| v1.1.0 (primer snapshot, superado) | `v1.1.0` | [10.5281/zenodo.22839517](https://doi.org/10.5281/zenodo.22839517) | 19 sep 2026 | Snapshot del commit `35d8199`, anterior a las correcciones de la revisión final. Sigue publicado; lo sustituye el de arriba. |
| **v1.0.1** (informe/portada) | `v1.0.1` | [10.5281/zenodo.22445216](https://doi.org/10.5281/zenodo.22445216) | 6 sep 2026 | Cierre real de la Entrega Final: correcciones de las Entregas 1A/1B/3 aplicadas (cobertura 63,17 %, CSP endurecida, catálogo de SP completo, evidencia OWASP real) — ver `docs/observaciones/OBSERVACIONES.md`. |
| **v1.0.0** (movido, examen final) | `v1.0.0` | — (no vuelve a archivarse; ver `v1.0.0-zenodo-archive`) | movido 11 sep 2026 | Apunta hoy al commit de cierre real que se defiende en el examen final, no al release de agosto. |
| v1.0.0 (original) | `v1.0.0-zenodo-archive` | [10.5281/zenodo.21988564](https://doi.org/10.5281/zenodo.21988564) | 18 ago 2026 | El commit exacto que el DOI de agosto archivó, preservado bajo este nombre tras mover `v1.0.0`. |

- **Enlace permanente a la última versión:** [`https://doi.org/10.5281/zenodo.21988563`](https://doi.org/10.5281/zenodo.21988563) (DOI de concepto — usar este enlace cuando se quiera citar "el software" en general, no una corrida específica).
- **Registro de la versión actual:** `https://zenodo.org/records/22883939`
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

> ### ⚠️ «Editar» y «Nueva versión» hacen cosas distintas
>
> El registro publicado muestra los dos botones juntos, y confundirlos es el error más caro de este
> procedimiento:
>
> | Botón | Qué hace | Resultado si se usa aquí |
> |---|---|---|
> | **Editar** | Modifica ese mismo registro en el sitio | Conserva su DOI, **sus archivos** y su fecha. Cambiarle la etiqueta a `v1.1.0` dejaría un DOI que *dice* v1.1.0 y *contiene* el ZIP de v1.0.0, fechado antes que v1.0.1 — un registro que afirma archivar algo que no tiene |
> | **Nueva versión** | Crea un registro nuevo con DOI propio | ✅ Lo correcto. Encadena `v1.0.0 → v1.0.1 → v1.1.0` bajo el mismo DOI de concepto |
>
> «Editar» sirve para corregir un metadato equivocado de una versión ya publicada (un ORCID mal
> escrito, por ejemplo), nunca para convertir una versión en otra.
>
> Conviene además pulsar «Nueva versión» desde la **versión más reciente** (`v1.0.1`,
> [10.5281/zenodo.22445216](https://doi.org/10.5281/zenodo.22445216)) y no desde `v1.0.0`: funciona
> desde cualquiera, pero así el formulario llega prellenado con los metadatos más nuevos.

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
