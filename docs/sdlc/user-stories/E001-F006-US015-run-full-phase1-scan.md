# US015 — Developer runs a full Phase 1 scan

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F006 — CLI Scan Orchestration
**Priority:** must | **Estimate:** 8 SP
**Depends on:** US001, US002, US003, US006, US007, US008, US009, US010, US011, US012, US013, US014 | **Blocks:** US016

> As a **Developer**, I want **a single command that runs the complete Phase 1 pipeline**, so that **I can index my codebase without orchestrating individual steps**.

### Acceptance Criteria

- [ ] Running `scan` executes manifest parsing → dependency resolution → AST analysis → redaction/filtering → persistence in sequence
- [ ] Progress is reported per stage with file counts and elapsed time
- [ ] If a stage fails, the CLI reports which stage failed and exits with a non-zero code
- [ ] Successful scan produces both the JSON index and populated SQLite database
