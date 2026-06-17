# Product Requirement Document (PRD)

## AI-Driven Reverse Engineering CLI for Spec-Driven Development (SDD)

> **Version 4.0** — Fully revised, unified in English, and optimized for engineering execution. This version introduces a **Two-Pass Deterministic Linker** architecture for inter-file structural tracing (call graph, database access, outbound HTTP, event flows) without LLM dependencies. Phase 2 is scoped to semantic enrichment only. Incorporates high-performance local Spring JDBC state storage, a **Dual-Engine Hybrid Indexing Pipeline** (Maven + JavaParser AST), an annotation-driven heuristic fallback strategy, a **Declarative Asynchronous Execution Model managed natively by Spring (`@Async`)**, streamed Map-Reduce processing to eliminate memory constraints, pure-Java parsing boundaries, and advanced CLI visual telemetry.

---

## 1. Executive Summary & Objectives

### 1.1 Purpose

**Spec-Driven Development (SDD)** is the architectural practice of treating a highly structured, technology-agnostic business specification—not source code—as the primary, authoritative artifact of a software project. It is the single source of truth from which automated tests, clean implementation, and functional documentation are derived.

This document specifies the requirements for a Command Line Interface (CLI) application built on the JVM ecosystem using **Spring AI** and **Embabel**. It is designed to perform deep, automated reverse engineering on legacy enterprise codebases. The tool extracts embedded business rules, domain validations, and ecosystem invariants, consolidating them into highly structured Markdown documents organized by **"Use Cases / Functional Flows"**.

Crucially, the tool rejects direct test framework generation. Instead, it exports a markdown specification alongside a machine-readable **Semantic Manifest JSON**. This output serves as the decoupled, hyper-traceable source of truth for downstream automated processes, such as a specialized AI Skill responsible for compiling BDD **Gherkin feature files (`.feature`)** or re-architecting applications.

### 1.2 Core Objectives

* **De-technify Enterprise Code:** Abstract dense, legacy technical implementations (Java/Spring, SQL procedural logic, state frameworks) into clear business intentions, process parameters, and edge cases.
* **Eradicate Context Rot & Hallucinations:** Enforce a stateless, short-lived execution pattern with absolute context isolation per file to maximize LLM analytical accuracy.
* **Provide End-to-End Visibility:** Connect scattered functional points, bridging frontend interactions, synchronous REST endpoints, asynchronous event topologies, and hidden procedural database layers.
* **Provide a Standard Data Contract for Test Generation:** Output predictable, structured semantic graphs that bridge the gap between static code structures and dynamic business tests.

---

## 2. Core Architecture & Product Paradigms

The CLI rejects the unpredictable, conversational agent-loop pattern. It adopts a sequential, stateful, compiler-like engine divided into three strict phases.

```
[Phase 1: Deterministic Indexing] ──> [Phase 2: Stateful Dynamic Execution] ──> [Phase 3: Cascaded Chunked Synthesis]

```

### 2.1 Phase 1: Deterministic Multi-Language Indexing

Before any LLM interaction takes place, the CLI scans the physical workspace using local code-graph and parsing tools. To maintain platform-agnostic distribution without native OS-level JNI bindings (such as Tree-sitter), the engineering stack enforces pure-Java AST parsers (e.g., **JavaParser** for Spring/Java modules). This phase runs as a deterministic compiler-pass requiring zero network connectivity or LLM credentials. It maps signatures, endpoint routes, call frameworks, and event publishers into a local intermediate contract file, eliminating structural exploration overhead during LLM execution.

To enable inter-file structural tracing without LLM dependencies, Phase 1 operates a **two-pass deterministic linker** architecture:

1. **Pass 1 — Declaration Collection:** Every source file is parsed with JavaParser to extract method signatures, field types, and component stereotypes into a global `DeclarationRegistry` held in memory. No resolution or analysis is performed — only structural registration.
2. **Pass 2 — Resolution Analysis:** Each file is re-analysed with the full visitor suite. Resolution visitors (`CallGraphVisitor`, `DbAccessVisitor`, `OutboundHttpVisitor`) resolve method calls, database access patterns, and outbound HTTP calls against the registry built in Pass 1.
3. **Post-Pass Link Resolution:** Once all files are processed, deterministic resolvers match event producers to consumers (`TopicLinkResolver`) and register outbound HTTP calls (`FloatingLinkResolver`).

This design ensures that Controller → Service → Repository / Database / External System traces are resolved without LLM calls. The LLM is reserved exclusively for semantic enrichment in Phase 2.

#### 2.1.1 Dual-Engine Indexing & Ecosystem Discovery

To maximize dependency resolution accuracy without inducing build-time hard blocks, Phase 1 operates a dual-engine indexing sequence categorized into "Static Compilation Telemetry" and "Text-Based AST Structural Parsing."

1. **Ecosystem Discovery Step (Optional / Non-Blocking):**
* At boot time, the engine checks for the presence of a build-system configuration (e.g., `pom.xml`).
* If present, the CLI spawns an isolated background OS process to execute an offline maven dependency tree evaluation:
  `mvn com.github.ferstl:depgraph-maven-plugin:4.0.3:graph -DgraphFormat=json -DoutputDirectory=.`
* If successful, the resulting artifact is ingested to pre-populate classpaths, third-party libraries, and multi-module relationships.
* **Fault-Tolerance:** If Maven is missing, the workspace lacks a repository connection, or the project is in a non-compiling state, the process must catch the failure gracefully, log an optimization warning (`"Ecosystem telemetry unavailable; proceeding with standalone heuristic mode"`), and proceed unhindered.


2. **Pure-Java AST Parsing Step (Authoritative / Mandatory):**
* Regardless of build-system state, JavaParser runs as the authoritative engine to build an in-memory Abstract Syntax Tree (AST). It handles "dirty code" (broken syntax or non-compiling files) by evaluating them directly as text streams.
* **Type Solver Fallback:** The indexer must configure JavaParser's `CombinedTypeSolver` to prioritize local source directories first. If a type cannot be resolved statically (e.g., a third-party framework or missing compiled class), the engine must gracefully switch to an **Annotation-Driven Strategy** rather than throwing a parsing exception. If a class contains methods with `@PostMapping`, it is designated as a REST entrypoint regardless of what base framework class it extends.



#### 2.1.2 AST Parsing Strategy (Java Domain)

The indexer leverages a dedicated `VoidVisitorAdapter<Context>` traversal strategy to capture four structural dimensions:

* **Component Identification & Types:** Detect classes, interfaces, and records. Identify stereotypes by inspecting class-level annotations (e.g., `@RestController`, `@Service`, `@Component`, `@Repository`).
* **Inbound Ingress Points (HTTP/Events/Scheduled):**
* *REST Endpoints (Pass 2):* Map methods annotated with `@RequestMapping`, `@PostMapping`, `@GetMapping`, etc. Extract literal path strings and HTTP verbs. Implemented via `SpringEndpointDetector` (`@Component` implementing `EndpointDetector` SPI).
* *Servlet Endpoints (Pass 2):* Map `HttpServlet` subclasses (both `javax.servlet.http.HttpServlet` and `jakarta.servlet.http.HttpServlet`). Detect `doGet`/`doPost`/`doPut`/`doDelete`/`doPatch`/`doHead`/`doTrace`/`doOptions` method names and map to HTTP verbs. Extract URL patterns from `@WebServlet` annotation (`javax.servlet.annotation.WebServlet` and `jakarta.servlet.annotation.WebServlet`). Validate method signature includes `HttpServletRequest` and `HttpServletResponse` parameters. Implemented via `ServletEndpointDetector` (`@Component` implementing `EndpointDetector` SPI). Adding a new endpoint framework (e.g., JAX-RS) requires only a new `@Component EndpointDetector` class — zero changes to the delegating `EndpointVisitor`.
* *web.xml Endpoint Discovery (Post-Pass):* After Java AST analysis, `**/web.xml` files are discovered and DOM-parsed via `WebXmlAnalyzer`. `<servlet>` elements are mapped to `<servlet-class>` and `<servlet-mapping>` elements to `<url-pattern>`, producing `EndpointInfo` entries with `httpMethod=""` (all methods) and the mapped servlet class name. No Java source modification or cross-referencing with `@WebServlet` annotations is performed — both sources produce independent `EndpointInfo` entries.
* *Event Consumers:* Map methods annotated with message broker listener frameworks:
  * *Kafka:* `@KafkaListener(topics = "...")` — extract target topics.
  * *RabbitMQ:* `@RabbitListener(queues = "...")` — extract target queues.
  * *ActiveMQ (JMS):* `@JmsListener(destination = "...")` — extract target destinations.
* *Scheduled Tasks:* Map methods annotated with `@Scheduled`. Extract cron expressions, fixed-delay, fixed-rate strings, and trigger zone.


* **Outbound Egress Points (Dependencies & Triggers):**
* *Method Invocations:* Capture method call expressions (`MethodCallExpr`) matching internal package boundaries to map deep structural call trees.
* *Event Publications:* Identify calls to event-broker templates and extract destination targets:
  * *Kafka:* `KafkaTemplate.send(topic, ...)` / `KafkaTemplate.send(topic, key, ...)`.
  * *RabbitMQ:* `RabbitTemplate.convertAndSend(exchange, routingKey, ...)` / `RabbitTemplate.send(exchange, routingKey, ...)`.
  * *ActiveMQ (JMS):* `JmsTemplate.convertAndSend(destination, ...)` / `JmsTemplate.send(destination, ...)`.
* *Unresolved References:* Outbound calls targeting signatures outside the indexed codebase or missing from the ecosystem discovery payload are caught and recorded.


* **Companion Rules & Validations:**
* *Custom Constraints:* Scan field and parameter annotations. If an annotation maps to a custom validation constraint (e.g., `@ValidOrder`), resolve its `validatedBy` target class via AST imports.

* **Inter-File Call Resolution (Pass 2):**
  * A `CallGraphVisitor` resolves each `MethodCallExpr` against the `DeclarationRegistry` built in Pass 1.
  * For calls matching internal package boundaries, the edge is recorded with source file, target file, and method signatures.
  * Overloaded methods are recorded as `AMBIGUOUS` when argument count cannot discriminate.
  * Calls to JDK (`String.*`, `List.*`), Spring framework internals, and third-party libraries not resolved by the dependency graph are recorded as `unresolved_signatures`.
  * Calls inside `@EventListener` method bodies are captured with up to 3 levels of nesting (existing behavior, now resolved against the registry).

 * **Database Access Patterns (Pass 2):**
   * Detect `JdbcTemplate.update(query, args)`, `.query(sql, ...)`, `.queryForObject(sql, ...)`.
   * Detect Hibernate Session operations: `session.save()`, `.get()`, `.load()`, `.delete()`, `.createQuery(hql)`, `.createNativeQuery(sql)`, `.byNaturalId()`.
   * Detect `@Procedure(name = "...")` on repository methods.
   * Detect `EntityManager.persist()`, `.merge()`, `.find()`, `.createQuery()`.
   * Detect `@Transactional` on method or class level as transaction boundaries (class-level deduplicated against method-level override).
   * Detect `NamedParameterJdbcTemplate` and `SimpleJdbcCall`.
   * SQL string literals are extracted from the AST; procedure names are extracted from annotation attributes; table names are inferred from SQL strings where possible.
   * Spring Data interfaces (`CrudRepository`, `JpaRepository`) are registered as **virtual declarations** — their derived query methods (e.g., `findByLastName()`) have no AST body but are recognized as database access points.
   * Each detection path is implemented as a standalone `DbAccessDetector` component wired via Spring DI — adding a new database technology requires only a new class with zero changes to existing detector code.

* **Outbound HTTP Client Patterns (Pass 2):**
  * Detect `RestTemplate.getForObject(url, ...)`, `.postForObject(url, ...)`, `.exchange(url, method, ...)`, `.put()`, `.delete()`.
  * Detect `WebClient` fluent builder chains: `.method(HttpMethod.GET).uri(url).retrieve()`, `.get().uri(url).retrieve()`, `.post().uri(url).retrieve()`.
  * Detect `@FeignClient(name = "...", url = "...")` on interfaces with method-level `@GetMapping`, `@PostMapping`, etc.
  * Detect `RestClient` (Spring 6.1) fluent chains: `.get().uri(url)`, `.post().uri(url)`, `.put().uri(url)`, `.delete()`.
  * Detect `@HttpExchange` / `@GetExchange` / `@PostExchange` / `@PutExchange` / `@DeleteExchange` on interfaces (Spring 6).
  * Detect `java.net.http.HttpClient.send(request, handler)` and `.sendAsync(request, handler)` with `HttpRequest.newBuilder().uri(url)`.
  * Detect legacy `HttpURLConnection` via `url.openConnection()`, `.setRequestMethod()`, `.connect()`.
  * Detect Apache `HttpClient` / `HttpComponents` via `new HttpGet(url)`, `new HttpPost(url)`, `new HttpPut(url)`, `new HttpDelete(url)`, and `CloseableHttpClient.execute()`.
  * Detect OkHttp via `OkHttpClient.newCall(request)` with `Request.Builder().url(url).get()/.post()/.put()/.delete()`.
  * URL literals are captured as-is; SpEL expressions and environment variable references (e.g., `${services.url}/api/v1/orders`) are captured as patterns with an `isExpression` flag.
  * Each detected call is registered as a `floating_link` in the SQLite store.

* **View-Returning Controller Detection (Pass 2):**
  * Detect controller methods returning `ModelAndView`, `String` (logical view name), `View`, or `void` (with implicit view from request path).
  * Each endpoint is tagged with `servesView: true` and the extracted `viewName`.
  * Methods in `@RestController` classes or annotated with `@ResponseBody` are excluded (they return serialized data, not views).

* **Template File Parsing (Post-Pass):**
  * After Java AST analysis completes, template files (`.jsp`, Thymeleaf `.html`) are discovered and parsed to extract frontend-to-backend HTTP references.
  * **JSP Form Detection:** Extract `<form action="..." method="...">` — capture HTTP method, URL pattern, and field names from `<input name="...">`.
  * **JSP Link Detection:** Extract `<a href="...">` anchor links.
  * **Thymeleaf Form Detection:** Extract `<form th:action="@{...}" th:method="...">` — capture HTTP method, Thymeleaf expression URL pattern, and field names.
  * **Thymeleaf Link Detection:** Extract `<a th:href="@{...}">` anchor links.
  * SpEL expressions in Thymeleaf `@{...}` paths are captured with an `isExpression` flag.
  * Each form/link is recorded as a `TemplateFormInfo` finding with the template file path, HTTP method, URL pattern, and field names.

* **Template-to-Endpoint Floating Link Resolution (Post-Pass):**
  * After template parsing, form action URLs are matched against known controller `EndpointInfo` paths.
  * Exact literal matches are linked with confidence 1.0; path-parameterized matches (e.g., `/owners/{ownerId}` ↔ form action `/owners/5`) at confidence 0.8.
  * Matched pairs are registered as `template_endpoint_links` in the index output, enabling end-to-end frontend-to-backend traceability.

#### 2.1.3 The Intermediate Boundary: `code-graph-index.json`

The indexer flushes its in-memory graph into a standardized local JSON file saved in the execution root. This serves as an un-decoupled debugging boundary for developers and provides an extensible plugin path for other language processors in future iterations.

```json
{
  "repository_name": "order-management-service",
  "indexed_at": "2026-06-12T08:15:00Z",
  "files": [
    {
      "file_path": "src/main/java/com/acme/orders/controller/OrderController.java",
      "module_tag": "order-management",
      "component_type": "REST_ENDPOINT",
      "source_hash": "a1b2c3d4e5f6...",
      "paired_test_file": "src/test/java/com/acme/orders/controller/OrderControllerTest.java",
      "ingress_points": [
        {
          "type": "HTTP",
          "verb": "POST",
          "path": "/api/v1/orders"
        }
      ],
      "egress_points": [
        {
          "type": "INTERNAL_CALL",
          "target_signature": "com.acme.orders.service.OrderService.createOrder"
        }
      ],
      "companion_logic_dependencies": {
        "custom_validators": [
          "com.acme.orders.validation.OrderValidator"
        ],
        "database_procedures": []
      },
      "unresolved_signatures": [
        "com.thirdparty.telemetry.MetricsLogger.logEntry"
      ],
      "call_graph_edges": [
        {
          "source_method": "createOrder",
          "target_class": "com.acme.orders.service.OrderService",
          "target_method": "createOrder",
          "target_file": "src/main/java/com/acme/orders/service/OrderService.java",
          "resolved": true
        }
      ]
    },
    {
      "file_path": "src/main/java/com/acme/orders/service/OrderService.java",
      "module_tag": "order-management",
      "component_type": "SERVICE_LOGIC",
      "source_hash": "f9e8d7c6b5a4...",
      "paired_test_file": null,
      "ingress_points": [
        {
          "type": "SCHEDULED",
          "schedule": "0 0 2 * * ?"
        }
      ],
      "egress_points": [
        {
          "type": "TOPIC_PUBLISH",
          "broker": "KAFKA",
          "channel": "order-events-topic"
        },
        {
          "type": "DATABASE_PROCEDURE_CALL",
          "procedure_name": "PR_RESERVE_INVENTORY"
        },
        {
          "type": "DATABASE_CALL",
          "access_type": "JDBCTEMPLATE_QUERY",
          "sql_literal": "SELECT * FROM orders WHERE status = ?",
          "table_hint": "orders"
        },
        {
          "type": "HTTP_CALL",
          "method": "POST",
          "url_pattern": "${payment.service.url}/api/v1/charges",
          "is_expression": true,
          "encapsulated_in": "processPayment"
        }
      ],
      "companion_logic_dependencies": {
        "custom_validators": [],
        "database_procedures": [
          "schema/procedures/PR_RESERVE_INVENTORY.sql"
        ]
      },
      "unresolved_signatures": []
    }
  ],
  "topic_links": [
    {
      "broker": "KAFKA",
      "topic": "order-events-topic",
      "producer_task_id": "abc123...",
      "consumer_task_id": "def456..."
    }
  ],
  "floating_links": [
    {
      "method": "POST",
      "url_pattern": "${payment.service.url}/api/v1/charges",
      "is_expression": true,
      "source_task_id": "abc123..."
    }
  ]
}

```

#### 2.1.4 Secret Redaction Gate

A mandatory, pre-LLM pipeline interceptor scans code using an embedded open-source secret scanning core abstraction (e.g., Gitleaks/TruffleHog shared signature mechanics). Passwords, hardcoded credentials, and API literals are replaced in-memory with a stable placeholder (e.g., `[REDACTED:secret_type]`). The physical file on disk remains completely untouched.

#### 2.1.5 Exclusion of Code Patterns

Third-party vendor packages, generated code stubs, and build artifacts matching the manifest's `exclude_patterns` are dropped immediately, protecting the execution matrix from scope bloat.

#### 2.1.6 SQLite Ingestion Pipeline

Immediately after Pass 2 resolution completes, the indexer persists all findings to the SQLite store. For each file, a `tasks` row is created or updated. For each resolved call graph edge, a row is inserted into `execution_findings`. Topic links, floating links, and metrics are written to their respective tables.

```sql
INSERT INTO tasks (
    task_id, 
    file_path, 
    module_tag, 
    status, 
    source_hash, 
    paired_test_path, 
    json_payload
) VALUES (
    ?, -- SHA-256 environmental hash calculated per Section 5.1
    ?, -- file_path
    ?, -- module_tag
    'PENDING', -- Initial State
    ?, -- source_hash
    ?, -- paired_test_file (nullable)
    NULL -- Reserved for Phase 2 executor output
);

```

*Idempotency Rule:* The database insert loop must handle existing keys by validating hashes or dropping/recreating stale pending tasks to allow smooth re-indexing commands.

**Complete SQLite Schema:**

```sql
CREATE TABLE IF NOT EXISTS tasks (
    task_id         TEXT PRIMARY KEY,
    file_path       TEXT NOT NULL,
    module_tag      TEXT NOT NULL,
    status          TEXT NOT NULL DEFAULT 'PENDING',
    source_hash     TEXT NOT NULL,
    paired_test_path TEXT,
    json_payload    TEXT,
    created_at      TEXT DEFAULT (datetime('now')),
    updated_at      TEXT DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS execution_findings (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id         TEXT NOT NULL REFERENCES tasks(task_id),
    finding_type    TEXT NOT NULL DEFAULT 'UNKNOWN',
    finding_json    TEXT NOT NULL,
    resolved        INTEGER NOT NULL DEFAULT 1,
    schema_version  TEXT NOT NULL DEFAULT '1.0',
    created_at      TEXT DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS topic_links (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    broker          TEXT NOT NULL,
    topic_or_queue  TEXT NOT NULL,
    producer_task_id TEXT REFERENCES tasks(task_id),
    consumer_task_id TEXT REFERENCES tasks(task_id),
    resolved_status TEXT NOT NULL DEFAULT 'PENDING',
    confidence      REAL DEFAULT 1.0,
    created_at      TEXT DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS floating_links (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    method          TEXT NOT NULL,
    url_or_path     TEXT NOT NULL,
    is_expression   INTEGER NOT NULL DEFAULT 0,
    source_task_id  TEXT NOT NULL REFERENCES tasks(task_id),
    target_endpoint TEXT,
    confidence      REAL,
    resolved_status TEXT NOT NULL DEFAULT 'PENDING',
    created_at      TEXT DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS metrics (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    run_id          TEXT NOT NULL,
    tasks_total     INTEGER DEFAULT 0,
    tasks_completed INTEGER DEFAULT 0,
    edges_resolved  INTEGER DEFAULT 0,
    edges_unresolved INTEGER DEFAULT 0,
    topic_links_resolved INTEGER DEFAULT 0,
    floating_links_registered INTEGER DEFAULT 0,
    tokens_consumed INTEGER DEFAULT 0,
    api_cost_estimated REAL DEFAULT 0.0,
    phase           TEXT NOT NULL,
    recorded_at     TEXT DEFAULT (datetime('now'))
);
```

#### 2.1.7 Developer Implementation Checklist: Phase 1

* [ ] Implement `project-manifest.yaml` parsing.
* [ ] Integrate optional non-blocking `depgraph-maven-plugin` execution step.
* [ ] Integrate **JavaParser** core engines without native OS wrapper layers.
* [ ] Configure `CombinedTypeSolver` with annotation fallback heuristic behavior.
* [x] Implement `EndpointDetector` SPI + `SpringEndpointDetector` + `ServletEndpointDetector` (OCP-friendly pluggable detector interface, covers both Spring MVC and Servlet-based endpoints).
* [ ] Implement `unresolved_signatures` collection inside the traversal visitor.
* [ ] Create the Secret Redaction pipeline filter (in-memory swapping of hardcoded strings to `[REDACTED:secret_type]`).
* [ ] Output a structurally valid `code-graph-index.json` file.
* [ ] Write the Spring JDBC ingestion loop to transform JSON entities into `PENDING` relational rows inside SQLite, applying `PRAGMA journal_mode=WAL;`.
* [ ] Implement `GlobalDeclarationRegistry` (Pass 1 collector).
* [ ] Implement `CallGraphVisitor` (Pass 2 method call resolver).
* [ ] Implement `DbAccessVisitor` + `DbAccessDetector` SPI (Pass 2 database access patterns, OCP-friendly pluggable detector interface).
* [x] Implement `OutboundHttpVisitor` + 9 `HttpClientDetector` implementations (Pass 2 outbound HTTP calls for RestTemplate, WebClient, FeignClient, RestClient, @HttpExchange, java.net.http.HttpClient, HttpURLConnection, Apache HttpClient, OkHttp).
* [ ] Implement `TopicLinkResolver` (post-pass producer&#8596;consumer matching).
* [ ] Implement `FloatingLinkResolver` (post-pass URL&#8596;endpoint matching).
* [ ] Implement `execution_findings`, `topic_links`, `floating_links`, `metrics` tables with extended columns.
* [ ] Update `IndexWriter` with new finding types and link sections.
* [ ] Add metrics collector for edges, topic links, floating links counts.

---

### 2.2 Phase 2: Semantic Enrichment (LLM-Scoped)

The structural trace produced by Phase 1 resolves all deterministic call paths (Controller &#8594; Service &#8594; Repository, database access, event flows, outbound HTTP calls). Phase 2 enriches this structural trace with **business semantics** for patterns that cannot be inferred from AST analysis alone.

* **The Planner:** Uses the resolved call graph and SQLite topic/floating links from Phase 1 to identify files requiring semantic enrichment. A file qualifies for Phase 2 if any of these conditions are met:
  * It has more than `llm-unresolved-threshold` (default: 5) unresolved signatures.
  * It is a Spring Data interface (no AST body to analyse).
  * It contains a stored procedure call with a body flagged for LLM interpretation.
  * It is a custom `ConstraintValidator` with a complex `isValid` body.
  * Test file assertions require semantic extraction (see §3.4).

* **The Centralized Lightweight State Store:** An embedded SQLite database managed via high-performance, low-overhead native **Spring JDBC (JdbcTemplate)** instead of an ORM framework. It tracks enriched findings alongside the Phase 1 structural data.

* **The Executors:** Short-lived, isolated software workers configured as native Spring beans with `@Async("orchestratorTaskExecutor")`. Each Executor receives **the pre-resolved structural context** from Phase 1 (its own call graph edges, database accesses, and link registrations) plus the raw source file. The LLM prompt explicitly instructs the model to NOT resolve structural dependencies (those are already complete) and to focus only on:
  * Business purpose description (1&#8211;2 sentences per method).
  * Implicit validation rules not captured by annotations.
  * Inferred SQL for Spring Data derived query methods.
  * Business logic interpretation of stored procedures.
  * Edge cases extracted from test file assertions.

* **The Orchestrator:** Processes the enrichment DAG, submits tasks asynchronously to the Spring pool, and tracks progress via `CompletableFuture<ExecutionFinding>` responses. The enriched `ExecutionFinding` JSON (§4) is merged with the Phase 1 structural data in the SQLite store.

* **Phase Synchronization Barrier:** Phase 3 (synthesis) is blocked until ALL Phase 2 enrichment tasks complete, using `CompletableFuture.allOf(...)`. Phase 2 is skipped entirely if `--llm-threshold` is set to 0 or no files qualify.

---

### 2.3 Phase 3: Cascaded Chunked Semantic Synthesis (Map-Reduce)

To process thousands of individual file analyses without overflowing model context limits or causing JVM `OutOfMemoryError` conditions on large workspaces, synthesis occurs hierarchically through streamed **Embabel** pipelines:

1. Individual file JSON entities are queried sequentially from SQLite using standard **SQL Streaming Cursors** (`ResultSet` streaming).
2. Entities are loaded, processed, and synthesized in memory inside fixed module blocks (**Chunked Processing**), generating clean **Module Summaries**.
3. Distributed network routes, database pointers, and message topics are cross-referenced and resolved.
4. A **Semantic Validation Agent** audits a randomized sample of findings against raw source files to catch and reject semantic deviations or technical jargon.
5. The engine outputs the human-centric Markdown Specification and the machine-readable **Semantic Manifest JSON**.

---

### 2.4 SQLite Core: Concurrency, State Management, and Fault Tolerance

To ensure high-performance concurrent processing locally without requiring a dedicated external database server, the embedded persistence layer must be configured to eliminate database locking errors (`database is locked`) while permitting true concurrent reading operations.

1. **Write-Ahead Logging (WAL) Mode:** The JDBC engine must enforce `PRAGMA journal_mode=WAL;`. This permits concurrent reading threads (such as the Orchestrator traversing the DAG or logging metrics) to execute completely unhindered by writing threads (Executors committing async results).
2. **Busy Timeout Queueing:** The connection must initialize with `PRAGMA busy_timeout=5000;`. If multiple execution threads attempt to write to disk simultaneously, the secondary threads will pause and queue gracefully for up to 5000ms instead of throwing immediate transactional exceptions.
3. **Connection Pool Concurrency Alignment:** To fully utilize WAL mode without deadlocks, the system configures a pooled approach allowing concurrent reads while serializing writes where necessary. The connection pool size is opened to a maximum of 10 (`maximum-pool-size=10`), leveraging `PRAGMA synchronous=NORMAL;` to balance disk write safety with rapid local parallel processing across multiple threads.

---

## 3. Functional Requirements

### 3.1 Multi-Repository Manifest Configuration

The CLI must ingest an external `project-manifest.yaml` configuration file to govern scanning scopes across distributed microservices, mono-repos, or multi-repo structures.

**Example manifest contract:**

```yaml
targets:
  - name: order-management-service
    path: ./repos/order-service
    layer: backend
    tech_profile: java-spring-legacy
    entry_points:
      - src/main/java/com/acme/orders/controller/OrderController.java
    exclude_patterns:
      - "**/generated/**"
      - "**/*Pb.java"

execution:
  max-concurrent-llm-calls: 5
  max-discovery-depth: 3
  semantic-validation-sample-rate: 0.20

```

### 3.2 Full-Stack Flow Stitching (Floating Links)

The application must trace execution pathways across network boundaries.

**Phase 1 (Deterministic):** The `OutboundHttpVisitor` detects outbound HTTP calls from `RestTemplate`, `WebClient`, `@FeignClient`, `RestClient` (Spring 6.1), `@HttpExchange` (Spring 6), `java.net.http.HttpClient`, `HttpURLConnection`, Apache `HttpClient`, and OkHttp declarations. Each call is registered as a `floating_link` in the SQLite store with its HTTP method, URL pattern (literal or expression), and source file task ID. After all files are processed, the `FloatingLinkResolver` performs deterministic matching: if a URL pattern is a literal string matching a known backend endpoint path (same HTTP method + path), the link is marked `RESOLVED` with confidence 1.0. If the URL contains path variables or query parameters matching structural patterns, the link is marked `RESOLVED` with confidence 0.8. Unresolved links remain `PENDING` for optional Phase 2 semantic enrichment, where the LLM can infer the intended target from method name, payload structure, and endpoint descriptions.

### 3.3 Asynchronous Message & Scheduled Trigger Tracing (Topic & Scheduled Links)

The system must bridge decoupling gaps created by event-driven and time-driven patterns.

**Phase 1 (Deterministic):** When a visitor identifies a message dispatcher block (e.g., `KafkaTemplate.send("order-topic", ...)`), it registers the publication in the analysis result. After all files are processed, the `TopicLinkResolver` performs a deterministic SQL JOIN across all scanned targets: it matches `broker` + `topic_or_queue` values between producers and consumers. For each matching pair, a `topic_link` row is created with status `RESOLVED`. This covers Kafka, RabbitMQ, and ActiveMQ/JMS flows within the same manifest execution.

Methods annotated with `@Scheduled` are traced as time-based inbound triggers; their schedule metadata is captured and linked to the business operations they initiate via the call graph resolved in Pass 2.

### 3.4 Test Suite Mining (Assertion Extraction)

To capture intended validations that may be obscured by technical debt in production classes, the Planner matches production files with their corresponding test files (e.g., `OrderService.java` paired with `OrderServiceTest.java`). The Executor processes both files concurrently, translating assertions (`assertEquals`, `assertThrows`, `expect()`) into functional edge cases and validation requirements.

### 3.5 Dynamic Re-Planning Loop & Phase 2 Threshold

If an Executor uncovers an unindexed runtime dependency during LLM file analysis (such as dynamic reflections or factory class routing), it attaches a `discovered_dependency` array to its JSON output.

* **Branch Isolation:** The Orchestrator pauses execution **only for that specific branch**, registers the new file tasks into the SQLite store as `PENDING`, updates task priorities, and triggers them asynchronously. Other branches of the DAG continue running completely uninterrupted.
* **Phase 2 Threshold Guard:** The Phase 1 linker does not perform dynamic re-planning. If a deterministic resolution fails (unresolved signature), it is logged and counted. Only when the unresolved count per file exceeds `llm-unresolved-threshold` (default: 5) does the file qualify for Phase 2 enrichment.
* **Phase Synchronization Barrier:** To prevent Phase 3 (Map-Reduce consolidation) from building partial or corrupted system maps, a strict execution barrier is enforced via Spring-managed completion frameworks. The engine is completely blocked from initiating Phase 3 if *any* task in the state store is flagged as `PENDING` or `RUNNING`. Using `CompletableFuture.allOf(...)`, synthesis only triggers when all futures across all branches have completed successfully and resolved.

### 3.6 Automated Constraint Extraction & Context Budgeting

To capture domain rules enforced via custom framework validations without breaking file context boundaries or overflowing LLM processing windows:

1. **Static Analysis Mapping:** During Phase 1, the indexer identifies interfaces annotated with custom constraints (e.g., `@jakarta.validation.Constraint` or `@javax.validation.Constraint`) and maps them to their respective physical implementation classes via the `validatedBy` attribute.
2. **Logic Slicing:** When a production class utilizing a custom validation annotation is queued for an Executor, the Orchestrator extracts **only the internal body of the matching `public boolean isValid(...)` method**.
3. **Context Budgeting Protocol:** Before compiling the final Executor prompt, the system calculates the combined token weight of the target source file, its test file, and any extracted custom constraint slices using the model's explicit tokenization engine (e.g., Tiktoken for OpenAI, Anthropic Tokenizer for Claude). If the total token count exceeds 80% of the model's native context window, the system bypasses direct raw inclusion and triggers an intermediate, highly dense LLM semantic pre-summarization step for the companion logic slices before attaching them to the prompt under the `### COMPANION CUSTOM VALIDATORS` section.

### 3.7 Database Logic Processing

Legacy architectures often hide critical business logic inside procedural database entities, bypassing basic source file analysis.

**Phase 1 — Structural Detection:**

1. **DDL Source Collection:** The CLI ingests database schemas by monitoring Flyway/Liquibase migration directories or accepting a manual file dump using the `--db-schema` parameter.
2. **Persistence Mapping (Deterministic):** When source code files declare native procedure calls (`@Procedure` annotations or explicit `CALL / EXECUTE` blocks), the `DbAccessVisitor` extracts the procedure name and registers a `DATABASE_PROCEDURE_CALL` egress point. If DDL sources are available, the matching `CREATE PROCEDURE` or `CREATE FUNCTION` block is extracted and associated with the call.
3. **SQL String Extraction:** JDBC template invocations with inline SQL strings are captured as `DATABASE_CALL` egress points with the SQL literal and any inferable table reference.
4. **Spring Data Virtual Declarations:** Repository interfaces extending `CrudRepository` or `JpaRepository` are registered as virtual database access points. Derived query methods (e.g., `findByLastName()`) are recorded with their inferred entity type.

**Phase 2 — Semantic Interpretation (when needed):**

5. **Syntax Fallback Gateway:** If the local static DDL parser fails to isolate the procedure body due to vendor-specific SQL extensions or complex non-ANSI legacy schemas, the raw schema segment is delegated to an LLM text-cleaning pass.
6. **Semantic Deserialization:** The procedural SQL code block is sent to the LLM Executor which converts database constructs into standardized functional definitions:
* Internal `RAISE_APPLICATION_ERROR` signals or exception states map to **Business Rejection Rules** (`validations`).
* Cursor loops (`CURSOR + LOOP`) map to structural processing steps or collection groupings.
* Database transaction controls (`COMMIT / ROLLBACK`) map to functional **Edge Cases** (`edge_cases`).



### 3.8 Embabel Semantic Consolidation & Resolution Pipeline

Phase 3 orchestrates individual data pieces into a unified system map using a structured Map-Reduce pattern powered by Embabel.

**Note:** Topic link resolution (broker + topic matching) and floating link registration (HTTP client detection) are now performed deterministically in Phase 1. Embabel's role in Phase 3 is limited to:

1. **Semantic Reduce Phase:** Embabel groups enriched `ExecutionFinding` JSON records from Phase 2 by logical architectural boundaries. A targeted reasoning loop uses an efficient model to collapse duplicate validation signatures and overlapping data maps, generating clean, modular summaries.
2. **Algorithmic Link Consolidation:**
* *Topic Links:* Phase 1 results are confirmed and any cross-manifest topic pairs that could not be resolved within a single scan are now matched.
* *Floating Links:* Embabel assesses open HTTP client contracts (still `PENDING` after Phase 1) against known endpoints. If the structural parameters, HTTP verbs, and DTO layouts match with a calculated semantic confidence of $\ge 85\%$, the connection is saved to the store as a `RESOLVED_FLOATING_LINK`.


3. **Quality Audit Corrective Path:** If the randomized quality audit sampling managed by the Semantic Validation Agent fails to achieve the minimum $\ge 92\%$ pass rate, the entire synthesis batch for that module is automatically quarantined. The pipeline halts final file writing, increases the validation sample rate to 100% for that module, and routes the tasks through a multi-model consensus validation loop to isolate and correct the deviating outputs.
4. **Decoupled Asset Generation:** The pipeline outputs two matching assets simultaneously: a clean, readable Markdown Specification document and a rich, machine-readable `semantic_manifest.json` file embedded with AST tracing coordinates and business mappings.

---

## 4. Technical Data Contracts (Execution Finding Schema)

Every Executor must produce an output payload conforming strictly to the following JSON Schema. No unmapped or unstructured text is allowed within the state persistence layer.

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "ExecutionFinding",
  "type": "object",
  "required": [
    "metadata",
    "business_abstraction",
    "business_rules_and_guardrails",
    "test_insights",
    "architectural_connections",
    "discovered_dependencies"
  ],
  "properties": {
    "metadata": {
      "type": "object",
      "required": ["task_id", "target_name", "file_path", "tech_profile", "module_tag", "timestamp"],
      "properties": {
        "task_id":      { "type": "string" },
        "target_name":  { "type": "string" },
        "file_path":    { "type": "string" },
        "tech_profile": { "type": "string" },
        "module_tag":   { "type": "string" },
        "timestamp":    { "type": "string", "format": "date-time" }
      }
    },
    "business_abstraction": {
      "type": "object",
      "required": ["purpose", "happy_paths"],
      "properties": {
        "purpose": { "type": "string" },
        "happy_paths": {
          "type": "array",
          "items": {
            "type": "object",
            "required": ["flow_name", "description"],
            "properties": {
              "flow_name":   { "type": "string" },
              "description": { "type": "string" }
            }
          }
        }
      }
    },
    "business_rules_and_guardrails": {
      "type": "object",
      "required": ["validations", "edge_cases"],
      "properties": {
        "validations": {
          "type": "array",
          "items": {
            "type": "object",
            "required": ["field_or_context", "rule", "error_behavior"],
            "properties": {
              "field_or_context": { "type": "string" },
              "rule":             { "type": "string" },
              "error_behavior":   { "type": "string" }
            }
          }
        },
        "edge_cases": {
          "type": "array",
          "items": {
            "type": "object",
            "required": ["scenario", "business_consequence"],
            "properties": {
              "scenario":             { "type": "string" },
              "business_consequence": { "type": "string" }
            }
          }
        }
      }
    },
    "test_insights": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["test_file_path", "scenario_verified", "hidden_rule_uncovered"],
        "properties": {
          "test_file_path":       { "type": "string" },
          "scenario_verified":    { "type": "string" },
          "hidden_rule_uncovered": { "type": "string" }
        }
      }
    },
    "architectural_connections": {
      "type": "object",
      "required": ["inbound", "outbound"],
      "properties": {
        "inbound": {
          "type": "object",
          "required": ["http_endpoints", "event_subscriptions", "scheduled_triggers"],
          "properties": {
            "http_endpoints": {
              "type": "array",
              "items": {
                "type": "object",
                "required": ["method", "path_pattern", "description"],
                "properties": {
                  "method":       { "type": "string" },
                  "path_pattern": { "type": "string" },
                  "description":  { "type": "string" }
                }
              }
            },
            "event_subscriptions": {
              "type": "array",
              "items": {
                "type": "object",
                "required": ["broker", "topic_or_queue", "payload_structure"],
                "properties": {
                  "broker":            { "type": "string" },
                  "topic_or_queue":    { "type": "string" },
                  "payload_structure": { "type": "string" }
                }
              }
            },
            "scheduled_triggers": {
              "type": "array",
              "items": {
                "type": "object",
                "required": ["schedule_expression", "description"],
                "properties": {
                  "schedule_expression": { "type": "string" },
                  "description":         { "type": "string" }
                }
              }
            }
          }
        },
        "outbound": {
          "type": "object",
          "required": ["http_calls", "event_publications"],
          "properties": {
            "http_calls": {
              "type": "array",
              "items": {
                "type": "object",
                "required": ["method", "url_or_path", "encapsulated_in", "is_external", "external_contract_hint"],
                "properties": {
                  "method":                  { "type": "string" },
                  "url_or_path":             { "type": "string" },
                  "encapsulated_in":         { "type": "string" },
                  "is_external":             { "type": "boolean" },
                  "external_contract_hint":  { "type": "string" }
                }
              }
            },
            "event_publications": {
              "type": "array",
              "items": {
                "type": "object",
                "required": ["broker", "topic_or_queue", "routing_key", "business_trigger"],
                "properties": {
                  "broker":          { "type": "string" },
                  "topic_or_queue":  { "type": "string" },
                  "routing_key":      { "type": "string" },
                  "business_trigger": { "type": "string" }
                }
              }
            }
          }
        }
      }
    },
    "discovered_dependencies": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["file_path", "reason", "discovery_depth"],
        "properties": {
          "file_path":       { "type": "string" },
          "reason":          { "type": "string" },
          "discovery_depth": { "type": "integer" }
        }
      }
    }
  }
}

```

---

## 5. Non-Functional Requirements & Guardrails

### 5.1 Idempotency via Environmental Content Hashing

To prevent token spend on unmodified assets during repetitive execution loops, task identifiers within the local state layer are evaluated dynamically using an expanded, environment-aware cryptographically isolated composite formula:

$$\text{Task ID} = \text{SHA-256}\bigl(\text{file\_path} \mathbin{\|} \text{SHA-256}(\text{file\_content}) \mathbin{\|} \text{SHA-256}(\text{test\_file\_content}) \mathbin{\|} \text{SHA-256}(\text{model\_target\_id}) \mathbin{\|} \text{SHA-256}(\text{system\_prompt\_version})\bigr)$$

If an asset has no paired test class, `SHA-256(test_file_content)` is replaced with the literal fallback string `"no-test-file"`. Any change to the physical file contents, the configured target model ID, or the internal LLM prompt engineering template automatically alters the resulting Task ID, invalidating the cache and triggering a fresh analysis.

### 5.2 Infinite Loop & Graph Prevention

* **Visited Registry:** The Planner maintains an active thread-safe set of file hashes currently flagged as `SUCCESS` or `RUNNING`. Redundant evaluation requests targeting an active hash are discarded immediately.
* **Max Hop Depth:** A configurable `max-discovery-depth` parameter (default: `3`) tracks boundary crossings (e.g., repository transitions, synchronous-to-asynchronous transformations). If a discovery sequence exceeds $N$ hops from its root entry point, processing for that branch is frozen, an alert is logged, and the task status is set to `AWAITING_HUMAN_REVIEW`.

### 5.3 Data Integrity & Schema Validation

The local persistence layer rejects malformed payloads. Before an Executor task transitions to `SUCCESS`, its JSON string must validate against the strict JSON Schema defined in Section 4. Validation failures trigger an immediate transition to `FAILED` with a structural error classification, preventing corrupt data from entering Phase 3.

### 5.4 Threading Infrastructure & Resilient In-Thread Backoff

Concurrency limits are managed declaratively via Spring's core context execution properties rather than hardcoded semaphore logic inside code loops. The application initializes a dedicated `ThreadPoolTaskExecutor` bean bound to the name `orchestratorTaskExecutor`.

* **Queue Control:** The pool queue capacity must be sufficiently deep to accommodate large parallel DAG discovery spikes without overflow exceptions.
* **In-Thread Resiliency:** Model interactions within the `@Async` task context employ an active exponential backoff strategy (initial delay: 2s, multiplier: 2.0, capped at 60s, maximum retry attempts: 3). If an HTTP 429 (Rate Limit Exceeded) is received from the AI provider, the running thread pauses natively (`Thread.sleep()`) and securely retries the processing step. If rate limits persist after all retries, the task transitions to `FAILED`.

### 5.5 Crash Recovery & Warm Start Protocol

In the event of an abrupt process termination (e.g., manual kills via `Ctrl+C`, network timeouts, loss of local power), the application protects data integrity through a pre-run reconciliation loop upon restart.

1. **Orphan Mitigation:** The system queries the SQLite task matrix for entries stuck in the `RUNNING` state.
2. **Reversion Step:** These orphaned tasks are automatically rolled back from `RUNNING` to `PENDING`. Associated JSON fragments are inspected for schema compliance; if they are valid, the task transitions directly to `SUCCESS` to leverage cache recovery.
3. **DAG Realignment:** The Planner rebuilds the dependency graph from the updated database state, resuming analysis with zero metadata corruption or double token expenditures.

### 5.6 CLI Visual Telemetry & UX

To avoid "black box" silence during long evaluation windows on deep workspaces (up to 3 hours), the CLI must print structured console telemetry using standard terminal utilities:

* An active, real-time dynamic progress bar mapping `Completed / Total Tasks` compiled from the SQLite store.
* Explicit counter metrics charting total tokens consumed and estimated API spend.
* Immediate console warnings when an execution thread triggers a local backoff retry due to model rate-limiting.

### 5.7 CLI Command Surface

The application must expose the following commands via Spring Shell:

| Command | Arguments | Purpose |
|---|---|---|
| `scan` | `[--manifest path]` | Run Phase 1 (indexing) only — produces `code-graph-index.json` and populates SQLite |
| `plan` | `[--manifest path]` | Show the execution DAG without running executors (dry DAG view) |
| `run` | `[--manifest path] [--dry-run] [--llm-threshold N]` | Execute all 3 phases end-to-end. Phase 2 LLM enrichment only activates for files exceeding N unresolved signatures (default: 5). |
| `status` | | Show current SQLite task state summary and counters |
| `resume` | `[--manifest path]` | Warm-start recovery: reconcile orphaned `RUNNING` tasks, rebuild DAG, resume |
| `validate` | `[--manifest path]` | Validate manifest schema and code-graph-index.json structure |
| `clear` | `[--manifest path]` | Delete all tasks in SQLite store and remove output JSON index files |

The `--dry-run` flag on the `run` command enables simulation mode (see §5.8).

### 5.8 Testing Isolation & Simulation Mode

To verify system orchestrations and engine state transitions within CI/CD pipelines without generating external provider token fees, the application must support a strict simulation mode via an execution flag (`--dry-run`). When active, Spring AI calls are intercepted by local stubs that validate prompt schema layout accuracy and return deterministic static JSON fragments matching Section 4 contracts.

---

## 6. Output Artifact Structures

### 6.1 Human-Centric Specification Template (Functional Flow Document)

The final Markdown artifact written by Phase 3 combines the full-stack system discoveries into a readable, business-centric document following this format:

```markdown
# Functional Flow Specification: [Flow Name]

## 1. User Journey & Interaction Overview
- **Trigger Component (UI):** [e.g., CheckoutPage.tsx — Button "Confirm Purchase"]
- **Local Validation Rules:** [Form constraint mappings]
- **Downstream Entry API:** [e.g., POST /api/v1/orders]

## 2. Ecosystem Preconditions
- **Async Condition:** [e.g., Kafka topic "inventory-status" must be active]
- **Database Dependency:** [e.g., Stored Procedure 'PR_CALCULATE_TAX' must be compiled]

## 3. Business Rules Matrix (BDD Input Source)
| Scenario ID | Context (Given) | Trigger Action (When) | Expected System State (Then) | Data Bounds (Examples) | Source Task IDs |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **TR-01** | Active Account | Order finalized | Set status to PROCESSED | balance > price | `e2a71b...` |
| **TR-02** | Invalid Balance | Order finalized | Reject with TransactionException | balance < price | `f9c21d...` |

## 4. Discovered Edge Cases & Invariants
- **Constraint A:** Database level transaction rollback enforced on stock failure (Mapped from Stored Procedure).
- **Constraint B:** Validation pattern mismatch triggers immediate HTTP 400 rejection (Mapped from Custom Validator).

## 5. Unresolved Dependencies & Review Tasks
- [List any tasks marked AWAITING_HUMAN_REVIEW here. Resolving these tasks requires running the CLI with explicit target context injection profiles.]

```

### 6.2 Machine-to-Machine Integration: `semantic_manifest.json`

To allow external applications to process the extracted logic without losing architectural traceability, the system outputs a decoupled relational JSON schema. This schema explicitly accommodates incomplete or suspended AST coordinates arising from tasks categorized as `AWAITING_HUMAN_REVIEW` to ensure validation compliance:

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "SemanticManifest",
  "type": "object",
  "required": ["manifest_version", "system_name", "flows"],
  "properties": {
    "manifest_version": { "type": "string", "enum": ["3.0.0"] },
    "system_name": { "type": "string" },
    "flows": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["flow_id", "name", "steps", "traceability_graph"],
        "properties": {
          "flow_id": { "type": "string" },
          "name": { "type": "string" },
          "steps": {
            "type": "array",
            "items": {
              "type": "object",
              "required": ["step_index", "component_type", "business_rule"],
              "properties": {
                "step_index": { "type": "integer" },
                "component_type": { "type": "string", "enum": ["UI_CONTROLLER", "REST_ENDPOINT", "VALIDATOR", "STORED_PROCEDURE", "EVENT_LISTENER"] },
                "business_rule": { "type": "string" }
              }
            }
          },
          "traceability_graph": {
            "type": "object",
            "required": ["nodes", "edges"],
            "properties": {
              "nodes": {
                "type": "array",
                "items": {
                  "type": "object",
                  "required": ["node_id", "file_reference", "ast_signature", "lines"],
                  "properties": {
                    "node_id": { "type": "string" },
                    "file_reference": { "type": "string" },
                    "ast_signature": { "type": ["string", "null"] },
                    "lines": { "type": ["string", "null"] }
                  }
                }
              },
              "edges": {
                "type": "array",
                "items": {
                  "type": "object",
                  "required": ["source_node", "target_node", "link_type"],
                  "properties": {
                    "source_node": { "type": "string" },
                    "target_node": { "type": "string" },
                    "link_type": { "type": "string", "enum": ["DETERMINISTIC_CALL", "FLOATING_HTTP", "TOPIC_KAFKA", "TOPIC_RABBITMQ", "TOPIC_ACTIVEMQ", "DATABASE_CALL"] }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

```

---

## 7. Acceptance Criteria

A CLI run is considered successful when all of the following conditions are met.

### 7.1 Extraction Coverage

| Metric | Minimum Threshold | Validation Method |
| --- | --- | --- |
| **HTTP Inbound Endpoints** | $\ge 95\%$ | Verification matching entry paths found in the static Code Graph against the final Markdown matrix. |
| **Pub/Sub Channels (Kafka, RabbitMQ, ActiveMQ)** | $\ge 90\%$ | Cross-reference of matching topic, queue, and destination structures resolved between publisher and consumer modules across all supported brokers. |
| **Custom Constraints** | $\ge 95\%$ | Verification that fields annotated with custom validators have their corresponding `isValid` logic slices extracted and represented. |
| **Database Procedures** | $\ge 90\%$ | Complete end-to-end extraction and parsing of procedural statements invoked within active backend tasks. |
| **Plan Execution Completeness** | $\ge 98\%$ | Processing tasks that reach the `SUCCESS` state (excluding tasks explicitly flagged as `AWAITING_HUMAN_REVIEW`). |
| **Inter-File Call Resolution Rate** | $\ge 75\%$ | Ratio of resolved `MethodCallExpr` nodes to total non-JDK `MethodCallExpr` nodes in the codebase. |
| **Database Access Point Detection** | $\ge 90\%$ | Verification that all `@Procedure` annotations and JdbcTemplate calls are captured. |
| **Outbound HTTP Client Detection** | $\ge 90\%$ | Verification that all `RestTemplate`/`WebClient`/`FeignClient` usages are registered as `floating_links`. |
| **Topic Link Resolution** | $\ge 95\%$ | Cross-reference of matching topic/queue/destination pairs between producers and consumers. |
| **Phase 2 LLM Spend per Scan** | $\le 20\%$ of files | Only files exceeding `llm-unresolved-threshold` or flagged by semantic criteria qualify for LLM enrichment. |

### 7.2 Semantic & Structural Quality

| Metric | Minimum Threshold | Control Mechanism |
| --- | --- | --- |
| **Semantic Validation Pass Rate** | $\ge 92\%$ | Randomized spot-checks executed by the Semantic Validation Agent without triggering a final batch quarantine event. |
| **Traceability Integrity** | $100\%$ | Verification that every business rule maps directly to a valid file path and line location in the source repo. |
| **Technical Jargon Filtering** | $\le 3\%$ | Extracted business description lines that contain framework-specific jargon (e.g., *autowired*, *JPA repo*, *bean*). |
| **Manifest Schema Compliance** | $100\%$ | Structural verification of the exported `semantic_manifest.json` against its formal schema definitions. |

### 7.3 Performance, Concurrency, and Infrastructure

| Metric | Target Threshold | Test Condition Scenario |
| --- | --- | --- |
| **Database Connection Failures** | $0$ incidents | Zero exceptions due to file locks (`database is locked`) when executing at maximum acceleration under a pool-size of 10. |
| **Time to First Finding** | $\le 1$ minute | Continuous execution duration from tool activation to the serialization of the first task record in SQLite. |
| **Warm Start Recovery Duration** | $\le 30$ seconds | Execution time required for the Orchestrator to resolve pending/orphaned states, scan compliance, and re-align the processing DAG after a crash. |
| **Idempotent Spend Guard** | $0$ tokens consumed | Executing the CLI against an unmodified repository workspace with active environmental states must complete using 100% cached records in $\le 60$ seconds. |
| **Total Runtime Window** | $\le 3$ hours | Processing a standard 10,000-file monorepository workspace utilizing Spring-managed processing channels. |

### 7.4 Safety & Compliance

* **Read-Only Operation:** Zero code mutations or workspace asset alterations executed on the target source repository during any phase of operation.
* **Zero Leakage:** Complete containment of hardcoded secrets or infrastructure keys utilizing standard local engine library gates; no plaintext sensitive literals are allowed within outbound API payload histories.
* **Preservation of Review Tasks:** Every execution branch flagged as `AWAITING_HUMAN_REVIEW` must be preserved and listed within Section 5 of the output specification.

---

## Appendix: Verified Tech Stack & Ecosystem Dependencies

### Maven `pom.xml` Architecture Blueprint

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.2</version>
        <relativePath/>
    </parent>

    <groupId>com.github.ehdez73</groupId>
    <artifactId>code2req</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <name>AI Reverse Engineering CLI</name>
    <description>AI-Driven Reverse Engineering CLI for Spec-Driven Development (SDD)</description>

    <properties>
        <java.version>21</java.version>
        <spring-ai.version>1.0.0</spring-ai.version>
        <spring-shell.version>3.4.2</spring-shell.version>
        <sqlite-jdbc.version>3.45.1.0</sqlite-jdbc.version>
        <javaparser.version>3.25.9</javaparser.version>
        <embabel-agent.version>0.1.0-SNAPSHOT</embabel-agent.version>
    </properties>

    <repositories>
        <repository>
            <id>embabel-releases</id>
            <url>https://repo.embabel.com/artifactory/libs-release</url>
            <releases><enabled>true</enabled></releases>
            <snapshots><enabled>false</enabled></snapshots>
        </repository>
        <repository>
            <id>embabel-snapshots</id>
            <url>https://repo.embabel.com/artifactory/libs-snapshot</url>
            <releases><enabled>false</enabled></releases>
            <snapshots><enabled>true</enabled></snapshots>
        </repository>
        <repository>
            <id>spring-milestones</id>
            <url>https://repo.spring.io/milestone</url>
            <snapshots><enabled>false</enabled></snapshots>
        </repository>
    </repositories>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>${spring-ai.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>org.springframework.shell</groupId>
                <artifactId>spring-shell-dependencies</artifactId>
                <version>${spring-shell.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <!-- Core Spring Framework -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.shell</groupId>
            <artifactId>spring-shell-starter</artifactId>
        </dependency>

        <!-- Lightweight Native Persistence Layer replacing Hibernate ORM -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.xerial</groupId>
            <artifactId>sqlite-jdbc</artifactId>
            <version>${sqlite-jdbc.version}</version>
        </dependency>

        <!-- Declarative @Async support for Phase 2 orchestration -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>

        <!-- Pure Java Static Analysis Parsing Layer -->
        <dependency>
            <groupId>com.github.javaparser</groupId>
            <artifactId>javaparser-core</artifactId>
            <version>${javaparser.version}</version>
        </dependency>

        <!-- AI Core Ecosystem -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-openai</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-anthropic</artifactId>
        </dependency>
        <!-- YAML Manifest Parsing -->
        <dependency>
            <groupId>com.fasterxml.jackson.dataformat</groupId>
            <artifactId>jackson-dataformat-yaml</artifactId>
        </dependency>

        <!-- Metrics & Telemetry for CLI Visual UX (see §5.6) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- JSON Schema Validation -->
        <dependency>
            <groupId>com.networknt</groupId>
            <artifactId>json-schema-validator</artifactId>
            <version>1.5.2</version>
        </dependency>

        <!-- Embabel Semantic Consolidation Pipeline (Phase 3 Map-Reduce) -->
        <dependency>
            <groupId>com.embabel.agent</groupId>
            <artifactId>embabel-agent-starter</artifactId>
            <version>${embabel-agent.version}</version>
        </dependency>

        <!-- Testing Infrastructure -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.shell</groupId>
            <artifactId>spring-shell-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <jvmArguments>-Djline.terminal=jline.UnixTerminal</jvmArguments>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### Application Infrastructure Profile (`application.properties`)

```properties
spring.application.name=code2req

# application.properties
spring.main.web-application-type=none

spring.shell.interactive.enabled=true

# HikariCP Data Source Target Setup for Embedded SQLite Cache
spring.datasource.url=jdbc:sqlite:.code2req_cache.db
spring.datasource.driver-class-name=org.sqlite.JDBC

# HikariCP Concurrency Isolation (Optimized for multi-thread WAL reads and busy timeouts)
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.connection-init-sql=PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000; PRAGMA synchronous=NORMAL;

# ===================================================================
# Spring Task Execution (Declarative Async Thread Pool Governance)
# ===================================================================
spring.task.execution.pool.core-size=5
spring.task.execution.pool.max-size=10
spring.task.execution.pool.queue-capacity=1000
spring.task.execution.thread-name-prefix=c2r-executor-

# Enforce explicit Async support termination lifecycles
spring.task.execution.shutdown.await-termination=true
spring.task.execution.shutdown.await-termination-period=30s


```