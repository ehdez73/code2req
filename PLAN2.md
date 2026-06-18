# E003/E004 — Semantic Enrichment & Agentic Functional Requirement Extraction (Phase 2 + Phase 3)

## Quick Start
- Build: `mvn clean compile`
- Test: `mvn test`
- Run: `export OPENROUTER_API_KEY=sk-or-v1-...` then `mvn spring-boot:run` (or create `.env` file — loaded automatically)
  - `plan --manifest project-manifest.yaml` — dry DAG view (no LLM)
  - `run --manifest project-manifest.yaml` — Phase 2 + Phase 3 (via OpenRouter, model from `OPENROUTER_MODEL` env var)
  - `run --manifest ... --llm-threshold 0` — run without LLM enrichment
  - `run --manifest ... --dry-run` — simulation mode (no API calls, no key required)

## Prerequisites (Already Done in Phase 1)
- SQLite store with `tasks`, `execution_findings`, `topic_links`, `floating_links`, `metrics` tables
- `TaskStore` with `PENDING`/`RUNNING`/`SUCCESS`/`FAILED` status queries (`findByStatus`)
- `ExecutionFindingStore` (save, saveAllForTask, countByType)
- `MetricsStore` (save, getLatestForPhase)
- `TaskIdHasher` (deterministic SHA-256 — needed for discovered_dependency tasks)
- JSON Schema validation dependency (`networknt/json-schema-validator` in pom.xml)
- Spring AI OpenAI dependency in pom.xml (used as OpenAI-compatible client for OpenRouter)
- Spring AOP + `@Async` pool configured in `application.properties`
- Spring Shell CLI infrastructure (`scan`, `resume`, `validate`, `status`, `clean`)
- `ProjectManifest` with `ExecutionConfig` (max-concurrent-llm-calls, max-discovery-depth, semantic-validation-sample-rate)

## Key Decisions

1. **Phase 2 is Epic E003** (Semantic Enrichment), **Phase 3 is Epic E004** (Agentic Functional Requirement Extraction) — both numbered sequentially after E001/E002 to keep the SDLC context clean.
2. **Embabel** is used as an **agentic framework** (GOAP goal-oriented action planning), not as a map-reduce pipeline. Phase 3's Embabel agent decides *what to investigate next* based on ambiguity. See PRD §2.3, §3.8.
3. **Phase 3 agent is scoped to decision-making only** — pure Java services handle data loading (`CodebaseKnowledge`), SQLite I/O, and output file writing.
4. **`@EnableAsync`** must be added to `Application.java` — currently absent.
5. **`TaskStatus` needs `AWAITING_HUMAN_REVIEW`** — required by PRD §5.2 (max hop depth), §3.5 (branch isolation), and §2.3 (unresolvable ambiguity gaps).
6. **`plan` command** is purely a DAG read — queries SQLite for qualified tasks, displays what would run. Zero LLM calls.
7. **`run` command** orchestrates Phase 2 (Spring AI LLM enrichment) then Phase 3 (Embabel agent) with a synchronization barrier in between.
8. **`status` command** already exists — extend to show Phase 2 counters (tokens consumed, estimated cost, per-task enrichment status) and Phase 3 counters (flow extraction rate, ambiguity gaps).
9. **Spring AI `ChatClient.Builder`** is auto-configured via `spring-ai-openai` when OpenAI-compatible credentials are present — no manual bean creation needed. The OpenAI client is configured to point at OpenRouter (`spring.ai.openai.base-url=https://openrouter.ai/api/v1`).
10. **OpenRouter** is the sole LLM provider for Phase 2. Configured via `OPENROUTER_API_KEY` env var; model selected via `OPENROUTER_MODEL` env var (default: `deepseek/deepseek-v4-flash:free`). `.env` file is loaded automatically via `spring.config.import=optional:file:.env`.
11. **Phase 3 guardrails** (`max-investigation-steps-per-flow`, `max-tokens-per-run`, `ambiguity-confidence-threshold`) are added to `ExecutionConfig` and `project-manifest.yaml`.

---

## Progress

### Epic E003 — Semantic Enrichment (PRD §2.2, §3.4–§3.7)

#### F016: Planner (US041, US042)

Reads the SQLite task store after Phase 1 and determines which tasks qualify for LLM enrichment. Pure query + rule engine — no LLM calls.

**9 qualification rules** (all enabled at runtime):
1. File has > `llm-unresolved-threshold` (default: 5) unresolved signatures — queries `execution_findings` with `finding_type = 'CALL_GRAPH_EDGE' AND resolved = 0`
2. File is a Spring Data interface (e.g., `CrudRepository`, `JpaRepository`) — queries `finding_type = 'SPRING_DATA_INTERFACE'`
3. File contains a stored procedure call — queries `finding_type = 'DATABASE_PROCEDURE_CALL'`
4. File is a custom `ConstraintValidator` with a complex `isValid` body — queries `finding_type = 'CONSTRAINT_VALIDATOR'`
5. File has a paired test file with assertions — file system check for `*Test.java` or `*IT.java`
6. File has unresolved floating links — queries `floating_links` with `resolved_status = 'PENDING'` matched to `tasks.file_path`
7. File has a scheduled task (`@Scheduled` annotation) — queries `finding_type = 'SCHEDULED_TASK'`
8. File contains a native SQL query (`@Query(nativeQuery=true)`, `@NamedNativeQuery`, `EntityManager.createNativeQuery()`, `Session.createNativeQuery()/createSQLQuery()`, raw JDBC) — queries `finding_type = 'NATIVE_SQL_QUERY'`
9. File contains a JPQL/HQL query (`@Query(...)`, `@NamedQuery`, `EntityManager.createQuery()`, `Session.createQuery()`) — queries `finding_type = 'JPQL_HQL_QUERY'`

**Structural fixes completed:**
- [x] `AnalysisFinding` interface gains `default boolean isResolved() { return true; }` — `CallGraphEdge` overrides to return `STATUS_RESOLVED.equals(resolvedStatus)`
- [x] `ExecutionFindingStore.saveAllForTask()` uses `finding.isResolved()` instead of hardcoded `true`
- [x] New `FindingType` constants: `SPRING_DATA_INTERFACE`, `DATABASE_PROCEDURE_CALL`, `CONSTRAINT_VALIDATOR`, `NATIVE_SQL_QUERY`, `JPQL_HQL_QUERY` — created by a re-classification step in `ScanPipeline` after initial persist
- [x] `FloatingLinkStore` gains `findSourceFilePathsByResolvedStatus(String)` query for planner access
- [x] `llmQualificationRules` removed from `ExecutionConfig` (rule filtering not needed — all rules always enabled)

- [x] **US041** (must): Planner qualifies tasks for LLM enrichment based on 9 self-contained rule components (Strategy Pattern)
- [ ] **US042** (should): Planner supports dry-run DAG view via `plan` command — tracked in F019
- [x] Gherkin: `docs/sdlc/features/E003-F016-planner.feature`
- [x] Depends on: Phase 1 complete (SQLite populated with task rows and execution_findings)
- [x] Classes: `Phase2Planner` (orchestrator), `QualificationRule` (interface), `PlanningContext` (shared data access), `PlannerDecision` (record), `QualificationReason` (enum), and 9 rule `@Component` classes in `planner/rule/` (`SpringDataInterfaceRule`, `StoredProcedureCallRule`, `CustomConstraintValidatorRule`, `ScheduledTaskPresentRule`, `UnresolvedSignaturesRule`, `UnresolvedFloatingLinkRule`, `TestAssertionsPresentRule`, `NativeSqlQueryRule`, `JpqlHqlQueryRule`)
- [x] Modified: `AnalysisFinding` (add `isResolved()`), `CallGraphEdge` (override `isResolved()`), `ExecutionFindingStore` (use per-finding status), `FindingType` (5 new constants), `ScanPipeline` (re-classification step with 2 new cases), `FloatingLinkStore` (query by status), `Phase2Planner` (strategy refactor)
- [x] Verify: `mvn test` — 366 tests pass, planner correctly qualifies/doesn't qualify
- [ ] Manual: `plan` CLI command is tracked in F019

#### F017: LLM Executor Framework (US043, US044)

Individual file enrichment workers. Each executor receives the pre-resolved structural context from Phase 1 plus raw source file content. LLM prompt instructs the model to NOT resolve structural dependencies (already done) and focus on business semantics.

**LLM Provider:** Executors route through **OpenRouter** via Spring AI's OpenAI-compatible client. The base URL points to `https://openrouter.ai/api/v1`. Model is set via `OPENROUTER_MODEL` env var (default: `deepseek/deepseek-v4-flash:free`). API key is read from `OPENROUTER_API_KEY` env var, loaded from `.env` via `spring.config.import=optional:file:.env`.

Key behaviors:
- Spring `@Async("orchestratorTaskExecutor")` method returning `CompletableFuture<ExecutionFinding>`
- Exponential backoff: initial 2s, multiplier 2.0, cap 60s, max 3 retries (PRD §5.4)
- Context budgeting: if combined token weight > 80% of model context window, trigger pre-summarization step (PRD §3.6)
- Output validated against JSON Schema §4 before transition to `SUCCESS`
- `--dry-run` mode: Spring AI calls intercepted by local `SimulationStub` returning deterministic static JSON (zero API calls, no key required)
- Persists enriched JSON to `execution_findings` table via `ExecutionFindingStore`
- Attaches `discovered_dependency` array when unindexed runtime deps uncovered (PRD §3.5)

- [x] **US043** (must): Executor enriches a single file via Spring AI + `@Async`, validates output against §4 JSON Schema, handles exponential backoff and discovered dependencies
- [x] **US044** (should): Executor supports `--dry-run` mode with deterministic stubs — zero API calls
- [x] Gherkin: `docs/sdlc/features/E003-F017-llm-executor.feature`
- [x] Depends on: F016 (Planner), `@EnableAsync` on Application.java, Spring AI auto-configuration (OpenRouter config via `OPENROUTER_API_KEY` + `OPENROUTER_MODEL` env vars)
- [x] Classes: `SemanticExecutor`, `ExecutionFindingValidator` (JSON Schema), `ContextBudgetCalculator`, `SimulationStub`
- [x] New finding type in `FindingType`: `SEMANTIC_ENRICHMENT`
- [x] Verify: `mvn test` — 389 tests pass (F017 executor tests included: dry-run produces valid output, schema validation works, budget calculation correct, simulation stub deterministic)
- [ ] Manual: `run --dry-run --manifest ...` — verify enrichment output without API calls

#### F018: Orchestrator (US045)

Manages the enrichment DAG, submits tasks async, tracks progress, handles dynamic re-planning.

Key behaviors:
- Processes planner decisions (F016) as a DAG
- Submits tasks via `@Async` to Spring pool
- Tracks via `CompletableFuture<ExecutionFinding>` responses
- **Phase Synchronization Barrier**: `CompletableFuture.allOf(...)` — blocks Phase 3 until ALL Phase 2 tasks complete (PRD §2.2)
- **Dynamic Re-Planning Loop**: when an executor discovers a dependency (PRD §3.5):
  - Register new task in SQLite as `PENDING`
  - Pause only that branch (other branches continue)
  - Process the new task, wait for completion, resume original branch
- **Max Hop Depth**: configurable (default: 3). Exceeded → `AWAITING_HUMAN_REVIEW` (PRD §5.2)
- **Visited Registry**: thread-safe set of hashes to prevent redundant evaluation (PRD §5.2)
- Writes `metrics` after Phase 2 completes (tokens consumed, cost estimate)

- **US045** (must): Orchestrator manages enrichment DAG, submits tasks async, implements Phase 2→3 barrier via CompletableFuture.allOf(), handles dynamic re-planning with branch isolation, enforces max-hop-depth
- [x] Gherkin: `docs/sdlc/features/E003-F018-orchestrator.feature`
- [ ] Depends on: F016, F017, `TaskStore.findByStatus()`, `TaskStatus.AWAITING_HUMAN_REVIEW`
- [ ] Classes: `Phase2Orchestrator`, `EnrichmentDag`, `BranchState`
- [ ] Modified: `TaskStatus` (add `AWAITING_HUMAN_REVIEW`), `MetricsStore` (Phase 2 metrics writing)
- [ ] Verify: `mvn test` — orchestrator submits all qualified tasks, barrier blocks until all complete
- [ ] Manual: `run --dry-run --manifest ...` — verify all Phase 2 tasks complete with barrier

#### F019: CLI Commands — `plan` and `run` (US046, US047)

- `plan --manifest path` — dry DAG view: queries SQLite for the planning decisions F016 would make and displays qualified tasks grouped by target, with qualification reason per task. Zero LLM calls.
- `run --manifest path [--dry-run] [--llm-threshold N]` — orchestrates Phase 2 then Phase 3 end-to-end:
  - Phase 2: Planner → Executors → Orchestrator with barrier
  - Phase 3: Synthesis (delegates to E004)
  - `--llm-threshold 0` skips Phase 2 entirely (degenerate case)
  - `--dry-run` simulation mode (deterministic stubs, no API spend)
- `status` — extend existing command to show Phase 2 metrics (enriched tasks, tokens consumed, estimated cost, pending/complete counts)

- **US046** (must): `plan` command displays qualified tasks grouped by target with qualification reasons — zero LLM calls, zero SQLite mutations
- **US047** (must): `run` command orchestrates Phase 2 → Phase 3 with `--dry-run`, `--llm-threshold N` support; `status` shows Phase 2+3 counters
- [x] Gherkin: `docs/sdlc/features/E003-F019-cli-run-command.feature`
- [ ] Depends on: F016, F017, F018, E004 (Phase 3 output)
- [ ] Classes: `PlanCommand`, `RunCommand`
- [ ] Modified: `StatusCommand` (Phase 2+3 metrics columns)
- [ ] Verify: `mvn test` — plan produces correct DAG, run with `--dry-run` completes without network calls
- [ ] Manual: `plan --manifest ...` then `run --dry-run --manifest ...` — verify end-to-end flow

#### F020: Test Suite Mining (US048, PRD §3.4)

Match production files with paired test files (e.g., `OrderService.java` ↔ `OrderServiceTest.java`). Extract assertions and translate to edge cases.
- Discovery: pair files by file name pattern (strip `Test` suffix)
- Extraction: `assertEquals`, `assertThrows`, `expect()` → functional edge cases and validation requirements
- Both test and production file sent to the same executor for concurrent processing
- Merged into the `test_insights` array in the `ExecutionFinding` JSON schema (§4)

- **US048** (should): Test file paired by naming convention (strip Test suffix); assertEquals/assertThrows extracted into test_insights array
- [x] Gherkin: `docs/sdlc/features/E003-F020-test-suite-mining.feature`
- [ ] Depends on: F017 (executor framework)
- [ ] Classes: `TestFileMatcher`, `TestAssertionExtractor`, `PairedExecutionResolver`
- [ ] Verify: `mvn test` — paired test files produce `test_insights` with extracted scenarios
- [ ] Manual: run with `--dry-run` on petclinic — verify test files paired and assertions extracted

---

### Epic E004 — Agentic Functional Requirement Extraction (PRD §2.3, §3.8, §6)

#### F021: Embabel Agent Framework Setup (US049)

Uncomment and configure Embabel in pom.xml. Embabel provides the GOAP (Goal-Oriented Action Planning) engine for Phase 3's agentic extraction.

- **US049** (must): Embabel dependency uncommented in pom.xml, framework initializes at startup, AgentPlatform available for Phase 3
- [x] Gherkin: `docs/sdlc/features/E004-F021-embabel-setup.feature`
- [ ] Depends on: Phase 2 completion, Embabel repo availability
- [ ] Modified: `pom.xml` (uncomment embabel-agent-starter), `application.properties` (Embabel config if needed), `model/ExecutionConfig.java` (add guardrail fields)
- [ ] Verify: `mvn compile` succeeds with Embabel on classpath
- [ ] Manual: run with `--dry-run` — confirm Embabel initialises without error

#### F022: CodebaseKnowledge Builder + Phase 3 Orchestrator (US050, PRD §2.3)

Pure-Java services that prepare data for the Embabel agent and handle output after it completes. The agent itself is focused solely on decision-making — data plumbing is separate.

**Phase3Orchestrator (pure Java):**
1. Queries SQLite for ALL Phase 1 findings (call graph edges, topic links, floating links, endpoint maps) and Phase 2 enriched `ExecutionFinding` records.
2. Assembles an in-memory `CodebaseKnowledge` domain model — a rich, queryable object graph grouping all structural + semantic knowledge.
3. Passes `CodebaseKnowledge` as initial working memory to the Embabel agent.
4. After agent completes, invokes pure-Java writers for output artifacts.

**CodebaseKnowledge domain model:**
- `StructuralGraph` — call graph edges, component stereotypes, endpoint registries, database access patterns.
- `SemanticEnrichment` — all Phase 2 `ExecutionFinding` records keyed by file path.
- `LinkRegistry` — topic links (producer↔consumer), floating links (HTTP calls), template links.
- Query methods: `getFlowCandidates()`, `findUnresolvedLinks()`, `getComponentsByType()`, `getCallersOf(target)`, `getCalleesOf(source)`.

**No fallback needed** — Embabel is the agent framework (decision-making), not a data-processing pipeline. If Embabel repo is unavailable, Phase 3 cannot run.

- **US050** (must): Phase3Orchestrator aggregates Phase 1 (structural) + Phase 2 (enriched) data into CodebaseKnowledge with query methods (getFlowCandidates, getCallersOf, getCalleesOf, findUnresolvedLinks)
- [x] Gherkin: `docs/sdlc/features/E004-F022-codebase-knowledge.feature`
- [ ] Depends on: F021 (Embabel integration), Phase 2 enriched data in SQLite
- [ ] Classes: `Phase3Orchestrator`, `CodebaseKnowledge`, `StructuralGraph`, `SemanticEnrichment`, `LinkRegistry`
- [ ] Verify: `mvn test` — CodebaseKnowledge correctly aggregates Phase 1 + Phase 2 data
- [ ] Manual: `run --dry-run --manifest ...` — verify orchestrator loads data and invokes agent

#### F023: Embabel Agent — Goals, Actions, Output (US051, PRD §2.3)

The Embabel agent that extracts functional requirements. This is the core of Phase 3.

**Agent specification:**
- `@Agent(description = "Extract functional requirements from enriched codebase analysis")`
- **Mode:** Focused — invoked explicitly by `Phase3Orchestrator` with `CodebaseKnowledge` as input.
- **Goals:**
  - `FunctionalFlowCoverage` — All candidate flows have complete descriptions.
  - `BusinessRuleCompleteness` — All business rules include pre/postconditions and error behaviors.
  - `TraceabilityVerified` — Every requirement maps to a source location.
  - `LinkConsistency` — Floating links and topic publications are matched or documented as unresolvable.
- **Actions:**
  - `AnalyzeFindings` — Group `ExecutionFinding` records by flow boundaries using call graph from `CodebaseKnowledge`. Produce candidate `FunctionalFlow` objects.
  - `ResolveAmbiguity` — When a candidate flow has knowledge gaps, search `CodebaseKnowledge` query methods; fall back to raw source file reads if needed.
  - `CrossReferenceFloatingLinks` — Match unresolved HTTP calls and topic publications against known endpoints.
  - `SynthesizeFunctionalSpec` — Aggregate resolved knowledge into the final spec.
  - `QuarantineUnresolvable` — Flag flows exceeding investigation budget; set to `AWAITING_HUMAN_REVIEW`.
- **Guardrails** (from `ExecutionConfig`):
  - `max-investigation-steps-per-flow` (default: 5) — prevents runaway exploration.
  - `max-tokens-per-run` (default: 500000) — hard LLM token budget.
  - `ambiguity-confidence-threshold` (default: 0.7) — below this, quarantine instead of continuing.

**Output classes (pure Java, called after agent completes):**
- `FunctionalFlow` — one per extracted flow: trigger, steps, outcomes, business rules, traceability.
- `MarkdownSpecWriter` — writes `spec-output/*.md` per PRD §6.1 template.
- `SemanticManifestWriter` — writes `spec-output/semantic_manifest.json` with full traceability.

- **US051** (must): Embabel agent in Focused mode with GOAP planning: AnalyzeFindings → candidate flows, ResolveAmbiguity → codebase search, CrossReferenceFloatingLinks → link resolution, QuarantineUnresolvable → guardrail enforcement, SynthesizeFunctionalSpec → Markdown + JSON output
- [x] Gherkin: `docs/sdlc/features/E004-F023-embabel-agent.feature`
- [ ] Depends on: F022 (CodebaseKnowledge), Embabel framework
- [ ] Classes (agent): `FunctionalRequirementAgent`, `AnalyzeFindingsAction`, `ResolveAmbiguityAction`, `CrossReferenceLinksAction`, `SynthesizeSpecAction`, `QuarantineAction`
- [ ] Classes (domain): `FunctionalFlow`, `BusinessRule`, `EndpointSpec`, `CodePattern`, `AmbiguityGap`, `FlowStep`, `TraceabilityEntry`
- [ ] Classes (output): `MarkdownSpecWriter`, `SemanticManifestWriter`
- [ ] Verify: `mvn test` — agent produces valid FunctionalFlow objects from sample CodebaseKnowledge
- [ ] Manual: `run --dry-run --manifest ...` — inspect `spec-output/` for both artifacts

#### F024: Quality Audit (US052, PRD §3.8)

Post-agent validation pass (pure Java, not part of the Embabel agent):
- Sample rate: `semantic-validation-sample-rate` from manifest (default: 20%)
- Pass threshold: ≥ 92% pass rate
- On failure: flag batch for human review, increase sample rate to 100% for next run
- Output: audit report in Markdown (spec-output/audit-report.md)

- **US052** (should): Post-agent quality audit (pure Java) samples 20% of requirements against source files, enforces ≥92% pass rate, flags batch for review on failure
- [x] Gherkin: `docs/sdlc/features/E004-F024-quality-audit.feature`
- [ ] Depends on: F023 (output artifacts exist to audit)
- [ ] Classes: `Phase3QualityAudit`, `AuditSample`, `AuditReport`
- [ ] Verify: `mvn test` — audit passes on deterministic dry-run output (100% pass rate)
- [ ] Manual: run with `--dry-run` — verify audit report generated in `spec-output/`

---

## Story Index

| Story | Feature | Priority | Phase |
|-------|---------|----------|-------|
| US041 | F016 | must | E003 — Planner |
| US042 | F016 | should | E003 — Planner |
| US043 | F017 | must | E003 — Executor |
| US044 | F017 | should | E003 — Executor (dry-run) |
| US045 | F018 | must | E003 — Orchestrator |
| US046 | F019 | must | E003 — `plan` command |
| US047 | F019 | must | E003 — `run` command |
| US048 | F020 | should | E003 — Test Suite Mining |
| US049 | F021 | must | E004 — Embabel Agent Framework Setup |
| US050 | F022 | must | E004 — CodebaseKnowledge + Orchestrator |
| US051 | F023 | must | E004 — Embabel Agent (Goals, Actions, Output) |
| US052 | F024 | should | E004 — Quality Audit |

## Phase Dependency Graph

```
E003 — Phase 2 (Semantic Enrichment)             E004 — Phase 3 (Agentic Functional Requirement Extraction)
┌──────────────────────┐                         ┌───────────────────────────────────┐
│ F016: Planner        │                         │ F021: Embabel Agent Framework     │
│ (qualification rules)│                         │ (uncomment + configure in POM)    │
└──────────┬───────────┘                         └────────────┬──────────────────────┘
           │                                                   │
           ▼                                                   ▼
┌──────────────────────┐                         ┌───────────────────────────────────┐
│ F017: Executor       │────── Phase Barrier ────│ F022: CodebaseKnowledge Builder   │
│ (@Async + Spring AI) │    CompletableFuture    │ (pure Java: aggregate Phase 1+2)  │
└──────────┬───────────┘        .allOf(...)       └────────────┬──────────────────────┘
           │                                                   │
           ▼                                                   ▼
┌──────────────────────┐                         ┌───────────────────────────────────┐
│ F018: Orchestrator   │                         │ F023: Embabel Agent               │
│ (DAG + re-planning)  │                         │ (GOAP: goals + actions + output)  │
└──────────┬───────────┘                         └────────────┬──────────────────────┘
           │                                                   │
           ▼                                                   ▼
┌──────────────────────┐                         ┌───────────────────────────────────┐
│ F019: CLI Commands   │                         │ F024: Quality Audit (post-agent)  │
│ (plan / run /status) │                         │ (20% sample → ≥92% pass rate)     │
└──────────────────────┘                         └───────────────────────────────────┘

F020: Test Suite Mining ── depends on F017 (executor framework)

Separation of concerns: Data I/O and output writing are PURE JAVA, not agentic.
The Embabel agent handles ONLY decision-making (what to investigate, goal tracking).
```

## Modified / New Files Summary

### Modified (existing files changed, F016-related additions in *italic*, completed items prefixed with ✓):
| File | Change |
|------|--------|
| ✓ `Application.java` | Add `@EnableAsync` |
| ✓ `config/AppConfig.java` | Add `@Bean("orchestratorTaskExecutor")` `ThreadPoolTaskExecutor` (core=5, max=10, queue=1000) |
| ✓ `application.properties` | Add OpenRouter config (`spring.ai.openai.base-url`, `spring.ai.openai.api-key`, `spring.ai.openai.chat.options.model`, `spring.config.import=optional:file:.env`); rename thread prefix to `c2r-orchestrator-` |
| `model/TaskStatus.java` | Add `AWAITING_HUMAN_REVIEW` |
| ✓ `model/AnalysisFinding.java` | Add `default boolean isResolved() { return true; }` |
| ✓ `analyzer/callgraph/CallGraphEdge.java` | Override `isResolved()` to return `STATUS_RESOLVED.equals(resolvedStatus)` |
| ✓ `store/ExecutionFindingStore.java` | `saveAllForTask` uses `finding.isResolved()` instead of hardcoded `true` |
| ✓ `store/FindingType.java` | Add `SPRING_DATA_INTERFACE`, `DATABASE_PROCEDURE_CALL`, `CONSTRAINT_VALIDATOR`, `NATIVE_SQL_QUERY`, `JPQL_HQL_QUERY`, `SEMANTIC_ENRICHMENT` |
| ✓ `pipeline/ScanPipeline.java` | Add re-classification step after `persistFindings()` to create granular FindingType rows (now 5 mapping cases) |
| ✓ `store/FloatingLinkStore.java` | Add `findSourceFilePathsByResolvedStatus(String)` query for planner |
| ✓ `planner/Phase2Planner.java` | Refactored with Strategy Pattern — delegates to 9 `QualificationRule` components |
| `shell/StatusCommand.java` | Add Phase 2 metrics (tokens, cost, enriched count) + Phase 3 metrics (flow extraction rate, ambiguity gaps) |
| ✓ `pom.xml` | Add `spring-ai-client-chat`, `spring-ai-autoconfigure-model-chat-client` dependencies |
| `model/ExecutionConfig.java` | Add `maxInvestigationStepsPerFlow`, `maxTokensPerRun`, `ambiguityConfidenceThreshold` (removed `llmQualificationRules`) |
| `project-manifest.yaml` | Add `llm-unresolved-threshold` and guardrail fields under `execution:` (removed `llm-qualification-rules`) |

### New files:
| Package | Files |
|---------|-------|
| `planner/` | `QualificationRule.java` (interface), `PlanningContext.java` (shared data access), `PlannerDecision.java` (record), `QualificationReason.java` (enum) |
| `planner/rule/` | `SpringDataInterfaceRule`, `StoredProcedureCallRule`, `CustomConstraintValidatorRule`, `ScheduledTaskPresentRule`, `UnresolvedSignaturesRule`, `UnresolvedFloatingLinkRule`, `TestAssertionsPresentRule`, `NativeSqlQueryRule`, `JpqlHqlQueryRule`, `AbstractFindingTypeRule` (base class) |
| ✓ `executor/` | `SemanticExecutor`, `ExecutionFindingValidator`, `ContextBudgetCalculator`, `SimulationStub` |
| ✓ `model/` | `ExecutionFinding` (nested record hierarchy matching §4 schema) |
| ✓ `resources/schema/` | `execution-finding-schema.json` (embedded §4 JSON Schema) |
| `orchestrator/` | `Phase2Orchestrator`, `EnrichmentDag`, `BranchState` |
| `executor/testmining/` | `TestFileMatcher`, `TestAssertionExtractor`, `PairedExecutionResolver` |
| `synthesis/` | `Phase3Orchestrator`, `CodebaseKnowledge`, `StructuralGraph`, `SemanticEnrichment`, `LinkRegistry` |
| `synthesis/agent/` | `FunctionalRequirementAgent`, `AnalyzeFindingsAction`, `ResolveAmbiguityAction`, `CrossReferenceLinksAction`, `SynthesizeSpecAction`, `QuarantineAction` |
| `synthesis/domain/` | `FunctionalFlow`, `BusinessRule`, `EndpointSpec`, `CodePattern`, `AmbiguityGap`, `FlowStep`, `TraceabilityEntry` |
| `synthesis/output/` | `MarkdownSpecWriter`, `SemanticManifestWriter` |
| `synthesis/audit/` | `Phase3QualityAudit`, `AuditSample`, `AuditReport` |
| `shell/` | `PlanCommand`, `RunCommand` |

## Verification Guide

- Unit: `mvn test`
- Manual dry-run: `run --dry-run --manifest project-manifest.yaml` — no API calls made
- Manual full: `run --manifest project-manifest.yaml` — needs `OPENROUTER_API_KEY` env set (model configurable via `OPENROUTER_MODEL`)
- JSON schema validation: enriched `ExecutionFinding` output validates against PRD §4 schema
- Phase 2 only: `run --manifest ...` then `status` — confirm per-task enrichment status + token counters
- Phase 2 + Phase 3: `run --manifest ... --dry-run` — verify all phases complete end-to-end without network calls
- Output inspection: `spec-output/` contains both `*.md` (functional flows) and `semantic_manifest.json` (traceability graph)
- Phase 3 quarantine: verify flows marked `AWAITING_HUMAN_REVIEW` appear in spec Section 5
