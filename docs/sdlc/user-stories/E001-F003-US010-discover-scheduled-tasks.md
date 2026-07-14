# US010 — Developer discovers scheduled tasks

**Epic:** E001 — Deterministic Multi-Language Indexing
**Feature:** F003 — Java Source AST Analysis
**Priority:** should | **Estimate:** 3 SP
**Depends on:** US006 | **Blocks:** US011

> As a **Developer**, I want **the tool to capture `@Scheduled` methods with their cron expressions and fixed-rate/fixed-delay configurations**, so that **I can understand all time-triggered behavior in the system**.

### Acceptance Criteria

- [ ] Methods annotated with `@Scheduled` are identified with their scheduling parameters (cron, fixedRate, fixedDelay, initialDelay)
- [ ] Each scheduled method is linked to its owning component
- [ ] The scheduling parameters are extracted as raw text values
- [ ] Scheduled tasks are categorized as: cron-based, fixed-rate, fixed-delay
