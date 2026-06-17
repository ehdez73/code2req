# US052 — Tech Lead validates output quality via audit

**Epic:** E004 — Agentic Functional Requirement Extraction
**Feature:** F024 — Quality Audit
**Priority:** should | **Estimate:** 3 SP
**Depends on:** US051 (output artifacts exist) | **Blocks:** —

> As a **Tech Lead**, I want **a post-agent quality audit that validates extracted requirements against source files**, so that **I can trust that the specification is accurate before sharing it with stakeholders**.

### Acceptance Criteria

- [ ] `semantic-validation-sample-rate` (default 0.20) controls the sample size
- [ ] Sampled requirements are validated against raw source file content
- [ ] Pass threshold is ≥ 92% pass rate
- [ ] On failure: batch is flagged for human review, sample rate increases to 100%
- [ ] Audit report is written to `spec-output/audit-report.md`
- [ ] The audit is pure Java (not part of the Embabel agent) — runs post-agent

### INVEST Flags

- observability
- compliance
