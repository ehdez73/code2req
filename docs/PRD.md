# Product Requirement Document (PRD)

## AI-Driven Reverse Engineering CLI for Spec-Driven Development (SDD)

> **Version 5.9** — Added §2.2 Generation & Quality Audit (F024 / US052) with manifest schema enforcement and quarantined flow documentation. Phase 2 enrichment is now optional. `INDEXED` tasks (Phase 1 only) proceed directly to Phase 3 — the LLM derives business rules, edge cases, and non-functional requirements from source code snippets. Phase 2 enrichment provides richer context (test insights, discovered dependencies) when available, but is no longer a mandatory gate. Removed 4 redundant qualification rules (Spring Data interfaces, @Scheduled tasks, native SQL/JPQL-HQL queries) — the Phase 3 LLM already receives their source code snippets and can derive the same semantics without a separate enrichment call.

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

The CLI adopts a multi-phase pipeline that transitions from deterministic compilation (Phase 1) through optional per-file LLM enrichment (Phase 2) to an agentic extraction phase (Phase 3) and a pure-Java generation phase (Phase 4). When Phase 2 is skipped, Phase 3 derives business semantics directly from source code snippets.

```
[scan] ──> [enrich] ──> [extract] ──> [generate]
  (P1)       (P2)          (P3)           (P4)
              │
              └── (optional, skip via INDEXED)
```

- **`scan`** (Phase 1): Deterministic indexing — AST parsing, secret redaction, SQLite persistence. Zero network calls.
- **`enrich`** (Phase 2): Qualification pass + per-file LLM enrichment via Spring AI `@Async`. Optional — skipped when no file qualifies.
- **`extract`** (Phase 3): Embabel GOAP agent traces entry-point-driven flows and extracts functional requirements.
- **`generate`** (Phase 4): Pure-Java output writers produce the Markdown specification and `semantic_manifest.json`.

> **Note:** A Language Extension Framework (SPI for non-Java language parsers) was originally planned as E002 but is **formally postponed**. The current implementation is Java/Spring-only via JavaParser. The SPI, parser registry, and routing components described in stories US018-US022 (F007-F009) are de-scoped and retained for future reference.

### 2.1 Phase 1: Deterministic Indexing

Before any LLM interaction takes place, the CLI scans the physical workspace using local code-graph and parsing tools. To maintain platform-agnostic distribution without native OS-level JNI bindings (such as Tree-sitter), the engineering stack enforces pure-Java AST parsers (e.g., **JavaParser** for Spring/Java modules). This phase runs as a deterministic compiler-pass requiring zero network connectivity or LLM credentials. It maps signatures, endpoint routes, call frameworks, and event publishers into a local intermediate contract file, eliminating structural exploration overhead during LLM execution.

To enable inter-file structural tracing without LLM dependencies, Phase 1 operates a **two-pass deterministic linker** architecture:

1. **Pass 1 — Declaration Collection:** Every source file is parsed with JavaParser to extract method signatures, field types, and component stereotypes into a global `DeclarationRegistry` held in memory. No resolution or analysis is performed — only structural registration.
2. **Pass 2 — Resolution Analysis:** Each file is re-analysed with the full visitor suite. Resolution visitors (`CallGraphVisitor`, `DbAccessVisitor`, `OutboundHttpVisitor`) resolve method calls, database access patterns, and outbound HTTP calls against the registry built in Pass 1.
3. **Post-Pass Link Resolution:** Once all files are processed, deterministic resolvers match event producers to consumers (`TopicLinkResolver`) and register outbound HTTP calls (`FloatingLinkResolver`).

This design ensures that Controller → Service → Repository / Database / External System traces are resolved without LLM calls. The LLM is reserved for semantic enrichment — either via Phase 2 (per-file) or derived directly from source code snippets in Phase 3 (per-flow) when enrichment is skipped.

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

### 2.2 Phase 4: Generation & Quality Audit

The output phase produces the two final artifacts — a human-readable Markdown specification and a machine-readable `semantic_manifest.json` — and performs a structural quality audit before persisting.

* **Markdown Specification:** `SynthesizeSpecAction` transforms the extraction domain model (cross-referenced flows, orphaned methods, ambiguity gaps) into a structured Markdown document organized by feature and functional flow. The spec includes resolved flows, unresolved dependencies (Section 5), business rules, edge cases, non-functional requirements, and traceability graphs.
* **Semantic Manifest JSON:** `ManifestMapper` converts extraction-domain objects into 16 typed Java records (e.g., `SemanticManifest`, `ManifestFlow`, `ManifestBusinessRule`) annotated with `@JsonNaming(SnakeCaseStrategy)` for snake_case serialization. Jackson `ObjectMapper` writes the final `semantic_manifest.json` to the spec output directory. The record types themselves enforce the JSON schema at compile time — unknown fields are impossible, and all required fields are guaranteed present.
* **Quality Audit (US052 / F024):** After the manifest is produced, a post-agent structural validation ensures output integrity:
  * **Schema conformance:** The typed record hierarchy mirrors the `semantic-manifest-schema.json` (draft-07) exactly. Conformance is enforced at compile time — no runtime schema validation is required. A dedicated `ManifestPojoSchemaTest` verifies full, minimal, and edge-case serializations against the schema at test time.
  * **Quarantined flow documentation:** Flows with ambiguity gaps (`AWAITING_HUMAN_REVIEW`) are tagged with `review_required: true` and include an `unresolved_reason` object (reason type, detail text, confidence score). They appear in the spec's Section 5 (Unresolved Dependencies), ensuring quarantined flows are visible even when the agent cannot fully resolve them.
  * **Pure Java, post-agent:** The quality audit runs inside the output writers (`SynthesizeSpecAction`), not in the Embabel agent. It is deterministic, requires no LLM calls, and single-file failures never block the full pipeline.