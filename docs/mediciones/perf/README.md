# Rendimiento — índice

Esta carpeta reúne las mediciones de rendimiento del sistema:

- **[`lighthouse/LIGHTHOUSE-REPORT.md`](lighthouse/LIGHTHOUSE-REPORT.md)** — 6 corridas reales de Lighthouse (3 desktop + 3 mobile) contra el build de producción del frontend. Datos crudos en [`lighthouse/prod-runs/`](lighthouse/prod-runs/).
- **Pruebas de carga k6** — el script, los 5 resultados de corrida y el análisis estadístico completo (percentiles, IC 95%, Wilcoxon) de caché fría vs. caliente viven en **[`/k6/`](../../../k6/)**, en la raíz del repositorio (no dentro de `docs/`), porque ahí es donde `k6 run` los ejecuta directamente contra el script y donde se necesita que estén para que un `make bench` los encuentre sin rutas relativas frágiles. El reporte completo con metodología, tablas y reproducción está en [`/k6/README.md`](../../../k6/README.md).
- **[`OPTIMIZACION-CONSULTAS.xlsx`](OPTIMIZACION-CONSULTAS.xlsx)** — análisis con `EXPLAIN (ANALYZE, BUFFERS, TIMING, SUMMARY)` de dos consultas costosas contra la base de datos real (1,010,242 registros): 5 ejecuciones antes/después de crear el índice correspondiente, por consulta, con el mismo formato de tabla de nodos usado en clase. Los índices quedaron aplicados en [`V12__indices_optimizacion_consultas.sql`](../../../backend/src/main/resources/db/migration/V12__indices_optimizacion_consultas.sql).

Ver también [`../DATA-PROVENANCE.md`](../DATA-PROVENANCE.md) para el mapeo completo de qué script/archivo crudo origina cada tabla o figura citada en el informe final.
