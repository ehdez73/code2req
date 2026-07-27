# Product Requirement Document (PRD)

## AI-Driven Reverse Engineering CLI for Spec-Driven Development (SDD)

> **Version 5.13** — Eliminated Phase 2 as a standalone phase. Enrichment is now flow-driven inside Phase 3 — the new `EnrichFlowAction` (GOAP action between `TraceFlow` and `AnalyzeFlow`) inspects each traced flow and enriches only files with enrichment-worthy characteristics (stored procedures, custom validators, complex SQL, unresolved HTTP calls, linked validators/aspects, test files, or complex entry points). Non-enriched files are handled directly from source code snippets. Phase 1 extended with `AspectVisitor` (AOP detection), `ValidatorLinkResolver`, and `AspectLinkResolver` for annotation-to-processor linking. Pipeline simplified to `scan → extract → generate`. Bumped from v5.12.

---

## 1. Executive Summary & Objectives

### 1.1 Purpose

**Spec-Driven Development (SDD)** is the architectural practice of treating a highly structured, technology-agnostic business specification—not source code—as the primary, authoritative artifact of a software project. It is the single source of truth from which automated tests, clean implementation, and functional documentation are derived.

This document specifies the requirements for a Command Line Interface (CLI) application built on the JVM ecosystem using **Spring AI (via OpenRouter)** and **Embabel**. It is designed to perform deep, automated reverse engineering on legacy enterprise codebases. The tool extracts embedded business rules, domain validations, and ecosystem invariants, consolidating them into highly structured Markdown documents organized by **"Use Cases / Functional Flows"**.

Crucially, the tool rejects direct test framework generation. Instead, it exports a markdown specification alongside a machine-readable **Semantic Manifest JSON**. This output serves as the decoupled, hyper-traceable source of truth for downstream automated processes, such as a specialized AI Skill responsible for compiling BDD **Gherkin feature files (`.feature`)** or re-architecting applications.

### 1.2 Core Objectives

* **De-technify Enterprise Code:** Abstract dense, legacy technical implementations (Java/Spring, SQL procedural logic, state frameworks) into clear business intentions, process parameters, and edge cases.
* **Eradicate Context Rot & Hallucinations:** Enforce a stateless, short-lived execution pattern with absolute context isolation per file to maximize LLM analytical accuracy.
* **Provide End-to-End Visibility:** Connect scattered functional points, bridging frontend interactions, synchronous REST endpoints, asynchronous event topologies, and hidden procedural database layers.
* **Provide a Standard Data Contract for Test Generation:** Output predictable, structured semantic graphs that bridge the gap between static code structures and dynamic business tests.

---

## 2. Core Architecture & Product Paradigms

The CLI adopts a multi-phase pipeline that transitions from deterministic compilation (Phase 1) through optional entry-point-focused LLM enrichment (Phase 2) to an agentic extraction phase (Phase 3) and a pure-Java generation phase (Phase 4). When Phase 2 is skipped, Phase 3 derives business semantics directly from source code snippets.

```
[scan] ──> [extract] ──> [generate]
  (P1)       (P3)         (P4)
              ↑
       enrichment is flow-driven,
       cached across flows
```

- **`scan`** (Phase 1): Deterministic indexing — AST parsing, secret redaction, SQLite persistence. Zero network calls.
- **`extract`** (Phase 3): Embabel GOAP agent traces entry-point-driven flows. The new `EnrichFlowAction` enriches flow-relevant files on-demand, then `AnalyzeFlow` extracts functional requirements using both enrichment and source code snippets.
- **`generate`** (Phase 4): Pure-Java output writers produce the Markdown specification and `semantic_manifest.json`.

> **Note:** A Language Extension Framework (SPI for non-Java language parsers) was originally planned as E002 but is **formally postponed**. The current implementation is Java/Spring-only via JavaParser. The SPI, parser registry, and routing components described in stories US018-US022 (F007-F009) are de-scoped and retained for future reference.

### 2.1 Phase 1: Deterministic Indexing

Before any LLM interaction takes place, the CLI scans the physical workspace using local code-graph and parsing tools. To maintain platform-agnostic distribution without native OS-level JNI bindings (such as Tree-sitter), the engineering stack enforces pure-Java AST parsers (e.g., **JavaParser** for Spring/Java modules). This phase runs as a deterministic compiler-pass requiring zero network connectivity or LLM credentials. It maps signatures, endpoint routes, call frameworks, and event publishers into a local intermediate contract file, eliminating structural exploration overhead during LLM execution.

To enable inter-file structural tracing without LLM dependencies, Phase 1 operates a **two-pass deterministic linker** architecture:

1. **Pass 1 — Declaration Collection:** Every source file is parsed with JavaParser to extract method signatures, field types, and component stereotypes into a global `DeclarationRegistry` held in memory. No resolution or analysis is performed — only structural registration.
2. **Pass 2 — Resolution Analysis:** Each file is re-analysed with the full visitor suite. Resolution visitors (`CallGraphVisitor`, `DbAccessVisitor`, `OutboundHttpVisitor`) resolve method calls, database access patterns, and outbound HTTP calls against the registry built in Pass 1.
3. **Post-Pass Link Resolution:** Once all files are processed, deterministic resolvers match event producers to consumers (`TopicLinkResolver`) and register outbound HTTP calls (`FloatingLinkResolver`).

This design ensures that Controller → Service → Repository / Database / External System traces are resolved without LLM calls. The LLM is reserved for semantic enrichment — either via Phase 2 (entry-point-level) or derived directly from source code snippets in Phase 3 (per-flow) when enrichment is skipped.

#### 2.1.1 Pure-Java AST Structural Parsing

Regardless of build-system state, JavaParser runs as the authoritative engine to build an in-memory Abstract Syntax Tree (AST). It handles "dirty code" (broken syntax or non-compiling files) by evaluating them directly as text streams.

* **Parser Configuration:** The indexer configures JavaParser's `LanguageLevel` per file based on the target's `java-version` (via `JavaVersionMapper`), then parses with `StaticJavaParser`. No `CombinedTypeSolver` or type-resolution layer is configured — component classification uses annotation presence as the primary strategy, falling back to structural heuristics (naming conventions, package patterns) when type information is unavailable.



#### 2.1.2 AST Parsing Strategy (Java Domain)

The indexer leverages a dedicated `VoidVisitorAdapter<Context>` traversal strategy to capture four structural dimensions:

* **Component Identification & Types:** Detect classes, interfaces, and records. Identify stereotypes by inspecting class-level annotations (e.g., `@RestController`, `@Service`, `@Component`, `@Repository`).
* **Bean Method Detection (Pass 2):** Detect `@Bean`-annotated methods in `@Configuration` and `@SpringBootApplication` classes. Extract the explicit bean name (from `@Bean("name")`, `@Bean(name="name")`, or `@Bean(value="name")`) or fall back to the method name for implicit declarations. Capture the declared return type, enclosing configuration class name, and source file path. Interfaces, abstract classes, and non-`@Configuration` classes are skipped. Results are stored as `BEAN_METHOD` findings and consumed by Phase 2's `BeanDefinitionResolver` for inter-bean reference resolution.
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
   * Spring Data interfaces (`CrudRepository`, `JpaRepository`) are registered as **virtual declarations** — their derived query methods (e.g., `findByLastName()`) have no AST body but are recognized as database access points. Methods with `@Query` are no longer skipped — they produce findings with the query string and a `nativeQuery` flag distinguishing native SQL from JPQL/HQL.
   * Detect `@Query(value = "...", nativeQuery = true/false)` on Spring Data repository methods — extracts the query string and routes to `NATIVE_SQL` or `JPQL_HQL` type.
   * Detect `@NamedQuery(query = "...")` and `@NamedNativeQuery(query = "...")` on entity classes — individual and container (`@NamedQueries`/`@NamedNativeQueries`) forms supported. Routes to `JPQL_HQL` or `NATIVE_SQL` type respectively.
   * Detect `EntityManager.createNativeQuery(sql)` (→ `NATIVE_SQL`), `createQuery(jpql)` (→ `JPQL_HQL`), `createNamedQuery(name)` (→ `JPQL_HQL`). Non-query EM methods (`persist`, `merge`, `find`, etc.) remain `ENTITY_MANAGER`.
   * Detect `Session.createNativeQuery(sql)` and `Session.createSQLQuery(sql)` (→ `NATIVE_SQL`), `Session.createQuery(hql)` (→ `JPQL_HQL`). Non-query Session methods (`save`, `get`, `delete`, etc.) remain `HIBERNATE_SESSION`.
   * Detect raw JDBC calls: `Connection.prepareStatement(sql)` / `prepareCall(sql)` on `connection`/`conn` scope; `Statement.executeQuery(sql)` / `executeUpdate(sql)` / `execute(sql)` / `executeLargeUpdate(sql)` / `addBatch(sql)` on any scope with a string first argument (→ `NATIVE_SQL`).
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

* **Spring XML Configuration Analysis (Post-Pass):**
  * After Java AST analysis completes, `**/*.xml` files are discovered and DOM-parsed to identify Spring XML configuration content (root namespace `http://www.springframework.org/schema/beans`).
  * **Bean Declarations:** `<bean id="..." class="..." scope="..." factory-method="...">` elements are extracted, capturing id/class/scope/factory-method. `<alias name="..." alias="...">` mappings are recorded.
  * **Namespace Elements:** Elements in known Spring namespaces (util, jdbc, task, cache, tx, aop, context, lang, jee, jms, mvc, oxm) are resolved to their Java types where applicable via a namespace registry.
  * **Component Scan:** `<context:component-scan base-package="...">` base packages are captured.
  * **AOP/TX/Cache Configuration:** `<aop:config>`, `<tx:*>`, and `<cache:*>` elements are flagged as infrastructure configuration.
  * **Scheduled Tasks:** `<task:scheduled ref="..." method="..." cron="..." fixed-rate="..." fixed-delay="...">` elements are extracted with task type classification (cron/fixed-rate/fixed-delay).
  * **JMS Listeners:** `<jms:listener destination="..." ref="..." method="..." response-destination="...">` elements are captured.
  * **Import Resolution:** `<import resource="...">` references are followed recursively with cycle detection via a visited-set. `classpath:`, `classpath*:`, `file:` prefixes and glob patterns are resolved by the resource path resolver.
  * **@ImportResource Bridge:** Java classes annotated with `@ImportResource` trigger XML analysis of the referenced Spring config files, with findings attributed to the Java source file's analysis result.

#### 2.1.3 Pre-processing: Secret Redaction & File Exclusion

Before analysis begins, two pre-processing steps prepare source files for safe and targeted downstream processing: in-memory secret redaction and pattern-based file exclusion. Both operate during Phase 1 file discovery, before any AST parsing or LLM transmission.

* **Secret Redaction (US011):** In-memory only — source files on disk are never modified. Before any content is sent to the LLM (Phase 2 enrichment or Phase 3 extraction), known secret patterns (passwords, API keys, connection strings, tokens, private keys) are replaced with `[REDACTED:type]` placeholders via `SecretRedactor`. The redactor runs on the parsed AST string representation and operates deterministically — no network calls. This ensures zero secret leakage regardless of pipeline configuration.
* **File Exclusion (US012):** Generated code, build artifacts, VCS metadata, IDE files, and vendor packages are filtered out before any parsing occurs, ensuring only hand-written business logic reaches the analyzer. Nine default glob patterns are always applied:
  * `target/**`, `build/**`, `generated/**` — build tool output
  * `.git/**` — VCS metadata
  * `node_modules/**` — npm dependencies
  * `.gradle/**`, `.idea/**` — IDE and build cache files
  * `*.class`, `*.jar` — compiled artifacts
  * Patterns starting with `**/` are normalized to `{,**/}` syntax for Java's `PathMatcher` to match at any directory depth.
  * **Override mechanism:** Users specify additional `exclude_patterns` per scan target in `project-manifest.yaml` (via `ScanTarget.excludePatterns()`). These are **additive** to the defaults — there is no replacement mode.
  * **Pipeline integration:** `ExcludeFilter` is a Spring `@Service` consumed by `ScanCommand` in three discovery flows: Java source files, `web.xml` files, and Spring XML configuration files. Exclusion is purely a logical filter on the file list; excluded files are never read or parsed.
  * **Reporting:** Excluded file counts are logged at `INFO` level per scan target.

#### 2.1.4 SQLite as the Canonical Index Store

All analysis findings are written directly to the embedded SQLite database via the `execution_findings`, `topic_links`, and `floating_links` tables. SQLite serves as the canonical data store for all downstream pipeline phases. Per-file metadata (content hash, status, target assignment) is recorded in the `tasks` table.

#### 2.1.5 Snapshot & Restore

Point-in-time snapshots of local state (SQLite database + JSON extraction cache) enable safe experimentation and rollback during iterative analysis. The feature is realized by three components: `SnapshotService` (business logic), `RefreshableDataSource` (runtime pool-swapping proxy), and `SnapshotCommand` (CLI surface). Epic **E005**, feature **F025**, stories **US053** (create/list) and **US054** (restore).

* **Snapshot creation:** Uses SQLite's `VACUUM INTO` for transactionally consistent database copies. Copies the live SQLite database and `extraction-cache.json` into a timestamped subdirectory under `code2req.snapshot.dir` (default `./snapshots/`). Auto-generates names (e.g., `snapshot_20260717T120000`) when `--name` is omitted. Each directory includes a `snapshot.json` metadata file recording name, ISO-8601 date, CLI version, and a file manifest with SHA-256 checksums and byte sizes. Duplicate names are rejected. If the cache file does not exist, it is silently skipped — the SQLite DB alone is sufficient.
* **Snapshot listing:** `snapshot list` reads `snapshot.json` from every subdirectory and displays name, date, and per-file sizes. Corrupt or missing metadata on a per-snapshot basis produces a graceful fallback entry with an error field rather than aborting the full listing.
* **Restore lifecycle:** `snapshot restore --name <label>` follows a five-step sequence: (1) validate that the snapshot directory exists and contains `sqlite.db`; (2) drain the active HikariCP pool via `hds.close()`; (3) overwrite live `sqlite.db` and `extraction-cache.json` with snapshot copies; (4) create a new `HikariDataSource` with the same JDBC URL, driver, pool size (10), WAL pragmas, and `busy_timeout=5000`; (5) hot-swap via `RefreshableDataSource.replaceDelegate()` so all subsequent `getConnection()` calls route to the new pool. The internal `JdbcTemplate` is also replaced. No CLI restart is required.
* **RefreshableDataSource:** A `DataSource` proxy wrapping a `volatile` delegate reference. Wired as the `@Primary` bean across the application, enabling live pool replacement without re-injecting any `@Autowired` beans.
* **CLI subcommands:** Three shell commands: `snapshot create [--name <label>]`, `snapshot list`, `snapshot restore --name <label>`. The `--name` parameter is optional for create, required for restore. Errors report clear messages (missing snapshot, non-existent name).
* **Lifecycle and configuration:** Snapshots are independent of the `clean` command — `clean` never touches the `snapshots/` directory. The snapshot root is configurable via `code2req.snapshot.dir` (default `./snapshots`) and is gitignored via the `snapshots/` pattern.

#### 2.1.6 Phase 2: Entry-Point LLM Semantic Enrichment

After Phase 1 (deterministic indexing) completes, the pipeline performs an optional LLM enrichment pass focused on **entry-point files** — controllers, scheduled tasks, event listeners, and message consumers. These files bear the business operations users interact with, making them the highest-value targets for semantic enrichment. The enrichment adds context (business purpose, validation rules, edge cases, test insights, architectural connections) that is beyond the reach of static AST analysis, and is consumed by Phase 3's per-flow analysis.

**Non-entry-point files** (services, repositories, domain classes, etc.) are **not enriched individually**. Instead, Phase 3's flow analysis sends their source code as part of the flow-trace context — the LLM derives business rules, validations, and edge cases directly from the traced method bodies. This avoids redundant LLM calls: Phase 2 already captures the entry point's business context, while Phase 3's LLM receives the full traced flow with snippets of all participating files.

Phase 2 is broken into three sub-phases:

1. **Qualification (Planner):** A rule engine selects which INDEXED files require LLM enrichment — only entry-point files (controllers, scheduled tasks, event listeners) qualify by default, with additional deficit-oriented rules for edge cases.
2. **Structural Context Assembly:** For each qualified file, Phase 1 findings (call graph edges, endpoints, event listeners, DB access, outbound HTTP calls) are queried from SQLite and assembled into a JSON payload.
3. **Execution (Orchestrator + Executor):** Entry-point enrichment is submitted to the LLM concurrently. The orchestrator manages batches, collects results, and handles dynamic re-planning when file-level enrichment discovers inter-file dependencies.

##### Qualification Rules

The `EnrichmentPlanner` evaluates every INDEXED task against a configurable set of `QualificationRule` components. A task qualifies when any rule returns true; matching rules do not short-circuit — all matching reasons are recorded for traceability.

| Rule | Trigger | Purpose |
|------|---------|---------|
| `UnresolvedSignaturesRule` | File has >`llm-unresolved-threshold` (default: 5) unresolved `CALL_GRAPH_EDGE` findings | Files with many unknown call targets likely depend on external types whose role requires LLM interpretation |
| `StoredProcedureCallRule` | File has a `DATABASE_PROCEDURE_CALL` finding | Stored procedures contain opaque business logic that static analysis cannot decode |
| `CustomConstraintValidatorRule` | File has a `CONSTRAINT_VALIDATOR` finding | Custom validation annotations (`@ValidOrder` etc.) need LLM to explain the validation semantics |
| `UnresolvedFloatingLinkRule` | File has `floating_links` with `resolved_status = 'PENDING'` | Outbound HTTP calls to unresolved external endpoints require human-like analysis to classify |
| `TestAssertionsPresentRule` | File has a paired test file (naming convention match) | Tests encode hidden business expectations; the LLM enriches both source and test insights concurrently |
| `DtoValidationRule` | File has `VALIDATOR` finding + one of `ENDPOINT`/`COMPONENT`/`DB_ACCESS` | DTOs with Bean Validation annotations in a non-trivial context benefit from semantic enrichment |
| `EntryPointRule` | File has an `ENDPOINT`, `SCHEDULED_TASK`, `KAFKA_LISTENER`, `RABBITMQ_LISTENER`, `ACTIVEMQ_LISTENER`, or `EVENT_LISTENER` finding (including XML-declared listeners) | Entry points define the business operations users interact with — their semantic context is the highest-value input for Phase 3's flow analysis |

The first rule (`EntryPointRule`) is the primary gate: only files that Phase 1 identified as entry points qualify for enrichment. Non-entry-point classes (services, repositories, domain models) are handled by Phase 3, which sends their traced method bodies as inline source code snippets in the flow-analysis prompt. The remaining deficit-oriented rules run alongside the entry-point gate and may add additional qualification reasons where an entry-point file also has stored procedure calls, custom validators, unresolved floating links, etc.

Rules are discovered automatically via Spring component scanning — adding a new rule requires only a `@Component` class implementing `QualificationRule`.

##### Structural Context Assembly

When `EnrichmentOrchestrator.buildSubmitBatch()` prepares a task for enrichment, it calls `StructuralContextAssembler.assemble(taskId)` instead of passing `null`. The assembler queries `execution_findings` by task ID, groups findings by type (excluding enrichment/flow types), and builds a JSON object where each finding type becomes a top-level key and the value is an array of the parsed `finding_json` objects:

```
{
    "call_graph_edges": [{"source": "...", "target": "...", "resolved": true}],
    "endpoints": [{"path": "/api/orders", "method": "GET"}],
    "db_access": [{"operation": "query", "sql": "SELECT * FROM orders"}],
    ...
}
```

This payload is injected into the LLM prompt's `STRUCTURAL CONTEXT` section. The system prompt instructs the LLM to treat these as "already resolved — do not re-derive" and only report references outside this context as `discovered_dependencies`. This eliminates redundant LLM work and reduces token spend.

##### Batch Parallelism & Concurrency Model

The orchestrator submits enrichment futures in batches. All tasks within a batch are fired concurrently via `CompletableFuture` on a dedicated `orchestratorTaskExecutor` thread pool:

```
for each batch iteration:
    for each decision in batch:
        future = executor.enrich(task, decision, source, test, context)
        // returns immediately — work is queued on thread pool
    waitForAll(futures)  // blocks until ALL complete
    processCompletedBatch(futures)  // may discover new dependencies
```

| Pool Parameter | Default | Configuration Key |
|----------------|---------|-------------------|
| Core pool size | 5 | `code2req.enrichment.max-concurrent-llm-calls` |
| Max pool size | max(10, core) | — |
| Queue capacity | 1000 | — |
| Await termination | 30 seconds | — |

Each task within a batch runs in parallel on the thread pool. The `waitForAll()` barrier (`CompletableFuture.allOf().join()`) prevents processing results until the entire batch completes — this is necessary because discovered dependencies from one file may affect how subsequent batches are built.

Execution mode is controlled by `code2req.enrichment.execution-mode` (`async` / `sync`). In `async` mode (default), work is submitted to the thread pool. In `sync` mode, `doEnrich()` runs on the caller thread, forcing serial execution (useful for debugging with local LLMs).

##### Dynamic Replanning

When an LLM response contains `discovered_dependencies`, the orchestrator registers them as new `PlannerDecision` entries and creates a new batch iteration. These are inherently sequential — a parent file must complete before its discovered children can be enriched. The DAG ensures branch isolation: a slow parent only blocks its own branch, not unrelated branches.

Discovered dependencies beyond `max-discovery-depth` (default: 3, via `code2req.indexing.max-discovery-depth`) are transitioned to `AWAITING_HUMAN_REVIEW`.

##### Configuration Summary

| Property | Default | Description |
|----------|---------|-------------|
| `code2req.enrichment.execution-mode` | `async` | `async` for thread-pool concurrency, `sync` for serial debugging |
| `code2req.enrichment.max-concurrent-llm-calls` | 5 | Thread pool core size and concurrency degree |
| `code2req.enrichment.llm-unresolved-threshold` | 5 | Unresolved signature count triggering `UnresolvedSignaturesRule` |
| `code2req.enrichment.strict-response-format` | `true` | Enforce JSON Schema on LLM response format |
| `code2req.enrichment.semantic-validation-sample-rate` | — | Fraction of enrichment results to validate (0.0–1.0, not yet implemented) |
| `code2req.enrichment.max-tokens-per-run` | — | Aggregate token budget per enrichment session |

### 2.2 Phase 3: Agentic Extraction (Embabel GOAP)

Phase 3 uses an **Embabel GOAP agent** to transform the indexed code graph (Phase 1) and optional entry-point enrichment (Phase 2) into structured functional requirements. The agent discovers entry points, traces execution flows through the call graph, extracts business rules and edge cases, and groups related flows into features. Per ADR-006, Phase 3 is split into two CLI commands: `extract` (agentic analysis) and `generate` (pure-Java output synthesis). This section describes the agentic `extract` command; output generation is covered in §2.3.

The agent operates on a `CodebaseKnowledge` object built from the indexed SQLite store, which contains all endpoints, event listeners, scheduled tasks, method declarations, call graph edges, database access findings, and outbound HTTP calls discovered in Phase 1.

#### 2.2.1 GOAP Agent Architecture

The Embabel GOAP agent chains seven actions based on goal completion rather than a fixed pipeline. Each action is a Spring `@Component` implementing a common action interface:

* **`DiscoverEntryPoints`:** Scans `CodebaseKnowledge` for all entry-point candidates — HTTP endpoints (`@RequestMapping`, servlet paths), event listeners (`@KafkaListener`, `@RabbitListener`, `@JmsListener`, `@EventListener`), and scheduled tasks (`@Scheduled`). Trivial endpoints (actuator, health, metrics, swagger) are filtered out. Each surviving entry point receives a priority score based on: Phase 2 enrichment availability, method complexity, user-facing heuristics, and presence of associated test files.
* **`TraceFlow`:** Follows call graph edges from the highest-priority unscheduled entry point. Flow steps trace from the entry point through service layers to repositories, database calls, and external HTTP services with adaptive depth. Sub-chain caching reuses already-traced service chains when sibling entry points share same-service call paths. Unresolved calls (external services, third-party libraries) are recorded in the flow as external references.
* **`AnalyzeFlow`:** Extracts functional requirements from each traced flow — a user story title, one or more Gherkin scenarios (given/when/then), business rules with provenance, edge cases, and non-functional requirements. For each flow, it sends a single LLM prompt containing: (1) the entry point's Phase 2 enrichment context (validations, edge cases, test insights), (2) a structural flow summary (component type → class → method for each step), and (3) **source code snippets** — the class header (annotations + declaration) and traced method bodies for every file in the flow, extracted by line range from disk. Intermediate files (services, repositories, etc.) are provided as raw code snippets rather than pre-computed enrichment, since the LLM can derive business rules and validations directly from the traced method bodies. Supports progressive disclosure: flows output MINIMAL, STANDARD, or FULL detail based on their complexity score, preventing trivial flows from drowning out complex ones.
* **`GroupFlows`:** Clusters related flows into features using semantic similarity — from entry-point enrichment data (where available) and structural graph proximity. Each group becomes a feature section in the final specification.
* **`CrossReferenceFlows`:** Detects inter-flow dependencies by analyzing flow steps against each other. Produces typed edges: `DELEGATES_TO` (one flow explicitly calls another's entry point), `PUBLISHES_EVENT` (flow emits a message consumed by another flow), `CONSUMES_EVENT` (flow starts in response to a message produced by another flow). Orphaned methods — methods reachable from no entry point — are flagged as either dead code or missing entry points.
* **`QuarantineFlow`:** When a flow exceeds any guardrail (depth limit, token budget, ambiguity threshold), it is tagged `AWAITING_HUMAN_REVIEW` with an `unresolved_reason` payload. Quarantined flows are preserved in the output (Section 5 of the spec) rather than silently dropped, ensuring visibility regardless of agent confidence.
* **`QuarantineFlow`:** When a flow exceeds any guardrail (depth limit, token budget, ambiguity threshold), it is tagged `AWAITING_HUMAN_REVIEW` with an `unresolved_reason` payload. Quarantined flows are preserved in the output (Section 5 of the spec) rather than silently dropped, ensuring visibility regardless of agent confidence.

**Guardrails** are configuration-driven via `application.properties`:
* `max-flow-depth` (default: 5) — maximum call-chain depth before a flow is quarantined
* `max-tokens-per-run` (default: 500000) — aggregate LLM token budget for a single `extract` session

### 2.3 Phase 4: Generation & Quality Audit

The output phase produces the two final artifacts — a human-readable Markdown specification and a machine-readable `semantic_manifest.json` — and performs a structural quality audit before persisting.

* **Markdown Specification:** `SynthesizeSpecAction` transforms the extraction domain model (cross-referenced flows, orphaned methods, ambiguity gaps) into a structured Markdown document organized by feature and functional flow. The spec includes resolved flows, unresolved dependencies (Section 5), business rules, edge cases, non-functional requirements, and traceability graphs.
* **Semantic Manifest JSON:** `ManifestMapper` converts extraction-domain objects into 16 typed Java records (e.g., `SemanticManifest`, `ManifestFlow`, `ManifestBusinessRule`) annotated with `@JsonNaming(SnakeCaseStrategy)` for snake_case serialization. Jackson `ObjectMapper` writes the final `semantic_manifest.json` to the spec output directory. The record types themselves enforce the JSON schema at compile time — unknown fields are impossible, and all required fields are guaranteed present.
* **Quality Audit (US052 / F024):** After the manifest is produced, a post-agent structural validation ensures output integrity:
  * **Schema conformance:** The typed record hierarchy mirrors the `semantic-manifest-schema.json` (draft-07) exactly. Conformance is enforced at compile time — no runtime schema validation is required. A dedicated `ManifestPojoSchemaTest` verifies full, minimal, and edge-case serializations against the schema at test time.
  * **Quarantined flow documentation:** Flows with ambiguity gaps (`AWAITING_HUMAN_REVIEW`) are tagged with `review_required: true` and include an `unresolved_reason` object (reason type, detail text, confidence score). They appear in the spec's Section 5 (Unresolved Dependencies), ensuring quarantined flows are visible even when the agent cannot fully resolve them.
  * **Pure Java, post-agent:** The quality audit runs inside the output writers (`SynthesizeSpecAction`), not in the Embabel agent. It is deterministic, requires no LLM calls, and single-file failures never block the full pipeline.