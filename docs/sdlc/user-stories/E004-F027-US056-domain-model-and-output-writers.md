# US056 — Domain model and output writers

**Epic:** E004 — Agentic Functional Requirement Extraction
**Feature:** F027 — Domain Model + Output Writers
**Priority:** must | **Estimate:** 3 SP
**Depends on:** US051 (Embabel agent output) | **Blocks:** —

> As a **Developer**, I want **a clean domain model for Phase 3 output and writers that produce Markdown + JSON**, so that **the specification is structured, human-readable, and machine-verifiable**.

### Acceptance Criteria

- [ ] Domain records are defined: `EntryPoint`, `ExecutionFlow`, `FlowStep`, `FunctionalFlow`, `GherkinScenario`, `BusinessRule`, `EdgeCase`, `ExternalCall`, `NonFunctionalRequirement`, `FunctionalFeature`, `FlowRelationship`, `AmbiguityGap`, `OrphanedMethod`
- [ ] All domain records use Java record types with deterministic `equality` based on business keys
- [ ] `BusinessRule` has an optional `externalCall` field (`ExternalCall` with `httpMethod`, `url`, `timeoutMs`, `retryStrategy`, `fallbackBehavior`)
- [ ] `EdgeCase` has a `severity` field (`"LOW"` / `"MEDIUM"` / `"HIGH"`)
- [ ] `FunctionalFlow` has a `nonFunctionalRequirements` list (`NonFunctionalRequirement` with `category`, `requirement`, `sourceFile`)
- [ ] `MarkdownSpecWriter` produces `spec-output/spec.md` with table of contents, features, Gherkin scenarios, business rules (with per-rule source file and external call details), edge cases (with Severity column), Non-Functional Requirements section, cross-flow relationships, and Mermaid diagrams for complex flows
- [ ] Mermaid diagrams in spec.md use `graph TD` with method name + class label on each node
- [ ] `SemanticManifestWriter` produces `spec-output/semantic_manifest.json` matching JSON schema from PLAN-Phase3 §4.2
- [ ] The manifest includes `manifest_version: "3.0.0"`, `system_name`, `generated_at`, features, flows, acceptance criteria, business rules (with `external_call` sub-object), edge cases (with `severity` field), `non_functional_requirements` array per flow, cross-flow relationships, orphaned methods, and unresolved dependencies
- [ ] Extraction cache path is configurable via `code2req.output.spec-dir` and `code2req.output.extraction-cache-file` application properties
- [ ] Output is validated against the JSON schema before writing — schema failure sets Phase 3 marker to FAILED
- [ ] The output directory (`spec-output/`) is created if it does not exist
- [ ] Writers are pure Java (not agentic) — invoked by `Phase3Orchestrator` after agent completes

### INVEST Flags

- integration
- encapsulation
