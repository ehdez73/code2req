# US028 — Developer cleans all local data

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F006 — CLI Scan Orchestration
**Priority:** should | **Estimate:** 1 SP
**Depends on:** none | **Blocks:** nothing

> As a **Developer**, I want **a command that cleans all cached/local data**, so that **I can start a fresh scan without manually deleting files**.

### Acceptance Criteria

- [ ] Running `clean` with a populated task store deletes all rows from the SQLite tasks table
- [ ] Running `clean` deletes all rows from the SQLite execution_findings, topic_links, floating_links, and metrics tables
- [ ] Running `clean` removes the output spec directory
- [ ] Running `clean` with an empty store reports a graceful message with no errors
- [ ] The `--manifest` flag can be used to resolve custom output paths from a manifest file
- [ ] Running `clean` without `--manifest` falls back to default output paths
