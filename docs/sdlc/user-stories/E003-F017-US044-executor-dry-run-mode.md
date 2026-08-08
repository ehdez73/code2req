# US044 — Executor supports dry-run simulation mode
**Implementation Status:** implemented  

**Epic:** E003 — Semantic Enrichment
**Feature:** F017 — LLM Executor Framework
**Priority:** should | **Estimate:** 2 SP
**Depends on:** US043 | **Blocks:** US047

> As a **Developer**, I want **to run the enrichment pipeline in dry-run mode without making API calls**, so that **I can verify the orchestration and output format in CI without spending tokens**.

### Acceptance Criteria

- [ ] When `--dry-run` is active, no LLM calls are made
- [ ] Spring AI calls are intercepted by local stubs
- [ ] Stubs return deterministic static JSON matching the §4 schema
- [ ] The static JSON is persisted to `execution_findings` normally
- [ ] The pipeline completes end-to-end with no network calls

### INVEST Flags

- testability
