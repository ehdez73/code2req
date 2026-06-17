# E003/E004 — Semantic Enrichment & Synthesis (Phase 2 + Phase 3)

## Quick Start
- Build: `mvn clean compile`
- Test: `mvn test`
- Run: `mvn spring-boot:run` then:
  - `plan --manifest project-manifest.yaml` — dry DAG view (no LLM)
  - `run --manifest project-manifest.yaml` — Phase 2 + Phase 3
  - `run --manifest ... --llm-threshold 0` — run without LLM enrichment
  - `run --manifest ... --dry-run` — simulation mode (no API calls)

## Prerequisites (Already Done in Phase 1)
- SQLite store with `tasks`, `execution_findings`, `topic_links`, `floating_links`, `metrics` tables
- `TaskStore` with `PENDING`/`RUNNING`/`SUCCESS`/`FAILED` status queries (`findByStatus`)
- `ExecutionFindingStore` (save, saveAllForTask, countByType)
- `MetricsStore` (save, getLatestForPhase)
- `TaskIdHasher` (deterministic SHA-256 — needed for discovered_dependency tasks)
- JSON Schema validation dependency (`networknt/json-schema-validator` in pom.xml)
- Spring AI OpenAI + Anthropic dependencies in pom.xml
- Spring AOP + `@Async` pool configured in `application.properties`
- Spring Shell CLI infrastructure (`scan`, `resume`, `validate`, `status`, `clean`)
- `ProjectManifest` with `ExecutionConfig` (max-concurrent-llm-calls, max-discovery-depth, semantic-validation-sample-rate)

## Key Decisions

1. **Phase 2 is Epic E003** (Semantic Enrichment), **Phase 3 is Epic E004** (Semantic Synthesis) — both numbered sequentially after E001/E002 to keep the SDLC context clean.
2. **Embabel will be uncommented** for Phase 3 map-reduce synthesis.
3. **`@EnableAsync`** must be added to `Application.java` — currently absent.
4. **`TaskStatus` needs `AWAITING_HUMAN_REVIEW`** — required by PRD §5.2 (max hop depth) and §3.5 (branch isolation).
5. **`plan` command** is purely a DAG read — queries SQLite for qualified tasks, displays what would run. Zero LLM calls.
6. **`run` command** orchestrates Phase 2 (LLM enrichment) then Phase 3 (synthesis) with a synchronization barrier in between.
7. **`status` command** already exists — extend to show Phase 2 counters (tokens consumed, estimated cost, per-task enrichment status).
8. **Spring AI `ChatClient.Builder`** is auto-configured when OpenAI/Anthropic credentials are present — no manual bean creation needed.

---

## Progress

### Epic E003 — Semantic Enrichment (PRD §2.2, §3.4–§3.7)

#### F016: Planner (US041, US042)

Reads the SQLite task store after Phase 1 and determines which tasks qualify for LLM enrichment. Pure query + rule engine — no LLM calls.

Qualification rules (§2.2):
- File has > `llm-unresolved-threshold` (default: 5) unresolved signatures
- File is a Spring Data interface (empty AST body — e.g., `CrudRepository`, `JpaRepository`)
- File contains a stored procedure call flagged for LLM interpretation
- File is a custom `ConstraintValidator` with a complex `isValid` body
- File has a paired test file with assertions needing semantic extraction

- [ ] Gherkin: `docs/sdlc/features/E003-F016-planner.feature`
- [ ] Depends on: Phase 1 complete (SQLite populated with task rows and execution_findings)
- [ ] Classes: `Phase2Planner`, `PlannerDecision`, `PlannerQualificationReason`
- [ ] Modified: `ExecutionConfig` (already has `maxConcurrentLlmCalls`, `maxDiscoveryDepth`, `semanticValidationSampleRate`)
- [ ] Verify: `mvn test` — planner correctly qualifies/doesn't qualify based on task findings
- [ ] Manual: run `plan --manifest ...` — confirm list matches expectations

#### F017: LLM Executor Framework (US043, US044)

Individual file enrichment workers. Each executor receives the pre-resolved structural context from Phase 1 plus raw source file content. LLM prompt instructs the model to NOT resolve structural dependencies (already done) and focus on business semantics.

Key behaviors:
- Spring `@Async("orchestratorTaskExecutor")` method returning `CompletableFuture<ExecutionFinding>`
- Exponential backoff: initial 2s, multiplier 2.0, cap 60s, max 3 retries (PRD §5.4)
- Context budgeting: if combined token weight > 80% of model context window, trigger pre-summarization step (PRD §3.6)
- Output validated against JSON Schema §4 before transition to `SUCCESS`
- `--dry-run` mode: Spring AI calls intercepted by local stubs returning deterministic static JSON
- Persists enriched JSON to `execution_findings` table via `ExecutionFindingStore`
- Attaches `discovered_dependency` array when unindexed runtime deps uncovered (PRD §3.5)

- [ ] Gherkin: `docs/sdlc/features/E003-F017-llm-executor.feature`
- [ ] Depends on: F016 (Planner), `@EnableAsync` on Application.java, Spring AI auto-configuration
- [ ] Classes: `SemanticExecutor`, `ExecutionFindingValidator` (JSON Schema), `ContextBudgetCalculator`, `SimulationStub`
- [ ] New finding types in `FindingType`: `SEMANTIC_ENRICHMENT` (or store the full `ExecutionFinding` JSON via `execution_findings`)
- [ ] Verify: `mvn test` — executor produces valid ExecutionFinding JSON, dry-run produces deterministic output
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

- [ ] Gherkin: `docs/sdlc/features/E003-F018-orchestrator.feature`
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

- [ ] Gherkin: `docs/sdlc/features/E003-F019-cli-run-command.feature`
- [ ] Depends on: F016, F017, F018, E004 (Phase 3 output)
- [ ] Classes: `PlanCommand`, `RunCommand`
- [ ] Modified: `StatusCommand` (Phase 2 metrics columns)
- [ ] Verify: `mvn test` — plan produces correct DAG, run with `--dry-run` completes without network calls
- [ ] Manual: `plan --manifest ...` then `run --dry-run --manifest ...` — verify end-to-end flow

#### F020: Test Suite Mining (US048, PRD §3.4)

Match production files with paired test files (e.g., `OrderService.java` ↔ `OrderServiceTest.java`). Extract assertions and translate to edge cases.
- Discovery: pair files by file name pattern (strip `Test` suffix)
- Extraction: `assertEquals`, `assertThrows`, `expect()` → functional edge cases and validation requirements
- Both test and production file sent to the same executor for concurrent processing
- Merged into the `test_insights` array in the `ExecutionFinding` JSON schema (§4)

- [ ] Gherkin: `docs/sdlc/features/E003-F020-test-suite-mining.feature`
- [ ] Depends on: F017 (executor framework)
- [ ] Classes: `TestFileMatcher`, `TestAssertionExtractor`, `PairedExecutionResolver`
- [ ] Verify: `mvn test` — paired test files produce `test_insights` with extracted scenarios
- [ ] Manual: run with `--dry-run` on petclinic — verify test files paired and assertions extracted

---

### Epic E004 — Semantic Synthesis (PRD §2.3, §3.8, §6)

#### F021: Embabel Integration (US049)

Uncomment and configure Embabel in pom.xml. Embabel is the map-reduce pipeline for Phase 3 synthesis.

Pre-requisite check: Embabel snapshot repo accessibility. If repo is unreachable, fall back to a lightweight Java-only alternative (streaming grouping + Markdown generation).

- [ ] Gherkin: `docs/sdlc/features/E004-F021-embabel-setup.feature`
- [ ] Depends on: Phase 2 completion, Embabel repo availability
- [ ] Modified: `pom.xml` (uncomment embabel-agent-starter), `application.properties` (Embabel config if needed)
- [ ] Verify: `mvn compile` succeeds with Embabel on classpath
- [ ] Manual: run with `--dry-run` — confirm Embabel initialises without error

#### F022: Map-Reduce Synthesis Pipeline (US050, PRD §2.3)

Process enriched `ExecutionFinding` records from Phase 2 through Embabel's map-reduce:
- Stream enriched JSON from SQLite using streaming cursors (ResultSet streaming)
- Map phase: group by module boundaries
- Reduce phase: collapse duplicate validations, merge overlapping data maps
- Generate clean Module Summaries

Algorithmic link consolidation (confirmed in Phase 3):
- Topic links: confirm Phase 1 matches, cross-manifest pairs resolved
- Floating links: open HTTP contracts assessed against known endpoints (confidence ≥ 85% → `RESOLVED_FLOATING_LINK`)

- [ ] Gherkin: `docs/sdlc/features/E004-F022-map-reduce-synthesis.feature`
- [ ] Depends on: F021 (Embabel), Phase 2 enriched data
- [ ] Classes: `Phase3Orchestrator`, `SynthesisMapper`, `SynthesisReducer`, `ModuleSummary`
- [ ] Verify: `mvn test` — module summaries produced from sample enriched data
- [ ] Manual: `run --dry-run --manifest ...` — verify synthesis phase completes with summaries

#### F023: Semantic Manifest + Markdown Output (US051, PRD §6)

Phase 3 produces two matching artifacts:
1. **Human-centric Markdown Specification** — per functional flow, formatted as defined in PRD §6.1
2. **Machine-readable `semantic_manifest.json`** — structured JSON with AST tracing coordinates and business mappings

- [ ] Gherkin: `docs/sdlc/features/E004-F023-output-artifacts.feature`
- [ ] Depends on: F022 (synthesis pipeline)
- [ ] Classes: `MarkdownSpecWriter`, `SemanticManifestWriter`, `FunctionalFlow`, `FlowBuilder`
- [ ] Output format: Markdown per PRD §6.1 template; JSON with full traceability
- [ ] Output path: `spec-output/` (consistent with Phase 1's `code-graph-index.json`)
- [ ] Verify: `mvn test` — Markdown matches expected template, JSON validates against schema
- [ ] Manual: `run --dry-run --manifest ...` — inspect `spec-output/` for both artifacts

#### F024: Quality Audit (US052, PRD §3.8)

Semantic Validation Agent performs randomized sampling of enriched findings against raw source files:
- Sample rate: `semantic-validation-sample-rate` from manifest (default: 20%)
- Pass threshold: ≥ 92% pass rate
- On failure: quarantine module, increase sample rate to 100%, trigger multi-model consensus loop
- Output: audit report in Markdown

- [ ] Gherkin: `docs/sdlc/features/E004-F024-quality-audit.feature`
- [ ] Depends on: F023 (output artifacts exist to audit)
- [ ] Classes: `SemanticValidationAgent`, `AuditSample`, `AuditReport`, `ConsensusValidator`
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
| US049 | F021 | must | E004 — Embabel Integration |
| US050 | F022 | must | E004 — Map-Reduce Pipeline |
| US051 | F023 | must | E004 — Output Artifacts |
| US052 | F024 | should | E004 — Quality Audit |

## Phase Dependency Graph

```
E003 — Phase 2 (Semantic Enrichment)             E004 — Phase 3 (Synthesis)
┌──────────────────────┐                         ┌──────────────────────────┐
│ F016: Planner        │                         │ F021: Embabel Integration│
│ (qualification rules)│                         │ (uncomment + configure)  │
└──────────┬───────────┘                         └────────────┬─────────────┘
           │                                                   │
           ▼                                                   │
┌──────────────────────┐                         ┌────────────▼─────────────┐
│ F017: Executor       │────── Phase Barrier ────│ F022: Map-Reduce Pipeline│
│ (@Async + Spring AI) │    CompletableFuture    │ (stream → group → reduce)│
└──────────┬───────────┘        .allOf(...)       └────────────┬─────────────┘
           │                                                   │
           ▼                                                   ▼
┌──────────────────────┐                         ┌──────────────────────────┐
│ F018: Orchestrator   │                         │ F023: Output Artifacts   │
│ (DAG + re-planning)  │                         │ (Markdown + JSON)        │
└──────────┬───────────┘                         └────────────┬─────────────┘
           │                                                   │
           ▼                                                   ▼
┌──────────────────────┐                         ┌──────────────────────────┐
│ F019: CLI Commands   │                         │ F024: Quality Audit      │
│ (plan / run /status) │                         │ (sampling + consensus)   │
└──────────────────────┘                         └──────────────────────────┘

F020: Test Suite Mining ── depends on F017 (executor framework)

Phase 13 (E002 — Language Extension Framework) ── POSTPONED / SCOPE REDUCED
```

## Modified / New Files Summary

### Modified (existing files changed):
| File | Change |
|------|--------|
| `Application.java` | Add `@EnableAsync` |
| `model/TaskStatus.java` | Add `AWAITING_HUMAN_REVIEW` |
| `shell/StatusCommand.java` | Add Phase 2 metrics columns (tokens, cost, enriched count) |
| `pom.xml` | Uncomment `embabel-agent-starter` dependency |
| `store/FindingType.java` | Add `SEMANTIC_ENRICHMENT` type |
| `model/ExecutionConfig.java` | Already has all required fields — no change needed |

### New files:
| Package | Files |
|---------|-------|
| `plan/` | `Phase2Planner`, `PlannerDecision`, `PlannerQualificationReason` |
| `executor/` | `SemanticExecutor`, `ExecutionFindingValidator`, `ContextBudgetCalculator`, `SimulationStub` |
| `orchestrator/` | `Phase2Orchestrator`, `EnrichmentDag`, `BranchState` |
| `executor/testmining/` | `TestFileMatcher`, `TestAssertionExtractor`, `PairedExecutionResolver` |
| `synthesis/` | `Phase3Orchestrator`, `SynthesisMapper`, `SynthesisReducer`, `ModuleSummary` |
| `synthesis/output/` | `MarkdownSpecWriter`, `SemanticManifestWriter`, `FunctionalFlow`, `FlowBuilder` |
| `synthesis/audit/` | `SemanticValidationAgent`, `AuditSample`, `AuditReport`, `ConsensusValidator` |
| `shell/` | `PlanCommand`, `RunCommand` |

## Verification Guide

- Unit: `mvn test`
- Manual dry-run: `run --dry-run --manifest project-manifest.yaml` — no API calls made
- Manual full: `run --manifest project-manifest.yaml` — needs `OPENAI_API_KEY` or `ANTHROPIC_API_KEY` env set
- JSON schema validation: enriched `ExecutionFinding` output validates against PRD §4 schema
- Phase 2 only: `run --manifest ...` then `status` — confirm per-task enrichment status + token counters
- Output inspection: `spec-output/` contains both `*.md` and `semantic_manifest.json`
