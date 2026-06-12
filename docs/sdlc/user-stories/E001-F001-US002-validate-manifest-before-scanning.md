# US002 — Developer validates manifest before scanning

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F001 — Project Configuration & Manifest Parsing
**Priority:** should | **Estimate:** 2 SP
**Depends on:** US001 | **Blocks:** —

> As a **Developer**, I want **to verify my manifest is well-formed without running a full scan**, so that **I catch configuration issues early**.

### Acceptance Criteria

- [ ] Unknown or misspelled fields produce warnings but don't block processing
- [ ] Missing required fields produce an error with field name and expected value
- [ ] Invalid YAML syntax produces an error with line number and parse detail
- [ ] Validation completes within 1 second for a typical manifest
