# US003 — Developer recovers gracefully from manifest errors

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F001 — Project Configuration & Manifest Parsing
**Priority:** should | **Estimate:** 2 SP
**Depends on:** US001 | **Blocks:** —

> As a **Developer**, I want **clear guidance when a scan target can't be resolved**, so that **I can fix the issue and re-run without confusion**.

### Acceptance Criteria

- [ ] A target path that doesn't exist logs the path and a suggested fix (e.g., "target path not found: ./missing-repo — verify the path exists")
- [ ] A target directory with no `.java` files logs a warning and is skipped without failing the entire scan
- [ ] The final summary reports how many targets succeeded, warned, and failed
