# 🏛️ SYSTEM ARCHITECTURE (C4 MODEL)

**Project:** UTEQ Pre-Defense Management System
**Standard:** C4 Model (Simon Brown) — Levels 1, 2 and 3

*(Rest of this repository's documentation is in Spanish, the language of the academic report; these
three diagrams and their labels are kept in English per the reviewer's requirement, so they can be
read/indexed independently of the surrounding prose.)*

---

## 📌 Level 1: System Context Diagram

The diagram below shows how the **UTEQ Pre-Defense Management System** interacts with the different
people (roles) and external systems.

```mermaid
graph TD
    Estudiante["👤 Graduating Student<br/>(Primary User)"]
    Docente["👤 Faculty / Committee Member / Chair<br/>(Evaluator)"]
    Coordinador["👤 Degree Program Coordinator<br/>(Academic Administrator)"]

    Sistema["🏛️ UTEQ Pre-Defense Management System<br/>[Software System]"]
    MailService["✉️ UTEQ SMTP Mail Service<br/>[External System]"]

    Estudiante -->|"Submits requests and uploads pre-project PDF"| Sistema
    Docente -->|"Scores rubrics and digitally signs minutes"| Sistema
    Coordinador -->|"Assigns committees in bulk and schedules rooms"| Sistema
    Sistema -->|"Sends scheduling and minutes notifications"| MailService
```

---

## 📌 Level 2: Container Diagram

The container diagram describes the high-level applications and data stores that make up the system.

```mermaid
graph TD
    UserBrowser["🌐 Web Browser / Client<br/>(HTML5 / CSS3 / JS)"]

    subgraph "UTEQ Application Infrastructure"
        Frontend["🎨 Angular 21 SPA Frontend<br/>[Web Container / Nginx]<br/>Port: 4200"]
        Backend["⚙️ Spring Boot 3.2.1 REST API<br/>[Java 17 / JVM Container]<br/>Port: 8080"]
        PostgreSQL["🗄️ PostgreSQL 15 Relational Database<br/>[DB Container]<br/>Port: 5432"]
        Redis["⚡ Redis 7 Cache Store<br/>[In-Memory Container]<br/>Port: 6379"]
    end

    UserBrowser -->|"HTTPS / REST API JSON / HTTP-Only Cookies"| Frontend
    Frontend -->|"HTTP REST Requests / Bearer JWT Header"| Backend
    Backend -->|"Spring Data JPA + PL/pgSQL Stored Procedures"| PostgreSQL
    Backend -->|"JWT refresh tokens + request cache and external universities API"| Redis
```

---

## 📌 Level 3: Backend Component Diagram

The component diagram details the internal structure of the Backend container (`presustentaciones.jar`).

```mermaid
graph TD
    subgraph "Spring Boot Backend Container"
        SecurityFilter["🔐 JwtAuthenticationFilter<br/>(Token and Cookie Validation)"]
        AuthController["🎮 AuthController<br/>(Login, Register, Refresh)"]
        SubmissionController["🎮 SubmissionController<br/>(Request CRUD)"]
        EvaluationController["🎮 EvaluationController<br/>(Rubric Scoring)"]
        MinutesController["🎮 MinutesController<br/>(Signatures and PDF)"]

        SubmissionService["🛠️ SubmissionServiceImpl<br/>(Request Business Logic)"]
        EvaluationService["🛠️ EvaluationServiceImpl<br/>(Evaluation Business Logic)"]
        MinutesService["🛠️ MinutesServiceImpl<br/>(Minutes Business Logic)"]

        JPARepositories["📦 Spring Data JPA Repositories<br/>(Elementary CRUD)"]
        StoredProcedures["🗄️ PL/pgSQL Stored Procedures<br/>(sp_calcular_promedio, sp_generar_reporte)"]
    end

    SecurityFilter --> AuthController
    SecurityFilter --> SubmissionController
    SecurityFilter --> EvaluationController
    SecurityFilter --> MinutesController

    SubmissionController --> SubmissionService
    EvaluationController --> EvaluationService
    MinutesController --> MinutesService

    SubmissionService --> JPARepositories
    EvaluationService --> StoredProcedures
    MinutesService --> StoredProcedures
```
