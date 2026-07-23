# Gap Analysis: PRD vs. Implementation

> **Date:** 2026-07-16
> **PRD Version:** 5.10
> **Last PRD update:** §2.2 Phase 3: Agentic Extraction — Embabel GOAP architecture (6 actions, guardrails) + §2.2.2 Interactive Mode (US057 / F028 — SPI with "Write your own" option, default interactive, `--headless` opt-out, LLM re-evaluation feedback loop). **Partially implemented** (SPI + NoOp + Interactive + QuarantineFlowAction + schema + CLI flags done; AnalyzeFlowAction deferred)  
> **Source:** `docs/PRD.md` vs. `src/main/java/com/github/ehdez73/code2req/`

---

## A. PRD Features NOT Fully Implemented

| # | PRD Reference | Gap | Severity |
|---|---|---|---|
| 1 | §2.1.2 DB Access — `SimpleJdbcCall` | `SimpleJdbcCall.execute()` / `executeFunction()` / `executeObject()` have no detector. `NamedParameterJdbcTemplate` works (via `"npjt"` scope in `JdbcTemplateDetector`) but `SimpleJdbcCall` is completely absent from the codebase. No class under `indexing/domain/analyzer/db/detector/` handles it. | Medium |
| 2 | §2.1.2 DB Access — `Session.createNamedQuery()` | `createNamedQuery` is handled correctly by `EntityManagerDetector` (line 19 of `EM_METHODS`) but is **missing from `HibernateSessionDetector.SESSION_METHODS`**. `DbAccessHelper.isJpqlQueryMethod("createNamedQuery")` returns `true`, but the detector's guard clause on line 32 (`if (!SESSION_METHODS.contains(callName)) return;`) silently skips it. Any Hibernate `Session.createNamedQuery()` call is ignored. | Low |
| ~~3~~ | ~~§2.1.2 — Missing unit tests~~ | ~~These four components have production code but **zero dedicated unit tests**:~~ | ~~Medium~~ |
| | ~~§2.1.2 Template File Parsing~~ | ~~**`JspTemplateParser`** — only covered incidentally via `ScanCommand` integration tests~~ | |
| | ~~§2.1.2 web.xml Endpoint Discovery~~ | ~~**`WebXmlAnalyzer`** — no test class exists~~ | |
| | ~~§2.1.2 Template-to-Endpoint Link Resolution~~ | ~~**`TemplateLinkResolver`** — no test class exists~~ | |
| | ~~§2.1.2 Servlet Endpoints~~ | ~~**`ServletEndpointDetector`** — no test class exists~~ | |

---

## B. Features Implemented But NOT in PRD

| # | Feature | Location | Description |
|---|---|---|---|
| 1 | **Java `@Bean` method detection** | `indexing/domain/analyzer/bean/java/BeanMethodVisitor.java`, `BeanMethodInfo.java` | Detects `@Bean` methods in `@Configuration` classes — not described in PRD |
| 2 | **XML Spring bean analysis** | `indexing/domain/analyzer/bean/xml/` (11 files) | Full XML parsing: beans, aliases, component-scan, AOP config, `<import>` chains, namespace beans, `<task:scheduled-tasks>`, `<jms:listener-container>` |
| 3 | **`@EventListener` + `ApplicationEventPublisher`** | `indexing/domain/analyzer/event/listener/` (4 files) | PRD only mentions broker listeners (Kafka, RabbitMQ, ActiveMQ). Implementation also detects in-process event publish/subscribe with up to 3 levels of call-chain nesting |
| 4 | **Test mining infrastructure** | `TestFileMatcher.java`, `PairedExecutionResolver.java`, `TestImportIndex.java` | Automatic test-to-source file pairing and execution resolution — not in PRD |
| ~~5~~ | ~~**Phase 2 enrichment qualification rules**~~ | ~~`enrichment/domain/planner/rule/` (7 rule classes)~~ | ~~Now documented in PRD §2.1.6. Includes new `ResolvedPhase1DepsRule`, `StructuralContextAssembler`, and batch parallelism model~~ |
| 6 | **Phase 3 Embabel GOAP agent actions** | `extraction/adapter/agent/action/` (6 action classes) | PRD describes Phase 3 at high level only; the GOAP action taxonomy (`DiscoverEntryPoints`, `TraceFlow`, `AnalyzeFlow`, `GroupFlows`, `CrossReferenceFlows`, `QuarantineFlow`) and `FunctionalRequirementAgent` are implementation details |
| 7 | **Phase 4 spec generation pipeline** | `generation/` (17 files) | Full markdown + JSON manifest generation with `SynthesizeSpecAction`, `ManifestMapper`, and 15+ manifest POJOs — PRD only mentions "Semantic Manifest JSON" as an export artifact |
| ~~8~~ | ~~**Secret redaction**~~ | ~~`SecretRedactor.java`~~ | ~~In-memory redaction of passwords, API keys, connection strings before LLM transmission — now documented in PRD §2.1.3~~ |
| ~~9~~ | ~~**Snapshot / restore**~~ | ~~`SnapshotService.java`, `RefreshableDataSource.java`~~ | ~~SQLite database snapshot, list, and restore for safe experimentation — now documented in PRD §2.1.5~~ |
| 10 | **Project manifest validation** | `ManifestLoader.java`, `ManifestValidator.java`, `ValidateCommand.java` | YAML project manifest validation with field checks and warnings — not in PRD |
| ~~11~~ | ~~**Exclude filtering**~~ | ~~`ExcludeFilter.java`~~ | ~~File/pattern exclusion during scan (e.g., `target/`, `.git/`, custom patterns) — now documented in PRD §2.1.3~~ |
| 12 | **Suggestion system** | `SuggestionService.java`, `CommandSuggestionAspect.java` | Next-action suggestions to guide user workflow based on pipeline state |
| 13 | **Extended CLI commands** | `infrastructure/cli/command/` (11 commands) | PRD only describes `scan`. Implementation has: `plan`, `enrich`, `extract`, `generate`, `run`, `clean`, `status`, `validate`, `snapshot`, `task` |
| 14 | **Orphan recovery** | `OrphanRecovery.java` | Automatic recovery of stuck enrichment tasks (e.g., tasks stuck in `ENRICHING` state) |

---

## C. PRD Descriptions That Match Implementation (no gap)

- Two-pass deterministic linker architecture (Pass 1 declaration collection → Pass 2 resolution analysis)
- All 9 HTTP client detectors: RestTemplate, WebClient, FeignClient, RestClient, @HttpExchange, java.net.HttpClient, HttpURLConnection, Apache HttpClient, OkHttp
- All 8 DB access detectors: JdbcTemplate, HibernateSession, EntityManager, SpringDataJpa, @Procedure, @Transactional, NamedQuery, RawJdbc
- View-returning controller detection with `servesView` / `viewName` in `SpringEndpointDetector`
- `@RestController` / `@ResponseBody` exclusion from view detection
- Template file parsing (JSP + Thymeleaf) via `TemplateParser` SPI
- Template-to-endpoint floating link resolution with confidence scoring (1.0 exact, 0.8 path-parameterized, ≥0.5 threshold)
- `EndpointDetector` SPI with `SpringEndpointDetector` + `ServletEndpointDetector` implementations
- Event broker detection (Kafka `@KafkaListener`/`KafkaTemplate`, RabbitMQ `@RabbitListener`/`RabbitTemplate`, ActiveMQ `@JmsListener`/`JmsTemplate`)
- Topic link resolution (producer→consumer matching)
- `CallGraphVisitor` resolving against `DeclarationRegistry` from Pass 1
- Overloaded method resolution with `AMBIGUOUS` classification
- `@EventListener` body call chain capture (up to 3 levels)
- Pure-JavaParser AST with `LanguageLevel` per file via `JavaVersionMapper`; no `CombinedTypeSolver`
- Annotation-first component classification with structural fallback
- `web.xml` DOM parsing via `WebXmlAnalyzer`
- SQLite as canonical index store (WAL mode, `busy_timeout=5000`)
- No ORM — Spring JDBC (`JdbcTemplate`) with HikariCP
- Deterministic SHA-256 task IDs
- Phase 2 enrichment is optional — `INDEXED` tasks proceed directly to Phase 3
- In-memory secret redaction (files on disk never modified)
- Single-file failures never block full scan
- CLI is a Spring Shell app with `spring.main.web-application-type=none`
- Quality audit with manifest schema enforcement (typed POJOs + `@JsonNaming` + quarantined flow `review_required` tagging)
- Phase 3 Embabel GOAP agent architecture (6 actions, guardrails, priority scoring, sub-chain caching, progressive disclosure per §2.2.1)
- Interactive Mode US057/F028 — `UserInteractionService` SPI (ask/confirm/select), `NoOpUserInteractionService` default, deferred `InteractiveUserInteractionService`, `AmbiguityGap` audit trail, `user_responses` SQLite table, `run --interactive` CLI flag (§2.2.2)

---

## D. Recommended Actions

1. **Implement `SimpleJdbcCallDetector`** — new `@Component implements DbAccessDetector`, following the existing detector pattern. Covers `SimpleJdbcCall.execute()`, `executeFunction()`, `executeObject()`.
2. **Fix `HibernateSessionDetector.SESSION_METHODS`** — add `"createNamedQuery"` to the list so it's not silently skipped.
3. ~~**Write unit tests for:**~~
   ~~- `JspTemplateParser`~~
   ~~- `WebXmlAnalyzer`~~
   ~~- `TemplateLinkResolver`~~
   ~~- `ServletEndpointDetector`~~
   ~~(All four now covered — 63 tests total. Bugfix applied in `ServletEndpointDetector`: `extractServletPaths` returned immutable `List.of()`, causing `UnsupportedOperationException` when no `@WebServlet` annotation was present.)~~
4. **Consider updating the PRD** to document the additional features (section B) that have accrued since v5.6 was written — particularly the XML bean analysis, event listener detection, test mining, ~~secret redaction,~~ ~~snapshot/restore,~~ ~~exclude filtering,~~ ~~Phase 2 enrichment qualification rules~~, and extended CLI. ~~(Secret redaction and exclude filtering now covered in v5.10 §2.1.3; snapshot/restore in §2.1.5; quality audit in §2.3; Embabel agent architecture + Interactive Mode in §2.2; Phase 2 now in §2.1.6)~~
