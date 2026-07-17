# US022 — Developer adds a parser without changing the pipeline

**Epic:** E002 — Language Extension Framework
**Feature:** F009 — Shared Pipeline Integration
**Priority:** should | **Estimate:** 5 SP
**Status:** Postponed — E002 is formally de-scoped. This story is retained for future reference.
**Depends on:** US018, US021 | **Blocks:** —

> As a **Developer**, I want **to add a new language parser without modifying the core pipeline (redaction, output, orchestration)**, so that **the tool can be extended cheaply and safely**.

### Acceptance Criteria

- [ ] The existing redaction (US011) and exclude filtering (US012) work with any parser's output without modification
- [ ] The JSON index output (US013) includes language-agnostic fields with parser-specific metadata in optional fields
- [ ] The SQLite persistence (US014) stores parser output identically regardless of source language
- [ ] The scan orchestration (US015) treats all parser outputs uniformly
