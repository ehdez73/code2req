# US034 — Developer reviews structured trace output

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F014 — Structured Trace SQLite Persistence
**Priority:** should | **Estimate:** 3 SP
**Depends on:** US013, US030, US031, US033 | **Blocks:** nothing

> As a **Developer**, I want **the full structural trace (call graph edges, topic links, floating links) persisted in SQLite**, so that **I can query the trace programmatically and verify the accuracy of the scan**.

### Acceptance Criteria

- [ ] execution_findings table stores all call graph edges with type, source, target, and resolved flag
- [ ] topic_links table stores all resolved event producer-consumer pairs
- [ ] floating_links table stores all detected outbound HTTP calls
- [ ] metrics table stores counts of edges, topic links, and floating links per run
- [ ] All tables survive a scan restart via idempotent INSERT OR REPLACE
