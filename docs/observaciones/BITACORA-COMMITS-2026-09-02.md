# Bitácora: tres commits vacíos y el reporte que citaba hashes inexistentes (2026-09-02)

**Commits afectados:** `de0eeef`, `1139344`, `4b5aa34`
**Autor:** carla22072004 <czamoraa5@uteq.edu.ec>
**Fecha de autor:** 2026-09-02, 21:18:38 / 21:19:30 / 21:31:28 (-05:00)
**Fecha de commit (los tres):** 2026-09-02, 21:48:35 / 21:48:35 / 21:48:36 (-05:00)

## Qué pasó

Durante la ronda de correcciones del 2026-09-02, en `docs/observaciones/ENTREGA-FINAL.md` (creado
en el commit `3f7f6ef`, 21:47:13) quedaron citados 5 hashes de commit como evidencia de las
correcciones aplicadas esa noche. Ninguno de esos 5 hashes existe en el historial de este
repositorio — se verificó con `git cat-file -e` sobre cada uno.

Tres commits con mensajes que describen exactamente el contenido de esas correcciones
(`docs: corregir trazabilidad de commits`, `docs: corregir evidencias de rendimiento k6`,
`docs: completar ADR-007`) sí existen, pero **no modifican ningún archivo** — se verifica con
`git show --stat <hash>`, que no devuelve ningún archivo en el resumen. Su fecha de autor está
repartida entre las 21:18 y las 21:31, pero los tres quedaron registrados en git (`CommitDate`) en
el mismo minuto, 21:48:35–36 — un segundo de diferencia entre sí. Esa combinación (fechas de autor
distintas, fecha de commit casi idéntica, contenido vacío) es la firma típica de una reescritura o
un rebase hecho al cierre de la sesión de trabajo, no de tres commits escritos y guardados uno por
uno como sugieren sus mensajes.

El trabajo real que esos mensajes describen sí existe, solo que en otros commits: por ejemplo,
`docs/adr/ADR-007-despliegue-docker.md` se completó en el commit `14d6ecb` (2026-08-31), no en
`4b5aa34` (2026-09-02) como su mensaje sugiere.

## Por qué se documenta aquí en vez de reescribir la historia

Igual que en `BITACORA-COMMITS-2026-09-05.md`: estos tres commits ya estaban publicados en
`origin` cuando se detectó el problema, y tienen **97 commits posteriores** hasta la fecha de esta
nota. Reescribirlos (rebase o `filter-branch`) cambiaría el hash de esos 97 commits, incluidos los
5 hashes reales que reemplazan a los falsos en `ENTREGA-FINAL.md` (corregidos por separado, ver
commit `53ab207`) y el commit de cierre que cita la portada del informe final. Exigiría además un
`push --force` sobre una rama compartida, rompiendo la copia local de cualquier integrante que ya
hubiera hecho `pull`. Se prefiere un historial con tres commits vacíos y una nota que los explique,
antes que reescribir 97 commits a días del cierre para "limpiar" tres que ya no tienen forma de
ocultarse (siguen en el reflog y en cualquier clon anterior de todas formas).

## Cómo verificar que esta nota es fiel

```bash
# Confirmar que los tres commits no tocan ningun archivo
git show --stat de0eeef0d177d4344ad8dc74a9055d9c470c621e
git show --stat 1139344d3a3eb0db76c384147b54e55a79f2fa5a
git show --stat 4b5aa34b493fff3e6356a8128ff7438897301acf

# Confirmar la fecha de autor vs. fecha de commit de cada uno
git log -1 --format="autor: %ad%ncommit: %cd" --date=iso de0eeef0d177d4344ad8dc74a9055d9c470c621e
git log -1 --format="autor: %ad%ncommit: %cd" --date=iso 1139344d3a3eb0db76c384147b54e55a79f2fa5a
git log -1 --format="autor: %ad%ncommit: %cd" --date=iso 4b5aa34b493fff3e6356a8128ff7438897301acf

# Confirmar que ENTREGA-FINAL.md se creo un minuto antes de que estos tres quedaran registrados
git log -1 --format="%ad" --date=iso 3f7f6efc28f6b9cc70a59e106a63a68a40dbc070

# Confirmar donde se completo realmente el ADR-007
git log --oneline --all -- docs/adr/ADR-007-despliegue-docker.md
```

## Seguimiento (2026-09-13): correo al docente-director, sin respuesta

El punto 1 de la revisión del examen suspenso ("Conversación sobre la ronda del 2 de septiembre",
peso 0,5) exige, según la nota del propio ingeniero en esa revisión, que el punto se cierre en
conversación directa con él ("Este punto se cierra con usted, no en el repositorio") — no basta con
documentación en el repo.

Alava Alvarado le escribió por correo el 2026-09-13 a las 18:51 (asunto "Sobre el Punto 1 de la
revisión del examen final", `jalavaa@uteq.edu.ec` → `gguerrero`), explicando: (a) que el equipo no
tiene registro de que esa conversación se haya dado y no pudo identificar con certeza a qué se
refería; (b) el origen real de los tres commits vacíos del 2 de septiembre documentados arriba, por
si la conversación esperada tenía que ver con ellos; y pidiendo indicación de cómo abordar el punto.
Se adjuntó la captura de la fila exacta de la revisión del ingeniero como referencia. Evidencia:
[`evidencia-correo-2026-09-13-punto1.png`](evidencia-correo-2026-09-13-punto1.png).

Una conversación presencial no es viable en el tiempo que queda antes del plazo del examen, por lo
que el correo es el sustituto práctico disponible. A la fecha de esta nota, sin respuesta del
ingeniero. Lo que dependía del equipo para este punto — identificar el origen de los commits,
preguntar directamente en vez de asumir, y dejar constancia fechada del intento — está hecho; lo que
falta (la respuesta/conversación en sí) depende exclusivamente del ingeniero, según su propio
criterio de cierre, y no es algo que el equipo pueda completar unilateralmente.
