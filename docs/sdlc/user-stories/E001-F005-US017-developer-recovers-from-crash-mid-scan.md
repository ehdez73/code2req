# US017 — Developer recovers from crash mid-scan

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F005 — Index Output & SQLite Persistence
**Priority:** should | **Estimate:** 5 SP
**Depends on:** US014 | **Blocks:** —

> As a **Developer**, I want **the tool to detect and recover from an interrupted scan**, so that **I don't lose progress or re-process already-completed files after a crash**.

### Acceptance Criteria

- [ ] On startup, the tool detects tasks stuck in `RUNNING` state (orphans) in the SQLite task store
- [ ] Orphaned tasks are reverted from `RUNNING` to `PENDING` for re-processing
- [ ] Existing valid JSON fragments from orphaned tasks are inspected for schema compliance; compliant fragments transition to `SUCCESS` (cache recovery)
- [ ] The dependency graph is rebuilt from the updated database state after orphan reconciliation
- [ ] Recovery completes within 30 seconds for a typical interrupted workspace
