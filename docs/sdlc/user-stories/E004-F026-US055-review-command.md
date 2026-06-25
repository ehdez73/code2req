# US055 — Developer reviews and resolves AWAITING_HUMAN_REVIEW tasks

**Epic:** E004 — Agentic Functional Requirement Extraction
**Feature:** F026 — Review CLI Command
**Priority:** should | **Estimate:** 3 SP
**Depends on:** US051 (Phase 3 quarantines), F018 (Phase 2 hop depth quarantines) | **Blocks:** —
**Story:** US055

> As a **Developer**, I want **a dedicated `review` CLI command to inspect and resolve tasks and flows flagged as AWAITING_HUMAN_REVIEW**, so that **I can understand why the pipeline stalled and decide whether to accept the gap or reset for re-processing**.

### Acceptance Criteria

- [ ] `review list` shows all `AWAITING_HUMAN_REVIEW` tasks grouped by reason type (`HOP_DEPTH`, `STEPS_EXCEEDED`, `LOW_CONFIDENCE`)
- [ ] Each listing includes flow name, detail message, confidence score, and source file path
- [ ] `review show --task <id>` displays full context: task findings, quarantine reason JSON, source trace chain
- [ ] `review accept --task <id>` documents gap in spec Section 5, resets task to INDEXED, preserves `HUMAN_REVIEW_REASON` findings
- [ ] `review reset --task <id>` deletes `HUMAN_REVIEW_REASON` findings, resets to INDEXED for re-processing
- [ ] `review accept-all` batch accepts all quarantined flows
- [ ] `review reset-all` batch resets all for re-processing
- [ ] After accept, the flow appears in spec-output Section 5 as explicitly unresolved
- [ ] After reset, next `run` re-qualifies the task via planner (may re-quarantine if root cause persists)

### INVEST Flags

- observability
- usability
