# Makefile para despliegue, verificación y reproducción del entorno PFC-UTEQ

.PHONY: help up down restart logs ps clean all build test bench audit docs pdf wait-backend verify

help:
	@echo "Comandos disponibles:"
	@echo "  make up          - Levanta todo el entorno con Docker Compose"
	@echo "  make down        - Detiene y elimina contenedores"
	@echo "  make restart     - Reinicia los servicios"
	@echo "  make logs        - Muestra los logs en tiempo real"
	@echo "  make ps          - Muestra el estado de los contenedores"
	@echo "  make clean       - Limpia volumenes y contenedores huerfanos"
	@echo "  make build       - Compila el backend y construye el build de produccion del frontend"
	@echo "  make test        - Corre la suite de tests del backend (JaCoCo incluido)"
	@echo "  make bench       - Corre las 5 pruebas de carga k6 contra localhost:8080"
	@echo "  make audit       - Corre SpotBugs/find-sec-bugs (SQL dinamico) + npm audit"
	@echo "  make docs        - Regenera las figuras de docs/mediciones/perf/figuras/ y valida la matriz de trazabilidad"
	@echo "  make pdf         - Compila Informe-Final/informe-final.tex a PDF (requiere latexmk/MiKTeX o TeX Live)"
	@echo "  make verify      - Re-corre las verificaciones de VERIFICACION.md que no requieren Docker"
	@echo "                     (SUS, JaCoCo, Javadoc, nombres en espanol, autorizacion de endpoints,"
	@echo "                     etiqueta v1.1.0, cifras de P11, commits vacios de P12) e imprime OK/WARN/FAIL"
	@echo "                     por punto. No reemplaza 'make test'/Lighthouse/k6 para los puntos que"
	@echo "                     dependen de la topologia completa -- ver VERIFICACION.md."
	@echo "  make all         - Objetivo de reproducibilidad (Criterio R1): desde una clonacion limpia,"
	@echo "                     construye, levanta todos los contenedores, espera a que las migraciones"
	@echo "                     Flyway se apliquen, corre tests + benchmarks + auditoria + reportes, y"
	@echo "                     compila el PDF final. Sale con codigo 0 solo si TODO lo anterior tuvo exito."

up:
	@echo "Levantando la infraestructura de contenedores..."
	docker compose up -d --build

down:
	@echo "Deteniendo los servicios..."
	docker compose down

restart: down up

logs:
	docker compose logs -f

ps:
	docker compose ps

clean:
	docker compose down -v --remove-orphans

## --- Objetivos exigidos por la guía (Fase 6) ---

build:
	@echo "Compilando el backend..."
	cd backend && ./mvnw -q compile
	@echo "Construyendo el build de produccion del frontend..."
	cd Frontend && npx ng build --configuration production

test:
	@echo "Corriendo la suite de tests del backend (JaCoCo se genera en la fase test)..."
	@# Hallazgo real (verificacion 2026-08-31): "./mvnw test" en crudo no carga .env, asi que
	@# DB_PASSWORD cae al valor por defecto de application.properties ("postgresAdmin"), que
	@# nunca coincide con la contrasena real que usa Docker Compose (DB_PASSWORD=postgresAdminPassword
	@# en .env) -- PreSustentacionesApplicationTests (@SpringBootTest real) fallaba con
	@# "password authentication failed" contra el Postgres real y correcto, no por un bug de la
	@# app. Se carga .env aqui mismo, igual que ya lo hace Docker Compose automaticamente.
	set -a; [ -f .env ] && . ./.env; set +a; cd backend && ./mvnw test

bench:
	@echo "Corriendo 5 pruebas de carga k6 independientes contra localhost:8080/api/v1..."
	@echo "Requiere el backend corriendo (make up, o mvnw spring-boot:run) y k6 instalado."
	@mkdir -p k6/runs
	@for i in 1 2 3 4 5; do \
		echo "--- Corrida $$i/5 ---"; \
		(cd k6 && k6 run --summary-export=runs/run-$$i-summary.json load-test.js) || exit 1; \
	done
	@echo "5 corridas completas. Resumenes crudos en k6/runs/run-{1..5}-summary.json"

audit:
	@echo "Analisis estatico de seguridad (SpotBugs + find-sec-bugs, incluye SQL dinamico)..."
	./scripts/audit-sql-dynamic.sh
	@echo ""
	@echo "npm audit del frontend..."
	cd Frontend && npm audit || true

docs:
	@echo "Regenerando figuras de rendimiento (docs/mediciones/perf/figuras/)..."
	python scripts/gen-figuras.py
	@echo "Copiando figuras regeneradas a Informe-Final/figuras/ (hallazgo real 2026-09-06: este paso no existia, asi que el PDF podia quedar compilando contra una figura vieja sin que nada lo advirtiera)..."
	cp docs/mediciones/perf/figuras/fig-lighthouse-scores.png docs/mediciones/perf/figuras/fig-k6-p95-por-corrida.png docs/mediciones/perf/figuras/fig-cache-fria-vs-caliente.png Informe-Final/figuras/
	@echo "Validando la matriz de trazabilidad contra el repositorio real..."
	./scripts/validate-traceability.sh || true

pdf:
	@echo "Compilando el PDF del informe final (Informe-Final/informe-final.tex)..."
	cd Informe-Final && latexmk -pdf -interaction=nonstopmode -halt-on-error informe-final.tex
	@echo "PDF generado: Informe-Final/informe-final.pdf"
	@echo "Compilando el PDF del SRS (docs/requisitos/SRS-v1.0.1.tex)..."
	cd docs/requisitos && latexmk -pdf -interaction=nonstopmode -halt-on-error SRS-v1.0.1.tex
	@echo "PDF generado: docs/requisitos/SRS-v1.0.1.pdf"

verify:
	@echo "Verificacion reproducible (VERIFICACION.md) -- partes que no requieren Docker levantado..."
	@sh scripts/verify.sh

## --- Objetivo de reproducibilidad end-to-end (Fase 10, Criterio R1) ---

wait-backend:
	@echo "Esperando a que el backend responda healthy en /actuator/health (las migraciones Flyway se aplican en el arranque de Spring Boot)..."
	@i=0; \
	while [ $$i -lt 30 ]; do \
		if curl -sf http://localhost:8080/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then \
			echo "Backend UP (migraciones aplicadas)."; \
			exit 0; \
		fi; \
		i=$$((i + 1)); \
		echo "  intento $$i/30, esperando 5s..."; \
		sleep 5; \
	done; \
	echo "ERROR: el backend no respondio healthy tras 150s. Revisa 'docker compose logs backend'."; \
	exit 1

all: build up wait-backend test bench audit docs pdf
	@echo ""
	@echo "=== make all completado con exito ==="
	@echo "Contenedores arriba, migraciones Flyway aplicadas, tests corridos (JaCoCo), benchmarks k6"
	@echo "ejecutados, auditoria de seguridad corrida, figuras/trazabilidad regeneradas y PDF final"
	@echo "compilado en Informe-Final/informe-final.pdf."
