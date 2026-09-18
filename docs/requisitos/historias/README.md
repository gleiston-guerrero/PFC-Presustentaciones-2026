# Historias de Usuario finales (v1.0.0)

Formato **Connextra** ("Como / Quiero / Para"), con checklist **INVEST** (Independent, Negotiable,
Valuable, Estimable, Small, Testable) y criterios de aceptación en **Gherkin**. Sustituyen a las 5 HUs
informales de [`../historico/SRS-v0.9.0-rc.md`](../historico/SRS-v0.9.0-rc.md), que dejaban sin
formalizar 7 de los 12 requisitos ya referenciados en [`../../trazabilidad/matriz.csv`](../../trazabilidad/matriz.csv).

| HU | Requisito | Título | Prioridad MoSCoW | Prueba automatizada real |
|---|---|---|---|---|
| [HU-01](HU-01.md) | RF-01 | Autenticación segura | Must | ✅ `JwtTokenProviderTest.java` |
| [HU-02](HU-02.md) | RF-02 | Registro de solicitud | Must | ✅ `SubmissionServiceImplTest.java` |
| [HU-03](HU-03.md) | RF-03 | Asignación de jurados | Must | ✅ `PanelistServiceImplTest.java` |
| [HU-04](HU-04.md) | RF-04 | Programación de cronograma | Must | ✅ `ScheduleServiceImplTest.java` |
| [HU-05](HU-05.md) | RF-05 | Evaluación por rúbrica | Must | ✅ `RubricEvaluationServiceImplTest.java` |
| [HU-06](HU-06.md) | RF-06 | Generación de actas | Must | ✅ `MinutesServiceImplTest.java` |
| [HU-07](HU-07.md) | RF-07 | Firma digital de actas | Should | ✅ `MinutesServiceImplTest.java` |
| [HU-08](HU-08.md) | RF-08 | Notificaciones | Could | ❌ Sin prueba dedicada |
| [HU-09](HU-09.md) | RF-09 | Reportes de defensas | Could | ❌ Sin prueba dedicada |
| [HU-10](HU-10.md) | RF-10 | Gestión de salas | Should | ❌ Sin prueba dedicada |
| [HU-11](HU-11.md) | RF-11 | Gestión de usuarios | Must | ✅ `AppUserServiceImplTest.java` |
| [HU-12](HU-12.md) | RF-12 | Carga de anteproyecto | Must | ✅ `ProposalServiceImplTest.java` |

**Estado real de verificación de los Must (2026-08-29): 8 de 8 (100%)**, verificado ejecutando
[`../../../scripts/validate-traceability.sh`](../../../scripts/validate-traceability.sh) contra el
repositorio — no es una afirmación de este documento, es la salida real del script. La cifra subió desde
el 5/8 (62.5%) original de la Fase 6: se añadieron `SubmissionServiceImplTest.java` (RF-02),
`ScheduleServiceImplTest.java` (RF-04) y `MinutesServiceImplTest.java` (RF-06, el último que faltaba) —
los tres pasan (verificado con `./mvnw test`, 106/106 tests). **Esto cubre los 8 requisitos Must, no los
12 totales:** RF-08, RF-09 y RF-10 (prioridad Could/Should) siguen sin prueba automatizada dedicada
(9/12 = 75% general) — no se declara 100% de trazabilidad general. Ver
[`../../trazabilidad/matriz.csv`](../../trazabilidad/matriz.csv) v1.0.0 para el detalle completo y
[`CHANGELOG-REQ.md`](../CHANGELOG-REQ.md) para qué se hizo con este hallazgo.
