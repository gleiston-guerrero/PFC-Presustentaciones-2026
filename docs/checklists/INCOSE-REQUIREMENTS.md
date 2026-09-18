# Checklist — INCOSE Guide for Writing Requirements (v4)

**Referencia:** INCOSE (2023). *Guide for Writing Requirements*, INCOSE-TP-2010-006-04. Define 9
características de calidad **individuales** por requisito (Necesario, Apropiado, No ambiguo, Completo,
Singular, Factible, Verificable, Correcto, Conforme) y 6 características de calidad del **conjunto**
completo de requisitos (Completo, Consistente, Factible, Comprensible, Verificable como conjunto,
Acotado).
**Actualizado (Fase 7, 2026-08-17):** extendido de 3 requisitos de muestra a los **12 RF completos**,
tras reescribir las historias de usuario en formato Connextra+INVEST+Gherkin
([`../requisitos/historias/`](../requisitos/historias/)) y los casos de uso Cockburn
([`../requisitos/casos-de-uso/`](../requisitos/casos-de-uso/)). Este archivo se anexa al SRS final
([`../requisitos/SRS-v1.0.0.pdf`](../requisitos/SRS-v1.0.0.pdf)) como exige el criterio D0R.

## Parte 1 — 9 características individuales, los 12 requisitos

Leyenda: ✅ Cumple · 🟡 Cumple parcialmente · 🔴 No cumple

| RF | Necesario | Apropiado | No ambiguo | Completo | Singular | Factible | Verificable | Correcto | Conforme |
|---|---|---|---|---|---|---|---|---|---|
| RF-01 Autenticación | ✅ | ✅ | ✅ | 🟡 | ✅ | ✅ | ✅ | ✅ | 🟡 |
| RF-02 Registro solicitud | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 🟡 | 🟡 |
| RF-03 Asignar jurados | ✅ | ✅ | 🔴 | 🟡 | ✅ | ✅ | ✅ | ✅ | 🟡 |
| RF-04 Programar cronograma | ✅ | ✅ | ✅ | ✅ | 🟡 | ✅ | ✅ | 🟡 | 🟡 |
| RF-05 Evaluar por rúbrica | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 🟡 |
| RF-06 Generar actas | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 🟡 | 🟡 |
| RF-07 Firma digital | 🟡 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 🟡 | 🟡 |
| RF-08 Notificaciones | 🟡 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 🟡 | 🟡 |
| RF-09 Reportes | 🟡 | ✅ | ✅ | 🟡 | ✅ | ✅ | ✅ | 🟡 | 🟡 |
| RF-10 Gestión de salas | 🟡 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 🟡 | 🟡 |
| RF-11 Gestión de usuarios | ✅ | ✅ | ✅ | 🟡 | 🟡 | ✅ | ✅ | ✅ | 🟡 |
| RF-12 Anteproyectos | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 🟡 |

**Notas sobre los 🔴 y patrones recurrentes:**

- **RF-03 "No ambiguo" = 🔴**: "3 docentes jurados" en el enunciado original no aclara si es un mínimo
  o un valor exacto. El código sí fija la regla (exactamente 3, ver `PanelistServiceImpl`), pero la
  redacción del requisito por sí sola sigue siendo ambigua — no se corrigió la prosa del requisito en
  esta ronda, solo se documentó el hallazgo.
- **"Conforme" = 🟡 en los 12**: ningún requisito usa lenguaje normativo tipo "shall" — todos están en
  formato Historia de Usuario (Connextra), que es una convención de proyecto válida pero no el "lenguaje
  imperativo estándar de la industria de sistemas" que INCOSE prefiere para requisitos formales de nivel
  sistema. Es una decisión de estilo consciente (HU es más legible para el equipo y el docente), no un
  descuido — se declara aquí para que quede explícito.
- **"Correcto" = 🟡 en varios**: para RF-02, RF-04, RF-06, RF-07, RF-08, RF-09, RF-10 no existe una
  prueba automatizada real que confirme que la implementación hace exactamente lo que el requisito dice
  (ver [`../requisitos/CHANGELOG-REQ.md`](../requisitos/CHANGELOG-REQ.md) y
  [`../trazabilidad/matriz.csv`](../trazabilidad/matriz.csv)) — "correcto" se evaluó aquí contra el
  código fuente leído manualmente, no contra evidencia automatizada, así que se marca parcial en vez de
  ✅ pleno.
- **RF-07/08/09/10 "Necesario" = 🟡**: son las 4 HU marcadas `Should`/`Could` en
  [`../requisitos/historias/README.md`](../requisitos/historias/README.md) — necesarias para la
  experiencia completa, pero el flujo académico central no se bloquea si no existieran.

## Parte 2 — 6 características de calidad del conjunto completo

| Característica | Cumple | Evidencia / justificación |
|---|---|---|
| **Completo** (cubre todo el alcance declarado) | 🟡 | Los 12 RF cubren el ciclo completo descrito en el SRS (§1.2 Alcance). No cubren explícitamente reglas de borde institucionales (p. ej. qué pasa si un estudiante reprueba y necesita una segunda pre-sustentación) — no documentado como requisito separado |
| **Consistente** (sin contradicciones internas) | ✅ | Se corrigió en esta fase una inconsistencia real: `SRS.md` v0.9.0-rc numeraba HU-04/HU-05 de forma distinta a `matriz.csv` (HU-04 significaba "Evaluación" en un documento y "Cronograma" en el otro) — resuelto adoptando la numeración de `matriz.csv` (más completa) como canónica en v1.0.0 |
| **Factible** (implementable con los recursos reales) | ✅ | Los 12 están implementados y funcionando (verificado end-to-end en la Fase 5) |
| **Comprensible** (lenguaje claro para todos los interesados) | ✅ | Formato Connextra + Gherkin es deliberadamente más legible para stakeholders no técnicos que notación formal |
| **Verificable como conjunto** (existe una forma de confirmar que el conjunto se cumplió) | 🟢 | Ver `matriz.csv` v1.0.0: **8/8 requisitos Must tienen prueba automatizada real (100%, actualizado 2026-08-29** tras agregar `MinutesServiceImplTest.java`, el último que faltaba). El criterio D0R exige 100% — se cumple para los Must; a nivel de los 12 requisitos totales del sistema (incluyendo Could/Should) es 9/12 (75%): RF-08, RF-09 y RF-10 siguen sin prueba dedicada, declarado explícitamente, no se afirma 100% general |
| **Acotado** (el alcance no crece sin control) | ✅ | El conjunto pasó de 5 HU formalizadas (v0.9.0-rc) a 12 (v1.0.0) por una razón concreta y única: completar lo que `matriz.csv` ya asumía que existía, no por expansión de alcance no planificada — ver `CHANGELOG-REQ.md` |

## Conclusión

El punto más débil real, tanto a nivel individual como de conjunto, es **verificabilidad automatizada
efectiva**: el lenguaje de los requisitos es verificable (criterios de aceptación en Gherkin existen
para los 12), pero la **evidencia automatizada que los verifique en la práctica** solo existe para 5 de
12. Esta es la brecha concreta que el criterio D0R exige cerrar antes de que el SRS pueda calificarse
por encima de insuficiente, y queda declarada explícitamente en vez de maquillarse con un 100% falso.
