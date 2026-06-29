# Phase 3 — Actionable TODO Checklist

> **Source:** `docs/PLAN-Phase3.md` · **Status:** Draft · **Updated:** 2026-06-26

---

## P — Prerequisites (F021 Embabel Setup)test

- [x] P.1 `embabel-agent-starter` dependency in `pom.xml`
- [x] P.2 `embabel-agent-starter-dockermodels` dependency added
- [x] P.3 `embabel.models.default-llm` / `cheapest` / `best` in `application.properties`
- [x] P.4 `mvn compile` succeeds with Embabel on classpath
- [x] P.5 Embabel initializes at application startup without errors
- [x] P.6 `HelloAgent` + `HelloCommand` exist as smoke test

---

## 2 — F022 CodebaseKnowledge Query Helpers (US050)

> **New files:** `EntryPoint.java`, `EntryPointType.java` in `extraction/domain/model/`, `MethodIdentifier.java` in `extraction/`
> **Modified files:** `StructuralGraph.java` (+entry point fields/methods), `Phase3Orchestrator.java` (+ep loading), `CodebaseKnowledge.java` (+delegations), `SemanticEnrichment.java` (+test insight helpers)
> **Phase 2 fix:** `Phase2Orchestrator.java` now populates `Task.pairedTestPath` (was previously discarded)

- [x] 2.1 Add `getEntryPoints()` — returns all entry point types (HTTP, SCHEDULED, KAFKA, RABBITMQ, ACTIVEMQ, EVENT_LISTENER). Maps all six record types to a unified `EntryPoint` record. `StructuralGraph.getEntryPoints()` + `CodebaseKnowledge.getEntryPoints()` delegation.
- [x] 2.2 Add `getCallersOf(target)` / `getCalleesOf(source)` — directed graph traversal (already existed)
- [x] 2.3 Add `getAllKnownMethods()` — for orphaned method detection. Collects methods from entry point records (ScheduledTaskInfo, KafkaInfo, RabbitMqInfo, ActiveMqInfo, EventListenerInfo) that have explicit method names. NOTE: `ComponentInfo` has no method-level data — methods from components are discovered during tracing, not from this query.
- [x] 2.4 Add `findUnresolvedLinks()` — returns PENDING floating/topic links (already existed)
- [x] 2.5 Add `getComponentsByType(type)` — filter by annotation type (already existed)
- [ ] 2.6 Pass CodebaseKnowledge as Embabel blackboard initial memory — infrastructure prepared (AgentPlatform injection point in Phase3Orchestrator; full impl in F023)

---

## 3 — F023 Embabel Agent (US051)

### 3.1 Domain Records (`extraction/domain/model/`)


- [x] 3.1.1 `EntryPoint` record + `EntryPointType` enum (§3.1) — enhanced with id, httpMethod, path, trivial, pathVariables, schedule, topicOrQueue
- [x] 3.1.2 `ExecutionFlow` record + `FlowStatus` enum (§3.2)
- [x] 3.1.3 `FlowStep` record + `FlowStepComponentType` enum (§3.3)
- [x] 3.1.4 `FunctionalFlow` record + `ComplexityLevel` enum (§3.4)
- [x] 3.1.5 `GherkinScenario` record (List\<String\> givenSteps, whenSteps, thenSteps) (§3.5)
- [x] 3.1.6 `BusinessRule` record (§3.6)
- [x] 3.1.7 `EdgeCase` record (§3.7)
- [x] 3.1.8 `FunctionalFeature` record (§3.8)
- [x] 3.1.9 `FlowRelationship` record + `FlowRelationshipType` enum (§3.9)
- [x] 3.1.10 `AmbiguityGap` record + `GapReason` enum (§3.10)
- [x] 3.1.11 `OrphanedMethod` record (§3.11)
- [x] `mvn test` succeeds

### 3.2 GOAP Actions (`extraction/agent/`)

- [x] 3.2.1 **DiscoverEntryPoints** — query CodebaseKnowledge, filter trivial, score by priority (§2.4), sort, detect orphans (pre: KNOWLEDGE_LOADED, post: ENTRY_POINTS_DISCOVERED)
- [x] 3.2.2 **TraceFlow** — pop highest-priority entry point, follow call graph edges, build FlowSteps, sub-chain cache (§2.6), adaptive depth (max 5) (pre: ENTRY_POINTS_DISCOVERED, post: FLOW_TRACED / ALL_FLOWS_TRACED)
- [x] 3.2.3 **AnalyzeFlow** — check Phase 2 enrichment per FlowStep, fallback to LLM inference, extract user story + Gherkin + rules + edge cases, progressive disclosure (§2.7) (pre: FLOW_TRACED, post: FLOW_ANALYZED)
- [x] 3.2.4 **GroupFlows** — cluster by semantic similarity (§2.8), merge into FunctionalFeatures, assign names (pre: FLOW_ANALYZED / ALL_FLOWS_TRACED, post: FLOWS_GROUPED)
- [x] 3.2.5 **CrossReferenceFlows** — match floating HTTP links + topic publications to endpoints, record FlowRelationships (pre: FLOWS_GROUPED, post: CROSS_REFS_RESOLVED)
- [x] 3.2.6 **QuarantineFlow** — record reason (STEPS_EXCEEDED / LOW_CONFIDENCE / HOP_DEPTH), set confidence, add to quarantine list (pre: FLOW_TRACED, post: FLOW_QUARANTINED)
- [x] 3.2.7 **SynthesizeSpec** — invoke MarkdownSpecWriter + SemanticManifestWriter, validate output (pre: FLOWS_GROUPED + CROSS_REFS_RESOLVED, post: SPEC_SYNTHESIZED)
- [x] `mvn test` succeeds


### 3.3 Agent Assembly

- [x] 3.3.1 Create GOAP agent class (`@Agent`) with all 7 actions wired
- [x] 3.3.2 Define all 9 world-state conditions (§2.2)
- [x] 3.3.3 Implement sub-chain cache (§2.6)
- [x] 3.3.4 Implement flow priority scoring formula (§2.4)
- [x] 3.3.5 Implement orphaned method detection (§2.5)
- [x] 3.3.6 Implement Mermaid diagram generation (for ComplexityLevel.FULL flows)
- [x] 3.3.7 Implement Phase 3 crash marker: PENDING → ENRICHING → ENRICHED / FAILED
- [x] 3.3.8 Add `--force-phase3` flag for re-execution
- [ ] `mvn test` succeeds

---

## 4 — F026 Review CLI Commands (US055)

- [ ] 4.1 `review list` — shows AWAITING_HUMAN_REVIEW tasks grouped by reason type
- [ ] 4.2 `review show --task <id>` — full quarantine context with reason JSON + source trace chain
- [ ] 4.3 `review accept --task <id>` — preserves HUMAN_REVIEW_REASON findings, sets task to INDEXED
- [ ] 4.4 `review reset --task <id>` — deletes HUMAN_REVIEW_REASON findings, sets task to INDEXED
- [ ] 4.5 `review accept-all` — batch accept all quarantined flows
- [ ] 4.6 `review reset-all` — batch reset all for re-processing
- [ ] 4.7 After accept, flow appears in spec Section 5 (Unresolved Dependencies)
- [ ] 4.8 After reset, next `run` re-qualifies via planner
- [ ] `mvn compile` succeeds


---

## 5 — F027 Output Writers (US056)

### 5.1 MarkdownSpecWriter

- [ ] 5.1.1 Generate `spec-output/spec.md` with table of contents
- [ ] 5.1.2 Render feature sections: user story + description
- [ ] 5.1.3 Render execution flow (Mermaid graph TD diagram for ComplexityLevel.FULL)
- [ ] 5.1.4 Render business rules table (ID, Rule, Precondition, Postcondition, Error Behavior)
- [ ] 5.1.5 Render edge cases table (Scenario, Business Consequence)
- [ ] 5.1.6 Render Gherkin acceptance criteria per feature
- [ ] 5.1.7 Render Cross-Flow Relationships section
- [ ] 5.1.8 Render Unresolved Dependencies section (quarantined flows)
- [ ] 5.1.9 Render Orphaned Methods section
- [ ] 5.1.10 Render traceability table (Component, File, Lines)

### 5.2 SemanticManifestWriter

- [ ] 5.2.1 Generate `spec-output/semantic_manifest.json` conforming to §4.2 schema
- [ ] 5.2.2 Emit `manifest_version: "3.0.0"`, `system_name`, `generated_at`
- [ ] 5.2.3 Emit `features[]` with `feature_id`, `name`, `description`, `flows[]`
- [ ] 5.2.4 Each flow: `flow_id`, `entry_point`, `steps[]`, `user_story`, `acceptance_criteria[]`
- [ ] 5.2.5 Each flow: `business_rules[]`, `edge_cases[]`, `complexity`, `mermaid_diagram`
- [ ] 5.2.6 Quarantined flows: `review_required: true` + `unresolved_reason` object
- [ ] 5.2.7 Root-level `cross_flow_relationships[]` array
- [ ] 5.2.8 Root-level `orphaned_methods[]` array
- [ ] 5.2.9 Validate output against JSON schema before persisting; failure → Phase 3 FAILED
- [ ] 5.2.10 Auto-create `spec-output/` directory if missing

---

## 6 — F028 Interactive Mode (US057)

- [ ] 6.1 Define `UserInteractionService` SPI: `ask(prompt, context) → String`, `confirm(message) → boolean`, `select(options, prompt) → String`
- [ ] 6.2 Implement `NoOpUserInteractionService` — default, headless, returns null/true/null
- [ ] 6.3 Implement `InteractiveUserInteractionService` — stdin/stdout terminal prompts
- [ ] 6.4 Wire agent actions to call SPI when confidence < `ambiguity-confidence-threshold` (0.7)
- [ ] 6.5 Create `AmbiguityGap` on every ambiguity event (for audit trail)
- [ ] 6.6 Create `user_responses` SQLite table (session_id, question, answer, created_at)
- [ ] 6.7 Add `run --interactive` flag to opt in (default: headless)

---

## 7 — Tests

- [ ] 7.1 Unit (domain) — record construction + validation, standard JUnit
- [ ] 7.2 Unit (actions) — each `@Action` via `IntegrationTestUtils.dummyProcessContext()` + real blackboard
- [ ] 7.3 Unit (writers) — Markdown + JSON output compared against expected templates
- [ ] 7.4 Integration (dry-run) — end-to-end through RunCommand with `SimulationStub`, zero API spend
- [ ] 7.5 Integration (full) — real LLM calls via OpenRouter with test manifest

---

## 8 — Documentation

- [ ] 8.1 `docs/PRD.md` — rewrite sections 2.3, 3.8, 6.1, 6.2
- [ ] 8.2 Javadoc on all public API methods in `extraction/` package

### SDLC Artifacts (all created)

- [x] 8.3 `docs/sdlc/features/E004-F021-embabel-setup.feature`
- [x] 8.4 `docs/sdlc/features/E004-F022-codebase-knowledge.feature`
- [x] 8.5 `docs/sdlc/features/E004-F023-embabel-agent.feature`
- [x] 8.6 `docs/sdlc/features/E004-F024-quality-audit.feature`
- [x] 8.7 `docs/sdlc/features/E004-F026-review-command.feature`
- [x] 8.8 `docs/sdlc/features/E004-F027-domain-model-output-writers.feature`
- [x] 8.9 `docs/sdlc/features/E004-F028-interactive-mode.feature`
- [x] 8.10 All 7 user story `.md` files in `docs/sdlc/user-stories/E004-*`
- [x] 8.11 `docs/sdlc/sdlc-context.json` — registered F026, F027, F028 + US055, US056, US057
- [x] 8.12 `docs/PLAN-Phase3.md` — added F026, US055 + SDLC artifact cross-references
- [x] 8.13 `docs/PLAN-Phase3-steps.md` — this file

---

## Execution Order

```
1.  F022:     2.1 → 2.2 → 2.3 → 2.4 → 2.5 → 2.6
2.  F023.1:   3.1.1 → 3.1.2 → ... → 3.1.11
3.  F023.2:   3.2.1 → 3.2.2 → 3.2.3 → 3.2.4 → 3.2.5 → 3.2.6 → 3.2.7
4.  F023.3:   3.3.1 → 3.3.2 → ... → 3.3.8
5.  F027.1:   5.1.1 → ... → 5.1.10
6.  F027.2:   5.2.1 → ... → 5.2.10
7.  F026:     4.1 → 4.2 → ... → 4.8
8.  F028:     6.1 → 6.2 → ... → 6.7
9.  Tests:    7.1 → 7.2 → 7.3 → 7.4 → 7.5
10. Docs:     8.1 → 8.2
```


Always verify with `mvn clean test` 


---

## Progress

| Feature | Steps | Done |
|---------|-------|------|
| P Prerequisites | 8 | 6 |
| 2 F022 Query Helpers | 6 | 5 |
| 3.1 F023 Domain Records | 11 | 11 |
| 3.2 F023 GOAP Actions | 7 | 7 |
| 3.3 F023 Agent Assembly | 8 | 8 |
| 4 F026 Review CLI | 8 | 0 |
| 5.1 F027 MarkdownSpecWriter | 10 | 0 |
| 5.2 F027 SemanticManifestWriter | 10 | 0 |
| 6 F028 Interactive Mode | 7 | 0 |
| 7 Tests | 5 | 0 |
| 8.1–8.2 Docs (pending) | 2 | 0 |
| 8.3–8.13 SDLC Artifacts | 11 | 11 |
