# US005 — Developer operates without Maven available

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F002 — Dependency Graph Resolution
**Priority:** should | **Estimate:** 2 SP
**Depends on:** US001 | **Blocks:** —

> As a **Developer**, I want **the scan to proceed when dependency resolution is not available**, so that **I can still analyze codebases without Maven installed**.

### Acceptance Criteria

- [ ] When Maven is absent, `pom.xml` is missing, or resolution fails, CLI logs a clear warning with the reason
- [ ] Standalone heuristic mode activates automatically — no manual flag required
- [ ] AST analysis proceeds with best-effort type resolution
- [ ] The final summary includes a note that dependency metadata was unavailable
