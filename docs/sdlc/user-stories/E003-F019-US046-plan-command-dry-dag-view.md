# US046 — Developer views execution DAG with plan command

**Epic:** E003 — Semantic Enrichment
**Feature:** F019 — CLI Commands
**Priority:** must | **Estimate:** 2 SP
**Depends on:** US041 (Planner) | **Blocks:** —

> As a **Developer**, I want **to run the plan command and see which tasks will be enriched**, so that **I can verify the qualification rules and estimate token spend before running**.

### Acceptance Criteria

- [ ] `plan --manifest path` displays qualified tasks grouped by scan target
- [ ] Each qualified task shows its qualification reason
- [ ] Non-qualified tasks are listed with reason "NONE"
- [ ] Zero LLM calls are made during plan
- [ ] Zero SQLite mutations occur during plan (read-only)

### INVEST Flags

- observability
