# Gap Analysis Report — code2req

**Date:** 2026-07-17
**Scope:** PRD v5.6, User Stories (63), Gherkin Features (29), ADRs (6), Source Code (162 main sources + 16 tests)

---

## 1. Executive Summary

### Overall Alignment Assessment: MODERATE (B)

The code2req project exhibits strong traceability between documentation and implementation for the **Phase 1 (Indexing) and Phase 4 (Generation)** domains. However, **Phase 2 (Enrichment) has notable implementation gaps**, **Phase 3 (Extraction) has deferred features** documented but not built, and the entire **E002 (Language Extension Framework) epic is fully documented but has zero implementation**.

### Major Risks

| Risk | Impact |
|------|--------|
| **E002 completely unimplemented** (8 documented items) | Misalignment between documented capabilities and actual product — 5 user stories, 3 feature files describe a feature that doesn't exist |
| **Phase 3 Interactive Mode unimplemented** | US057 interactive user prompts for agent ambiguity resolution are documented but deferred — currently headless-only |
| **Review Command unimplemented** | US055 describes a CLI command to inspect/resolve `AWAITING_HUMAN_REVIEW` tasks — no code exists |
| ~~**XML Bean Analysis undocumented**~~ | ~~66 code references, 5 model records, 1 full analyzer, 1 test file — zero documentation coverage~~ |
| ~~**`@Bean` Method Detection undocumented**~~ | ~~`BeanMethodVisitor`, `BeanMethodInfo`, 1 full test — zero documentation coverage~~ |

### Areas with Highest Divergence

1. ~~**Undocumented Implementation**: XML bean parsing (`XmlBeanAnalyzer`) and `@Bean` method detection have extensive code with no corresponding user stories, features, or PRD sections.~~
2. **Documented but Unimplemented**: E002 (Multi-Language) entire epic — 5 user stories, 3 feature files, zero code.
3. **Documented but Unimplemented (Deferred)**: Interactive mode (`InteractiveUserInteractionService`), Review command.

### Documentation Quality Assessment

- **PRD**: High quality — detailed, versioned, covers architecture comprehensively. Outdated w.r.t. ADR-006 (still refers to 3-phase pipeline, while code has 4-phase with `generate`).
- **User Stories**: Good coverage — 63 stories spanning all epics. Some story titles in the file header mismatch the file name (e.g., `US064` story header reads `US056`).
- **Gherkin Features**: Good coverage — 27 features covering all epics. All `@draft` tagged — none are marked `@final`.
- **ADRs**: Excellent — 6 records covering key decisions. ADR-006 is the most recent and accurately reflects the extract/generate split.

### Traceability Maturity Assessment: B+

- All 29 features cross-reference their user stories correctly.
- All 63 user stories are linked to features.
- ADRs reference related stories.
- PRD references are implicit (no formal trace IDs in PRD).

---

## 2. Traceability Matrix

| Requirement / Capability | PRD | User Story | Feature | ADR | Code Reference | Status |
|---|---|---|---|---|---|---|
| Manifest parsing & validation | §1, §2 | US001-003 | F001 | — | `ManifestLoader`, `ManifestValidator` | Correctly Implemented |
| Java AST component discovery | §2.1.1-2.1.2 | US006 | F003 | ADR-001 | `ComponentVisitor` | Correctly Implemented |
| HTTP endpoint mapping | §2.1.2 | US007 | F003 | ADR-001 | `SpringEndpointDetector`, `ServletEndpointDetector` | Correctly Implemented |
| Event listener tracing | §2.1.2 | US008 | F003 | ADR-001 | `EventListenerVisitor` | Correctly Implemented |
| Custom validation extraction | §2.1.2 | US009 | F003 | ADR-001 | `ValidatorVisitor` | Correctly Implemented |
| Scheduled task discovery | §2.1.2 | US010 | F003 | ADR-001 | `ScheduledTaskVisitor` | Correctly Implemented |
| Kafka event flows | §2.1.2 | US023 | F003 | ADR-001 | `KafkaVisitor`, `KafkaTopicLinkResolver` | Correctly Implemented |
| RabbitMQ event flows | §2.1.2 | US026 | F003 | ADR-001 | `RabbitMqVisitor`, `RabbitMqTopicLinkResolver` | Correctly Implemented |
| ActiveMQ event flows | §2.1.2 | US027 | F003 | ADR-001 | `ActiveMqVisitor`, `ActiveMqTopicLinkResolver` | Correctly Implemented |
| Servlet endpoints via web.xml | §2.1.2 | US058 | F003 | ADR-001 | `WebXmlAnalyzer` | Correctly Implemented |
| Secret redaction | §2.1.1 | US011 | F004 | ADR-005 | `SecretRedactor` | Correctly Implemented |
| Exclude filtering | — | US012 | F004 | — | `ExcludeFilter` | Correctly Implemented |
| Two-pass pipeline + call graph | §2.1 | US030, US036 | F010 | ADR-001 | `Pass1DeclarationCollector`, `GlobalDeclarationRegistry`, `CallGraphVisitor` | Correctly Implemented |
| Database access detection | §2.1.2 | US031, US059-061 | F011 | — | 8 `*Detector` classes + `DbAccessVisitor` | Correctly Implemented |
| Outbound HTTP detection | §2.1.2 | US032 | F012 | — | 9 `*Detector` classes + `OutboundHttpVisitor` | Correctly Implemented |
| Event link resolution | §2.1.2 | US033 | F013 | — | `TopicLinkResolver`, 3 broker resolvers | Correctly Implemented |
| SQLite persistence + crash recovery | §2.1.3 | US013, US014, US017 | F005, F014 | ADR-002, ADR-003, ADR-004 | `TaskStore`, `ExecutionFindingStore`, 3 more stores | Correctly Implemented |
| CLI scan orchestration | §2.1 | US015, US016, US028 | F006 | — | `ScanCommand` | Correctly Implemented |
| View-returning controller detection | §2.1.2 | US037, US062 | F015 | — | `SpringEndpointDetector` (servesView logic) | Correctly Implemented |
| JSP/Thymeleaf template parsing | §2.1.2 | US038, US039 | F015 | — | `JspTemplateParser`, `ThymeleafTemplateParser` | Correctly Implemented |
| Template-to-endpoint link resolution | §2.1.2 | US040 | F015 | ADR-001 | `TemplateLinkResolver` | Correctly Implemented |
| Planner (Phase 2 qualification) | §2 | US041, US042, US064 | F016 | — | 6 `QualificationRule` implementations | Correctly Implemented |
| LLM enrichment executor | §2 | US043, US044, US063 | F017 | — | `LlmEnrichmentService`, `SimulationStub` | Correctly Implemented |
| Enrichment orchestrator | §2 | US045 | F018 | — | `EnrichmentOrchestrator` | Correctly Implemented |
| Plan/run CLI commands | §2 | US046, US047 | F019 | — | `PlanCommand`, `RunCommand` | Correctly Implemented |
| Test suite mining | §2 | US048 | F020 | — | `TestFileMatcher`, `PairedExecutionResolver` | Correctly Implemented |
| Embabel agent setup | §2 | US049 | F021 | — | pom.xml + agent-config.yaml | Correctly Implemented |
| CodebaseKnowledge | §2 | US050 | F022 | — | `CodebaseKnowledge`, `ExtractionOrchestrator` | Correctly Implemented |
| Embabel agent extraction | §2 | US051 | F023 | ADR-006 | 6 agent actions + `FunctionalRequirementAgent` | Correctly Implemented |
| Quality audit | — | US052 | F024 | — | `SemanticManifestWriter` schema validation | Correctly Implemented |
| Snapshot/restore | — | US053, US054 | F025 | — | `SnapshotService`, `SnapshotCommand` | Correctly Implemented |
| Review command | — | US055 | F026 | — | **No code** | Not Implemented |
| Domain model + output writers | §2, §6 | US056 | F027 | ADR-006 | 17 manifest model records + `SynthesizeSpecAction` | Correctly Implemented |
| Interactive mode | — | US057 | F028 | — | **No `InteractiveUserInteractionService` code** | Documented but Not Implemented |
| Multi-language parser SPI | §2 (future) | US018-022 | F007-009 | — | **No `LanguageParser` interface or registry** | Documented but Not Implemented |
| XML bean analysis | §2.1.2 | US065-067 | F029 | — | `XmlBeanAnalyzer`, `ImportResourceVisitor`, 6 model records, test | Correctly Implemented |
| `@Bean` method detection | §2.1.2 | US068 | F030 | — | `BeanMethodVisitor`, `BeanMethodInfo`, test | Correctly Implemented |

---

## 3. Documentation Findings

### 3.1 PRD Requirements Missing from User Stories

| Requirement in PRD | PRD Reference | Impact |
|---|---|---|
| `JavaVersionMapper` maps `java-version` to `LanguageLevel` | §2.1.1 | Minor — implicit implementation detail not surfaced as user-facing capability |
| `@Query(value, nativeQuery=true/false)` on Spring Data methods | §2.1.2 | Minor — covered generically by US031 but nativeQuery distinction not explicit |
| `EntityManager.createNativeQuery`, `Session.createNativeQuery`, `Session.createSQLQuery` | §2.1.2 | Minor — covered by US031 EntityManager category, but specific method-level distinction not surfaced |
| `Statement.executeLargeUpdate`, `Statement.addBatch` | §2.1.2 | Minor — raw JDBC covered by US060 but these specific methods aren't enumerated |

### 3.2 PRD Requirements Missing from Features

Same as 3.1 — the PRD's technical specificity at the method-call level is not fully replicated in Gherkin scenarios, though these gaps are low-severity because the broader category (e.g., "raw JDBC") is covered.

### 3.3 User Stories Without PRD Coverage

| User Story | Topic | Risk |
|---|---|---|
| US053, US054 | Snapshot & Restore (E005) | Medium — full feature implemented without PRD coverage |
| US055 | Review Command (F026) | Medium — planned feature without PRD coverage |
| US057 | Interactive Mode (F028) | Medium — planned feature without PRD coverage |
| US052 | Quality Audit (F024) | Low — implementation detail of output validation |

### 3.4 Features Without PRD Coverage

| Feature | Topic | Risk |
|---|---|---|
| F024 | Quality Audit | Low — implementation detail |
| F025 | Snapshot & Restore | Medium — complete feature not in PRD |
| F026 | Review Command | Medium |
| F028 | Interactive Mode | Medium |

### 3.5 ADRs Without Clear Functional Justification

All 6 ADRs reference specific user stories or functional requirements. No orphaned ADRs.

### 3.6 Documentation Contradictions

| Source A | Source B | Contradiction |
|---|---|---|
| PRD §2 (pipeline diagram) | ADR-006 | PRD shows 3-phase pipeline (Indexing → Enrichment → Extraction). ADR-006 splits Extraction into `extract` + `generate`, creating a 5-step pipeline (`scan → plan → enrich → extract → generate`). PRD has not been updated to reflect this. |
| ~~US064 file header~~ | ~~Story title metadata~~ | ~~`US064` file is named `E003-F016-US064-planner-qualifies-dtos-with-bean-validation.md` but the story header reads `US056 — Planner qualifies DTOs with bean validation`. Wrong story number in header.~~ |

### 3.7 Ambiguous or Incomplete Documentation

| Location | Issue |
|---|---|
| All feature files | ~~All 29 features tagged `@draft` — none are marked `@final`. Unclear which are considered complete/stable.~~ 23 features promoted to `@final`; 6 incomplete features (F007-009/E002, F020/partial, F026, F028) remain `@draft`. |
| PRD | Refers to extract/generate as Phase 3 but pipeline diagram shows only 3 phases — conflicts with ADR-006's 4-phase model. |
| US048 | Acceptance criteria list is empty in the user story — `Test Suite Mining` has no defined acceptance criteria in the story file (only in the feature file). |

---

## 4. Implementation Findings

### 4.1 Implemented but Not Documented

~~#### 4.1.1 XML Bean Analysis (`XmlBeanAnalyzer`)
- **Code**: `src/main/java/.../indexing/domain/analyzer/bean/xml/` — 10 files
- **Test**: `XmlBeanAnalyzerTest.java`
- **Description**: Parses Spring XML configuration files (`applicationContext.xml`, etc.) to extract bean declarations, component scans, AOP config, JMS listeners, scheduled tasks, namespace beans, and alias definitions.
- **Missing from**: PRD, User Stories, Features, ADRs
- **Risk**: Medium — this is nontrivial functionality (6 finding types, full `ImportResourceVisitor` integration) that is invisible to anyone reading the product documentation.~~

~~#### 4.1.2 `@Bean` Method Detection (`BeanMethodVisitor`)
- **Code**: `src/main/java/.../indexing/domain/analyzer/bean/java/BeanMethodVisitor.java`, `BeanMethodInfo.java`
- **Test**: `BeanMethodVisitorTest.java`
- **Description**: Detects `@Bean`-annotated methods in `@Configuration` classes, capturing bean name (explicit or implicit), return type, declaring class, and source file.
- **Missing from**: PRD, User Stories, Features, ADRs
- **Risk**: Low-Medium — useful capability for Spring configuration discovery, entirely undocumented.~~

#### 4.1.3 Other Undocumented Code

| Code | Description | Risk |
|---|---|---|
| `AllowedLibrariesConfig` | Config for allowed library references | Low |
| `IndexingConfig` | File extension filters, max discovery depth | Low |
| `ScanPipelineResult` | Aggregated scan results | Low — internal plumbing |

### 4.2 Documented but Not Implemented

#### 4.2.1 E002 — Language Extension Framework (Multi-Language Support)
- **Impact**: **High** — 5 user stories, 3 feature files describe a complete language extension SPI that does not exist.
- **Evidence**: Grep for `LanguageParser` returns zero results. No parser interface, no registry, no routing.
- **Stories affected**: US018 (interface), US019 (implementation), US020 (registration), US021 (routing), US022 (pipeline integration)
- **Features affected**: F007 (Parser Abstraction SPI), F008 (Parser Discovery & Routing), F009 (Shared Pipeline Integration)
- **Mitigation**: Either implement E002 or remove/re-mark documentation as "planned" / "future scope".

#### 4.2.2 US055 — Review Command
- **Impact**: Medium — `review list`, `review show`, `review accept`, `review reset` commands documented but not implemented.
- **Evidence**: Grep for `ReviewCommand` or `review.*list.*AWAITING` returns zero results.
- **Note**: US055 references `AWAITING_HUMAN_REVIEW` state which exists in `TaskStatus.java`, but the UI for interacting with it is missing.

#### 4.2.3 US057 — Interactive Mode (`InteractiveUserInteractionService`)
- **Impact**: Medium — agent-user clarification flow documented but not implemented.
- **Evidence**: Grep for `InteractiveUserInteractionService`, `UserInteractionService`, `NoOpUserInteractionService` returns zero results.
- **Note**: US057 explicitly states `InteractiveUserInteractionService deferred` in its acceptance criteria.

### 4.3 Partially Implemented Items

| Item | Implemented | Missing |
|---|---|---|
| `Test Suite Mining (US048/F020)` | `TestFileMatcher` + `PairedExecutionResolver` exist and match tests to production code | The user story acceptance criteria list is empty — unclear if full translation of assertions to edge cases is complete |
| `AWAITING_HUMAN_REVIEW` support | `TaskStatus` enum includes `AWAITING_HUMAN_REVIEW` state; hop depth logic references it | No `ReviewCommand` to inspect/resolve these tasks |

### 4.4 Implementation Deviations

| Documented Expectation | Implementation Reality | Deviation |
|---|---|---|
| PRD §2 pipeline: 3 phases | Codebase: 4 phases (`scan → plan → enrich → extract → generate`) | Pipeline split documented in ADR-006 but PRD not updated |
| PRD §2 Phase 3: 7 agent actions | Code: Phase 3 has 6 agent actions (SynthesizeSpecAction moved to Phase 4/generate) | Intentional per ADR-006, but PRD still describes the old 7-action model |

### 4.5 Potentially Obsolete or Dead Functionality

No clearly dead code identified. The undocumented features (XML bean analysis, `@Bean` detection) are active and wired into the indexing pipeline.

---

## 5. Feature (Gherkin) Analysis

| Feature | PRD | User Stories | Implementation | Status |
|---|---|---|---|---|
| F001 — Manifest Parsing | Aligned | US001-003 | Complete | Aligned, Correctly Implemented |
| F003 — Java AST Analysis | Aligned | US006-010, 023, 026-027, 058 | Complete | Aligned, Correctly Implemented |
| F004 — Secret Redaction | Aligned | US011-012 | Complete | Aligned, Correctly Implemented |
| F005 — Index Output & SQLite | Aligned | US013-014, 017 | Complete | Aligned, Correctly Implemented |
| F006 — CLI Scan Orchestration | Aligned | US015-016, 028 | Complete | Aligned, Correctly Implemented |
| F010 — Two-Pass + Call Graph | Aligned | US030, 036 | Complete | Aligned, Correctly Implemented |
| F011 — Database Access | Aligned | US031, 059-061 | Complete | Aligned, Correctly Implemented |
| F012 — Outbound HTTP | Aligned | US032 | Complete | Aligned, Correctly Implemented |
| F013 — Event Link Resolution | Aligned | US033 | Complete | Aligned, Correctly Implemented |
| F014 — Structured Trace | Aligned | US034-035 | Complete | Aligned, Correctly Implemented |
| F015 — Template Form Detection | Aligned | US037-040, 062 | Complete | Aligned, Correctly Implemented |
| F029 — Spring XML Configuration Analysis | §2.1.2 | US065-067 | Complete | Aligned, Correctly Implemented |
| F030 — @Bean Method Detection | §2.1.2 | US068 | Complete | Aligned, Correctly Implemented |
| F007 — Parser Abstraction SPI | Not in PRD | US018-019 | **Not implemented** | Documented but Not Implemented |
| F008 — Parser Discovery | Not in PRD | US020-021 | **Not implemented** | Documented but Not Implemented |
| F009 — Shared Pipeline Integration | Not in PRD | US022 | **Not implemented** | Documented but Not Implemented |
| F016 — Planner | Aligned | US041-042, 064 | Complete | Aligned, Correctly Implemented |
| F017 — LLM Executor | Aligned | US043-044, 063 | Complete | Aligned, Correctly Implemented |
| F018 — Orchestrator | Aligned | US045 | Complete | Aligned, Correctly Implemented |
| F019 — CLI Commands | Aligned | US046-047 | Complete | Aligned, Correctly Implemented |
| F020 — Test Suite Mining | Aligned | US048 | Partially Complete | Partially Implemented |
| F021 — Embabel Setup | Aligned | US049 | Complete | Aligned, Correctly Implemented |
| F022 — Codebase Knowledge | Aligned | US050 | Complete | Aligned, Correctly Implemented |
| F023 — Embabel Agent | Aligned (per ADR-006) | US051 | Complete | Aligned, Correctly Implemented |
| F024 — Quality Audit | Not in PRD | US052 | Complete | Implemented but Not Documented |
| F025 — Snapshot & Restore | Not in PRD | US053-054 | Complete | Implemented but Not Documented |
| F026 — Review Command | Not in PRD | US055 | **Not implemented** | Documented but Not Implemented |
| F027 — Domain Model + Writers | Aligned (per ADR-006) | US056 | Complete | Aligned, Correctly Implemented |
| F028 — Interactive Mode | Not in PRD | US057 | **Not implemented** | Documented but Not Implemented |

---

## 6. User Story Analysis

| User Story | PRD | Feature | Implementation | Status |
|---|---|---|---|---|
| US001-003 (Manifest) | Aligned | F001 | Complete | Correctly Implemented |
| US006, US007, US008, US009, US010 (AST) | Aligned | F003 | Complete | Correctly Implemented |
| US023, US026, US027 (Broker Events) | Aligned | F003 | Complete | Correctly Implemented |
| US058 (web.xml) | Aligned | F003 | Complete | Correctly Implemented |
| US011 (Secret Redaction) | Aligned | F004 | Complete | Correctly Implemented |
| US012 (Exclude Filter) | Not in PRD | F004 | Complete | Implemented but Not Documented |
| US013, US014, US017 (Persistence) | Aligned | F005 | Complete | Correctly Implemented |
| US015, US016, US028 (Scan) | Aligned | F006 | Complete | Correctly Implemented |
| US030, US036 (Two-Pass) | Aligned | F010 | Complete | Correctly Implemented |
| US031, US059-061 (DB Access) | Aligned | F011 | Complete | Correctly Implemented |
| US032 (Outbound HTTP) | Aligned | F012 | Complete | Correctly Implemented |
| US033 (Event Links) | Aligned | F013 | Complete | Correctly Implemented |
| US034-035 (Trace) | Aligned | F014 | Complete | Correctly Implemented |
| US037-040, US062 (Views & Templates) | Aligned | F015 | Complete | Correctly Implemented |
| US065-067 (XML Bean Analysis) | §2.1.2 | F029 | Complete | Correctly Implemented |
| US068 (@Bean Method Detection) | §2.1.2 | F030 | Complete | Correctly Implemented |
| US018-022 (E002 Multi-Language) | Not in PRD | F007-009 | **Not implemented** | Documented but Not Implemented |
| US041-042, US064 (Planner) | Aligned | F016 | Complete | Correctly Implemented |
| US043-044, US063 (Executor) | Aligned | F017 | Complete | Correctly Implemented |
| US045 (Orchestrator) | Aligned | F018 | Complete | Correctly Implemented |
| US046-047 (CLI Plans) | Aligned | F019 | Complete | Correctly Implemented |
| US048 (Test Mining) | Aligned | F020 | Partially Complete | Partially Implemented |
| US049 (Embabel Setup) | Aligned | F021 | Complete | Correctly Implemented |
| US050 (CodebaseKnowledge) | Aligned | F022 | Complete | Correctly Implemented |
| US051 (Agent Extraction) | Aligned | F023 | Complete | Correctly Implemented |
| US052 (Quality Audit) | Not in PRD | F024 | Complete | Implemented but Not Documented |
| US053-054 (Snapshots) | Not in PRD | F025 | Complete | Implemented but Not Documented |
| US055 (Review Command) | Not in PRD | F026 | **Not implemented** | Documented but Not Implemented |
| US056 (Domain Model) | Aligned (per ADR-006) | F027 | Complete | Correctly Implemented |
| US057 (Interactive Mode) | Not in PRD | F028 | **Not implemented** | Documented but Not Implemented |

---

## 7. ADR Analysis

| ADR | Decision | Business Requirement | Implementation Alignment | Status |
|---|---|---|---|---|
| ADR-001 | JavaParser for AST | Java-only parsing, zero native deps | Fully implemented | Aligned |
| ADR-002 | Spring JDBC over ORM | SQLite persistence, explicit SQL | Fully implemented | Aligned |
| ADR-003 | SQLite WAL mode | Crash resilience, concurrent reads | Fully implemented | Aligned |
| ADR-004 | SHA-256 task IDs | Idempotent re-scans, cache hits | Fully implemented | Aligned |
| ADR-005 | In-memory secret redaction | Zero-touch on disk, no secret leakage | Fully implemented | Aligned |
| ADR-006 | Extract/Generate split | Fast regeneration, separation of concerns | Fully implemented | Aligned — but PRD not updated |

---

## 8. Traceability Gaps

| Gap Type | Count | Details |
|---|---|---|
| Requirements without user stories | 2 | `JavaVersionMapper` LanguageLevel mapping, `@Query` nativeQuery flag |
| User stories without features | 0 | All 63 stories link to a feature |
| Features without implementation | 3 | F007 (Parser SPI), F008 (Discovery), F009 (Integration) — all E002 |
| Code with no documented origin | 0 | ~~XML Bean Analysis (`XmlBeanAnalyzer`), `@Bean` method detection (`BeanMethodVisitor`)~~ |
| ADRs with no implementation evidence | 0 | All 6 ADRs have corresponding implementation |

---

## 9. Documentation Update Plan

| Priority | Document | Change | Reason | Impact | Owner |
|---|---|---|---|---|---|
| **High** | PRD | Add E002 (Multi-Language) as explicit epic or mark as deferred/future scope | 5 stories + 3 features documented but 0 code — misleading | Product trust | Product |
| **High** | PRD | Update pipeline diagram to reflect 5-step pipeline per ADR-006 | PRD still shows 3 phases; code has 4 phases + 5 commands | Architecture alignment | Architecture |
| **High** | PRD | Add Snapshot & Restore (E005) | Full feature implemented, 0 PRD coverage | Documentation completeness | Product |
| **Medium** | PRD | Add Interactive Mode (US057) and Review Command (US055) or mark as deferred | Documented but not implemented in stories; no PRD mention | Documentation completeness | Product |
| ~~**Medium** | User Stories | Create US for XML Bean Analysis | 10+ files of documented behavior, 0 stories | Undocumented features | Engineering~~ |
| ~~**Medium** | User Stories | Create US for `@Bean` method detection | `BeanMethodVisitor` with full tests, 0 stories | Undocumented features | Engineering~~ |
| ~~**Medium** | Features | Create Gherkin features for XML Bean Analysis | Same rationale | Undocumented features | Engineering~~ |
| ~~**Low** | Features | Promote all `@draft` tags to `@final` where implementation is complete | All features still marked draft | Quality signal | Engineering~~ |
| ~~**Low** | User Stories | Fix US064 header (says US056 instead of US064) | Story number mismatch | Accuracy | Engineering~~ |
| ~~**Low** | PRD | Add `@Bean` method detection | `BeanMethodVisitor` detects `@Bean`-annotated methods in `@Configuration` classes | Documentation completeness | Engineering~~ |

---

## 10. Implementation Backlog

| Priority | Source | Description | Business Impact | Complexity | Dependencies | Recommended Action |
|---|---|---|---|---|---|---|
| **High** | US018-022, F007-009 | Implement `LanguageParser` SPI, registration, routing, and pipeline integration | Unable to support non-Java codebases | High | — | Either implement or formally de-scope and update docs |
| **High** | US055, F026 | Implement `ReviewCommand` — list/show/accept/reset `AWAITING_HUMAN_REVIEW` tasks | Cannot resolve quarantined flows from CLI | Medium | US051, TaskStatus.AWAITING_HUMAN_REVIEW | Implement command |
| **Medium** | US057, F028 | Implement `InteractiveUserInteractionService` for stdin/stdout agent prompts | Agent cannot ask clarification questions; headless only | Medium | US051 Embabel agent | Implement SPI |
| **Medium** | US048, F020 | Complete test assertion mining — fill in empty acceptance criteria, verify assertion translation | Test-derived edge cases may be incomplete | Low | US043 Executor | Complete story definition |

---

## 11. Recommendations

### Restore Alignment
~~1. **Document the undocumented**: Create user stories and Gherkin features for XML Bean Analysis and `@Bean` method detection — both are nontrivial implemented features with zero documentation.~~
2. **Update the PRD**: Reflect the 5-step pipeline (per ADR-006), add E005 (Snapshots), and explicitly list E002 as future scope or remove it.
3. **Implement or de-scope E002**: Either build the `LanguageParser` SPI or formally move E002 stories to a "Planned" status with documentation updates.

### Improve Traceability
4. **Add trace IDs to PRD**: The PRD lacks explicit identifiers linking requirements to user stories. Add inline references (e.g., `[US001]`).
~~5. **Unify story numbering**: Fix the US064/US056 header mismatch.~~

### Reduce Documentation Debt
~~6. **Promote `@draft` tags**: All 29 feature files remain `@draft`. Review and promote to `@final` for completed features.~~
~~7. **Consolidate acceptance criteria**: US048 has an empty AC list in the story file but scenarios in the feature file — align them.~~

### Reduce Technical Debt
8. **Implement `ReviewCommand`**: Without it, `AWAITING_HUMAN_REVIEW` tasks are orphaned — the pipeline can stall with no resolution path.
9. **Implement interactive mode or document limitation**: US057 describes a key usability feature; without it, the agent silently accepts ambiguity below confidence threshold.

### Improve Governance
10. **Enforce code-doc traceability in CI**: Add a check that flags new code types without corresponding documentation updates.
11. **Require feature-doc updates for command changes**: When new CLI commands (like `SnapshotCommand`) are added, enforce PRD user-story updates in the review process.
