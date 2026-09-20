# Metadatos para el formulario de Zenodo — versión `v1.1.0`

Contenido listo para copiar en el borrador de **Nueva versión**. Cada campo va tal cual; las notas
entre paréntesis son instrucciones, no texto a copiar.

> **Esta es la SEGUNDA vez que se archiva `v1.1.0`.** La primera (2026-09-19, registro `22839517`, commit
> `35d8199`) se hizo antes de las correcciones de la revisión final; desde entonces cambiaron el informe, el
> Javadoc, los nombres de las pruebas y los verificadores, es decir, algo más que el registro del DOI. La
> etiqueta `v1.1.0` no se renombra (la revisión evalúa esa etiqueta): se archiva otra vez el contenido
> final bajo el mismo nombre de versión, y el registro anterior queda como el primer snapshot, superado.
> `make verify` (`scripts/p9-snapshot-zenodo.py`) exige que lo único que separe el commit archivado de la
> etiqueta sea el registro del DOI nuevo.

> ⚠️ **Antes de subir nada:** el paquete se genera desde el tag de Git. Comprobar que `v1.1.0` apunta
> al commit de cierre que se quiere archivar:
>
> ```bash
> git rev-list -n1 v1.1.0        # debe ser el commit de cierre, no uno anterior
> git rev-list --count v1.1.0..HEAD   # debe dar 0
> ```
>
> Si el segundo comando no da 0, el tag está por detrás y el snapshot archivaría un estado viejo.

---

## 1. Archivos

Un solo archivo, generado con:

```bash
sh scripts/zenodo-paquete.sh v1.1.0
```

Produce `PFC-Presustentaciones-2026-v1.1.0.tar.gz`.

**No uses «Importar archivos»** — ese botón trae el ZIP de la versión anterior. Si se importa y además
se sube el nuevo, el registro quedaría con dos archivos y dos versiones distintas del código dentro.

---

## 2. Identificador de objeto digital

**«No, necesito uno»** ← dejar marcada esta opción.

Zenodo acuña el DOI al publicar. No hay que pulsar «¡Obtén un DOI ahora!» salvo que se necesite el
número antes de publicar, que no es el caso.

---

## 3. Tipo de recurso

```
Software
```

---

## 4. Título

```
Sistema de Gestión de Pre-Sustentaciones de Titulación UTEQ
```

---

## 5. Autores

En este orden, con su afiliación y ORCID. Los cuatro son de `Universidad Técnica Estatal de Quevedo`.

| # | Apellidos | Nombres | ORCID |
|---|---|---|---|
| 1 | Álava Alvarado | Jean Pierre | `0009-0001-2878-2919` |
| 2 | Moncayo Loor | Xavier Alejandro | *(sin ORCID — dejar vacío)* |
| 3 | Zamora Arias | Carla Esthefanía | `0009-0000-7556-0457` |
| 4 | Barreto Rosado | Heider Dominick | `0009-0004-5561-1391` |

Moncayo Loor no tiene ORCID registrado. El campo se deja vacío en vez de inventar un identificador;
así está también en `CITATION.cff`.

---

## 6. Versión

```
v1.1.0
```

---

## 7. Fecha de publicación

```
(la fecha que imprima scripts/zenodo-paquete.sh)
```

Es la fecha del commit al que apunta el tag al empaquetar. La imprime `scripts/zenodo-paquete.sh` al
correr, para no teclearla de memoria.

---

## 8. Licencia

```
MIT License
```

---

## 9. Descripción

Copiar el bloque completo (Zenodo acepta HTML simple en este campo):

```html
<p>Versión <strong>v1.1.0</strong> del Sistema de Gestión de Pre-Sustentaciones de Trabajos de
Titulación de la Universidad Técnica Estatal de Quevedo. Aplicación web para la automatización,
gestión y evaluación del proceso de pre-sustentación.</p>

<p><strong>Arquitectura:</strong> Spring Boot 3 + Angular + PostgreSQL (PL/pgSQL) + Redis,
desplegada con Docker Compose y nginx.</p>

<p><strong>Cambios principales respecto de v1.0.1:</strong></p>
<ul>
  <li>Identificadores del código fuente traducidos al inglés, preservando de forma explícita cada
      nombre que viaja por HTTP (campos JSON, parámetros de consulta y variables de ruta) para no
      alterar el contrato con el frontend, y corrigiendo las regresiones que ese renombrado había
      introducido en los filtros de fecha de varios endpoints.</li>
  <li>Auditoría sistemática del contrato JSON entre backend y frontend, con comprobación automatizada
      incorporada a la verificación del proyecto.</li>
  <li>Segunda aplicación del instrumento SUS con marca de tiempo verificable por un tercero y
      consentimiento individual explícito (n=15), con corrección de Holm-Bonferroni sobre la familia
      de contrastes y el coeficiente alfa de Cronbach calculado y declarado.</li>
  <li>Verificación ejecutable: <code>make verify</code> corre la suite de pruebas real, el cuaderno de
      análisis, <code>javadoc</code> y el recálculo de las mediciones de rendimiento, y falla si algún
      resultado no coincide con lo publicado. Incluye comprobaciones de que ninguna expresión de
      autorización apunte a código inexistente, de que ningún nombre expuesto por HTTP dependa de un
      identificador interno, y de que no queden credenciales escritas en el repositorio.</li>
  <li>Documentación del código: los avisos de <code>javadoc</code> pasan de 682 a 170, y los 170
      restantes corresponden a una única causa declarada (constructores generados por Lombok que la
      herramienta no observa al analizar el código fuente). Los <code>@param</code> que solo repetían el
      nombre del parámetro pasan de 35 % a 0 %, y se recuperó documentación que <code>javadoc</code>
      descartaba en silencio (bloques apilados) y un comentario que había quedado dentro de una
      consulta JPQL.</li>
  <li>Nombres de las pruebas automatizadas: 789 métodos de prueba con nombre en español pasan a
      inglés, con una comprobación automatizada de tolerancia cero.</li>
  <li>Trazabilidad de las cifras publicadas: todas proceden de una corrida versionada, y una
      comprobación automatizada rechaza cualquier cifra que el expediente no respalde (cobertura, SUS,
      valores p, Lighthouse, archivos de evidencia citados). El propio verificador se prueba con un
      arnés de 33 mutaciones que inyecta defectos y exige que cada uno sea detectado.</li>
</ul>

<p><strong>Estado verificado de esta versión:</strong> 806 pruebas automatizadas (0 fallos, 0
errores); cobertura de 82,01 % de líneas (4022/4904) y 73,49 % de ramas (1483/2018) medida con JaCoCo
sobre PostgreSQL y Redis reales; informe final de 70 páginas compilado sin errores ni referencias sin
resolver; integración continua en verde en sus tres trabajos.</p>

<p><strong>Documentación incluida:</strong> informe final, especificación de requisitos bajo
ISO/IEC/IEEE 29148:2018, pruebas automatizadas con cobertura JaCoCo, pruebas de carga con k6 y
auditoría de seguridad OWASP.</p>

<p>El conjunto de datos de mediciones empíricas se archiva por separado, con su propia licencia
CC-BY 4.0: <a href="https://doi.org/10.5281/zenodo.22398713">10.5281/zenodo.22398713</a>.</p>
```

---

## 10. Palabras clave

Una por campo:

```
pre-sustentacion
titulacion
spring boot
angular
postgresql
uteq
c4 model
owasp
```

---

## 11. Identificadores relacionados

| Relación | Identificador | Tipo |
|---|---|---|
| `is supplemented by` | `10.5281/zenodo.22398713` | DOI (dataset de mediciones) |
| `is supplement to` | `https://github.com/gleiston-guerrero/PFC-Presustentaciones-2026/tree/v1.1.0` | URL (tipo Software) |

Y en la sección *Software* del formulario, **Repository URL**:

```
https://github.com/gleiston-guerrero/PFC-Presustentaciones-2026
```

---

## 12. Visibilidad

```
Público
```

Sin embargo (no aplicar embargo).

---

## Después de publicar

Zenodo devuelve un **DOI de versión** nuevo. Hay que registrarlo (lo hace quien tenga el repositorio abierto;
son archivos de registro, los únicos que pueden cambiar entre el snapshot y la etiqueta):

1. En [`ZENODO.md`](ZENODO.md): DOI de esta versión, enlace al registro, **commit archivado** (el commit
   al que apuntaba la etiqueta al empaquetar) y fila de la tabla de versiones. `p9-snapshot-zenodo.py` y
   `p9-zenodo-registro.py` leen de ahí.
2. En [`CITATION.cff`](../CITATION.cff): añadir el DOI a `identifiers` y marcar el anterior como el primer
   snapshot, superado.
3. En `README.md` y `VERIFICACION.md` (P9): el DOI de la versión.
4. Mover la etiqueta `v1.1.0` al último commit y volver a correr `make verify`.

**No hay que tocar el campo `doi:` de `CITATION.cff` ni el badge del README:** ambos citan el DOI de
concepto `10.5281/zenodo.21988563`, que pasa a resolver automáticamente a esta versión nueva.
