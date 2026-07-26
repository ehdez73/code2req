# US051 — Embabel agent extracts functional requirements

**Epic:** E004 — Agentic Functional Requirement Extraction
**Feature:** F023 — Embabel Agent (Entry-Point-Driven Extraction)
**Priority:** must | **Estimate:** 8 SP
**Depends on:** US050 (CodebaseKnowledge), Embabel framework | **Blocks:** US056, US057

> As a **Developer**, I want **an Embabel agent to discover entry points, trace execution flows through the call graph, and extract functional requirements**, so that **the output is a complete, traceable specification with user stories and Gherkin acceptance criteria organized by features**.

### Acceptance Criteria

- [ ] `DiscoverEntryPoints` scans CodebaseKnowledge for all entry points (HTTP endpoints, @Scheduled, @KafkaListener, @RabbitListener, @JmsListener, @EventListener)
- [ ] Trivial endpoints (actuator, health, metrics, swagger) are filtered out
- [ ] Each entry point receives a priority score (Phase 2 enrichment + complexity + user-facing + test file)
- [ ] `TraceFlow` follows call graph edges from the highest-priority unscheduled entry point
- [ ] Flow steps trace from entry point through services to repositories with adaptive depth
- [ ] Sub-chain caching reuses already-traced service chains for related entry points
- [ ] Unresolved calls (external services, third-party) are recorded in the flow
- [ ] `AnalyzeFlow` extracts user stories, Gherkin scenarios, business rules, and edge cases
- [ ] Progressive disclosure: MINIMAL, STANDARD, or FULL output based on complexity score
- [ ] GroupFlows clusters related flows into features using semantic similarity from Phase 2 enrichment
- [ ] `CrossReferenceFlows` detects inter-flow dependencies (DELEGATES_TO, PUBLISHES_EVENT, CONSUMES_EVENT)
- [ ] Orphaned methods unreachable from any entry point are flagged as dead code or missing entry points
- [ ] `SynthesizeSpec` produces both spec-output/spec.md and spec-output/semantic_manifest.json
- [ ] `QuarantineFlow` flags flows exceeding guardrails as AWAITING_HUMAN_REVIEW
- [ ] Guardrails are respected: `max-flow-depth` (5), `max-tokens-per-run` (500000), `ambiguity-confidence-threshold` (0.7)
- [ ] GOAP dynamically chains actions based on goal completion — not a fixed pipeline
- [ ] Output produces both Markdown spec (PRD §6.1 format) and semantic_manifest.json (PRD §6.2)
- [ ] Phase 3 crash marker (`__phase3_marker__`) with lifecycle PENDING -> ENRICHING -> ENRICHED/FAILED
### INVEST Flags

- integration
- agentic
