# US041 — Planner qualifies tasks for LLM enrichment

**Epic:** E003 — Semantic Enrichment
**Feature:** F016 — Planner
**Priority:** must | **Estimate:** 5 SP
**Depends on:** Phase 1 complete (SQLite populated) | **Blocks:** US043, US045, US046

> As a **Developer**, I want **the planner to automatically determine which files need LLM enrichment based on configurable rules**, so that **only files with genuine semantic gaps consume LLM tokens**.

### Acceptance Criteria

- [ ] A file with > `llm-unresolved-threshold` (default: 5) unresolved signatures qualifies (Rule 1)
- [ ] A Spring Data interface (CrudRepository, JpaRepository) qualifies regardless of unresolved count (Rule 2)
- [ ] A file with a stored procedure call flagged for LLM interpretation qualifies (Rule 3)
- [ ] A custom ConstraintValidator with an isValid body qualifies (Rule 4)
- [ ] A file with a paired test file containing assertions qualifies (Rule 5)
- [ ] A file with unresolved floating links (floating_links.resolved_status = 'PENDING') qualifies (Rule 6)
- [ ] A file with a scheduled task (@Scheduled annotation) qualifies (Rule 7)
- [ ] A file matching zero rules does NOT qualify
- [ ] A file matching multiple rules qualifies with all matching reasons recorded
- [ ] The planner makes zero LLM calls — pure query + rule engine
- [ ] Rules can be individually toggled via `llm-qualification-rules` array in manifest
- [ ] The unresolved threshold is configurable via `llm-unresolved-threshold` in manifest or CLI
- [ ] The `resolved` column in `execution_findings` accurately reflects per-finding resolution status (fixes `saveAllForTask` hardcoded `true`)

### INVEST Flags

- edge-cases
