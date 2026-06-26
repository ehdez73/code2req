# US052 — Tech Lead validates output quality via audit

**Epic:** E004 — Agentic Functional Requirement Extraction
**Feature:** F024 — Quality Audit (Simplified)
**Priority:** should | **Estimate:** 1 SP
**Depends on:** US051 (output artifacts exist) | **Blocks:** —

> As a **Tech Lead**, I want **the output artifacts to be structurally validated**, so that **I can trust the spec is well-formed and that quarantined flows are documented**.

### Acceptance Criteria

- [ ] `SemanticManifestWriter` validates its JSON output against the PRD §6.2 schema before persisting
- [ ] Schema validation failure sets the Phase 3 marker to FAILED (prevents incomplete output)
- [ ] Flows marked as AWAITING_HUMAN_REVIEW appear in spec Section 5 (Unresolved Dependencies)
- [ ] Quarantined flows have `review_required: true` in the semantic manifest
- [ ] The audit is pure Java (not part of the Embabel agent) — runs post-agent in the output writer

### INVEST Flags

- observability
- compliance
