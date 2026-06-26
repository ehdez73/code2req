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
- `TaskStore` with `PENDING`/`ENRICHING`/`INDEXED`/`ENRICHED`/`FAILED` status queries (`findByStatus`)
- `ExecutionFindingStore` (save, saveAllForTask, countByType)
- `MetricsStore` (save, getLatestForPhase)
- `TaskIdHasher` (deterministic SHA-256 — needed for discovered_dependency tasks)
- JSON Schema validation dependency (`networknt/json-schema-validator` in pom.xml)
- Spring AI OpenAI dependency in pom.xml (used as OpenAI-compatible client for OpenRouter)
- Spring AOP + `@Async` pool configured in `application.properties`
- Spring Shell CLI infrastructure (`scan`, `resume`, `validate`, `status`, `clean`)
- `ExecutionConfig` (`@ConfigurationProperties` bound from `application.properties` — max-concurrent-llm-calls, max-discovery-depth, semantic-validation-sample-rate, etc.)

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
11. **Phase 3 guardrails** (`max-investigation-steps-per-flow`, `max-tokens-per-run`, `ambiguity-confidence-threshold`) are in `ExecutionConfig` (populated from `application.properties`, no longer in `project-manifest.yaml`).
12. **Embabel agent is annotation-defined** — uses `@Agent` on a Spring `@Component` with `@Action`-annotated methods, auto-discovered by Embabel's `DelegatingAgentScanningBeanPostProcessor`. Requires `@EnableAgents` on `Application.java`.
13. **GOAP conditions are world-state string identifiers** managed on the Embabel blackboard (e.g., `codebase_knowledge_loaded`, `flows_analyzed`). Actions declare `pre = {...}` and `post = {...}` referencing these identifiers. See F023 for the complete condition table.
14. **Blackboard is the sole data channel** between `Phase3Orchestrator` and agent actions. Orchestrator places `CodebaseKnowledge` on the blackboard; actions write `FunctionalFlow` objects to it. Agent never reads SQLite or files directly.
15. **Embabel's `ProcessOptions.Budget` enforces token budgets** — `max-tokens-per-run` maps to `Budget.withTokens(N)`. Action step tracking and confidence scoring are in custom action method bodies.
16. **Test strategy uses Embabel's `IntegrationTestUtils`** — `dummyProcessContext()` and `dummyAgentPlatform()` mock infrastructure for unit-testing `@Action` methods without full Spring context.

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
- Error-feedback retry: on JSON parse failure, the LLM's broken output and the parse error are prepended to the retry prompt so the model can self-correct on the next attempt
- Context budgeting: if combined token weight > 80% of model context window, trigger pre-summarization step (PRD §3.6)
- Output validated against JSON Schema §4 before transition to `ENRICHED`
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
- **FAILED Task Recovery**: tasks that exhaust retries and transition to FAILED can be recovered via `task-set-status --task <id> --status INDEXED --delete-findings true` (single task) or `run --resume` (all interruptible states, now including `AWAITING_HUMAN_REVIEW` — findings cleaned, reset to INDEXED). On re-run, the planner skips tasks with existing SEMANTIC_ENRICHMENT findings (see F016) so only genuinely failed tasks are re-processed.
- **AWAITING_HUMAN_REVIEW Recovery**: `run --resume` now handles `AWAITING_HUMAN_REVIEW` tasks by resetting to INDEXED with findings cleaned. This allows the planner to re-qualify them after the root cause is resolved (e.g., providing missing source context or increasing budget thresholds). The spec output preserves the quarantine record even after reset.
- **Phase 3 Marker handled in RunCommand**: The Phase 3 status marker recovery (`ENRICHING → PENDING`, `.tmp` file cleanup) lives in `RunCommand.recoverPhase3Marker()`, not in Phase2Orchestrator. See F019.
- Writes `metrics` after Phase 2 completes (tokens consumed, cost estimate)

- [x] **US045** (must): Orchestrator manages enrichment DAG, submits tasks async, implements Phase 2→3 barrier via CompletableFuture.allOf(), handles dynamic re-planning with branch isolation, enforces max-hop-depth
- [x] Gherkin: `docs/sdlc/features/E003-F018-orchestrator.feature`
- [x] Depends on: F016, F017, `TaskStore.findByStatus()`, `TaskStatus.AWAITING_HUMAN_REVIEW`
- [x] Classes: `Phase2Orchestrator`, `EnrichmentDag`, `BranchState`, `CompletionStatus`
- [x] Modified: `TaskStatus` (add `AWAITING_HUMAN_REVIEW`)
- [x] Verify: `mvn test` — 406 tests pass (17 orchestrator-specific), orchestrator submits all qualified tasks, barrier blocks until all complete, dynamic re-planning with discovered dependencies, max-hop-depth enforcement with AWAITING_HUMAN_REVIEW, visited registry prevents redundant evaluation, Phase 2 metrics written after completion
- [ ] Manual: `run --dry-run --manifest ...` — verify all Phase 2 tasks complete with barrier

#### F019: CLI Commands — `plan` and `run` (US046, US047)

- `plan --manifest path` — dry DAG view: queries SQLite for the planning decisions F016 would make and displays qualified tasks grouped by target, with qualification reason per task. Zero LLM calls.
- `run --manifest path [--dry-run] [--resume] [--llm-threshold N] [--force-phase3] [--interactive] [--interactive-timeout N]` — orchestrates Phase 2 then Phase 3 end-to-end:
  - Phase 2: Planner → Executors → Orchestrator with barrier
  - Phase 3: Synthesis (delegates to E004)
  - `--llm-threshold 0` skips Phase 2 entirely (degenerate case)
  - `--dry-run` simulation mode (deterministic stubs, no API spend)
  - `--resume` recovers both Phase 2 (orphaned tasks) and Phase 3 (stuck ENRICHING marker, clean .tmp output files)
  - `--force-phase3` re-runs Phase 3 even if the marker shows ENRICHED (overrides idempotent skip)

  **`--resume` Phase 3 recovery (`recoverPhase3Marker()`):**
  - Called after `recoverOrphanedTasks()` (Phase 2) when `--resume` is set.
  - Queries the Phase 3 marker task (`SHA-256("__phase3_marker__")`):
    - `ENRICHING` → crash detected mid-Phase 3. Reset marker to `PENDING`, delete any `.tmp.*` files in `spec-output/`, log warning.
    - `ENRICHED` → Phase 3 completed cleanly. Leave marker as-is. Phase 3 will be skipped (see below).
    - `FAILED` → reset marker to `PENDING` so Phase 3 re-runs.
    - `PENDING` → no-op.
  - If marker is not found → first run, no-op.

  **Phase 3 skip-if-ENRICHED logic:**
  - Before `Phase3Orchestrator.execute()`, RunCommand checks the marker task.
  - If marker status is `ENRICHED` and `--force-phase3` is not set → skip Phase 3, print "Phase 3 already completed (use --force-phase3 to re-run)".
  - If `--force-phase3` is set → reset marker to `PENDING`, proceed with full execution.
- `status` — extend existing command to show Phase 2 metrics (enriched tasks, tokens consumed, estimated cost, pending/complete counts)

- **US046** (must): `plan` command displays qualified tasks grouped by target with qualification reasons — zero LLM calls, zero SQLite mutations
- **US047** (must): `run` command orchestrates Phase 2 → Phase 3 with `--dry-run`, `--llm-threshold N` support; `status` shows Phase 2+3 counters
- [x] Gherkin: `docs/sdlc/features/E003-F019-cli-run-command.feature`
- [x] Depends on: F016, F017, F018, E004 (Phase 3 output — **Phase3Orchestrator placeholder created**)
- [x] Classes: `PlanCommand`, `RunCommand`
- [x] Modified: `StatusCommand` (Phase 2+3 metrics columns)
- [x] `ExecutionConfig` — moved to `@ConfigurationProperties` bound from `application.properties`
- [x] New: `synthesis/Phase3Result.java`, `synthesis/Phase3Orchestrator.java` (E004 placeholder)
- [x] Verify: `mvn test` — 420 tests pass (6 new PlanCommand tests, 5 new RunCommand tests, 4 new StatusCommand metrics tests)
- [ ] Manual: `plan --manifest ...` then `run --dry-run --manifest ...` — verify end-to-end flow

#### F020: Test Suite Mining (US048, PRD §3.4)

Match production files with paired test files (e.g., `OrderService.java` ↔ `OrderServiceTest.java`). Extract assertions and translate to edge cases.
- Discovery: pair files by file name pattern (strip `Test` suffix)
- Extraction: `assertEquals`, `assertThrows`, `expect()` → functional edge cases and validation requirements
- Both test and production file sent to the same executor for concurrent processing
- Merged into the `test_insights` array in the `ExecutionFinding` JSON schema (§4)

- [x] **US048** (should): Test file paired by naming convention (strip Test suffix); assertEquals/assertThrows extracted into test_insights array
- [x] Gherkin: `docs/sdlc/features/E003-F020-test-suite-mining.feature`
- [x] Depends on: F017 (executor framework)
- [x] Classes: `TestFileMatcher`, `TestAssertionExtractor`, `PairedExecutionResolver`
- [x] New: `code2req.execution.test-suffixes` in `ExecutionConfig` / `application.properties` (configurable suffix list, default: `Test,IT`)
- [x] Modified: `Phase2Orchestrator` (test content resolution), `Task` (+pairedTestPath field), `TaskStoreSchema` (+paired_test_path column), `TaskStore` (+new column), `TestAssertionsPresentRule` (delegates to `TestFileMatcher`), `SemanticExecutor` (+structured `EXTRACTED TEST ASSERTIONS` section in prompt), `ExecutionConfig` (+testSuffixes)
- [x] Verify: `mvn test` — 440 tests pass (27 new: 12 TestFileMatcher, 11 TestAssertionExtractor, 4 PairedExecutionResolver)
- [ ] Manual: run with `--dry-run` on petclinic — verify test files paired and assertions extracted

---

### Epic E004 — Agentic Functional Requirement Extraction (PRD §2.3, §3.8, §6)

#### F021: Embabel Agent Framework Setup (US049)

Uncomment and configure Embabel in pom.xml. Embabel provides the GOAP (Goal-Oriented Action Planning) engine for Phase 3's agentic extraction.

- [x] **US049** (must): Embabel dependency uncommented in pom.xml, framework initializes at startup, AgentPlatform available for Phase 3
- [x] Gherkin: `docs/sdlc/features/E004-F021-embabel-setup.feature`
- [x] Depends on: Phase 2 completion, Embabel repo availability
- [x] Modified: `pom.xml` (uncomment embabel-agent-starter, set version 0.5.0, remove embabel-releases/embabel-snapshots repos)
- [x] Verify: `mvn compile` succeeds with Embabel on classpath
- [ ] Manual: run with `--dry-run` — confirm Embabel initialises without error

#### F022: CodebaseKnowledge Builder + Phase 3 Orchestrator (US050, PRD §2.3)

Pure-Java services that prepare data for the Embabel agent and handle output after it completes. The agent itself is focused solely on decision-making — data plumbing is separate.

**Phase3Orchestrator (pure Java):**
1. Queries SQLite for ALL Phase 1 findings (call graph edges, topic links, floating links, endpoint maps) and Phase 2 enriched `ExecutionFinding` records.
2. Assembles an in-memory `CodebaseKnowledge` domain model — a rich, queryable object graph grouping all structural + semantic knowledge.
3. Passes `CodebaseKnowledge` as initial working memory to the Embabel agent.
4. After agent completes, invokes pure-Java writers for output artifacts.

**CodebaseKnowledge domain model:**
- `StructuralGraph` — call graph edges, component stereotypes, endpoint registries, database access patterns, **all 6 entry point types (HTTP, SCHEDULED, KAFKA, RABBITMQ, ACTIVEMQ, EVENT_LISTENER)**.
- `SemanticEnrichment` — all Phase 2 `ExecutionFinding` records keyed by file path. Added `getAllTestInsights()`, `getTestFilePath()`, `getAllTestFilePaths()` convenience methods.
- `LinkRegistry` — topic links (producer↔consumer), floating links (HTTP calls), template links.
- Query methods: `getFlowCandidates()`, `findUnresolvedLinks()`, `getComponentsByType()`, `getCallersOf(target)`, `getCalleesOf(source)`, **`getEntryPoints()`**, **`getAllKnownMethods()`**, **`getEntryPointPriority()`**.
- **New types:** `EntryPoint` record + `EntryPointType` enum in `synthesis/domain/`, `MethodIdentifier` record in `synthesis/`.

**No fallback needed** — Embabel is the agent framework (decision-making), not a data-processing pipeline. If Embabel repo is unavailable, Phase 3 cannot run.

- [x] **US050** (must): Phase3Orchestrator aggregates Phase 1 (structural) + Phase 2 (enriched) data into CodebaseKnowledge with query methods (getFlowCandidates, getCallersOf, getCalleesOf, findUnresolvedLinks, getEntryPoints, getAllKnownMethods)
- [x] Gherkin: `docs/sdlc/features/E004-F022-codebase-knowledge.feature`
- [x] Depends on: F021 (Embabel integration), Phase 2 enriched data in SQLite
- [x] Classes: `Phase3Orchestrator`, `CodebaseKnowledge`, `StructuralGraph`, `SemanticEnrichment`, `LinkRegistry`, `EntryPoint` (+`EntryPointType`), `MethodIdentifier`
- [x] Verify: `mvn test` — Phase3Orchestrator tests pass (11 tests covering CodebaseKnowledge aggregation, all query methods, and entry point loading from SQLite)
- [x] Modified: `ExecutionFindingStore` (+findAllByType), `TopicLinkStore` (+findAll), `RunCommandTest` (updated Phase3Orchestrator constructor), `StructuralGraph` (+entry point fields + getEntryPoints + getAllKnownMethods + getEntryPointPriority), `Phase3Orchestrator` (+entry point deserialization), `CodebaseKnowledge` (+delegation methods), `SemanticEnrichment` (+test insight convenience methods)
- [x] **Phase 2 fix:** `Phase2Orchestrator` now resolves `Task.pairedTestPath` from `PairedExecutionResolver` during task creation (was previously discarded). This makes test file info available to Phase 3 without re-running test detection.
- [ ] **F023 dependency:** `Phase3Orchestrator.analyzeKnowledge()` is currently a pure-Java stub (counts flows by name). F023 replaces it with Embabel agent invocation — injects `AgentPlatform`, builds `ProcessOptions`, runs the agent, collects `FunctionalFlow` objects from the blackboard. See F023 for full spec. `Phase3Orchestrator` constructor has been prepared with AgentPlatform injection point.
- [x] Manual: `run --dry-run --manifest ...` — verify orchestrator loads data and entry points are discovered

#### F023: Embabel Agent — Goals, Actions, Output (US051, PRD §2.3, §3.8)

The Embabel agent that extracts functional requirements using Embabel's GOAP planner. The agent is a Spring `@Component` annotated with `@Agent`; its `@Action`-annotated methods are auto-discovered by Embabel's `DelegatingAgentScanningBeanPostProcessor`. `Phase3Orchestrator` places `CodebaseKnowledge` on the blackboard and invokes the agent via `AgentPlatform.runAgentFrom()`.

**Prerequisite — `@EnableAgents` on Application.java:**

Embabel's annotation scanning requires `@EnableAgents` on `Application.java` (or any `@Configuration` class). This registers `DelegatingAgentScanningBeanPostProcessor`.

```java
@SpringBootApplication
@EnableAsync
@EnableAgents
public class Application { ... }
```

**Agent definition (annotation-based, discovered at startup):**

```java
@Component
@Agent(name = "functional-requirement-extractor",
       description = "Extract functional requirements from enriched codebase analysis",
       planner = PlannerType.GOAP,
       scan = true)
public class FunctionalRequirementAgent { ... }
```

**World-state condition identifiers (GOAP preconditions/effects):**

Conditions are managed on the Embabel blackboard (`blackboard.setCondition(name, value)`) and drive GOAP planning. Defined as string constants in a `ConditionName` interface:

| Constant | Condition string | Set by | Purpose |
|---|---|---|---|
| `KNOWLEDGE_LOADED` | `codebase_knowledge_loaded` | Phase3Orchestrator (pre-agent) | CodebaseKnowledge is on the blackboard |
| `FLOWS_ANALYZED` | `flows_analyzed` | AnalyzeFindings | ExecutionFindings grouped into candidate FunctionalFlows |
| `AMBIGUITY_RESOLVED` | `ambiguity_resolved` | ResolveAmbiguity | Knowledge gaps searched via CodebaseKnowledge |
| `LINKS_XREF` | `links_cross_referenced` | CrossReferenceLinks | Floating links matched to known endpoints |
| `SPEC_SYNTHESIZED` | `spec_synthesized` | SynthesizeSpec | Final specification generated |
| `FLOWS_QUARANTINED` | `flows_quarantined` | QuarantineAction | Unresolvable flows flagged for human review |

**Blackboard data flow:**

```
Phase3Orchestrator                           Embabel Agent Blackboard
┌──────────────────────┐                    ┌──────────────────────────────┐
│ setCondition(K_LOADED)│───►               │ conditions: knowledge_loaded │
│ bind("knowledge",   )│───►                │ objects: CodebaseKnowledge   │
│   CodebaseKnowledge  │                    │                              │
└──────────────────────┘                    │ Action 1: analyzeFindings    │
                                            │ ├─ pre:  knowledge_loaded   │
        ▲                                   │ ├─ post: flows_analyzed     │
        │                                   │ └─ adds: FunctionalFlow[]   │
        │                                   │                              │
        │  objectsOfType(FunctionalFlow)    │ Action 2: resolveAmbiguity   │
        │  objectsOfType(AmbiguityGap)      │ ├─ pre:  flows_analyzed     │
        │  ← collect results ──────────────│ ├─ post: ambiguity_resolved  │
        │                                   │ └─ adds: AmbiguityGap[]     │
   Phase3Orchestrator                       │                              │
   (after agent completes)                  │ Action 3: crossRefLinks      │
                                            │ ├─ pre:  flows_analyzed     │
                                            │ ├─ post: links_cross_ref    │
                                            │ └─ updates: LinkMatch[]     │
                                            │                              │
                                            │ Action 4: synthesizeSpec     │
                                            │ ├─ pre:  flows,amb,links    │
                                            │ ├─ post: spec_synthesized   │
                                            │ └─ adds: FunctionalSpec     │
                                            │                              │
                                            │ Action 5: quarantineFlows    │
                                            │ ├─ pre:  flows_analyzed     │
                                            │ ├─ post: flows_quarantined  │
                                            │ └─ adds: quarantined list   │
                                            └──────────────────────────────┘
```

**Goals (discovered from `@AchievesGoal` annotations):**

| Goal | Precondition (world-state) | Description |
|---|---|---|
| `FunctionalFlowCoverage` | `flows_analyzed=T, ambiguity_resolved=T` | All candidate flows have complete descriptions |
| `BusinessRuleCompleteness` | `flows_analyzed=T, ambiguity_resolved=T` | All rules include pre/postconditions and errors |
| `TraceabilityVerified` | `flows_analyzed=T, ambiguity_resolved=T, spec_synthesized=T` | Every requirement maps to source code |
| `LinkConsistency` | `links_cross_referenced=T, spec_synthesized=T` | Floating links matched or documented |

**Actions (`@Action`-annotated methods on `FunctionalRequirementAgent`):**

| Method | `pre = {...}` | `post = {...}` | Description |
|---|---|---|---|
| `analyzeFindings` | `KNOWLEDGE_LOADED` | `FLOWS_ANALYZED` | Group ExecutionFindings by flow boundaries using call graph. Produce `FunctionalFlow` objects on blackboard. |
| `resolveAmbiguity` | `FLOWS_ANALYZED, KNOWLEDGE_LOADED` | `AMBIGUITY_RESOLVED` | Query `getCallersOf()` / `getCalleesOf()`. Fall back to raw source file reads if needed. |
| `crossReferenceLinks` | `FLOWS_ANALYZED` | `LINKS_XREF` | Match unresolved floating/topic links against known endpoints. |
| `synthesizeSpec` | `FLOWS_ANALYZED, AMBIGUITY_RESOLVED, LINKS_XREF` | `SPEC_SYNTHESIZED` | Aggregate resolved knowledge. Invoke pure-Java output writers. |
| `quarantineFlows` | `FLOWS_ANALYZED` | `FLOWS_QUARANTINED` | Mark flows exceeding investigation budget as `AWAITING_HUMAN_REVIEW`. |

Each method annotated as:
```java
@Action(description = "Group ExecutionFindings by flow boundaries",
        pre = {ConditionName.KNOWLEDGE_LOADED},
        post = {ConditionName.FLOWS_ANALYZED})
@AchievesGoal(description = "All candidate flows have complete descriptions")
public List<FunctionalFlow> analyzeFindings(
        @Provided CodebaseKnowledge knowledge,
        @State List<ExecutionFinding> findings) { ... }
```

**Guardrails (enforcement mapping):**

| Guardrail | Config property | Enforcement mechanism |
|---|---|---|
| `max-investigation-steps-per-flow` (default: 5) | `code2req.execution.max-investigation-steps-per-flow` | QuarantineAction tracks per-flow step count via blackboard counter |
| `max-tokens-per-run` (default: 500000) | `code2req.execution.max-tokens-per-run` | `ProcessOptions(budget = Budget.DEFAULT.withTokens(maxTokens))` |
| `ambiguity-confidence-threshold` (default: 0.7) | `code2req.execution.ambiguity-confidence-threshold` | ResolveAmbiguity action checks LLM-derived confidence; below threshold → sets condition to trigger QuarantineAction |

**User interaction SPI (deferred interactive mode):**

Agent actions query a `UserInteractionService` before falling back to LLM inference or quarantine. The SPI is designed now; the interactive terminal implementation is deferred.

```java
public interface UserInteractionService {
    /** Ask the user a yes/no/skip question about an ambiguity gap.
     *  @return Optional.of(answer) if user responded, empty if skip/timeout */
    Optional<String> ask(AmbiguityGap gap, Duration timeout);
}
```

| Implementation | Scope | Behavior |
|---|---|---|
| `NoOpUserInteractionService` | **F023 (now, default)** | Always returns `Optional.empty()` — agent falls through to LLM/ quarantine. No interactive prompts. |
| `InteractiveUserInteractionService` | **Post-F025 (deferred)** | Prompts via Spring Shell `LineReader` with configurable timeout. `--interactive` flag enables it. |

**Action flow with UserInteractionService:**

```
ResolveAmbiguity action:
  1. Compute gap signature = SHA-256(flow_id + missing_context)
  2. Check UserResponseStore for cached answer → if found, use it
  3. Call userInteractionService.ask(gap, timeout)
  4. If answer present → bind to blackboard as resolved context, set confidence = 1.0
  5. Save answer to UserResponseStore (persists across runs)
  6. If no answer → fall back to LLM inference
  7. If LLM confidence below threshold → create AmbiguityGap → quarantine
```

This ensures:
- Interactive mode is purely additive — non-interactive runs behave exactly as before.
- User answers survive crashes (stored in SQLite).
- The same question is never asked twice (signature cache check).
- Skipped questions fall through to the same logic as non-interactive mode.

**New domain/store classes:**

- `synthesis/interaction/UserInteractionService` — interface (SPI)
- `synthesis/interaction/NoOpUserInteractionService` — `@Component`, returns empty
- `model/UserResponse` — record: `signature`, `question`, `answer`, `confidenceGained`, `createdAt`
- `store/UserResponseStore` — Spring JDBC, `findBySignature()` / `save()` queries
- New table: `CREATE TABLE IF NOT EXISTS user_responses (signature TEXT PRIMARY KEY, question TEXT NOT NULL, answer TEXT NOT NULL, confidence_gained REAL DEFAULT 0.0, created_at TEXT DEFAULT (datetime('now')))`

**Phase3Orchestrator agent integration (modifies existing F022 class):**

`Phase3Orchestrator.execute()` is replaced from pure-Java stub to full agent invocation with crash recovery marker:

1. **Check Phase 3 marker:** Query marker task `SHA-256("__phase3_marker__")`. If status is `ENRICHED` and `--force-phase3` is not set → skip Phase 3 entirely, return last result from metrics. If status is `ENRICHING` → crash detected, reset to `PENDING`, clean `.tmp.*` output files.
2. **Upsert marker to `ENRICHING`:** `taskStore.save(markerTask)` with status `ENRICHING`. Crash after this point → `--resume` detects `ENRICHING` and cleans up.
3. **Inject** `AgentPlatform` and `ExecutionConfig` (new constructor params)
4. **Look up** the deployed agent: `agentPlatform.agents().filter(name = "functional-requirement-extractor")`
5. **Create** a fresh `InMemoryBlackboard`, set `KNOWLEDGE_LOADED = true`
6. **Bind** `CodebaseKnowledge` via `blackboard.bind("knowledge", knowledge)`
7. **Build** `ProcessOptions` with token budget
8. **Run**: `agentPlatform.runAgentFrom(agent, options, emptyMap())` → `CompletableFuture<AgentProcess>`
9. **Wait**: `future.get()` (block until agent finishes)
10. **Catch failures**: If agent throws → update marker to `FAILED`, abort.
11. **Collect**:
    - `agentProcess.blackboard.objectsOfType(FunctionalFlow.class)` — completed flows
    - `agentProcess.blackboard.objectsOfType(AmbiguityGap.class)` — unresolvable gaps
12. **Persist quarantined tasks**: For each `AmbiguityGap` with `confidence < ambiguityConfidenceThreshold` or step count exceeded:
    - Create `HUMAN_REVIEW_REASON` finding with JSON `{ "reason_type": "STEPS_EXCEEDED"|"LOW_CONFIDENCE", "detail": "...", "confidence": 0.0 }`
    - Save via `executionFindingStore.save()`, update task statuses to `AWAITING_HUMAN_REVIEW`
13. **Build Phase3Result**: Include `awaitingReviewReasons` — list of flow names + reason strings
14. **Write output files** (temp file + atomic rename):
    - `MarkdownSpecWriter.write()` → writes to `spec-output/.tmp.{flow-name}.md`, renames to `spec-output/{flow-name}.md`
    - `SemanticManifestWriter.write()` → writes to `spec-output/.tmp.semantic_manifest.json`, renames to `spec-output/semantic_manifest.json`
15. **Validate**: `SemanticManifestWriter` validates against PRD §6.2 schema; failure → update marker to `FAILED`
16. **Update marker to `ENRICHED`**: All outputs are now complete and valid. Crash before this point → `--resume` sees `ENRICHING` and re-runs.
17. **Persist metrics**: Write `Metric` record with token count, flows extracted, etc.

**Phase3Result record updated:**

```java
public record Phase3Result(
    int flowsExtracted,
    int ambiguityGaps,
    int awaitingReview,
    List<String> flowNames,
    List<String> awaitingReviewReasons   // NEW: flow name + reason per quarantined flow
) {
    public static Phase3Result empty() { ... }
}
```

**Output domain classes (POJO records in `synthesis/domain/`):**

- `FunctionalFlow` — flowId, name, trigger, steps `List<FlowStep>`, outcomes, businessRules `List<BusinessRule>`, traceabilityEntries `List<TraceabilityEntry>`
- `FlowStep` — stepIndex, componentType, description, sourceFile, astSignature
- `BusinessRule` — ruleId, description, preconditions, postconditions, errorBehavior, sourceFiles
- `TraceabilityEntry` — requirementId, filePath, astSignature, lines
- `AmbiguityGap` — flowId, missingContext, suggestedApproach, confidence
- `EndpointSpec` — path, method, description, sourceFile
- `CodePattern` — name, description, locations

**Output writers (pure Java in `synthesis/output/`):**

- `MarkdownSpecWriter` — receives `List<FunctionalFlow>` + `List<AmbiguityGap>`, writes `spec-output/{flow-name}.md` per PRD §6.1. Uses `OutputConfig.specDir()`.
- `SemanticManifestWriter` — receives `List<FunctionalFlow>`, writes `spec-output/semantic_manifest.json` per PRD §6.2 JSON Schema. Validates against bundled schema before writing (same pattern as F017's `ExecutionFindingValidator`).

**Output schema validation:**

New file `src/main/resources/schema/semantic-manifest-schema.json` (mirroring PRD §6.2). `SemanticManifestWriter` validates its own JSON output against this schema before persisting. Validation failure → log error, `Phase3Result` marked failed.

**Test strategy:**

| Level | What | How |
|---|---|---|
| Unit (actions) | Each `@Action` method in isolation | `IntegrationTestUtils.dummyProcessContext()` provides real `InMemoryBlackboard`. Create `FunctionalRequirementAgent`, invoke method, inspect blackboard state. |
| Unit (orchestrator) | Phase3Orchestrator with mocked agent | `@MockBean AgentPlatform`, verify `runAgentFrom()` called, stub result with pre-built blackboard containing `FunctionalFlow`. |
| Integration (dry-run) | Existing dry-run path | Unchanged — still calls `simulateKnowledge()`. No agent required. |
| Integration (full) | End-to-end through RunCommand | Enable `AgentPlatformAutoConfiguration` in dedicated test profile. Provide no-LLM `AgentPlatform` test bean. |

- **US051** (must): Embabel agent using GOAP with world-state conditions: `@Agent` + `@Action` + `@AchievesGoal`, blackboard-driven, invoked by Phase3Orchestrator via `AgentPlatform.runAgentFrom()`
- [x] Gherkin: `docs/sdlc/features/E004-F023-embabel-agent.feature`
- [ ] Depends on: F022 (CodebaseKnowledge), Embabel framework, `@EnableAgents` on `Application.java`, Phase3Orchestrator modifications (inject `AgentPlatform` + `ExecutionConfig`)
- [ ] New: `@EnableAgents` on `Application.java`
- [ ] Classes (agent): `FunctionalRequirementAgent` (`@Component` + `@Agent`), `ConditionName` (constants interface)
- [ ] Classes (interaction SPI): `UserInteractionService` (interface), `NoOpUserInteractionService` (`@Component`, default), `InteractiveUserInteractionService` (deferred post-F025)
- [ ] Classes (domain): `FunctionalFlow`, `BusinessRule`, `EndpointSpec`, `CodePattern`, `AmbiguityGap`, `FlowStep`, `TraceabilityEntry`, `UserResponse`
- [ ] Classes (output): `MarkdownSpecWriter`, `SemanticManifestWriter`
- [ ] Modified: `Phase3Orchestrator.java` (+`AgentPlatform`, +`ExecutionConfig`, agent invocation), `Application.java` (+`@EnableAgents`), `Phase3OrchestratorTest` (update constructor)
- [ ] Schema: `src/main/resources/schema/semantic-manifest-schema.json`
- [ ] Verify: `mvn test` — action unit tests pass with `dummyProcessContext()`, orchestrator test with mocked `AgentPlatform`
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

#### F026: Review CLI Command (US055)

Dedicated `review` command for managing `AWAITING_HUMAN_REVIEW` tasks and functional flows. Provides inspection, diagnosis, and resolution in a single workflow.

**Finding type:** `HUMAN_REVIEW_REASON` (new constant in `FindingType`). JSON payload:
```json
{
  "reason_type": "HOP_DEPTH" | "STEPS_EXCEEDED" | "LOW_CONFIDENCE",
  "detail": "Max hop depth 3 exceeded for dependency WarehouseClient.java",
  "confidence": 0.4,
  "source_task_id": "a1b2c3d4e5f6...",
  "flow_name": "PaymentProcessing"
}
```

**CLI commands:**

| Command | Description |
|---|---|
| `review list` | Lists all `AWAITING_HUMAN_REVIEW` tasks grouped by reason type, with flow name, detail, and confidence. |
| `review show --task <id>` | Shows full context: task findings, quarantine reason, source file path, trace chain. |
| `review accept --task <id>` | Accept gap: flow documented in spec Section 5 as explicitly unresolved. Task reset to INDEXED (findings NOT deleted — preserved for spec output). |
| `review reset --task <id>` | Reject gap: delete findings, reset to INDEXED. Next `run` re-qualifies via planner. |
| `review accept-all` | Batch accept all quarantined flows. |
| `review reset-all` | Batch reset all for re-processing. |

**Spec output integration:**

`MarkdownSpecWriter` reads tasks with `HUMAN_REVIEW_REASON` findings and renders them in Section 5:
```
## 5. Unresolved Dependencies & Review Tasks
- [Flow: PaymentProcessing] — review accept accepted gap: 3 investigation steps exhausted
  - Source: OrderService.java → PaymentGatewayClient.java (not in scan targets)
  - Reason: STEPS_EXCEEDED (max-investigation-steps-per-flow = 3)
  - CLI: review show --task a1b2c3d4e5f6...
```

`SemanticManifestWriter` reads quarantined flows and sets `review_required: true` with `unresolved_reason` object per PRD §6.2 schema.

- **US055** (should): `review` CLI command lists AWAITING_HUMAN_REVIEW tasks grouped by reason type, supports accept/reset per-task and batch, integrates with spec output Section 5
- [ ] Gherkin: `docs/sdlc/features/E004-F026-review-command.feature`
- [ ] Depends on: F023 (Phase 3 quarantines), F018 (Phase 2 hop depth quarantines), `FindingType.HUMAN_REVIEW_REASON`
- [ ] New finding type: `HUMAN_REVIEW_REASON` in `FindingType`
- [ ] Modified: `MarkdownSpecWriter` (Section 5 rendering), `SemanticManifestWriter` (`review_required` + `unresolved_reason`), `Phase3Orchestrator` (persist `HUMAN_REVIEW_REASON` findings + task statuses), `RunCommand.recoverOrphanedTasks` (+`AWAITING_HUMAN_REVIEW` → `INDEXED`)
- [ ] Classes: `ReviewCommand` (shell), `ReviewService` (business logic)
- [ ] Verify: `mvn test` — review list/accept/reset unit tests, spec Section 5 output verified against quarantine data
- [ ] Manual: `run --manifest ...` then `review list` — verify quarantined tasks appear with reasons

---

## Story Index

| Story | Feature | Priority | Phase |
|-------|---------|----------|-------|
| US041 | F016 | must | E003 — Planner |
| US042 | F016 | should | E003 — Planner |
| US043 | F017 | must | E003 — Executor |
| US044 | F017 | should | E003 — Executor (dry-run) |
| US045 ✓ | F018 | must | E003 — Orchestrator |
| US046 ✓ | F019 | must | E003 — `plan` command |
| US047 ✓ | F019 | must | E003 — `run` command |
| US048 ✓ | F020 | should | E003 — Test Suite Mining |
| US049 ✓ | F021 | must | E004 — Embabel Agent Framework Setup |
| US050 ✓ | F022 | must | E004 — CodebaseKnowledge + Orchestrator |
| US051 | F023 | must | E004 — Embabel Agent (Goals, Actions, Output) |
| US052 | F024 | should | E004 — Quality Audit |
| US055 | F026 | should | E004 — Review CLI Command |

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
└──────────┬───────────┘                         └───────────────────────────────────┘
           │                                                   │
           │               ┌────────────────────────────────────┘
           │               ▼
           │    ┌───────────────────────────────────┐
           └───►│ F026: Review CLI Command          │
                │ (AWAITING_HUMAN_REVIEW lifecycle) │
                └───────────────────────────────────┘

F020: Test Suite Mining ── depends on F017 (executor framework)

Separation of concerns: Data I/O and output writing are PURE JAVA, not agentic.
The Embabel agent handles ONLY decision-making (what to investigate, goal tracking).
```

## Modified / New Files Summary

### Modified (existing files changed, F016-related additions in *italic*, F022 additions in **bold**, completed items prefixed with ✓):
| File | Change |
|------|--------|
| ✓ `Application.java` | Add `@EnableAsync`. Add `@EnableAgents` (F023 Embabel annotation scanning). |
| ✓ `config/AppConfig.java` | Add `@Bean("orchestratorTaskExecutor")` `ThreadPoolTaskExecutor` (core=5, max=10, queue=1000) |
| ✓ `application.properties` | Add OpenRouter config (`spring.ai.openai.base-url`, `spring.ai.openai.api-key`, `spring.ai.openai.chat.options.model`, `spring.config.import=optional:file:.env`); rename thread prefix to `c2r-orchestrator-`; add `code2req.output.*` properties |
| ✓ `model/TaskStatus.java` | Add `AWAITING_HUMAN_REVIEW` |
| ✓ `model/AnalysisFinding.java` | Add `default boolean isResolved() { return true; }` |
| ✓ `analyzer/callgraph/CallGraphEdge.java` | Override `isResolved()` to return `STATUS_RESOLVED.equals(resolvedStatus)` |
| ✓ `store/ExecutionFindingStore.java` | `saveAllForTask` uses `finding.isResolved()` instead of hardcoded `true`. Added `countByTaskIdAndType()` query for planner. **Added `findAllByType()` query for Phase 3 CodebaseKnowledge aggregation.** |
| ✓ `store/FindingType.java` | Add `SPRING_DATA_INTERFACE`, `DATABASE_PROCEDURE_CALL`, `CONSTRAINT_VALIDATOR`, `NATIVE_SQL_QUERY`, `JPQL_HQL_QUERY`, `SEMANTIC_ENRICHMENT`. Add `HUMAN_REVIEW_REASON` (F026). |
| ✓ `pipeline/ScanPipeline.java` | Add re-classification step after `persistFindings()` to create granular FindingType rows (now 5 mapping cases) |
| ✓ `store/FloatingLinkStore.java` | Add `findSourceFilePathsByResolvedStatus(String)` query for planner |
| ✓ `planner/Phase2Planner.java` | Refactored with Strategy Pattern — delegates to 9 `QualificationRule` components. Extended with `ExecutionFindingStore` check to skip tasks that already have SEMANTIC_ENRICHMENT findings (prevents re-enrichment after reset). |
| ✓ `shell/StatusCommand.java` | Add Phase 2 metrics (tokens, cost, enriched count) + Phase 3 metrics (flow extraction rate, ambiguity gaps) |
| ✓ `pom.xml` | Add `spring-ai-client-chat`, `spring-ai-autoconfigure-model-chat-client` dependencies |
| ✓ `model/ExecutionConfig.java` | Converted to `@ConfigurationProperties(prefix="code2req.execution")`; defaults in `application.properties`; removed `defaultConfig()`; removed from `ProjectManifest` |
| ✓ `application.properties` | Add `code2req.execution.*` properties with all pipeline defaults |
| `project-manifest.yaml` | Removed `execution:` block entirely — config now in `application.properties` |
| ✓ `model/OutputConfig.java` | Converted to `@ConfigurationProperties(prefix="code2req.output")`; removed `defaultConfig()` and `@JsonProperty` |
| ✓ `model/ProjectManifest.java` | Removed `output` field and `outputConfig()` method |
| ✓ `output/IndexWriter.java` | `OutputConfig` injected via constructor instead of read from manifest |
| ✓ `shell/CleanCommand.java` | `OutputConfig` injected via constructor; removed `resolveOutputConfig()` |
| ✓ `config/AppConfig.java` | Added `OutputConfig.class` to `@EnableConfigurationProperties` |
| ✓ `project-manifest.yaml` | Removed `output:` block — config now in `application.properties` |
| ✓ `model/Task.java` | Added `String pairedTestPath` field |
| ✓ `store/TaskStoreSchema.java` | Added `paired_test_path TEXT` column |
| ✓ `store/TaskStore.java` | Updated `save()` and `rowMapper` for new column |
| ✓ `orchestrator/Phase2Orchestrator.java` | Inject `PairedExecutionResolver`; resolve and pass test content to executor. **F022: Populate `Task.pairedTestPath` from `PairedExecutionResolver.resolve()` (was previously discarded).** |
| ✓ `executor/SemanticExecutor.java` | Inject `TestAssertionExtractor`; add `EXTRACTED TEST ASSERTIONS` structured section to prompt |
| ✓ `planner/rule/TestAssertionsPresentRule.java` | Refactored to delegate to `TestFileMatcher` |
| ✓ `model/ExecutionConfig.java` | Added `List<String> testSuffixes` with `resolvedTestSuffixes()` defaulting to `["Test", "IT"]` |
| **`synthesis/StructuralGraph.java`** | **F022: Add fields + accessors for all 6 entry point types (SCHEDULED_TASK, KAFKA_LISTENER, RABBITMQ_LISTENER, ACTIVEMQ_LISTENER, EVENT_LISTENER). Add `getEntryPoints()`, `getAllKnownMethods()`, `getEntryPointPriority()` methods. Backward-compatible 4-arg constructor delegates to new 9-arg constructor.** |
| **`synthesis/CodebaseKnowledge.java`** | **F022: Add `getEntryPoints()`, `getAllKnownMethods()`, `getAllTestInsights()`, `getAllTestFilePaths()` delegation methods.** |
| **`synthesis/SemanticEnrichment.java`** | **F022: Add `getAllTestInsights()`, `getTestFilePath(filePath)`, `getAllTestFilePaths()` convenience methods for test insight extraction.** |
| **`synthesis/Phase3Orchestrator.java`** | **F022: `buildStructuralGraph()` now deserializes all 6 entry point finding types. Added `getTestFileMapping()` for paired test path lookup. AgentPlatform injection point prepared for F023.** |

### New files:
| Package | Files |
|---------|-------|
| `planner/` | `QualificationRule.java` (interface), `PlanningContext.java` (shared data access), `PlannerDecision.java` (record), `QualificationReason.java` (enum) |
| `planner/rule/` | `SpringDataInterfaceRule`, `StoredProcedureCallRule`, `CustomConstraintValidatorRule`, `ScheduledTaskPresentRule`, `UnresolvedSignaturesRule`, `UnresolvedFloatingLinkRule`, `TestAssertionsPresentRule`, `NativeSqlQueryRule`, `JpqlHqlQueryRule`, `AbstractFindingTypeRule` (base class) |
| ✓ `executor/` | `SemanticExecutor` (error-feedback retry), `ExecutionFindingValidator`, `ContextBudgetCalculator`, `SimulationStub` |
| ✓ `model/` | `ExecutionFinding` (nested record hierarchy matching §4 schema), `UserResponse` (F023) |
| ✓ `resources/schema/` | `execution-finding-schema.json` (embedded §4 JSON Schema). `semantic-manifest-schema.json` (F023, PRD §6.2 schema). |
| ✓ `orchestrator/` | `Phase2Orchestrator`, `EnrichmentDag`, `BranchState`, `CompletionStatus` |
| ✓ `executor/testmining/` | `TestFileMatcher`, `TestAssertionExtractor`, `PairedExecutionResolver` |
| `synthesis/agent/` | `FunctionalRequirementAgent` (`@Component` + `@Agent`, actions as `@Action` methods), `ConditionName` (world-state condition string constants) |
| `synthesis/interaction/` | `UserInteractionService` (SPI), `NoOpUserInteractionService` (`@Component`, default). `InteractiveUserInteractionService` deferred post-F025. |
| `synthesis/domain/` | `FunctionalFlow`, `BusinessRule`, `EndpointSpec`, `CodePattern`, `AmbiguityGap`, `FlowStep`, `TraceabilityEntry` |
| `synthesis/output/` | `MarkdownSpecWriter`, `SemanticManifestWriter` |
| `synthesis/audit/` | `Phase3QualityAudit`, `AuditSample`, `AuditReport` |
| ✓ `shell/` | `PlanCommand`, `RunCommand`. `ReviewCommand`, `ReviewService` (F026). |
| ✓ `synthesis/` | `Phase3Result` (updated in F023 with `awaitingReviewReasons`), `Phase3Orchestrator` (now implemented; modified in F023: +`AgentPlatform`, +`ExecutionConfig`, Phase 3 marker upsert, temp-file output, persist `HUMAN_REVIEW_REASON` findings + task statuses), `CodebaseKnowledge`, `StructuralGraph`, `SemanticEnrichment`, `LinkRegistry`, **`MethodIdentifier` (F022 — for orphaned method detection)** |
| ✓ `synthesis/domain/` | **`EntryPoint` (F022 — unified entry point record), `EntryPointType` (F022 — enum: HTTP, SCHEDULED, KAFKA, RABBITMQ, ACTIVEMQ, EVENT_LISTENER).** Also used by F023 domain records (`FunctionalFlow`, `BusinessRule`, etc.). |
| ✓ `store/TopicLinkStore.java` | **Added `findAll()` query for Phase 3 LinkRegistry** |
| `store/UserResponseStore.java` | `findBySignature()`, `save()` for user interaction cache (F023). |
| ✓ `shell/RunCommand.java` | `--resume` now handles `AWAITING_HUMAN_REVIEW` → `INDEXED` (F026). `--resume` also calls `recoverPhase3Marker()` (ENRICHING → PENDING, clean .tmp files). `--force-phase3`, `--interactive`, `--interactive-timeout` flags. Phase 3 skip-if-ENRICHED guard. |
| ✓ `shell/RunCommandTest.java` | **Updated `Phase3Orchestrator` constructor to pass all 5 stores** |
 
## Verification Guide

- Unit: `mvn test`
- Manual dry-run: `run --dry-run --manifest project-manifest.yaml` — no API calls made
- Manual full: `run --manifest project-manifest.yaml` — needs `OPENROUTER_API_KEY` env set (model configurable via `OPENROUTER_MODEL`)
- JSON schema validation: enriched `ExecutionFinding` output validates against PRD §4 schema; `semantic_manifest.json` output validates against PRD §6.2 schema (F023)
- Phase 3 agent: `@EnableAgents` required on `Application.java` — builds `FunctionalRequirementAgent` from `@Agent` + `@Action` annotations
- Phase 2 only: `run --manifest ...` then `status` — confirm per-task enrichment status + token counters
- Phase 2 + Phase 3: `run --manifest ... --dry-run` — verify all phases complete end-to-end without network calls
- Output inspection: `spec-output/` contains both `*.md` (functional flows) and `semantic_manifest.json` (traceability graph)
- Phase 3 quarantine: verify flows marked `AWAITING_HUMAN_REVIEW` appear in spec Section 5
- Review lifecycle: `review list` shows quarantined tasks grouped by reason; `review accept/reset` per-task and batch; `run --resume` recovers `AWAITING_HUMAN_REVIEW` tasks
- Phase 3 crash recovery: kill process during Phase 3, run `run --resume` — verify "Phase 3 was interrupted" warning, stale `.tmp.*` files cleaned, Phase 3 re-runs cleanly
- Phase 3 idempotent skip: run `run` twice without changes — second run skips Phase 3 ("use --force-phase3 to re-run")
- Phase 3 force re-run: `run --force-phase3` re-runs Phase 3 even when marker is ENRICHED
- User interaction (deferred): `UserInteractionService` SPI + `NoOpUserInteractionService` active by default. Verify that `run` without `--interactive` never blocks or prompts — non-interactive behavior is identical before and after F023.
