# US051 — Embabel agent extracts functional requirements

**Epic:** E004 — Agentic Functional Requirement Extraction
**Feature:** F023 — Embabel Agent (Goals, Actions, Output)
**Priority:** must | **Estimate:** 8 SP
**Depends on:** US050 (CodebaseKnowledge), Embabel framework | **Blocks:** US052

> As a **Developer**, I want **an Embabel agent to dynamically analyze the enriched codebase knowledge and extract functional requirements**, so that **the output is a complete, traceable specification with business rules and edge cases**.

### Acceptance Criteria

- [ ] Agent runs in Focused mode with `CodebaseKnowledge` as input
- [ ] `AnalyzeFindings` groups `ExecutionFinding` records into candidate `FunctionalFlow` objects using call graph
- [ ] `ResolveAmbiguity` searches CodebaseKnowledge (and falls back to raw files) when a flow has knowledge gaps
- [ ] `CrossReferenceFloatingLinks` matches unresolved HTTP/topic links to known endpoints
- [ ] `QuarantineUnresolvable` flags flows exceeding guardrails as `AWAITING_HUMAN_REVIEW`
- [ ] `SynthesizeFunctionalSpec` produces the final specification
- [ ] Guardrails are respected: `max-investigation-steps-per-flow` (5), `max-tokens-per-run` (500000), `ambiguity-confidence-threshold` (0.7)
- [ ] GOAP dynamically chains actions based on goal completion — not a fixed pipeline
- [ ] Output produces both `spec-output/*.md` (PRD §6.1 format) and `semantic_manifest.json` (full traceability)

### INVEST Flags

- integration
- agentic
