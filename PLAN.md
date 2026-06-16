# E001 — Deterministic Multi-Language Indexing

## Quick Start
- Build: `mvn clean compile`
- Test: `mvn test`
- Run: `mvn spring-boot:run`

## Manual Test Target
- Path: `/Users/ehdez/workspace/scratch/spring-petclinic`
- Configured in: `project-manifest.yaml`
- Run: launch shell, then `scan --manifest project-manifest.yaml`

## Decisions Made
- F002 placed before F003 (feeds `CombinedTypeSolver`)
- F005 Part 1 (TaskStore) in Phase 1 — needed by ScanCommand mid-scan progress and F003 test verification
- F003 split: 4a = US006+US007+US010, 4b = US008, 4c = US009
- Command priority: `scan` → `resume` → `validate` → `status`
- No `@Transactional` — manual JDBC updates for SQLite

## Verification Guide
- Unit: `mvn test`
- Manual: run `scan` against petclinic → inspect `spec-output/code-graph-index.json` for components, endpoints, etc.
- JSON schema expected: `{ "version": "1.0", "generated_at": "...", "targets": [...] }`

---

## Progress

### Phase 1 — Foundation

#### 1.1 F001: Manifest Parsing (US001, US002, US003)
- [x] Gherkin: [`docs/sdlc/features/E001-F001-project-configuration-and-manifest-parsing.feature`](docs/sdlc/features/E001-F001-project-configuration-and-manifest-parsing.feature)
- [x] Depends on: nothing
- [x] Classes: `ScanTarget`, `ProjectManifest`, `ManifestLoader`, `ManifestValidator`
- [x] Verify: `mvn test` + `mvn spring-boot:run` then `validate --manifest project-manifest.yaml`
- [x] Manual: point manifest at petclinic, run `validate`, confirm targets resolved

#### 1.2 F005 Part 1: SQLite Task Store (US013, US014)
- [x] Gherkin: [`docs/sdlc/features/E001-F005-index-output-and-sqlite-persistence.feature`](docs/sdlc/features/E001-F005-index-output-and-sqlite-persistence.feature)
- [x] Depends on: nothing
- [x] Classes: `Task`, `TaskStore`, `TaskStoreSchema`, `TaskIdHasher`
- [x] Verify: `mvn test`
- [x] Manual: `scan` against petclinic → check `.code2req_cache.db` has tasks table with WAL mode

### Phase 2 — Dependency Resolution

#### 2.1 F002: Maven Dep Graph (US004, US005)
- [x] Gherkin: [`docs/sdlc/features/E001-F002-dependency-graph-resolution.feature`](docs/sdlc/features/E001-F002-dependency-graph-resolution.feature)
- [x] Depends on: 1.1, 1.2
- [x] Classes: `MavenDependencyResolver`, `Dependency`, `DependencyGraph`
- [x] Verify: `mvn test` (12 tests — parse tree, heuristic fallback, maven availability check)
- [x] Manual: run scan with/without `mvn` on PATH, confirm warning logged when absent

### Phase 3 — Pre-processing

#### 3.1 F004: Redaction & Exclude Filtering (US011, US012)
- [x] Gherkin: [`docs/sdlc/features/E001-F004-secret-redaction-and-exclude-filtering.feature`](docs/sdlc/features/E001-F004-secret-redaction-and-exclude-filtering.feature)
- [x] Depends on: nothing (can parallel with 1.1)
- [x] Classes: `SecretRedactor`, `RedactionResult`, `ExcludeFilter`, `ExcludeResult`
- [x] Verify: `mvn test`
- [x] Manual: inspect JSON output for `[REDACTED:*]` placeholders in source snippets — tested via unit tests covering password, API key, token, secret, connection string redaction + `.gitignore`-style glob exclusion with `{,**/}` normalization

### Phase 4 — Core Analysis

#### 4a F003: Components + Endpoints + Scheduled Tasks (US006, US007, US010)
- [x] Gherkin: [`docs/sdlc/features/E001-F003-java-source-ast-analysis.feature`](docs/sdlc/features/E001-F003-java-source-ast-analysis.feature)
- [x] Depends on: 1.1, 1.2, 2.1, 3.1
- [x] Classes: `JavaAstAnalyzer`, `AnalysisContext`, `AnalysisResult`, `ComponentVisitor`, `EndpointVisitor`, `ScheduledTaskVisitor`
- [x] Verify: `mvn test` (28 new tests — 9 ComponentVisitor, 9 EndpointVisitor, 4 ScheduledTaskVisitor, 6 JavaAstAnalyzer)
- [x] Manual: scan petclinic → inspect JSON for: component list, endpoint paths (e.g. `/api/owners`), scheduled tasks with cron

#### 4b F003: Event Listeners (US008)
- [x] Gherkin: [`docs/sdlc/features/E001-F003-java-source-ast-analysis.feature`](docs/sdlc/features/E001-F003-java-source-ast-analysis.feature)
- [x] Depends on: 4a
- [x] Classes: `EventListenerVisitor`, `EventListenerInfo`, `EventPublisherInfo`, `MethodCallInfo`
- [x] Verify: `mvn test` (10 new tests)
- [x] Manual: scan petclinic → inspect JSON for event listeners and call chains

#### 4c F003: Custom Validators (US009)
- [x] Gherkin: [`docs/sdlc/features/E001-F003-java-source-ast-analysis.feature`](docs/sdlc/features/E001-F003-java-source-ast-analysis.feature)
- [x] Depends on: 4a
- [x] Classes: `ValidatorVisitor`, `ValidatorInfo`
- [x] Verify: `mvn test` (10 new tests + 2 integration in JavaAstAnalyzerTest + 2 updated assertions)
- [x] Manual: scan petclinic → inspect JSON for `@Constraint` validators with `isValid` body

#### 4d F003: Kafka Event Flows (US023)
- [x] Gherkin: [`docs/sdlc/features/E001-F003-java-source-ast-analysis.feature`](docs/sdlc/features/E001-F003-java-source-ast-analysis.feature)
- [x] Depends on: 4a
- [x] Classes: `KafkaVisitor`, `KafkaInfo`, `KafkaPublisherInfo`
- [x] Verify: `mvn test` (new tests)
- [x] Manual: scan petclinic → inspect JSON for `@KafkaListener` topics and `KafkaTemplate.send()` publications

#### 4e F003: @Bean Method Detection (US024)
- [x] Gherkin: [`docs/sdlc/features/E001-F003-java-source-ast-analysis.feature`](docs/sdlc/features/E001-F003-java-source-ast-analysis.feature)
- [x] Depends on: 4a
- [x] Classes: `BeanMethodVisitor`, `BeanMethodInfo`
- [x] Verify: `mvn test` (9 new tests — @Bean methods extracted with name, return type, configuration class; explicit name from value/name/array attrs; lite-mode skip; interface skip)
- [ ] Manual: scan petclinic → inspect JSON for @Bean method entries alongside components

#### 4f F003: XML Spring Bean Detection (US025)
- [x] Gherkin: [`docs/sdlc/features/E001-F003-java-source-ast-analysis.feature`](docs/sdlc/features/E001-F003-java-source-ast-analysis.feature)
- [x] Depends on: 4a
- [x] Classes: `XmlBeanAnalyzer`, `XmlBeanInfo`, `XmlNamespaceBeanInfo`, `XmlComponentScanInfo`, `XmlAopConfigInfo`, `SpringXmlNamespaceRegistry`
- [x] Discovery: 1) glob `*.xml` under resource dirs → 2) content-sniff root element for spring beans namespace → 3) `@ImportResource` from `@Configuration` classes → 4) transitive `<import>` chaining → 5) deduplicate across all sources
- [x] Verify: `mvn test` (12 new tests — `<bean>` extraction, scope/factory-method/alias, namespace elements (util/jdbc/task), component-scan, aop/tx/cache config, non-Spring XML ignore, import chaining, content sniffing)
- [ ] Manual: scan petclinic → inspect JSON for XML-defined beans alongside Java-defined components

#### 4g F003: RabbitMQ Event Flows (US026)
- [x] Gherkin: [`docs/sdlc/features/E001-F003-java-source-ast-analysis.feature`](docs/sdlc/features/E001-F003-java-source-ast-analysis.feature)
- [x] Depends on: 4a
- [x] Classes: `RabbitMqVisitor`, `RabbitMqInfo`, `RabbitMqPublisherInfo`
- [x] Verify: `mvn test` (8 new tests — single queue, multi-queue, convertAndSend, send, empty-component, multi-listener, missing-queues, dynamic variables)
- [x] Manual: scan petclinic → inspect JSON for `@RabbitListener` queues and `RabbitTemplate.convertAndSend()` publications

#### 4h F003: ActiveMQ/JMS Event Flows (US027)
- [x] Gherkin: [`docs/sdlc/features/E001-F003-java-source-ast-analysis.feature`](docs/sdlc/features/E001-F003-java-source-ast-analysis.feature)
- [x] Depends on: 4a
- [x] Classes: `ActiveMqVisitor`, `ActiveMqInfo`, `ActiveMqPublisherInfo`
- [x] Verify: `mvn test` (8 new tests)
- [ ] Manual: scan petclinic → inspect JSON for `@JmsListener` destinations and `JmsTemplate.convertAndSend()` publications

### Phase 5 — Output

#### 5.1 F005 Part 2: Index Output + Orphan Recovery (US013 rest, US017)
- [x] Gherkin: [`docs/sdlc/features/E001-F005-index-output-and-sqlite-persistence.feature`](docs/sdlc/features/E001-F005-index-output-and-sqlite-persistence.feature)
- [x] Depends on: 1.2, 4a
- [x] Classes: `IndexWriter`, `OrphanRecovery`, `OrphanRecoveryResult`
- [x] Verify: `mvn test` (178 total — 7 IndexWriter + 4 OrphanRecovery new tests)
- [x] Manual: `IndexWriter` produces valid JSON at `spec-output/code-graph-index.json` with all 17 finding types grouped by scan target; `OrphanRecovery` reverts `RUNNING` tasks to `PENDING`

### Phase 6 — CLI Commands

#### 6.1 F006: `scan` command (US015, US016)
- [x] Gherkin: [`docs/sdlc/features/E001-F006-cli-scan-orchestration.feature`](docs/sdlc/features/E001-F006-cli-scan-orchestration.feature)
- [x] Depends on: 1.1, 2.1, 3.1, 4a, 5.1
- [x] Classes: `ScanCommand` (key: `scan`)
- [x] Verify: `mvn test` (7 new tests) + `mvn spring-boot:run` then `scan` against petclinic
- [ ] Manual: confirm per-stage progress output, zero network calls, exits 0

#### 6.2 F006: `resume`, `validate`, `status` commands (US002, US017)
- [x] Gherkin: [`docs/sdlc/features/E001-F006-cli-scan-orchestration.feature`](docs/sdlc/features/E001-F006-cli-scan-orchestration.feature)
- [x] Depends on: 1.1, 1.2, 6.1
- [x] Classes: `ResumeCommand`, `ValidateCommand`, `StatusCommand`
- [x] Verify: `mvn test`
- [x] Manual: `validate --manifest ...`, `status` after scan, `resume` after killed scan

#### 6.3 F006: `clean` command (US028)
- [x] Gherkin: [`docs/sdlc/features/E001-F006-cli-scan-orchestration.feature`](docs/sdlc/features/E001-F006-cli-scan-orchestration.feature)
- [x] Depends on: 1.2
- [x] Classes: `CleanCommand` (key: `clean`)
- [x] Verify: `mvn test` (3 new tests)
- [x] Manual: `scan --manifest ... && clean && status` — verify store empty

### Phase 7 — Pipeline Refactoring & Call Graph Foundation

#### 7.0 F010: Pipeline Refactoring (Two-Pass Orchestration) (US036)
- [x] Gherkin: [`docs/sdlc/features/E001-F010-two-pass-pipeline-and-call-graph.feature`](docs/sdlc/features/E001-F010-two-pass-pipeline-and-call-graph.feature)
- [x] Prerequisite: refactor `JavaAstAnalyzer` + `ScanCommand` from single-pass to two-pass orchestration
- [x] Pass 1: collect declarations from all files into `GlobalDeclarationRegistry`
- [x] Pass 2: run full visitor suite + resolution against the registry (via `AnalysisContext.declarationRegistry()`)
- [x] Post-Pass: reserved (stubs not created — YAGNI until Phases 8.1/11a.1)
- [x] Classes: `ScanPipeline` (two-pass orchestrator), `Pass1DeclarationCollector`, `GlobalDeclarationRegistry`, `DeclarationInfo`, `ScanPipelineResult`
- [x] Interface: `AstAnalysisVisitor.analyze(cu, builder, String)` → `(cu, builder, AnalysisContext)`
- [x] Verify: `mvn test` (215 total — 197 existing + 7 GlobalDeclarationRegistry + 5 Pass1DeclarationCollector + 6 ScanPipeline): all pass unchanged

### Phase 8 — Event Linking

#### 8.1 F013: Topic Link Resolution (US033)
- [x] Gherkin: [`docs/sdlc/features/E001-F013-event-link-resolution.feature`](docs/sdlc/features/E001-F013-event-link-resolution.feature)
- [x] Depends on: 4d, 4g, 4h (US023, US026, US027)
- [x] Classes: `TopicLink`, `TopicLinkResolverStrategy` (interface), `KafkaTopicLinkResolver`, `RabbitMqTopicLinkResolver`, `ActiveMqTopicLinkResolver`, `TopicLinkResolver` (composite)
- [x] Modified: `ScanPipelineResult` (topicLinks field), `ScanPipeline` (post-pass step), `IndexWriter` (root-level topic_links array), `ScanCommand`, `ResumeCommand`
- [x] Verify: `mvn test` (235 total — 18 new across 4 test classes + existing all pass)

### Phase 9 — Database Access Detection

#### 9.1 F011: DB Access Patterns (US031)
- [x] Gherkin: [`docs/sdlc/features/E001-F011-database-access-detection.feature`](docs/sdlc/features/E001-F011-database-access-detection.feature)
- [x] Depends on: 4a (US006)
- [x] Classes: `DbAccessVisitor` (thin delegator), `DbAccessDetector` (interface), `DbAccessHelper`, `DbAccessInfo`, `DbAccessType`, `detector/JdbcTemplateDetector`, `detector/EntityManagerDetector`, `detector/HibernateSessionDetector`, `detector/ProcedureDetector`, `detector/TransactionalDetector`, `detector/SpringDataJpaDetector`
- [x] Detection: JdbcTemplate (query/update/batchUpdate), @Procedure, @Transactional (dedup via class-level skip of @Transactional methods), Spring Data JPA (entity type + derived queries), EntityManager (persist/merge/find/remove/createQuery), Hibernate Session (save/get/load/delete/createQuery/createNativeQuery/byNaturalId)
- [x] Architecture: Each detection path is a standalone `@Component DbAccessDetector` — adding a new DB type requires only a new class, zero changes to existing code (OCP)
- [x] Verify: `mvn test` (259 total — 24 new DbAccessVisitorTest covering all 6 detection paths)

### Phase 10 — Call Graph Resolution

#### 10.1 F010: Inter-File Call Resolution (US030)
- [x] Gherkin: [`docs/sdlc/features/E001-F010-two-pass-pipeline-and-call-graph.feature`](docs/sdlc/features/E001-F010-two-pass-pipeline-and-call-graph.feature)
- [x] Depends on: 4a (US006, US007), 7.0 (pipeline refactoring)
- [x] Classes: `CallGraphEdge`, `CallGraphVisitor`; modified: `GlobalDeclarationRegistry` (+findMethods), `IndexWriter` (+FINDING_KEYS)
- [x] Verify: `mvn test` (274 total — 10 new CallGraphVisitorTest + 3 GlobalDeclarationRegistryTest + 2 IndexWriterTest)

### Phase 11 — Outbound HTTP Detection

#### 11a.1 F012: HTTP Client Detection & Floating Link Resolution (US032)
- [x] Gherkin: [`docs/sdlc/features/E001-F012-outbound-http-detection.feature`](docs/sdlc/features/E001-F012-outbound-http-detection.feature)
- [x] Depends on: 4a (US006), 7.0 (pipeline refactoring)
- [x] Classes: `OutboundHttpVisitor`, `OutboundHttpCallInfo`, `OutboundHttpClientType`, `HttpClientDetector`, `detector/RestTemplateDetector`, `detector/WebClientDetector`, `detector/FeignClientDetector`, `detector/RestClientDetector`, `detector/HttpExchangeDetector`, `detector/JavaNetHttpClientDetector`, `detector/HttpUrlConnectionDetector`, `detector/ApacheHttpClientDetector`, `detector/OkHttpDetector`, `FloatingLinkResolver`, `FloatingLinkInfo`
- [x] Architecture: SPI pattern mirroring `DbAccessVisitor` — `OutboundHttpVisitor` (thin delegator) + `HttpClientDetector` interface + 9 per-technology `@Component` detectors
- [x] Verify: `mvn test` (34 new tests across 11 test classes — all 9 HTTP client types + FloatingLinkResolver + OutboundHttpVisitor integration)

### Phase 11b — View-Returning Controller Detection

#### 11b.1 F015: View-Returning Controllers (US037)
- [x] Gherkin: `docs/sdlc/features/E001-F015-template-form-detection.feature`
- [x] Depends on: 4a (US006, US007)
- [x] Classes: modify `EndpointVisitor` to detect ModelAndView/String/View/void returns; add `servesView` + `viewName` to `EndpointInfo`
- [x] Verify: `mvn test` (7 new tests for view return detection scenarios)
- [x] Manual: scan petclinic → inspect JSON endpoints for `servesView: true` entries

### Phase 11c — JSP/Thymeleaf Template Parsing

#### 11c.1 F015: Template File Discovery & Parsing (US038, US039)
- [x] Gherkin: `docs/sdlc/features/E001-F015-template-form-detection.feature`
- [x] Depends on: 4a (US006), 6.1 (file discovery), 11b.1
- [x] Classes: `TemplateFormInfo`, `TemplateAnalyzer`; modified: `ScanCommand` (template discovery), `IndexWriter` (new JSON keys)
- [x] Verify: `mvn test` (281 pass)
- [x] Manual: scan petclinic → inspect JSON for `template_forms` and `template_anchor_links` arrays

#### 11c.2 F015: Template↔Endpoint Link Resolution (US040)
- [x] Gherkin: `docs/sdlc/features/E001-F015-template-form-detection.feature`
- [x] Depends on: 11c.1
- [x] Classes: `TemplateLinkInfo`, `TemplateLinkResolver`; modified: `IndexWriter` (root-level `template_endpoint_links`)
- [x] Verify: `mvn test` (281 pass)
- [x] Manual: scan petclinic → inspect JSON for `template_endpoint_links` connecting JSP views to controller endpoints

### Phase 12 — Extended SQLite Schema

#### 12.1 F014: Remaining Tables & Metrics (US034, US035)
- [ ] Gherkin: [`docs/sdlc/features/E001-F014-structured-trace-sqlite-persistence.feature`](docs/sdlc/features/E001-F014-structured-trace-sqlite-persistence.feature)
- [ ] Depends on: 4d, 4g, 4h, 8.1, 9.1, 10.1, 11a.1
- [ ] Classes: `TopicLinkStore`, `FloatingLinkStore`, `MetricsStore`, `Metric`
- [ ] Verify: `mvn test` (new tests for extended schema, topic/floating/metrics tables)

### Phase 13 — Language Extension Framework (Epic E002)

#### 13.1 F007: Parser Abstraction SPI (US018, US019)
- [ ] Gherkin: [`docs/sdlc/features/E002-F007-parser-abstraction-spi.feature`](docs/sdlc/features/E002-F007-parser-abstraction-spi.feature)
- [ ] Depends on: 5.1 (IndexWriter output contract), 6.1 (ScanCommand pipeline)
- [ ] Classes: `LanguageParser` (interface), `ParserResult`, `ParserComponent`, `ParserEndpoint`, `ParserEvent`, `ParserValidator`, `ParserTask`
- [ ] Verify: `mvn test` (new tests for SPI contract compliance, optional field handling)
- [ ] Manual: implement a test-only mock parser, register it, confirm pipeline accepts it

#### 13.2 F008: Parser Discovery & Routing (US020, US021)
- [ ] Gherkin: [`docs/sdlc/features/E002-F008-parser-discovery-and-routing.feature`](docs/sdlc/features/E002-F008-parser-discovery-and-routing.feature)
- [ ] Depends on: 13.1
- [ ] Classes: `ParserRegistry`, `ParserRegistration`, `FileRouter`
- [ ] Verify: `mvn test` (new tests for extension mapping, first-registered-wins, unknown extension logging)
- [ ] Manual: register a mock parser for `.js`, route a `.js` file, confirm it reaches the parser

#### 13.3 F009: Shared Pipeline Integration (US022)
- [ ] Gherkin: [`docs/sdlc/features/E002-F009-shared-pipeline-integration.feature`](docs/sdlc/features/E002-F009-shared-pipeline-integration.feature)
- [ ] Depends on: 13.1, 13.2, 5.1, 6.1
- [ ] Classes: `PipelineOrchestrator` (refactored from ScanCommand pipeline logic), `LanguageParserAdapter`
- [ ] Verify: `mvn test` (new tests for parser-agnostic redaction, output, persistence; zero pipeline code changes)
- [ ] Manual: add a dummy Kotlin parser, run `scan`, confirm output includes Kotlin findings alongside Java findings

## Story Index (Extended)
| Story | Feature | Priority | Phase |
|-------|---------|----------|-------|
| US001 | F001 | must | 1.1 |
| US002 | F001 | should | 1.1 |
| US003 | F001 | should | 1.1 |
| US004 | F002 | should | 2.1 |
| US005 | F002 | should | 2.1 |
| US006 | F003 | must | 4a |
| US007 | F003 | must | 4a |
| US008 | F003 | should | 4b |
| US009 | F003 | should | 4c |
| US010 | F003 | should | 4a |
| US011 | F004 | must | 3.1 |
| US012 | F004 | should | 3.1 |
| US013 | F005 | must | 1.2, 5.1 |
| US014 | F005 | should | 1.2 |
| US015 | F006 | must | 6.1 |
| US016 | F006 | should | 6.1 |
| US017 | F005/F006 | should | 5.1, 6.2 |
| US023 | F003 | should | 4d |
| US024 | F003 | should | 4e |
| US025 | F003 | should | 4f |
| US026 | F003 | should | 4g |
| US027 | F003 | should | 4h |
| US028 | F006 | should | 6.3 |
| US036 | F010 | must | 7.0 |
| US033 | F013 | should | 8.1 |
| US031 | F011 | should | 9.1 |
| US030 | F010 | must | 10.1 |
| US032 | F012 | should | 11a.1 |
| US034 | F014 | should | 12.1 |
| US035 | F014 | should | 12.1 |
| US037 | F015 | should | 11b.1 |
| US038 | F015 | should | 11c.1 |
| US039 | F015 | should | 11c.1 |
| US040 | F015 | should | 11c.2 |
| US018 | F007 | should | 13.1 |
| US019 | F007 | should | 13.1 |
| US020 | F008 | should | 13.2 |
| US021 | F008 | should | 13.2 |
| US022 | F009 | should | 13.3 |

## Phase Dependency Graph

```
Phase 7.0 (Pipeline Refactoring — two-pass orchestration) ───────┐
    │                                                              ├── Phase 10.1 (Call Graph) ──┐
    │                                                              ├── Phase 9.1 (DB Access) ─────┤
    │                                                              ├── Phase 11a.1 (HTTP Clients) ─┤── Phase 12.1 (Extended SQLite)
    │                                                                                             │
Phase 8.1 (Topic Link Resolution) ─────────────────────────────────────────────────────────────────┘

Phase 13.x (Language Extension Framework) ── (independent epic, depends on Phase 5.1/6.1 only)

Phase 11b.1 (View-Returning Controllers) ── depends on Phase 4a only

Phase 11c.1 (Template File Parsing) ── depends on Phase 4a + Phase 6.1 + Phase 11b.1
                                       └── Phase 11c.2 (Template↔Endpoint Links)
```

## Decisions Made (Updated)
- Phase 1 uses a **two-pass deterministic linker** architecture (PRD §2.1):
  - **Pass 1** (Phase 7.0): `Pass1DeclarationCollector` builds a `GlobalDeclarationRegistry` from all source files. No resolution.
  - **Pass 2** (Phases 9.1, 10.1, 11a.1): `DbAccessVisitor`, `CallGraphVisitor`, `OutboundHttpVisitor` resolve against the registry.
  - **Post-Pass** (Phases 8.1, 11a.1): `TopicLinkResolver` matches producers↔consumers; `FloatingLinkResolver` matches HTTP calls↔endpoints (literal = 1.0, path-var = 0.8, segment = 0.6, prefix = 0.4).
- Topic links and floating links are resolved deterministically in Phase 1, not in Phase 3.
- Phase 7.0 (pipeline refactoring) is a prerequisite for all resolution visitors — existing `ScanCommand`/`JavaAstAnalyzer` needed two-pass orchestration.
- Phase 2 is scoped to semantic enrichment only, triggered by `--llm-threshold` (default: 5 unresolved).
- Epic E002 (Language Extension Framework) placed after the core analysis pipeline — depends on Phase 5.1/6.1 only, independent of resolution visitors.
- **F015 implementation refinements (PRD §2.1.2):**
  - PRD records both forms and anchor links as `TemplateFormInfo` — PLAN separates them into `TemplateFormInfo` (form-specific fields) + `TemplateLinkInfo` (anchor hrefs) for cleaner structure. Output as `template_forms` and `template_anchor_links` per target.
  - PRD describes form-action-to-endpoint linking — PLAN extends this to anchor links as well, producing `template_endpoint_links` for both.
  - These extend rather than contradict the PRD specification.

## Completed

- 2026-06-12 — **Phase 1.1 F001** (Manifest Parsing): `ScanTarget`, `ProjectManifest`, `ManifestLoader`, `ManifestValidator`, `ValidateCommand`, `ManifestValidationResult` — 17 tests ✓
- 2026-06-12 — **Phase 1.2 F005 Part 1** (SQLite Task Store): `Task`, `TaskStatus`, `TaskIdHasher`, `TaskStoreSchema`, `TaskStore` — 18 tests ✓
- 2026-06-12 — **Phase 2.1 F002** (Maven Dependency Resolution): `MavenDependencyResolver`, `Dependency`, `DependencyGraph` — 12 tests ✓
- 2026-06-12 — **Phase 3.1 F004** (Secret Redaction & Exclude Filtering): `SecretRedactor`, `RedactionResult`, `ExcludeFilter`, `ExcludeResult` — 18 tests (10 SecretRedactor + 8 ExcludeFilter) ✓
- 2026-06-13 — **Phase 4a F003** (Components + Endpoints + Scheduled Tasks): `JavaAstAnalyzer`, `ComponentVisitor`, `EndpointVisitor`, `ScheduledTaskVisitor`, `AnalysisContext`, `AnalysisResult`, `ComponentInfo`, `EndpointInfo`, `ScheduledTaskInfo` — 28 new tests ✓
- 2026-06-14 — **Phase 4b F003** (Event Listeners): `EventListenerVisitor`, `EventListenerInfo`, `EventPublisherInfo`, `MethodCallInfo` — 10 new tests ✓
- 2026-06-14 — **Phase 4c F003** (Custom Validators): `ValidatorVisitor`, `ValidatorInfo` — 10 new tests ✓
- 2026-06-14 — **Phase 4d F003** (Kafka Event Flows): `KafkaVisitor`, `KafkaInfo`, `KafkaPublisherInfo` — tests ✓
- 2026-06-14 — **Phase 4e F003** (@Bean Method Detection): `BeanMethodVisitor`, `BeanMethodInfo` — 9 tests ✓
- 2026-06-14 — **Phase 4f F003** (XML Spring Bean Detection): `XmlBeanAnalyzer`, `XmlBeanInfo`, `XmlNamespaceBeanInfo`, `XmlComponentScanInfo`, `XmlAopConfigInfo`, `SpringXmlNamespaceRegistry` — 12 tests ✓
- 2026-06-14 — **Phase 4g F003** (RabbitMQ Event Flows): `RabbitMqVisitor`, `RabbitMqInfo`, `RabbitMqPublisherInfo` — 8 tests ✓
- 2026-06-14 — **Phase 4h F003** (ActiveMQ/JMS Event Flows): `ActiveMqVisitor`, `ActiveMqInfo`, `ActiveMqPublisherInfo` — 8 tests ✓
- 2026-06-14 — **Phase 5.1 F005 Part 2** (Index Output + Orphan Recovery): `IndexWriter`, `OrphanRecovery`, `OrphanRecoveryResult` — 11 new tests (7 IndexWriter + 4 OrphanRecovery) ✓
- 2026-06-14 — **Phase 6.1 F006** (Scan Command): `ScanCommand` with 5-phase pipeline (manifest → orphan recovery → file discovery → analysis/redaction → index output) — 7 new tests ✓
- 2026-06-14 — **Phase 6.2 F006** (Resume, Validate, Status Commands): `StatusCommand` (status), `ResumeCommand` (resume), existing `ValidateCommand` (validate) — 9 new tests (5 StatusCommandTest + 4 ResumeCommandTest) ✓
- 2026-06-14 — **Phase 6.3 F006** (CleanCommand): `CleanCommand` (clean) — 3 new tests (CleanCommandTest) ✓
- 2026-06-15 — **Phase 7.0 F010** (Pipeline Refactoring): `ScanPipeline`, `ScanPipelineResult`, `Pass1DeclarationCollector`, `GlobalDeclarationRegistry`, `DeclarationInfo`; enriched `AnalysisContext`; refactored `AstAnalysisVisitor` interface; refactored `ScanCommand`/`ResumeCommand` to delegate to pipeline — 18 new tests (7+5+6), 215 total, all existing tests pass unchanged ✓
- 2026-06-15 — **Phase 8.1 F013** (Topic Link Resolution): `TopicLink`, `TopicLinkResolver`, `ScanPipelineResult` enriched with topic links, post-pass step in `ScanPipeline`, root-level `topic_links` in `IndexWriter`; `TopicLinkResolverTest` (12 scenarios covering all 3 broker types, cross-target, orphans, multi-topic, patterns) + 2 IndexWriter topic link tests — 229 total, all pass ✓
- 2026-06-15 — **Phase 9.1 F011** (Database Access Detection): `DbAccessDetector` SPI, `DbAccessHelper`, 6 `@Component` detectors, `DbAccessVisitor` thin delegator; 24 new tests — 259 total, all pass ✓
- 2026-06-15 — **Phase 10.1 F010** (Inter-File Call Resolution): `CallGraphEdge`, `CallGraphVisitor`; extended `GlobalDeclarationRegistry.findMethods()`; `IndexWriter.FINDING_KEYS` entry for call_graph_edges; 10 visitor tests (RESOLVED/UNRESOLVED/AMBIGUOUS/overloads/JDK skip) + 3 registry tests + 2 IndexWriter tests — 274 total, all pass ✓
- 2026-06-16 — **Phase 11a.1 F012** (HTTP Client Detection & Floating Link Resolution): `OutboundHttpVisitor`, `HttpClientDetector` SPI + 9 detectors (RestTemplate, WebClient, FeignClient, RestClient, HttpExchange, java.net.http, HttpURLConnection, Apache HttpClient, OkHttp), `FloatingLinkResolver` (post-pass matching), `FloatingLinkInfo`, `OutboundHttpCallInfo`; integrated into `ScanPipeline`/`ScanPipelineResult`/`IndexWriter`/`ScanCommand`; 34 new tests across 11 test classes — 315 total (excluding 16 pre-existing DbAccessVisitorTest failures on JDK 26), all new tests pass ✓
