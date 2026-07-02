# US045 — Orchestrator manages enrichment DAG

**Epic:** E003 — Semantic Enrichment
**Feature:** F018 — Orchestrator
**Priority:** must | **Estimate:** 5 SP
**Depends on:** US041, US043, `TaskStatus.AWAITING_HUMAN_REVIEW` | **Blocks:** US047

> As a **Developer**, I want **the orchestrator to manage the enrichment flow, submit tasks asynchronously, track completion, and handle dynamic re-planning**, so that **files are enriched efficiently and Phase 3 receives complete data**.

### Acceptance Criteria

- [ ] Processes planner decisions as a DAG
- [ ] Submits tasks via `CompletableFuture` + `taskExecutor.execute(work)` on the orchestrator thread pool
- [ ] Tracks progress via `CompletableFuture<ExecutionFinding>` responses
- [ ] Phase 2→3 barrier: `CompletableFuture.allOf(...)` blocks Phase 3 until ALL tasks complete
- [ ] Dynamic re-planning: discovered dependencies are registered as PENDING and processed
- [ ] Branch isolation: only the affected branch pauses during re-planning
- [ ] Max hop depth: configurable (default 3); exceeded → AWAITING_HUMAN_REVIEW
- [ ] Visited registry: thread-safe set of hashes prevents redundant evaluation
- [ ] Writes Phase 2 metrics (tokens consumed, cost estimate) on completion

### INVEST Flags

- concurrency
- error-handling
