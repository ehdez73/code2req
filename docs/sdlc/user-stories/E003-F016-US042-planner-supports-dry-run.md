# US042 — Planner supports dry-run DAG view
**Implementation Status:** deferred  

**Epic:** E003 — Semantic Enrichment
**Feature:** F016 — Planner
**Priority:** should | **Estimate:** 1 SP
**Depends on:** US041 | **Blocks:** —

> As a **Developer**, I want **to preview which tasks will be enriched before spending tokens**, so that **I can verify the qualification rules are working as expected**.

### Acceptance Criteria

- [ ] The `plan` command displays qualified tasks grouped by scan target
- [ ] Each qualified task shows its qualification reason(s)
- [ ] Non-qualified tasks are listed without qualification reason
- [ ] Zero LLM calls are made during the plan command
- [ ] Custom `--llm-threshold` values are reflected in the DAG view

### INVEST Flags

- edge-cases
