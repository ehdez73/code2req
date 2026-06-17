# US050 — System aggregates Phase 1+2 data into CodebaseKnowledge

**Epic:** E004 — Agentic Functional Requirement Extraction
**Feature:** F022 — CodebaseKnowledge Builder + Phase 3 Orchestrator
**Priority:** must | **Estimate:** 3 SP
**Depends on:** US049 (Embabel setup), Phase 2 enriched data | **Blocks:** US051

> As a **Developer**, I want **the system to aggregate all Phase 1 and Phase 2 data into a single queryable domain model**, so that **the Embabel agent can reason about the codebase holistically without querying SQLite directly**.

### Acceptance Criteria

- [ ] `Phase3Orchestrator` queries SQLite for ALL Phase 1 findings (call graph, endpoints, topic links, floating links, database access)
- [ ] `Phase3Orchestrator` queries SQLite for ALL Phase 2 enriched `ExecutionFinding` records
- [ ] `CodebaseKnowledge` is assembled with `StructuralGraph`, `SemanticEnrichment`, and `LinkRegistry` sub-models
- [ ] `getFlowCandidates()` returns potential functional flows grouped by call chains
- [ ] `getCallersOf(target)` and `getCalleesOf(source)` provide directed graph traversal
- [ ] `findUnresolvedLinks()` returns floating/topic links still in PENDING status
- [ ] `CodebaseKnowledge` is passed to the Embabel agent as initial working memory
- [ ] After agent completes, the orchestrator invokes pure-Java output writers

### INVEST Flags

- integration
