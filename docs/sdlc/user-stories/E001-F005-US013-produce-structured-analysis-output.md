# US013 — Developer produces structured analysis output

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F005 — Index Output & SQLite Persistence
**Priority:** must | **Estimate:** 3 SP
**Depends on:** US006, US007, US008, US009, US010, US011, US012 | **Blocks:** US015

> As a **Developer**, I want **the scan to generate a structured index of all findings**, so that **I can review the results or feed them into downstream tools**.

### Acceptance Criteria

- [ ] All findings (components, endpoints, listeners, validators, scheduled tasks) are included in a single structured index
- [ ] Findings are stored as typed JSON blobs in SQLite
- [ ] All findings are persisted to the SQLite `execution_findings`, `topic_links`, and `floating_links` tables
- [ ] If the output path is unwritable, the CLI exits with a clear error message

### INVEST Flags

- vertical-slice
