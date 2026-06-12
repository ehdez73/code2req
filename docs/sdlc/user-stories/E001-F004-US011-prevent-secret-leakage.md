# US011 — Developer prevents secret leakage

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F004 — Secret Redaction & Exclude Filtering
**Priority:** must | **Estimate:** 5 SP
**Depends on:** US006, US007, US008, US009, US010 | **Blocks:** US013, US014

> As a **Tech Lead**, I want **hardcoded credentials in source files to be replaced with placeholders before any analysis output**, so that **sensitive information never leaves my machine**.

### Acceptance Criteria

- [ ] Common secret patterns (passwords, API keys, tokens, connection strings) are detected and replaced with `[REDACTED:type]` placeholders
- [ ] Original source files on disk remain untouched — redaction is strictly in-memory
- [ ] Redacted content is what gets stored in the analysis output
- [ ] Non-secret values are left unchanged
