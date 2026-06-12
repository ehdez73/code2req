# US014 — Developer persists scan state locally

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F005 — Index Output & SQLite Persistence
**Priority:** should | **Estimate:** 5 SP
**Depends on:** US006, US007, US008, US009, US010, US011, US012 | **Blocks:** US015

> As a **Developer**, I want **scan progress and results to be stored in a local database**, so that **I can resume interrupted scans and avoid re-processing unchanged files**.

### Acceptance Criteria

- [ ] SQLite database is created and initialized on first scan
- [ ] WAL journal mode and 5000ms busy timeout are set on connection
- [ ] Each processing task gets a deterministic ID computed from file path, content hash, and configuration
- [ ] Re-running against an unchanged workspace uses cached records within 60 seconds
