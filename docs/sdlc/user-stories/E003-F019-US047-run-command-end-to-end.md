# US047 — Developer runs full Phase 2 + Phase 3 pipeline

**Epic:** E003 — Semantic Enrichment
**Feature:** F019 — CLI Commands
**Priority:** must | **Estimate:** 3 SP
**Depends on:** US041, US043, US045, E004 | **Blocks:** —

> As a **Developer**, I want **to run the complete pipeline from a single command**, so that **Phase 2 enrichment and Phase 3 functional requirement extraction happen automatically**.

### Acceptance Criteria

- [ ] `enrich --manifest path` orchestrates Phase 2 then Phase 3
- [ ] Phase 2: Planner → Executors → Orchestrator with barrier
- [ ] Phase 3: delegates to E004 (Embabel agent)
- [ ] `--llm-threshold 0` skips Phase 2 entirely
- [ ] `--dry-run` uses deterministic stubs, no API spend
- [ ] `status` shows Phase 2 counters: enriched tasks, tokens consumed, cost, pending/complete
- [ ] `status` shows Phase 3 counters: flow extraction rate, ambiguity gaps

### INVEST Flags

- integration
