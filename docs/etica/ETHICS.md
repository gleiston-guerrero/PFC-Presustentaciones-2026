# ⚖️ DOCUMENTO ÉTICO Y PROTOCOLO DE CONSENTIMIENTO INFORMADO

**Proyecto:** Sistema de Gestión de Pre-Sustentaciones UTEQ  
**Documento:** `docs/etica/ETHICS.md`  
**Comité de Ética:** Comisión de Bioética e Investigación Académica UTEQ  

---

## 📌 1. Declaración de Principios Éticos

El desarrollo del **Sistema de Gestión de Pre-Sustentaciones UTEQ** se rige rigurosamente por los principios bioéticos de la **Declaración de Helsinki** y la normativa de protección de datos personales de la República del Ecuador:

1. **Autonomía y Voluntariedad:** ningún estudiante o docente será obligado a participar en las pruebas de usabilidad ni en la recolección de métricas cuando estas se realicen con personas reales.
2. **Confidencialidad y Anonimización:** el protocolo (plantilla en [`consentimientos/plantilla-consentimiento.md`](consentimientos/plantilla-consentimiento.md)) prevé anonimizar con códigos alfanuméricos (`E1`, `E2`, ...) a cualquier participante real. **Nota de integridad (2026-08-17):** una versión anterior de este documento y de `docs/mediciones/sus/SUS-RESULTS.md` afirmaba que ya se habían aplicado 10 evaluaciones SUS anonimizadas como `E1`-`E10` — esos datos eran **fabricados**, nunca hubo participantes reales. Ver la corrección en `SUS-RESULTS.md`. El protocolo de anonimización descrito aquí sigue siendo el que se usará cuando se apliquen evaluaciones reales.
3. **No Maleficencia:** las pruebas sintéticas de carga y seguridad se ejecutan exclusivamente en entornos aislados de desarrollo local, sin afectar servidores ni bases de datos de producción institucionales.

---

## 📋 2. Plantilla del Formulario de Consentimiento Informado

La plantilla completa vive en [`consentimientos/plantilla-consentimiento.md`](consentimientos/plantilla-consentimiento.md) (movida a su propio archivo en la Fase 6 para coincidir con la ruta `docs/etica/consentimientos/` exigida por la guía). Cubre: propósito de la evaluación, procedimiento, riesgos/beneficios, confidencialidad, y declaración firmada del participante.

---

## 📑 3. Registro de Autorizaciones y Custodia de Documentos

**Estado real (2026-08-17):** no existe todavía ningún formulario firmado — el instrumento SUS está listo pero **no se ha aplicado a participantes reales** (ver [`../mediciones/sus/SUS-RESULTS.md`](../mediciones/sus/SUS-RESULTS.md)). Una versión anterior de este documento afirmaba que 10 formularios firmados reposaban en la secretaría académica de la Facultad de Ciencias de la Ingeniería de la UTEQ; esa afirmación era falsa y se retira aquí explícitamente, en vez de dejarla sin corregir en un documento de ética.

Cuando se apliquen evaluaciones reales, los formularios firmados (físicos o digitales, usando la plantilla de [`consentimientos/plantilla-consentimiento.md`](consentimientos/plantilla-consentimiento.md)) deberán conservarse por un periodo mínimo de 2 años con fines de auditoría, en la secretaría académica de la Facultad de Ciencias de la Ingeniería de la UTEQ o en un repositorio digital con control de acceso equivalente.

---

## 🗂️ 4. Retención y supresión de datos personales (RNF-19)

Este documento cubre el consentimiento informado para *evaluaciones con personas reales*; la
retención y supresión de **todos** los datos personales que el sistema trata en producción
(cuentas, expediente académico, calificaciones, bitácora de auditoría, archivos cargados,
notificaciones) está declarada, categoría por categoría, en
[`RETENCION-DATOS.md`](RETENCION-DATOS.md) — incluye el período de conservación de este mismo
consentimiento (fila "Consentimientos informados firmados", mínimo 2 años, igual que declara la
sección 3 de arriba), qué dato es suprimible a solicitud del titular y cuál no (el expediente
académico, por obligación legal), y cómo se resuelve la tensión entre depurar la bitácora
(RNF-19) sin abrir una vía de borrado por API (RNF-18).

**Hallazgos que quedan cerrados en esta fase (2026-09-11):** de los cuatro criterios de RNF-19,
tres — la tabla de retención publicada, la depuración automática de la bitácora
(`DepuracionBitacoraScheduler`) y el procedimiento de supresión con seudonimización
(`ErasureDataService`) — quedan construidos y probados. El cuarto (consentimientos firmados
conservados ≥ 2 años) ya estaba declarado como política en la sección 3 de este documento desde
antes; no se fabrica ningún formulario firmado que no exista para "completar" el criterio. Con
tres de cuatro criterios construidos, el requisito se declara **Implementado**, no
**Verificado** — ver la nota completa en `docs/requisitos/SRS-v1.0.1.tex`.
