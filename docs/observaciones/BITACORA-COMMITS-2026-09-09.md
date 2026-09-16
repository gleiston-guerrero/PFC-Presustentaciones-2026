# Bitácora: commit vacío para registrar una segunda identidad de GitHub como colaborador (2026-09-09)

**Commit afectado:** `3e7069c`
**Autor y committer:** `jalavaa-dev <jalavaa@uteq.edu.ec>`
**Fecha (autor y commit):** 2026-09-09, 22:48:06 / 22:50:00 (-05:00)
**Mensaje:** `chore: registrar participacion de Alava Alvarado en el historial del repo`

## Qué pasó

Este commit no modifica ningún archivo — se verifica con `git show --stat 3e7069c`, que no
devuelve ningún archivo en el resumen.

`jalavaa-dev <jalavaa@uteq.edu.ec>` es una segunda identidad de Git de Jean Pierre Alava
Alvarado (autor principal del repositorio bajo `Jean30042 <jeanalavaalavarado@gmail.com>`),
configurada con su correo institucional de la UTEQ. El docente-director pidió que esa cuenta
institucional también apareciera en la lista de colaboradores de GitHub del repositorio. Como
GitHub solo reconoce a alguien como "colaborador" (con el ícono correspondiente en la lista de
commits) si tiene al menos un commit registrado con ese email, se creó este commit vacío
específicamente para que esa cuenta quedara vinculada — no para registrar trabajo o contenido
adicional que no existiera ya.

## Por qué se documenta como anomalía de todos modos

Un commit vacío, con una identidad que no aparece en ningún otro lugar del historial ni en
`CITATION.cff`/`CONTRIBUTORS.md`, y cuyo mensaje dice literalmente "registrar participación", es
exactamente el patrón que en una auditoría de integridad se lee como un intento de simular
participación o trabajo que no ocurrió. No fue eso: la intención declarada y verificable (nunca
se agregó código, texto ni ningún artefacto bajo esta identidad, en este commit ni en ningún
otro) fue únicamente que la cuenta institucional quedara visible como colaboradora, a pedido del
docente. Se documenta aquí con la misma honestidad que los commits vacíos de
[`BITACORA-COMMITS-2026-09-02.md`](BITACORA-COMMITS-2026-09-02.md), en vez de dejarlo sin
explicar y que parezca lo primero.

## Cierre (2026-09-16, examen suspenso P12)

`jalavaa-dev` no estaba listada en `CITATION.cff` ni en `CONTRIBUTORS.md` como una identidad
adicional de Jean Pierre Alava Alvarado — el registro de autoría formal del proyecto sigue
siendo, correctamente, solo `Jean30042`, ya que `CITATION.cff` sigue un esquema fijo sin campo
para identidades de Git adicionales de un mismo autor. Se agregó la aclaración en texto libre en
`CONTRIBUTORS.md`, bajo la entrada de Alava Alvarado, dejando explícito que ambas identidades
corresponden a la misma persona y enlazando a esta bitácora.

## Cómo verificar que esta nota es fiel

```bash
# Confirmar que el commit no toca ningún archivo
git show --stat 3e7069c5ea40c596908dc7ef0661fea55c627ed9

# Confirmar que esta identidad no aparece en ningún otro commit del historial
git log --all --author="jalavaa-dev" --oneline

# Confirmar que no está declarada en los metadatos de autoría del proyecto
grep -i jalavaa CITATION.cff CONTRIBUTORS.md
```
